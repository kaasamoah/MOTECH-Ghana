package org.motechproject.server.service.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.motechproject.server.model.ExpectedEncounter;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;

public class ExpectedANCEncounterSchedule extends ExpectedEncounterSchedule {

	private static Log log = LogFactory
			.getLog(ExpectedANCEncounterSchedule.class);

	private String obsConceptName;

	@Override
	protected void performScheduleUpdate(Patient patient, Date date) {

		Date referenceDate = getReferenceDate(patient);
		if (referenceDate == null) {
			// Handle missing reference date as failed requirement
			removeExpectedCare(patient);
			return;
		}
		log.debug("Performing " + name + " schedule update: patient: "
				+ patient.getPatientId());

		List<Encounter> encounterList = registrarBean.getEncounters(patient,
				encounterTypeName, referenceDate);
		List<ExpectedEncounter> expectedEncounterList = registrarBean
				.getExpectedEncounters(patient, name);

		EncounterPredicate encounterPredicate = new EncounterPredicate();
		ExpectedEncounterDatePredicate expectedEncounterPredicate = new ExpectedEncounterDatePredicate();

		// Create ExpectedEncounter using next ANC date Obs in latest encounter
		// No encounters satisfy, since this is the most recent encounter
		if (!encounterList.isEmpty() && obsConceptName != null) {
			Date nextANCDate = null;
			Encounter latestEncounter = encounterList
					.get(encounterList.size() - 1);
			Set<Obs> encounterObsSet = latestEncounter.getAllObs();
			for (Obs encounterObs : encounterObsSet) {
				if (encounterObs.getConcept().isNamed(obsConceptName)) {
					nextANCDate = encounterObs.getValueDatetime();
					break;
				}
			}
			if (nextANCDate != null) {
				Date minDate = latestEncounter.getEncounterDatetime();
				Date lateDate = calculateDate(nextANCDate, lateValue,
						latePeriod);

				// Only create if there is no ExpectedEncounter already
				// scheduled with the same date
				expectedEncounterPredicate.setMinDate(minDate);
				expectedEncounterPredicate.setDueDate(nextANCDate);
				expectedEncounterPredicate.setLateDate(lateDate);
				ExpectedEncounter expectedEncounter = getEventExpectedEncounter(
						expectedEncounterList, expectedEncounterPredicate);

				if (expectedEncounter == null) {
					registrarBean.createExpectedEncounter(patient,
							encounterTypeName, minDate, nextANCDate, lateDate,
							null, name, name);
				}
			}
		}

		for (ExpectedEncounter expectedEncounter : expectedEncounterList) {
			Date minDate = expectedEncounter.getMinEncounterDatetime();
			Date maxDate = expectedEncounter.getMaxEncounterDatetime();

			// Find Encounter satisfying expected
			encounterPredicate.setMinDate(minDate);
			encounterPredicate.setMaxDate(maxDate);
			Encounter eventEncounter = getEventEncounter(encounterList,
					encounterPredicate);

			boolean eventExpired = maxDate != null && date.after(maxDate);

			if (eventEncounter != null) {
				// Remove existing satisfied ExpectedEncounter
				expectedEncounter.setEncounter(eventEncounter);
				expectedEncounter.setVoided(true);
				registrarBean.saveExpectedEncounter(expectedEncounter);
			} else if (eventExpired) {
				expectedEncounter.setVoided(true);
				registrarBean.saveExpectedEncounter(expectedEncounter);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected ExpectedEncounter getEventExpectedEncounter(
			List<ExpectedEncounter> expectedEncounterList,
			ExpectedEncounterPredicate expectedEncounterPredicate) {
		List<ExpectedEncounter> eventExpectedEncounter = (List<ExpectedEncounter>) CollectionUtils
				.select(expectedEncounterList, expectedEncounterPredicate);
		if (!eventExpectedEncounter.isEmpty()) {
			if (eventExpectedEncounter.size() > 1) {
				log.debug("Multiple matches for expected care : "
						+ eventExpectedEncounter.size());
			}
			// List is ascending by due date, remove first match
			ExpectedEncounter expectedEncounter = eventExpectedEncounter.get(0);
			// Unlike super class, Do not remove match from list
			return expectedEncounter;
		}
		return null;
	}

	@Override
	protected Date getReferenceDate(Patient patient) {
		// Calculate estimated pregnancy start date as 9 months before estimated
		// due date
		Date dueDate = registrarBean.getActivePregnancyDueDate(patient
				.getPatientId());
		if (dueDate != null) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(dueDate);
			calendar.add(Calendar.MONTH, -9);
			return calendar.getTime();
		}
		return null;
	}

	public String getObsConceptName() {
		return obsConceptName;
	}

	public void setObsConceptName(String obsConceptName) {
		this.obsConceptName = obsConceptName;
	}

}