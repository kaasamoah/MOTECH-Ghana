package org.motechproject.server.svc.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.motechproject.server.event.MessageProgram;
import org.motechproject.server.messaging.MessageNotFoundException;
import org.motechproject.server.model.ExpectedEncounter;
import org.motechproject.server.model.ExpectedObs;
import org.motechproject.server.model.GeneralPatientEncounter;
import org.motechproject.server.model.HIVStatus;
import org.motechproject.server.model.Message;
import org.motechproject.server.model.MessageDefinition;
import org.motechproject.server.model.MessageProgramEnrollment;
import org.motechproject.server.model.MessageStatus;
import org.motechproject.server.model.ScheduledMessage;
import org.motechproject.server.model.TroubledPhone;
import org.motechproject.server.model.WhoRegistered;
import org.motechproject.server.omod.ContextService;
import org.motechproject.server.omod.MotechIdVerhoeffValidator;
import org.motechproject.server.omod.MotechService;
import org.motechproject.server.omod.tasks.MessageProgramUpdateTask;
import org.motechproject.server.omod.tasks.NotificationTask;
import org.motechproject.server.omod.tasks.NurseCareMessagingTask;
import org.motechproject.server.svc.BirthOutcomeChild;
import org.motechproject.server.svc.OpenmrsBean;
import org.motechproject.server.svc.RegistrarBean;
import org.motechproject.server.util.GenderTypeConverter;
import org.motechproject.server.util.MotechConstants;
import org.motechproject.server.ws.WebServiceModelConverter;
import org.motechproject.server.ws.WebServiceModelConverterImpl;
import org.motechproject.ws.BirthOutcome;
import org.motechproject.ws.Care;
import org.motechproject.ws.ContactNumberType;
import org.motechproject.ws.DayOfWeek;
import org.motechproject.ws.Gender;
import org.motechproject.ws.HIVResult;
import org.motechproject.ws.HowLearned;
import org.motechproject.ws.InterestReason;
import org.motechproject.ws.LogType;
import org.motechproject.ws.MediaType;
import org.motechproject.ws.NameValuePair;
import org.motechproject.ws.RegistrantType;
import org.motechproject.ws.RegistrationMode;
import org.motechproject.ws.mobile.MessageService;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptDescription;
import org.openmrs.ConceptName;
import org.openmrs.ConceptNameTag;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.RelationshipType;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.UserService;
import org.openmrs.module.idgen.IdentifierSource;
import org.openmrs.module.idgen.LogEntry;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.scheduler.SchedulerException;
import org.openmrs.scheduler.SchedulerService;
import org.openmrs.scheduler.TaskDefinition;
import org.openmrs.util.AttributableDate;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.transaction.annotation.Transactional;

/**
 * An implementation of the RegistrarBean interface, implemented using a mix of
 * OpenMRS and module defined services.
 */
public class RegistrarBeanImpl implements RegistrarBean, OpenmrsBean {

	private static Log log = LogFactory.getLog(RegistrarBeanImpl.class);

	private ContextService contextService;
	public MessageService mobileService;
	private Map<String, MessageProgram> messagePrograms;

	public void setContextService(ContextService contextService) {
		this.contextService = contextService;
	}

	public void setMobileService(MessageService mobileService) {
		this.mobileService = mobileService;
	}

	public void setMessagePrograms(Map<String, MessageProgram> messagePrograms) {
		this.messagePrograms = messagePrograms;
	}

	public MessageProgram getMessageProgram(String programName) {
		return messagePrograms.get(programName);
	}

	@Transactional
	public void registerClinic(String name, Integer parentId) {

		LocationService locationService = contextService.getLocationService();

		Location clinic = new Location();
		clinic.setName(name);
		clinic.setDescription("A Ghana Clinic Location");
		locationService.saveLocation(clinic);

		if (parentId != null) {
			Location parent = locationService.getLocation(parentId);
			parent.addChildLocation(clinic);
			locationService.saveLocation(parent);

			copyParentHierarchy(parent, clinic);
			locationService.saveLocation(clinic);
		}
	}

	@Transactional
	public void registerNurse(String name, String nurseId, String phoneNumber,
			String clinicName) {
		LocationService locationService = contextService.getLocationService();
		Location clinic = locationService.getLocation(clinicName);

		registerNurse(name, nurseId, phoneNumber, clinic);
	}

	@Transactional
	public void registerNurse(String name, String nurseId, String phoneNumber,
			Integer clinicId) {
		LocationService locationService = contextService.getLocationService();
		Location clinic = locationService.getLocation(clinicId);

		registerNurse(name, nurseId, phoneNumber, clinic);
	}

	private void registerNurse(String name, String nurseId, String phoneNumber,
			Location clinic) {

		UserService userService = contextService.getUserService();
		PersonService personService = contextService.getPersonService();

		// User creating other users must have atleast the Privileges to be
		// given

		// TODO: Create nurses as person and use same User for all actions ?
		User nurse = new User();
		nurse.setUsername(name);

		// TODO: Nurse gender hardcoded, required for Person
		nurse.setGender(GenderTypeConverter.toOpenMRSString(Gender.FEMALE));

		nurse.addName(personService.parsePersonName(name));

		PersonAttributeType nurseIdAttrType = getNurseIdAttributeType();
		nurse.addAttribute(new PersonAttribute(nurseIdAttrType, nurseId));

		// Must be created previously through API or UI to lookup
		PersonAttributeType phoneNumberAttrType = getPhoneNumberAttributeType();
		nurse
				.addAttribute(new PersonAttribute(phoneNumberAttrType,
						phoneNumber));

		// TODO: Create Nurse role with proper privileges
		Role role = userService.getRole(OpenmrsConstants.PROVIDER_ROLE);
		nurse.addRole(role);

		userService.saveUser(nurse, "password");
	}

	@Transactional
	public Patient registerPatient(User nurse, Integer facilityId, Date date,
			RegistrationMode registrationMode, Integer motechId,
			RegistrantType registrantType, String firstName, String middleName,
			String lastName, String preferredName, Date dateOfBirth,
			Boolean estimatedBirthDate, Gender sex, Boolean insured,
			String nhis, Date nhisExpires, Patient mother, Integer community,
			String address, Integer phoneNumber, Date expDeliveryDate,
			Boolean deliveryDateConfirmed, Integer gravida, Integer parity,
			Boolean enroll, Boolean consent, ContactNumberType ownership,
			MediaType format, String language, DayOfWeek dayOfWeek,
			Date timeOfDay, InterestReason reason, HowLearned howLearned,
			Integer messagesStartWeek) {

		return registerPatient(registrationMode, motechId, registrantType,
				firstName, middleName, lastName, preferredName, dateOfBirth,
				estimatedBirthDate, sex, insured, nhis, nhisExpires, mother,
				community, address, phoneNumber, expDeliveryDate,
				deliveryDateConfirmed, gravida, parity, enroll, consent,
				ownership, format, language, dayOfWeek, timeOfDay, reason,
				howLearned, messagesStartWeek);
	}

	@Transactional
	public Patient registerPatient(RegistrationMode registrationMode,
			Integer motechId, RegistrantType registrantType, String firstName,
			String middleName, String lastName, String preferredName,
			Date dateOfBirth, Boolean estimatedBirthDate, Gender sex,
			Boolean insured, String nhis, Date nhisExpires, Patient mother,
			Integer community, String address, Integer phoneNumber,
			Date expDeliveryDate, Boolean deliveryDateConfirmed,
			Integer gravida, Integer parity, Boolean enroll, Boolean consent,
			ContactNumberType ownership, MediaType format, String language,
			DayOfWeek dayOfWeek, Date timeOfDay, InterestReason reason,
			HowLearned howLearned, Integer messagesStartWeek) {

		PatientService patientService = contextService.getPatientService();
		PersonService personService = contextService.getPersonService();

		// TODO: Handle storing community and hierarchy
		Patient patient = createPatient(motechId, firstName, middleName,
				lastName, preferredName, dateOfBirth, estimatedBirthDate, sex,
				insured, nhis, nhisExpires, community, address, phoneNumber,
				ownership, format, language, dayOfWeek, timeOfDay, howLearned,
				reason);

		patient = patientService.savePatient(patient);

		if (mother != null) {
			RelationshipType parentChildRelationshipType = personService
					.getRelationshipTypeByName(MotechConstants.RELATIONSHIP_TYPE_PARENT_CHILD);
			Relationship motherRelationship = new Relationship(mother, patient,
					parentChildRelationshipType);
			personService.saveRelationship(motherRelationship);
		}

		Integer pregnancyDueDateObsId = null;
		if (registrantType == RegistrantType.PREGNANT_MOTHER) {
			pregnancyDueDateObsId = registerPregnancy(patient, expDeliveryDate,
					deliveryDateConfirmed, gravida, parity, null);
		}

		enrollPatient(registrantType, patient, enroll, consent,
				messagesStartWeek, pregnancyDueDateObsId);

		return patient;
	}

	private void enrollPatientWithAttributes(RegistrantType patientType,
			Patient patient, Boolean enroll, Boolean consent,
			ContactNumberType ownership, Integer phoneNumber, MediaType format,
			String language, DayOfWeek dayOfWeek, Date timeOfDay,
			InterestReason reason, HowLearned howLearned,
			Integer messagesStartWeek, Integer pregnancyDueDateObsId) {

		setPatientAttributes(patient, null, phoneNumber, ownership, format,
				language, dayOfWeek, timeOfDay, howLearned, reason, null, null,
				null);

		enrollPatient(patientType, patient, enroll, consent, messagesStartWeek,
				pregnancyDueDateObsId);
	}

	private void enrollPatient(RegistrantType patientType, Patient patient,
			Boolean enroll, Boolean consent, Integer messagesStartWeek,
			Integer pregnancyDueDateObsId) {

		boolean enrollPatient = Boolean.TRUE.equals(enroll)
				&& Boolean.TRUE.equals(consent);

		Integer referenceDateObsId = null;
		String infoMessageProgramName = null;

		if (patientType == RegistrantType.PREGNANT_MOTHER) {
			infoMessageProgramName = "Weekly Pregnancy Message Program";

			referenceDateObsId = pregnancyDueDateObsId;

		} else if (patientType == RegistrantType.CHILD_UNDER_FIVE) {
			infoMessageProgramName = "Weekly Info Child Message Program";

			// TODO: If mother specified, Remove mother's pregnancy message
			// enrollment

		} else if (patientType == RegistrantType.OTHER) {
			infoMessageProgramName = "Weekly Info Pregnancy Message Program";

			if (messagesStartWeek != null && enrollPatient) {
				ObsService obsService = contextService.getObsService();

				Location ghanaLocation = getGhanaLocation();
				Date currentDate = new Date();

				Calendar calendar = Calendar.getInstance();
				// Convert weeks to days, plus one day
				calendar.add(Calendar.DATE, (messagesStartWeek * -7) + 1);
				Date referenceDate = calendar.getTime();

				Obs refDateObs = createDateValueObs(currentDate,
						getEnrollmentReferenceDateConcept(), patient,
						ghanaLocation, referenceDate, null, null);

				refDateObs = obsService.saveObs(refDateObs, null);
				referenceDateObsId = refDateObs.getObsId();
			}
		}

		if (enrollPatient && infoMessageProgramName != null) {

			addMessageProgramEnrollment(patient.getPatientId(),
					infoMessageProgramName, referenceDateObsId);

			// TODO: Base care enrollment on community
			addMessageProgramEnrollment(patient.getPatientId(),
					"Expected Care Message Program", null);
		}
	}

	@Transactional
	public void demoRegisterPatient(String motechId, String firstName,
			String middleName, String lastName, String prefName,
			Date birthDate, Boolean birthDateEst, Gender sex,
			Boolean registeredGHS, String regNumberGHS, Boolean insured,
			String nhis, Date nhisExpDate, String region, String district,
			String community, String address, Integer clinic,
			Boolean registerPregProgram, String primaryPhone,
			ContactNumberType primaryPhoneType, String secondaryPhone,
			ContactNumberType secondaryPhoneType, MediaType mediaTypeInfo,
			MediaType mediaTypeReminder, String languageVoice,
			String languageText, WhoRegistered whoRegistered, String religion,
			String occupation) {

		PatientService patientService = contextService.getPatientService();

		// TODO: Update demo patient registration
		Patient patient = createPatient(Integer.parseInt(motechId), firstName,
				middleName, lastName, prefName, birthDate, birthDateEst, sex,
				insured, nhis, nhisExpDate, null, address, Integer
						.parseInt(primaryPhone), primaryPhoneType,
				mediaTypeInfo, languageText, null, null, null, null);

		patient = patientService.savePatient(patient);

		if (registerPregProgram) {
			addMessageProgramEnrollment(patient.getPatientId(),
					"Demo Minute Message Program", null);
		}
	}

	@Transactional
	public void demoEnrollPatient(String regNumGHS) {
		Patient patient = getPatientByMotechId(regNumGHS);
		addMessageProgramEnrollment(patient.getPersonId(),
				"Input Demo Message Program", null);
	}

	@Transactional
	private Patient createPatient(Integer motechId, String firstName,
			String middleName, String lastName, String prefName,
			Date birthDate, Boolean birthDateEst, Gender sex, Boolean insured,
			String nhis, Date nhisExpDate, Integer community, String address,
			Integer phoneNumber, ContactNumberType phoneType,
			MediaType mediaType, String language, DayOfWeek dayOfWeek,
			Date timeOfDay, HowLearned howLearned, InterestReason whyInterested) {

		Patient patient = new Patient();

		String motechIdString = null;
		if (motechId == null) {
			motechIdString = generateMotechId();
		} else {
			motechIdString = motechId.toString();
			excludeIdForGenerator(motechIdString);
		}

		patient.addIdentifier(new PatientIdentifier(motechIdString,
				getMotechPatientIdType(), getGhanaLocation()));

		patient.addName(new PersonName(firstName, middleName, lastName));

		if (prefName != null) {
			PersonName preferredPersonName = new PersonName(prefName,
					middleName, lastName);
			preferredPersonName.setPreferred(true);
			patient.addName(preferredPersonName);
		}

		patient.setGender(GenderTypeConverter.toOpenMRSString(sex));
		patient.setBirthdate(birthDate);
		patient.setBirthdateEstimated(birthDateEst);

		if (address != null) {
			PersonAddress personAddress = new PersonAddress();
			personAddress.setAddress1(address);
			patient.addAddress(personAddress);
		}

		setPatientAttributes(patient, community, phoneNumber, phoneType,
				mediaType, language, dayOfWeek, timeOfDay, howLearned,
				whyInterested, insured, nhis, nhisExpDate);

		return patient;
	}

	private void setPatientAttributes(Patient patient, Integer community,
			Integer phoneNumber, ContactNumberType phoneType,
			MediaType mediaType, String language, DayOfWeek dayOfWeek,
			Date timeOfDay, HowLearned howLearned,
			InterestReason whyInterested, Boolean insured, String nhis,
			Date nhisExpDate) {

		List<PersonAttribute> attrs = new ArrayList<PersonAttribute>();

		if (community != null) {
			attrs.add(new PersonAttribute(getCommunityAttributeType(),
					community.toString()));
		}

		if (phoneNumber != null) {
			attrs.add(new PersonAttribute(getPhoneNumberAttributeType(),
					phoneNumber.toString()));
		}

		if (phoneType != null) {
			attrs.add(new PersonAttribute(getPhoneTypeAttributeType(),
					phoneType.name()));
		}

		if (mediaType != null) {
			attrs.add(new PersonAttribute(getMediaTypeAttributeType(),
					mediaType.name()));
		}

		if (language != null) {
			attrs
					.add(new PersonAttribute(getLanguageAttributeType(),
							language));
		}

		if (dayOfWeek != null) {
			attrs.add(new PersonAttribute(getDeliveryDayAttributeType(),
					dayOfWeek.name()));
		}

		if (timeOfDay != null) {
			SimpleDateFormat formatter = new SimpleDateFormat(
					MotechConstants.TIME_FORMAT_PERSON_ATTRIBUTE_DELIVERY_TIME);
			attrs.add(new PersonAttribute(getDeliveryTimeAttributeType(),
					formatter.format(timeOfDay)));
		}

		if (howLearned != null) {
			attrs.add(new PersonAttribute(getHowLearnedAttributeType(),
					howLearned.name()));
		}

		if (whyInterested != null) {
			attrs.add(new PersonAttribute(getWhyInterestedAttributeType(),
					whyInterested.name()));
		}

		if (insured != null) {
			attrs.add(new PersonAttribute(getInsuredAttributeType(), insured
					.toString()));
		}

		if (nhis != null) {
			attrs.add(new PersonAttribute(getNHISNumberAttributeType(), nhis));
		}

		if (nhisExpDate != null) {
			SimpleDateFormat formatter = new SimpleDateFormat(
					MotechConstants.DATE_FORMAT);
			attrs.add(new PersonAttribute(getNHISExpirationDateAttributeType(),
					formatter.format(nhisExpDate)));
		}

		for (PersonAttribute attr : attrs)
			patient.addAttribute(attr);
	}

	@Transactional
	public void editPatient(User nurse, Patient patient, String primaryPhone,
			ContactNumberType primaryPhoneType, String secondaryPhone,
			ContactNumberType secondaryPhoneType, String nhis, Date nhisExpires) {

		PatientService patientService = contextService.getPatientService();

		setPatientAttributes(patient, null, Integer.parseInt(primaryPhone),
				primaryPhoneType, null, null, null, null, null, null, null,
				nhis, nhisExpires);

		patientService.savePatient(patient);
	}

	@Transactional
	public void editPatient(Integer id, String firstName, String middleName,
			String lastName, String prefName, Date birthDate,
			Boolean birthDateEst, Gender sex, Boolean registeredGHS,
			String regNumberGHS, Boolean insured, String nhis,
			Date nhisExpDate, String region, String district, String community,
			String address, Integer clinic, String primaryPhone,
			ContactNumberType primaryPhoneType, String secondaryPhone,
			ContactNumberType secondaryPhoneType, MediaType mediaTypeInfo,
			MediaType mediaTypeReminder, String languageVoice,
			String languageText, String religion, String occupation,
			HIVStatus hivStatus) {

		PatientService patientService = contextService.getPatientService();

		Patient patient = patientService.getPatient(id);
		if (patient == null) {
			log.error("No matching patient for id: " + id);
			return;
		}

		patient.setBirthdate(birthDate);
		patient.setBirthdateEstimated(birthDateEst);
		patient.setGender(GenderTypeConverter.toOpenMRSString(sex));

		Set<PersonName> patientNames = patient.getNames();
		if (patientNames.isEmpty()) {
			patient.addName(new PersonName(firstName, middleName, lastName));
			if (prefName != null) {
				PersonName preferredPersonName = new PersonName(prefName,
						middleName, lastName);
				preferredPersonName.setPreferred(true);
				patient.addName(preferredPersonName);
			}
		} else {
			for (PersonName name : patient.getNames()) {
				if (name.isPreferred()) {
					if (prefName != null) {
						name.setGivenName(prefName);
						name.setFamilyName(lastName);
						name.setMiddleName(middleName);
					} else {
						patient.removeName(name);
					}
				} else {
					name.setGivenName(firstName);
					name.setMiddleName(middleName);
					name.setFamilyName(lastName);
				}
			}
		}

		PersonAddress patientAddress = patient.getPersonAddress();
		if (patientAddress == null) {
			patientAddress = new PersonAddress();
			patientAddress.setRegion(region);
			patientAddress.setCountyDistrict(district);
			patientAddress.setCityVillage(community);
			patientAddress.setAddress1(address);
			patient.addAddress(patientAddress);
		} else {
			patientAddress.setRegion(region);
			patientAddress.setCountyDistrict(district);
			patientAddress.setCityVillage(community);
			patientAddress.setAddress1(address);
		}

		PatientIdentifier patientId = patient.getPatientIdentifier();
		if (patientId == null) {
			patientId = new PatientIdentifier();
			patientId.setIdentifierType(getMotechPatientIdType());
			patientId.setLocation(getGhanaLocation());
			patientId.setIdentifier(regNumberGHS);
			patient.addIdentifier(patientId);
		} else {
			patientId.setIdentifier(regNumberGHS);
		}

		// TODO: Update to handle removed attributes and changed types
		setPatientAttributes(patient, null, Integer.parseInt(primaryPhone),
				primaryPhoneType, mediaTypeInfo, languageText, null, null,
				null, null, insured, nhis, nhisExpDate);

		patientService.savePatient(patient);
	}

	@Transactional
	public void stopPregnancyProgram(User nurse, Patient patient) {

		String[] pregnancyPrograms = { "Weekly Pregnancy Message Program",
				"Weekly Info Pregnancy Message Program" };

		Integer patientId = patient.getPatientId();

		for (String programName : pregnancyPrograms) {
			removeMessageProgramEnrollment(patientId, programName, null);
		}
	}

	@Transactional
	public void registerPregnancy(Integer id, Date dueDate,
			Boolean dueDateConfirmed, Boolean registerPregProgram,
			String primaryPhone, ContactNumberType primaryPhoneType,
			String secondaryPhone, ContactNumberType secondaryPhoneType,
			MediaType mediaTypeInfo, MediaType mediaTypeReminder,
			String languageVoice, String languageText,
			WhoRegistered whoRegistered, String howLearned) {

		PatientService patientService = contextService.getPatientService();

		Patient patient = patientService.getPatient(id);
		if (patient == null) {
			log.error("No matching patient for id: " + id);
			return;
		}

		// TODO: Update to handle removed attributes and changed types
		setPatientAttributes(patient, null, Integer.parseInt(primaryPhone),
				primaryPhoneType, mediaTypeInfo, languageText, null, null,
				null, null, null, null, null);

		patientService.savePatient(patient);

		Integer dueDateObsId = registerPregnancy(patient, dueDate,
				dueDateConfirmed, null, null, null);

		if (registerPregProgram) {
			addMessageProgramEnrollment(patient.getPatientId(),
					"Weekly Pregnancy Message Program", dueDateObsId);
		}
	}

	private Integer registerPregnancy(Patient patient, Date dueDate,
			Boolean dueDateConfirmed, Integer gravida, Integer parity,
			Integer height) {

		EncounterService encounterService = contextService
				.getEncounterService();
		ObsService obsService = contextService.getObsService();

		Date currentDate = new Date();
		Location ghanaLocation = getGhanaLocation();

		Encounter encounter = new Encounter();
		encounter
				.setEncounterType(getPregnancyRegistrationVisitEncounterType());
		encounter.setEncounterDatetime(currentDate);
		encounter.setPatient(patient);
		encounter.setLocation(ghanaLocation);
		encounter.setProvider(contextService.getAuthenticatedUser());
		encounter = encounterService.saveEncounter(encounter);

		Obs pregnancyObs = createObs(currentDate, getPregnancyConcept(),
				patient, ghanaLocation, encounter, null);

		Obs pregnancyStatusObs = createBooleanValueObs(currentDate,
				getPregnancyStatusConcept(), patient, ghanaLocation,
				Boolean.TRUE, encounter, null);
		pregnancyObs.addGroupMember(pregnancyStatusObs);

		Obs dueDateObs = null;
		if (dueDate != null) {
			dueDateObs = createDateValueObs(currentDate, getDueDateConcept(),
					patient, ghanaLocation, dueDate, encounter, null);
			pregnancyObs.addGroupMember(dueDateObs);
		}

		if (dueDateConfirmed != null) {
			Obs dueDateConfirmedObs = createBooleanValueObs(currentDate,
					getDueDateConfirmedConcept(), patient, ghanaLocation,
					dueDateConfirmed, encounter, null);
			pregnancyObs.addGroupMember(dueDateConfirmedObs);
		}

		if (gravida != null) {
			Obs gravidaObs = createNumericValueObs(currentDate,
					getGravidaConcept(), patient, ghanaLocation, gravida,
					encounter, null);
			pregnancyObs.addGroupMember(gravidaObs);
		}

		if (parity != null) {
			Obs parityObs = createNumericValueObs(currentDate,
					getParityConcept(), patient, ghanaLocation, parity,
					encounter, null);
			pregnancyObs.addGroupMember(parityObs);
		}

		if (height != null) {
			Obs heightObs = createNumericValueObs(currentDate,
					getHeightConcept(), patient, ghanaLocation, height,
					encounter, null);
			pregnancyObs.addGroupMember(heightObs);
		}

		obsService.saveObs(pregnancyObs, null);

		if (dueDateObs != null) {
			return dueDateObs.getObsId();
		}
		return null;
	}

	@Transactional
	public void registerPregnancy(User nurse, Date date, Patient patient,
			Date estDeliveryDate, Boolean enroll, Boolean consent,
			ContactNumberType ownership, Integer phoneNumber, MediaType format,
			String language, DayOfWeek dayOfWeek, Date timeOfDay,
			InterestReason reason, HowLearned howLearned,
			Integer messagesStartWeek) {

		Integer pregnancyDueDateObsId = checkExistingPregnancy(patient);

		if (pregnancyDueDateObsId == null) {
			pregnancyDueDateObsId = registerPregnancy(patient, estDeliveryDate,
					null, null, null, null);
		}

		enrollPatientWithAttributes(RegistrantType.PREGNANT_MOTHER, patient,
				enroll, consent, ownership, phoneNumber, format, language,
				dayOfWeek, timeOfDay, reason, howLearned, messagesStartWeek,
				pregnancyDueDateObsId);
	}

	private Integer checkExistingPregnancy(Patient patient) {
		Obs pregnancyObs = getActivePregnancy(patient.getPatientId());

		Integer pregnancyDueDateObsId = null;
		if (pregnancyObs != null) {
			log.warn("Entering Pregnancy for patient with active pregnancy, "
					+ "patient id=" + patient.getPatientId());

			Obs pregnancyDueDateObs = getActivePregnancyDueDateObs(patient
					.getPatientId(), pregnancyObs);
			if (pregnancyDueDateObs != null) {
				pregnancyDueDateObsId = pregnancyDueDateObs.getObsId();
			} else {
				log.warn("No due date found for active pregnancy, patient id="
						+ patient.getPatientId());
			}
		}
		return pregnancyDueDateObsId;
	}

	@Transactional
	public void registerANCMother(User nurse, Date date, Patient patient,
			String ancRegNumber, Date estDeliveryDate, Integer height,
			Integer gravida, Integer parity, Boolean enroll, Boolean consent,
			ContactNumberType ownership, Integer phoneNumber, MediaType format,
			String language, DayOfWeek dayOfWeek, Date timeOfDay,
			InterestReason reason, HowLearned howLearned,
			Integer messagesStartWeek) {

		EncounterService encounterService = contextService
				.getEncounterService();

		Integer pregnancyDueDateObsId = checkExistingPregnancy(patient);
		if (pregnancyDueDateObsId == null) {
			pregnancyDueDateObsId = registerPregnancy(patient, estDeliveryDate,
					null, gravida, parity, height);
		}

		enrollPatientWithAttributes(RegistrantType.PREGNANT_MOTHER, patient,
				enroll, consent, ownership, phoneNumber, format, language,
				dayOfWeek, timeOfDay, reason, howLearned, messagesStartWeek,
				pregnancyDueDateObsId);

		Location encounterLocation = getGhanaLocation();

		Encounter encounter = new Encounter();
		encounter.setEncounterType(getANCRegistrationEncounterType());
		encounter.setEncounterDatetime(date);
		encounter.setPatient(patient);
		encounter.setLocation(encounterLocation);
		encounter.setProvider(contextService.getAuthenticatedUser());

		Obs ancRegNumObs = createTextValueObs(date,
				getANCRegistrationNumberConcept(), patient, encounterLocation,
				ancRegNumber, encounter, null);
		encounter.addObs(ancRegNumObs);

		encounterService.saveEncounter(encounter);
	}

	@Transactional
	public void registerCWCChild(User nurse, Date date, Patient patient,
			String cwcRegNumber, Boolean enroll, Boolean consent,
			ContactNumberType ownership, Integer phoneNumber, MediaType format,
			String language, DayOfWeek dayOfWeek, Date timeOfDay,
			InterestReason reason, HowLearned howLearned,
			Integer messagesStartWeek) {

		enrollPatientWithAttributes(RegistrantType.CHILD_UNDER_FIVE, patient,
				enroll, consent, ownership, phoneNumber, format, language,
				dayOfWeek, timeOfDay, reason, howLearned, messagesStartWeek,
				null);

		EncounterService encounterService = contextService
				.getEncounterService();

		Location encounterLocation = getGhanaLocation();

		Encounter encounter = new Encounter();
		encounter.setEncounterType(getCWCRegistrationEncounterType());
		encounter.setEncounterDatetime(date);
		encounter.setPatient(patient);
		encounter.setLocation(encounterLocation);
		encounter.setProvider(contextService.getAuthenticatedUser());

		Obs cwcRegNumObs = createTextValueObs(date,
				getCWCRegistrationNumberConcept(), patient, encounterLocation,
				cwcRegNumber, encounter, null);
		encounter.addObs(cwcRegNumObs);

		encounterService.saveEncounter(encounter);
	}

	@Transactional
	public void recordMotherANCVisit(User nurse, Integer facilityId, Date date,
			Patient patient, Integer visitNumber, Integer ancLocation,
			String house, String community, Date estDeliveryDate,
			Integer bpSystolic, Integer bpDiastolic, Double weight,
			Integer ttDose, Integer iptDose, Boolean iptReactive,
			Boolean itnUse, Integer fht, Integer fhr,
			Boolean urineTestProteinPositive, Boolean urineTestGlucosePositive,
			Double hemoglobin, Boolean vdrlReactive, Boolean vdrlTreatment,
			Boolean dewormer, Boolean maleInvolved, Boolean pmtct,
			Boolean preTestCounseled, HIVResult hivTestResult,
			Boolean postTestCounseled, Boolean pmtctTreatment,
			Boolean referred, Date nextANCDate, String comments) {

		EncounterService encounterService = contextService
				.getEncounterService();

		Location location = getGhanaLocation();

		Encounter encounter = new Encounter();
		encounter.setEncounterType(getANCVisitEncounterType());
		encounter.setEncounterDatetime(date);
		encounter.setPatient(patient);
		encounter.setLocation(location);
		encounter.setProvider(contextService.getAuthenticatedUser());

		Obs pregnancyObs = getActivePregnancy(patient.getPatientId());
		if (pregnancyObs == null) {
			log.warn("Entered ANC visit for patient without active pregnancy, "
					+ "patient id=" + patient.getPatientId());
		}

		if (visitNumber != null) {
			Obs visitNumberObs = createNumericValueObs(date,
					getVisitNumberConcept(), patient, location, visitNumber,
					encounter, null);
			encounter.addObs(visitNumberObs);
		}
		if (ancLocation != null) {
			Obs ancLocationObs = createNumericValueObs(date,
					getANCPNCLocationConcept(), patient, location, ancLocation,
					encounter, null);
			encounter.addObs(ancLocationObs);
		}
		if (house != null) {
			Obs houseObs = createTextValueObs(date, getANCPNCLocationConcept(),
					patient, location, house, encounter, null);
			encounter.addObs(houseObs);
		}
		if (community != null) {
			Obs communityObs = createTextValueObs(date,
					getANCPNCLocationConcept(), patient, location, community,
					encounter, null);
			encounter.addObs(communityObs);
		}
		if (bpSystolic != null) {
			Obs bpSystolicObs = createNumericValueObs(date,
					getSystolicBloodPressureConcept(), patient, location,
					bpSystolic, encounter, null);
			encounter.addObs(bpSystolicObs);
		}
		if (bpDiastolic != null) {
			Obs bpDiastolicObs = createNumericValueObs(date,
					getDiastolicBloodPressureConcept(), patient, location,
					bpDiastolic, encounter, null);
			encounter.addObs(bpDiastolicObs);
		}
		if (weight != null) {
			Obs weightObs = createNumericValueObs(date, getWeightConcept(),
					patient, location, weight, encounter, null);
			encounter.addObs(weightObs);
		}
		if (ttDose != null) {
			Obs ttDoseObs = createNumericValueObs(date,
					getTetanusDoseConcept(), patient, location, ttDose,
					encounter, null);
			encounter.addObs(ttDoseObs);
		}
		if (iptDose != null) {
			Obs iptDoseObs = createNumericValueObs(date, getIPTDoseConcept(),
					patient, location, iptDose, encounter, null);
			encounter.addObs(iptDoseObs);
		}
		if (iptReactive != null) {
			Concept iptReactionValueConcept = null;
			if (Boolean.TRUE.equals(iptReactive)) {
				iptReactionValueConcept = getReactiveConcept();
			} else {
				iptReactionValueConcept = getNonReactiveConcept();
			}
			Obs iptReactiveObs = createConceptValueObs(date,
					getIPTReactionConcept(), patient, location,
					iptReactionValueConcept, encounter, null);
			encounter.addObs(iptReactiveObs);
		}
		if (itnUse != null) {
			Obs itnUseObs = createBooleanValueObs(date, getITNConcept(),
					patient, location, itnUse, encounter, null);
			encounter.addObs(itnUseObs);
		}
		if (fht != null) {
			Obs fhtObs = createNumericValueObs(date, getFundalHeightConcept(),
					patient, location, fht, encounter, null);
			encounter.addObs(fhtObs);
		}
		if (fhr != null) {
			Obs fhrObs = createNumericValueObs(date,
					getFetalHeartRateConcept(), patient, location, fhr,
					encounter, null);
			encounter.addObs(fhrObs);
		}
		if (urineTestProteinPositive != null) {
			Concept urineProteinTestValueConcept = null;
			if (Boolean.TRUE.equals(urineTestProteinPositive)) {
				urineProteinTestValueConcept = getPositiveConcept();
			} else {
				urineProteinTestValueConcept = getNegativeConcept();
			}
			Obs urineTestProteinPositiveObs = createConceptValueObs(date,
					getUrineProteinTestConcept(), patient, location,
					urineProteinTestValueConcept, encounter, null);
			encounter.addObs(urineTestProteinPositiveObs);
		}
		if (urineTestGlucosePositive != null) {
			Concept urineGlucoseTestValueConcept = null;
			if (Boolean.TRUE.equals(urineTestGlucosePositive)) {
				urineGlucoseTestValueConcept = getPositiveConcept();
			} else {
				urineGlucoseTestValueConcept = getNegativeConcept();
			}
			Obs urineTestProteinPositiveObs = createConceptValueObs(date,
					getUrineGlucoseTestConcept(), patient, location,
					urineGlucoseTestValueConcept, encounter, null);
			encounter.addObs(urineTestProteinPositiveObs);
		}
		if (hemoglobin != null) {
			Obs hemoglobinObs = createNumericValueObs(date,
					getHemoglobinConcept(), patient, location, hemoglobin,
					encounter, null);
			encounter.addObs(hemoglobinObs);
		}
		if (vdrlReactive != null) {
			Concept vdrlValueConcept = null;
			if (Boolean.TRUE.equals(vdrlReactive)) {
				vdrlValueConcept = getReactiveConcept();
			} else {
				vdrlValueConcept = getNonReactiveConcept();
			}
			Obs vdrlReactiveObs = createConceptValueObs(date, getVDRLConcept(),
					patient, location, vdrlValueConcept, encounter, null);
			encounter.addObs(vdrlReactiveObs);
		}
		if (vdrlTreatment != null) {
			Obs vdrlTreatmentObs = createBooleanValueObs(date,
					getVDRLTreatmentConcept(), patient, location,
					vdrlTreatment, encounter, null);
			encounter.addObs(vdrlTreatmentObs);
		}
		if (dewormer != null) {
			Obs dewormerObs = createBooleanValueObs(date, getDewormerConcept(),
					patient, location, dewormer, encounter, null);
			encounter.addObs(dewormerObs);
		}
		if (maleInvolved != null) {
			Obs maleInvolvedObs = createBooleanValueObs(date,
					getMaleInvolvementConcept(), patient, location,
					maleInvolved, encounter, null);
			encounter.addObs(maleInvolvedObs);
		}
		if (pmtct != null) {
			Obs pmtctObs = createBooleanValueObs(date, getPMTCTConcept(),
					patient, location, pmtct, encounter, null);
			encounter.addObs(pmtctObs);
		}
		if (preTestCounseled != null) {
			Obs preTestCounseledObs = createBooleanValueObs(date,
					getPreHIVTestCounselingConcept(), patient, location,
					preTestCounseled, encounter, null);
			encounter.addObs(preTestCounseledObs);
		}
		if (hivTestResult != null) {
			Obs hivResultObs = createTextValueObs(date,
					getHIVTestResultConcept(), patient, location, hivTestResult
							.name(), encounter, null);
			encounter.addObs(hivResultObs);
		}
		if (postTestCounseled != null) {
			Obs postTestCounseledObs = createBooleanValueObs(date,
					getPostHIVTestCounselingConcept(), patient, location,
					postTestCounseled, encounter, null);
			encounter.addObs(postTestCounseledObs);
		}
		if (pmtctTreatment != null) {
			Obs pmtctTreatmentObs = createBooleanValueObs(date,
					getPMTCTTreatmentConcept(), patient, location,
					pmtctTreatment, encounter, null);
			encounter.addObs(pmtctTreatmentObs);
		}
		if (referred != null) {
			Obs referredObs = createBooleanValueObs(date, getReferredConcept(),
					patient, location, referred, encounter, null);
			encounter.addObs(referredObs);
		}
		if (nextANCDate != null) {
			Obs nextANCDateObs = createDateValueObs(date,
					getNextANCDateConcept(), patient, location, nextANCDate,
					encounter, null);
			encounter.addObs(nextANCDateObs);
		}
		if (comments != null) {
			Obs commentsObs = createTextValueObs(date, getCommentsConcept(),
					patient, location, comments, encounter, null);
			encounter.addObs(commentsObs);
		}

		encounter = encounterService.saveEncounter(encounter);

		if (estDeliveryDate != null) {
			Obs pregnancyDueDateObs = getActivePregnancyDueDateObs(patient
					.getPatientId(), pregnancyObs);
			if (pregnancyDueDateObs != null) {
				updatePregnancyDueDateObs(pregnancyObs, pregnancyDueDateObs,
						estDeliveryDate, encounter);
			} else {
				log.warn("Cannot update pregnancy due date, "
						+ "no active pregnancy due date found, patient id="
						+ patient.getPatientId());
			}
		}
	}

	@Transactional
	public void recordPregnancyTermination(User nurse, Date date,
			Patient patient, Integer abortionType, Integer complication) {

		EncounterService encounterService = contextService
				.getEncounterService();
		ObsService obsService = contextService.getObsService();

		Location location = getGhanaLocation();

		Encounter encounter = new Encounter();
		encounter.setEncounterType(getPregnancyTerminationVisitEncounterType());
		encounter.setEncounterDatetime(date);
		encounter.setPatient(patient);
		encounter.setLocation(location);
		encounter.setProvider(contextService.getAuthenticatedUser());
		encounter = encounterService.saveEncounter(encounter);

		Obs pregnancyObs = getActivePregnancy(patient.getPatientId());

		if (abortionType != null) {
			Obs abortionTypeObs = createNumericValueObs(date,
					getTerminationTypeConcept(), patient, location,
					abortionType, encounter, null);
			abortionTypeObs.setObsGroup(pregnancyObs);
			obsService.saveObs(abortionTypeObs, null);
		}
		if (complication != null) {
			Obs complicationObs = createNumericValueObs(date,
					getTerminationComplicationConcept(), patient, location,
					complication, encounter, null);
			complicationObs.setObsGroup(pregnancyObs);
			obsService.saveObs(complicationObs, null);
		}
		Obs pregnancyStatusObs = createBooleanValueObs(date,
				getPregnancyStatusConcept(), patient, location, Boolean.FALSE,
				encounter, null);
		pregnancyStatusObs.setObsGroup(pregnancyObs);
		obsService.saveObs(pregnancyStatusObs, null);
	}

	@Transactional
	public void recordPregnancyDelivery(User nurse, Date date, Patient patient,
			Integer method, Integer outcome, Integer location,
			Integer deliveredBy, Boolean maternalDeath, Integer cause,
			List<BirthOutcomeChild> outcomes) {

		EncounterService encounterService = contextService
				.getEncounterService();
		ObsService obsService = contextService.getObsService();

		Location encounterLocation = getGhanaLocation();

		Encounter encounter = new Encounter();
		encounter.setEncounterType(getPregnancyDeliveryVisitEncounterType());
		encounter.setEncounterDatetime(date);
		encounter.setPatient(patient);
		encounter.setLocation(encounterLocation);
		encounter.setProvider(contextService.getAuthenticatedUser());
		encounter = encounterService.saveEncounter(encounter);

		Obs pregnancyObs = getActivePregnancy(patient.getPatientId());

		if (method != null) {
			Obs methodObs = createNumericValueObs(date,
					getDeliveryMethodConcept(), patient, encounterLocation,
					method, encounter, null);
			obsService.saveObs(methodObs, null);
		}
		if (outcome != null) {
			Obs outcomeObs = createNumericValueObs(date,
					getDeliveryOutcomeConcept(), patient, encounterLocation,
					outcome, encounter, null);
			obsService.saveObs(outcomeObs, null);
		}
		if (location != null) {
			Obs locationObs = createNumericValueObs(date,
					getDeliveryLocationConcept(), patient, encounterLocation,
					location, encounter, null);
			obsService.saveObs(locationObs, null);
		}
		if (deliveredBy != null) {
			Obs deliveredByObs = createNumericValueObs(date,
					getDeliveredByConcept(), patient, encounterLocation,
					deliveredBy, encounter, null);
			obsService.saveObs(deliveredByObs, null);
		}

		Obs pregnancyStatusObs = createBooleanValueObs(date,
				getPregnancyStatusConcept(), patient, encounterLocation,
				Boolean.FALSE, encounter, null);
		pregnancyStatusObs.setObsGroup(pregnancyObs);
		obsService.saveObs(pregnancyStatusObs, null);

		for (BirthOutcomeChild childOutcome : outcomes) {
			if (childOutcome.getOutcome() != null) {
				Obs childOutcomeObs = createTextValueObs(date,
						getBirthOutcomeConcept(), patient, encounterLocation,
						childOutcome.getOutcome().name(), encounter, null);
				obsService.saveObs(childOutcomeObs, null);
			}

			// TODO: Update to include registration mode in birth outcome
			Patient child = registerPatient(null, childOutcome.getMotechId(),
					RegistrantType.CHILD_UNDER_FIVE, childOutcome
							.getFirstName(), null, null, null, date, false,
					childOutcome.getSex(), null, null, null, patient, null,
					null, null, null, null, null, null, null, null, null, null,
					null, null, null, null, null, null);

			Integer opvDose = null;
			if (Boolean.TRUE.equals(childOutcome.getOpv())) {
				opvDose = 0;
			}

			if (Boolean.TRUE.equals(childOutcome.getBcg()) || opvDose != null) {
				recordChildPNCVisit(nurse, date, child, childOutcome.getBcg(),
						opvDose, null, null, null, null, null, null);
			}

			if (BirthOutcome.A != childOutcome.getOutcome()) {
				processPatientDeath(child, date);
			}
		}

		if (Boolean.TRUE.equals(maternalDeath)) {
			if (cause != null) {
				Obs maternalDeathCauseObs = createNumericValueObs(date,
						getMaternalDeathCauseConcept(), patient,
						encounterLocation, cause, encounter, null);
				obsService.saveObs(maternalDeathCauseObs, null);
			}

			processPatientDeath(patient, date);
		}
	}

	@Transactional
	public void recordPregnancyDeliveryNotification(User nurse, Date date,
			Patient patient) {

		EncounterService encounterService = contextService
				.getEncounterService();

		Location encounterLocation = getGhanaLocation();

		Encounter encounter = new Encounter();
		encounter
				.setEncounterType(getPregnancyDeliveryNotificationEncounterType());
		encounter.setEncounterDatetime(date);
		encounter.setPatient(patient);
		encounter.setLocation(encounterLocation);
		encounter.setProvider(contextService.getAuthenticatedUser());

		Obs pregnancyObs = getActivePregnancy(patient.getPatientId());
		if (pregnancyObs == null) {
			log
					.warn("Entered Pregnancy delivery notification for patient without active pregnancy, "
							+ "patient id=" + patient.getPatientId());
		}

		Obs pregnancyStatusObs = createBooleanValueObs(date,
				getPregnancyStatusConcept(), patient, encounterLocation,
				Boolean.FALSE, encounter, null);
		pregnancyStatusObs.setObsGroup(pregnancyObs);
		encounter.addObs(pregnancyStatusObs);

		encounterService.saveEncounter(encounter);

		sentDeliveryNotification(patient);
	}

	private void sentDeliveryNotification(Patient patient) {
		UserService userService = contextService.getUserService();
		List<User> users = userService.getAllUsers();

		WebServiceModelConverter wsModelConverter = new WebServiceModelConverterImpl();
		org.motechproject.ws.Patient wsPatient = wsModelConverter
				.patientToWebService(patient, true);
		org.motechproject.ws.Patient[] wsPatients = new org.motechproject.ws.Patient[] { wsPatient };

		MessageDefinition messageDef = getMessageDefinition("pregnancy.notification");
		if (messageDef == null) {
			log.error("Pregnancy delivery notification message does not exist");
			return;
		}
		String messageId = null;
		NameValuePair[] nameValues = new NameValuePair[0];
		MediaType mediaType = MediaType.TEXT;
		String languageCode = "en";

		// TODO: Only sent to facility phone for patient's community
		for (User user : users) {
			String phoneNumber = getPersonPhoneNumber(user);
			if (phoneNumber == null) {
				continue;
			}
			sendNurseMessage(messageId, nameValues, phoneNumber, languageCode,
					mediaType, messageDef.getPublicId(), null, null, wsPatients);
		}
	}

	@Transactional
	public void recordMotherPPCVisit(User nurse, Date date, Patient patient,
			Integer visitNumber, Boolean vitaminA, Integer ttDose) {

		EncounterService encounterService = contextService
				.getEncounterService();
		ObsService obsService = contextService.getObsService();

		Location location = getGhanaLocation();

		Encounter encounter = new Encounter();
		encounter.setEncounterType(getMotherPNCVisitEncounterType());
		encounter.setEncounterDatetime(date);
		encounter.setPatient(patient);
		encounter.setLocation(location);
		encounter.setProvider(contextService.getAuthenticatedUser());
		encounter = encounterService.saveEncounter(encounter);

		if (visitNumber != null) {
			Obs visitNumberObs = createNumericValueObs(date,
					getVisitNumberConcept(), patient, location, visitNumber,
					encounter, null);
			obsService.saveObs(visitNumberObs, null);
		}
		if (Boolean.TRUE.equals(vitaminA)) {
			Obs vitaminAObs = createConceptValueObs(date,
					getImmunizationsOrderedConcept(), patient, location,
					getVitaminAConcept(), encounter, null);
			obsService.saveObs(vitaminAObs, null);
		}
		if (ttDose != null) {
			Obs ttDoseObs = createNumericValueObs(date,
					getTetanusDoseConcept(), patient, location, ttDose,
					encounter, null);
			obsService.saveObs(ttDoseObs, null);
		}
	}

	@Transactional
	public void recordDeath(User nurse, Date date, Patient patient,
			Integer cause) {

		ObsService obsService = contextService.getObsService();

		Location location = getGhanaLocation();

		if (cause != null) {
			Obs deathCauseObs = createNumericValueObs(date,
					getDeathCauseConcept(), patient, location, cause, null,
					null);
			obsService.saveObs(deathCauseObs, null);
		}

		processPatientDeath(patient, date);
	}

	private void processPatientDeath(Patient patient, Date date) {
		PatientService patientService = contextService.getPatientService();
		PersonService personService = contextService.getPersonService();

		// Stop all messages and remove all message program enrollments
		removeAllMessageProgramEnrollments(patient.getPatientId());

		patient.setDead(true);
		patient.setDeathDate(date);
		patient = patientService.savePatient(patient);

		personService.voidPerson(patient, "Deceased");
	}

	@Transactional
	public void recordChildPNCVisit(User nurse, Date date, Patient patient,
			Boolean bcg, Integer opvDose, Integer pentaDose,
			Boolean yellowFever, Boolean csm, Boolean measles, Boolean ipti,
			Boolean vitaminA) {

		EncounterService encounterService = contextService
				.getEncounterService();
		ObsService obsService = contextService.getObsService();

		Location location = getGhanaLocation();

		Encounter encounter = new Encounter();
		encounter.setEncounterType(getChildPNCVisitEncounterType());
		encounter.setEncounterDatetime(date);
		encounter.setPatient(patient);
		encounter.setLocation(location);
		encounter.setProvider(contextService.getAuthenticatedUser());
		encounter = encounterService.saveEncounter(encounter);

		if (Boolean.TRUE.equals(bcg)) {
			Obs bcgObs = createConceptValueObs(date,
					getImmunizationsOrderedConcept(), patient, location,
					getBCGConcept(), encounter, null);
			obsService.saveObs(bcgObs, null);
		}
		if (opvDose != null) {
			Obs opvDoseObs = createNumericValueObs(date, getOPVDoseConcept(),
					patient, location, opvDose, encounter, null);
			obsService.saveObs(opvDoseObs, null);
		}
		if (pentaDose != null) {
			Obs pentaDoseObs = createNumericValueObs(date,
					getPentaDoseConcept(), patient, location, pentaDose,
					encounter, null);
			obsService.saveObs(pentaDoseObs, null);
		}
		if (Boolean.TRUE.equals(yellowFever)) {
			Obs yellowFeverObs = createConceptValueObs(date,
					getImmunizationsOrderedConcept(), patient, location,
					getYellowFeverConcept(), encounter, null);
			obsService.saveObs(yellowFeverObs, null);
		}
		if (Boolean.TRUE.equals(csm)) {
			Obs csmObs = createConceptValueObs(date,
					getImmunizationsOrderedConcept(), patient, location,
					getCSMConcept(), encounter, null);
			obsService.saveObs(csmObs, null);
		}
		if (Boolean.TRUE.equals(measles)) {
			Obs measlesObs = createConceptValueObs(date,
					getImmunizationsOrderedConcept(), patient, location,
					getMeaslesConcept(), encounter, null);
			obsService.saveObs(measlesObs, null);
		}
		if (Boolean.TRUE.equals(ipti)) {
			Obs iptiObs = createConceptValueObs(date,
					getImmunizationsOrderedConcept(), patient, location,
					getIPTiConcept(), encounter, null);
			obsService.saveObs(iptiObs, null);
		}
		if (Boolean.TRUE.equals(vitaminA)) {
			Obs vitaminAObs = createConceptValueObs(date,
					getImmunizationsOrderedConcept(), patient, location,
					getVitaminAConcept(), encounter, null);
			obsService.saveObs(vitaminAObs, null);
		}
	}

	@Transactional
	public void recordGeneralVisit(String chpsId, Date date,
			String serialNumber, Gender sex, Date birthDate, Boolean insured,
			Boolean newCase, Integer diagnosis, Integer secondaryDiagnosis,
			Boolean referred) {

		log.debug("Date: " + date + ", CHPS Id: " + chpsId + ", Serial: "
				+ serialNumber + ", Sex: " + sex + ", Birthdate: " + birthDate
				+ ", Insured: " + insured + ", New Case: " + newCase
				+ ", Diagnosis: " + diagnosis + ", Sec Diagnosis: "
				+ secondaryDiagnosis + ", Referred: " + referred);

		MotechService motechService = contextService.getMotechService();

		GeneralPatientEncounter encounter = new GeneralPatientEncounter();
		encounter.setFacilityId(chpsId);
		encounter.setDate(date);
		encounter.setSerialNumber(serialNumber);
		encounter.setSex(sex);
		encounter.setBirthDate(birthDate);
		encounter.setInsured(insured);
		encounter.setNewCase(newCase);
		encounter.setDiagnosis(diagnosis);
		encounter.setSecondaryDiagnosis(secondaryDiagnosis);
		encounter.setReferred(referred);

		motechService.saveGeneralPatientEncounter(encounter);
	}

	@Transactional
	public void recordChildVisit(User nurse, Date date, Patient patient,
			String serialNumber, Boolean newCase, Integer diagnosis,
			Integer secondDiagnosis, Boolean referred) {

		recordMotherChildGeneralVisit(nurse, date, patient, serialNumber,
				newCase, diagnosis, secondDiagnosis, referred);
	}

	@Transactional
	public void recordMotherVisit(User nurse, Date date, Patient patient,
			String serialNumber, Boolean newCase, Integer diagnosis,
			Integer secondDiagnosis, Boolean referred) {

		recordMotherChildGeneralVisit(nurse, date, patient, serialNumber,
				newCase, diagnosis, secondDiagnosis, referred);
	}

	private void recordMotherChildGeneralVisit(User nurse, Date date,
			Patient patient, String serialNumber, Boolean newCase,
			Integer diagnosis, Integer secondDiagnosis, Boolean referred) {

		EncounterService encounterService = contextService
				.getEncounterService();
		ObsService obsService = contextService.getObsService();

		Location location = getGhanaLocation();

		Encounter encounter = new Encounter();
		encounter.setEncounterType(getGeneralVisitEncounterType());
		encounter.setEncounterDatetime(date);
		encounter.setPatient(patient);
		encounter.setLocation(location);
		encounter.setProvider(contextService.getAuthenticatedUser());
		encounter = encounterService.saveEncounter(encounter);

		if (serialNumber != null) {
			Obs serialNumberObs = createTextValueObs(date,
					getSerialNumberConcept(), patient, location, serialNumber,
					encounter, null);
			obsService.saveObs(serialNumberObs, null);
		}
		if (newCase != null) {
			Obs newCaseObs = createBooleanValueObs(date, getNewCaseConcept(),
					patient, location, newCase, encounter, null);
			obsService.saveObs(newCaseObs, null);
		}
		if (diagnosis != null) {
			Obs diagnosisObs = createNumericValueObs(date,
					getPrimaryDiagnosisConcept(), patient, location, diagnosis,
					encounter, null);
			obsService.saveObs(diagnosisObs, null);
		}
		if (secondDiagnosis != null) {
			Obs secondDiagnosisObs = createNumericValueObs(date,
					getSecondaryDiagnosisConcept(), patient, location,
					secondDiagnosis, encounter, null);
			obsService.saveObs(secondDiagnosisObs, null);
		}
		if (referred != null) {
			Obs referredObs = createBooleanValueObs(date, getReferredConcept(),
					patient, location, referred, encounter, null);
			obsService.saveObs(referredObs, null);
		}
	}

	@Transactional
	public void log(LogType type, String message) {

		log.debug("log WS: type: " + type + ", message: " + message);

		String limitedMessage = message;
		if (limitedMessage.length() > 255) {
			limitedMessage = limitedMessage.substring(0, 255);
			log.debug("log WS: trimmed message: " + limitedMessage);
		}

		MotechService motechService = contextService.getMotechService();

		org.motechproject.server.model.Log log = new org.motechproject.server.model.Log();
		log.setDate(new Date());
		log.setType(type);
		log.setMessage(limitedMessage);
		motechService.saveLog(log);
	}

	@Transactional
	public void setMessageStatus(String messageId, Boolean success) {

		log.debug("setMessageStatus WS: messageId: " + messageId
				+ ", success: " + success);

		MotechService motechService = contextService.getMotechService();
		PersonService personService = contextService.getPersonService();

		Message message = motechService.getMessage(messageId);
		if (message == null) {
			throw new MessageNotFoundException();
		}

		Integer recipientId = message.getSchedule().getRecipientId();
		Person messageRecipient = personService.getPerson(recipientId);
		String phoneNumber = getPersonPhoneNumber(messageRecipient);
		TroubledPhone troubledPhone = motechService
				.getTroubledPhone(phoneNumber);

		if (success) {
			message.setAttemptStatus(MessageStatus.DELIVERED);

			if (troubledPhone != null) {
				motechService.removeTroubledPhone(phoneNumber);
			}
		} else {
			message.setAttemptStatus(MessageStatus.ATTEMPT_FAIL);

			if (troubledPhone == null) {
				motechService.addTroubledPhone(phoneNumber);
			} else {
				Integer sendFailures = troubledPhone.getSendFailures() + 1;
				troubledPhone.setSendFailures(sendFailures);
				motechService.saveTroubledPhone(troubledPhone);
			}
		}
		motechService.saveMessage(message);
	}

	public User getUserByPhoneNumber(String phoneNumber) {
		MotechService motechService = contextService.getMotechService();
		UserService userService = contextService.getUserService();

		PersonAttributeType phoneAttributeType = getPhoneNumberAttributeType();
		List<Integer> matchingUsers = motechService
				.getUserIdsByPersonAttribute(phoneAttributeType, phoneNumber);
		if (matchingUsers.size() > 0) {
			if (matchingUsers.size() > 1) {
				log.warn("Multiple Nurses found for phone number: "
						+ phoneNumber);
			}
			// If more than one user matches phone number, first user in list is
			// returned
			Integer userId = matchingUsers.get(0);
			return userService.getUser(userId);
		}
		log.warn("No Nurse found for phone number: " + phoneNumber);
		return null;
	}

	/* MotechService methods end */

	/* Controller methods start */
	public List<Location> getAllLocations() {
		LocationService locationService = contextService.getLocationService();
		return locationService.getAllLocations();
	}

	public List<User> getAllNurses() {
		UserService userService = contextService.getUserService();
		return userService.getAllUsers();
	}

	public List<Patient> getAllPatients() {
		PatientService patientService = contextService.getPatientService();
		List<PatientIdentifierType> motechPatientIdType = new ArrayList<PatientIdentifierType>();
		motechPatientIdType.add(getMotechPatientIdType());
		return patientService.getPatients(null, null, motechPatientIdType,
				false);
	}

	public List<Patient> getPatients(String firstName, String lastName,
			String preferredName, Date birthDate, String community,
			String phoneNumber, String nhisNumber, String motechId) {

		MotechService motechService = contextService.getMotechService();

		PersonAttributeType phoneNumberAttrType = getPhoneNumberAttributeType();
		PersonAttributeType nhisAttrType = getNHISNumberAttributeType();
		PatientIdentifierType motechIdType = getMotechPatientIdType();

		return motechService.getPatients(firstName, lastName, preferredName,
				birthDate, community, phoneNumber, phoneNumberAttrType,
				nhisNumber, nhisAttrType, motechId, motechIdType);
	}

	public List<Obs> getAllPregnancies() {
		ObsService obsService = contextService.getObsService();
		List<Concept> pregnancyConcept = new ArrayList<Concept>();
		pregnancyConcept.add(getPregnancyConcept());
		return obsService.getObservations(null, null, pregnancyConcept, null,
				null, null, null, null, null, null, null, false);
	}

	public List<ExpectedEncounter> getUpcomingExpectedEncounters(Patient patient) {
		MotechService motechService = contextService.getMotechService();
		Calendar calendar = Calendar.getInstance();
		Date currentDate = calendar.getTime();
		calendar.add(Calendar.DATE, 7);
		Date oneWeekLaterDate = calendar.getTime();
		return motechService.getExpectedEncounter(patient, null, currentDate,
				oneWeekLaterDate, null, currentDate, false);
	}

	public List<ExpectedObs> getUpcomingExpectedObs(Patient patient) {
		MotechService motechService = contextService.getMotechService();
		Calendar calendar = Calendar.getInstance();
		Date currentDate = calendar.getTime();
		calendar.add(Calendar.DATE, 7);
		Date oneWeekLaterDate = calendar.getTime();
		return motechService.getExpectedObs(patient, null, currentDate,
				oneWeekLaterDate, null, currentDate, false);
	}

	public List<ExpectedEncounter> getDefaultedExpectedEncounters(
			String[] groups) {
		MotechService motechService = contextService.getMotechService();
		Date currentDate = new Date();
		return motechService.getExpectedEncounter(null, groups, null, null,
				currentDate, currentDate, true);
	}

	public List<ExpectedObs> getDefaultedExpectedObs(String[] groups) {
		MotechService motechService = contextService.getMotechService();
		Date currentDate = new Date();
		return motechService.getExpectedObs(null, groups, null, null,
				currentDate, currentDate, true);
	}

	public List<ExpectedEncounter> getUpcomingExpectedEncounters(
			String[] groups, Date fromDate, Date toDate) {
		MotechService motechService = contextService.getMotechService();
		return motechService.getExpectedEncounter(null, groups, fromDate,
				toDate, null, fromDate, false);
	}

	public List<ExpectedObs> getUpcomingExpectedObs(String[] groups,
			Date fromDate, Date toDate) {
		MotechService motechService = contextService.getMotechService();
		return motechService.getExpectedObs(null, groups, fromDate, toDate,
				null, fromDate, false);
	}

	public List<ExpectedEncounter> getDefaultedExpectedEncounters(
			String[] groups, Date forDate) {
		MotechService motechService = contextService.getMotechService();
		return motechService.getExpectedEncounter(null, groups, null, null,
				forDate, forDate, true);
	}

	public List<ExpectedObs> getDefaultedExpectedObs(String[] groups,
			Date forDate) {
		MotechService motechService = contextService.getMotechService();
		return motechService.getExpectedObs(null, groups, null, null, forDate,
				forDate, true);
	}

	public List<ExpectedEncounter> getExpectedEncounters(Patient patient) {
		MotechService motechService = contextService.getMotechService();
		Date currentDate = new Date();
		return motechService.getExpectedEncounter(patient, null, null, null,
				null, currentDate, true);
	}

	public List<ExpectedObs> getExpectedObs(Patient patient) {
		MotechService motechService = contextService.getMotechService();
		Date currentDate = new Date();
		return motechService.getExpectedObs(patient, null, null, null, null,
				currentDate, true);
	}

	public List<Encounter> getRecentDeliveries() {
		EncounterService encounterService = contextService
				.getEncounterService();

		List<EncounterType> deliveryEncounterType = new ArrayList<EncounterType>();
		deliveryEncounterType.add(getPregnancyDeliveryVisitEncounterType());

		Calendar calendar = Calendar.getInstance();
		Date currentDate = calendar.getTime();
		calendar.add(Calendar.DATE, 2 * -7);
		Date twoWeeksPriorDate = calendar.getTime();

		return encounterService.getEncounters(null, null, twoWeeksPriorDate,
				currentDate, null, deliveryEncounterType, null, false);
	}

	public Date getCurrentDeliveryDate(Patient patient) {
		EncounterService encounterService = contextService
				.getEncounterService();

		List<EncounterType> deliveryEncounterType = new ArrayList<EncounterType>();
		deliveryEncounterType.add(getPregnancyDeliveryVisitEncounterType());

		List<Encounter> deliveries = encounterService.getEncounters(patient,
				null, null, null, null, deliveryEncounterType, null, false);

		if (!deliveries.isEmpty()) {
			// List is ascending by date, get last match to get most recent
			return deliveries.get(deliveries.size() - 1).getEncounterDatetime();
		}
		return null;
	}

	public List<Obs> getUpcomingPregnanciesDueDate() {
		Calendar calendar = Calendar.getInstance();
		Date currentDate = calendar.getTime();
		calendar.add(Calendar.DATE, 2 * 7);
		Date twoWeeksLaterDate = calendar.getTime();

		return getActivePregnanciesDueDateObs(currentDate, twoWeeksLaterDate);
	}

	public List<Obs> getOverduePregnanciesDueDate() {
		Date currentDate = new Date();
		return getActivePregnanciesDueDateObs(null, currentDate);
	}

	private List<Obs> getActivePregnanciesDueDateObs(Date fromDueDate,
			Date toDueDate) {
		MotechService motechService = contextService.getMotechService();

		Concept pregnancyDueDateConcept = getDueDateConcept();
		Concept pregnancyConcept = getPregnancyConcept();
		Concept pregnancyStatusConcept = getPregnancyStatusConcept();

		return motechService.getActivePregnanciesDueDateObs(fromDueDate,
				toDueDate, pregnancyDueDateConcept, pregnancyConcept,
				pregnancyStatusConcept);
	}

	private void updatePregnancyDueDateObs(Obs pregnancyObs, Obs dueDateObs,
			Date newDueDate, Encounter encounter) {
		ObsService obsService = contextService.getObsService();
		MotechService motechService = contextService.getMotechService();

		Integer existingDueDateObsId = dueDateObs.getObsId();

		Obs newDueDateObs = createDateValueObs(
				encounter.getEncounterDatetime(), getDueDateConcept(),
				encounter.getPatient(), encounter.getLocation(), newDueDate,
				encounter, null);
		newDueDateObs.setObsGroup(pregnancyObs);
		newDueDateObs = obsService.saveObs(newDueDateObs, null);

		obsService.voidObs(dueDateObs, "Replaced by new EDD value Obs: "
				+ newDueDateObs.getObsId());

		// Update enrollments using duedate Obs to reference new duedate Obs
		List<MessageProgramEnrollment> enrollments = motechService
				.getActiveMessageProgramEnrollments(null, null,
						existingDueDateObsId);
		for (MessageProgramEnrollment enrollment : enrollments) {
			enrollment.setObsId(newDueDateObs.getObsId());
			motechService.saveMessageProgramEnrollment(enrollment);
		}
	}

	public Patient getPatientById(Integer patientId) {
		PatientService patientService = contextService.getPatientService();
		return patientService.getPatient(patientId);
	}

	public Obs getActivePregnancy(Integer patientId) {
		MotechService motechService = contextService.getMotechService();

		List<Obs> pregnancies = motechService.getActivePregnancies(patientId,
				getPregnancyConcept(), getPregnancyStatusConcept());
		if (pregnancies.isEmpty()) {
			return null;
		} else if (pregnancies.size() > 1) {
			log.warn("More than 1 active pregnancy found for patient: "
					+ patientId);
		}
		return pregnancies.get(0);
	}

	public List<ScheduledMessage> getAllScheduledMessages() {
		MotechService motechService = contextService.getMotechService();
		return motechService.getAllScheduledMessages();
	}

	public List<ScheduledMessage> getScheduledMessages(
			MessageProgramEnrollment enrollment) {
		MotechService motechService = contextService.getMotechService();
		return motechService.getScheduledMessages(null, null, enrollment, null);
	}

	public List<org.motechproject.server.model.Log> getAllLogs() {
		MotechService motechService = contextService.getMotechService();
		return motechService.getAllLogs();
	}

	/* Controller methods end */

	public List<Obs> getObs(Patient patient, String conceptName,
			String valueConceptName, Date minDate) {
		ObsService obsService = contextService.getObsService();
		ConceptService conceptService = contextService.getConceptService();

		Concept concept = conceptService.getConcept(conceptName);
		Concept value = conceptService.getConcept(valueConceptName);

		List<Concept> questions = new ArrayList<Concept>();
		questions.add(concept);

		List<Concept> answers = null;
		if (value != null) {
			answers = new ArrayList<Concept>();
			answers.add(value);
		}

		List<Person> whom = new ArrayList<Person>();
		whom.add(patient);

		return obsService.getObservations(whom, null, questions, answers, null,
				null, null, null, null, minDate, null, false);
	}

	public List<ExpectedObs> getExpectedObs(Patient patient, String group) {
		MotechService motechService = contextService.getMotechService();
		return motechService.getExpectedObs(patient, new String[] { group },
				null, null, null, null, false);
	}

	public ExpectedObs createExpectedObs(Patient patient, String conceptName,
			String valueConceptName, Integer value, Date minDate, Date dueDate,
			Date lateDate, Date maxDate, String name, String group) {
		ConceptService conceptService = contextService.getConceptService();

		Concept concept = conceptService.getConcept(conceptName);
		Concept valueConcept = conceptService.getConcept(valueConceptName);

		ExpectedObs expectedObs = new ExpectedObs();
		expectedObs.setPatient(patient);
		expectedObs.setConcept(concept);
		expectedObs.setValueCoded(valueConcept);
		if (value != null) {
			expectedObs.setValueNumeric(new Double(value));
		}
		expectedObs.setMinObsDatetime(minDate);
		expectedObs.setDueObsDatetime(dueDate);
		expectedObs.setLateObsDatetime(lateDate);
		expectedObs.setMaxObsDatetime(maxDate);
		expectedObs.setName(name);
		expectedObs.setGroup(group);

		return saveExpectedObs(expectedObs);
	}

	public ExpectedObs saveExpectedObs(ExpectedObs expectedObs) {
		if (log.isDebugEnabled()) {
			log.debug("Saving schedule update: " + expectedObs.toString());
		}
		if (expectedObs.getDueObsDatetime() != null
				&& expectedObs.getLateObsDatetime() != null) {

			MotechService motechService = contextService.getMotechService();
			return motechService.saveExpectedObs(expectedObs);
		} else {
			log
					.error("Attempt to store ExpectedObs with null due or late date");
			return null;
		}
	}

	public List<Encounter> getEncounters(Patient patient,
			String encounterTypeName, Date minDate) {
		EncounterService encounterService = contextService
				.getEncounterService();

		EncounterType encounterType = encounterService
				.getEncounterType(encounterTypeName);

		List<EncounterType> encounterTypes = new ArrayList<EncounterType>();
		encounterTypes.add(encounterType);

		return encounterService.getEncounters(patient, null, minDate, null,
				null, encounterTypes, null, false);
	}

	public List<ExpectedEncounter> getExpectedEncounters(Patient patient,
			String group) {
		MotechService motechService = contextService.getMotechService();
		return motechService.getExpectedEncounter(patient,
				new String[] { group }, null, null, null, null, false);
	}

	public ExpectedEncounter createExpectedEncounter(Patient patient,
			String encounterTypeName, Date minDate, Date dueDate,
			Date lateDate, Date maxDate, String name, String group) {
		EncounterService encounterService = contextService
				.getEncounterService();

		EncounterType encounterType = encounterService
				.getEncounterType(encounterTypeName);

		ExpectedEncounter expectedEncounter = new ExpectedEncounter();
		expectedEncounter.setPatient(patient);
		expectedEncounter.setEncounterType(encounterType);
		expectedEncounter.setMinEncounterDatetime(minDate);
		expectedEncounter.setDueEncounterDatetime(dueDate);
		expectedEncounter.setLateEncounterDatetime(lateDate);
		expectedEncounter.setMaxEncounterDatetime(maxDate);
		expectedEncounter.setName(name);
		expectedEncounter.setGroup(group);

		return saveExpectedEncounter(expectedEncounter);
	}

	public ExpectedEncounter saveExpectedEncounter(
			ExpectedEncounter expectedEncounter) {
		if (log.isDebugEnabled()) {
			log
					.debug("Saving schedule update: "
							+ expectedEncounter.toString());
		}
		if (expectedEncounter.getDueEncounterDatetime() != null
				&& expectedEncounter.getLateEncounterDatetime() != null) {

			MotechService motechService = contextService.getMotechService();
			return motechService.saveExpectedEncounter(expectedEncounter);
		} else {
			log
					.error("Attempt to store ExpectedEncounter with null due or late date");
			return null;
		}
	}

	/* PatientObsService methods start */
	public Date getPatientBirthDate(Integer patientId) {
		PatientService patientService = contextService.getPatientService();
		Patient patient = patientService.getPatient(patientId);
		return patient.getBirthdate();
	}

	private List<Obs> getMatchingObs(Person person, Concept question,
			Concept answer, Integer obsGroupId, Date from, Date to) {

		ObsService obsService = contextService.getObsService();

		List<Concept> questions = null;
		if (question != null) {
			questions = new ArrayList<Concept>();
			questions.add(question);
		}

		List<Concept> answers = null;
		if (answer != null) {
			answers = new ArrayList<Concept>();
			answers.add(answer);
		}

		List<Person> whom = new ArrayList<Person>();
		whom.add(person);

		// patients, encounters, questions, answers, persontype, locations,
		// sort, max returned, group id, from date, to date, include voided
		List<Obs> obsList = obsService.getObservations(whom, null, questions,
				answers, null, null, null, null, obsGroupId, from, to, false);

		return obsList;
	}

	public int getNumberOfObs(Integer personId, String conceptName,
			String conceptValue) {

		ConceptService conceptService = contextService.getConceptService();
		PersonService personService = contextService.getPersonService();
		return getNumberOfObs(personService.getPerson(personId), conceptService
				.getConcept(conceptName), conceptService
				.getConcept(conceptValue));
	}

	public Date getLastObsCreationDate(Integer personId, String conceptName,
			String conceptValue) {

		ConceptService conceptService = contextService.getConceptService();
		PersonService personService = contextService.getPersonService();
		return getLastObsCreationDate(personService.getPerson(personId),
				conceptService.getConcept(conceptName), conceptService
						.getConcept(conceptValue));
	}

	public Date getLastObsDate(Integer personId, String conceptName,
			String conceptValue) {

		ConceptService conceptService = contextService.getConceptService();
		PersonService personService = contextService.getPersonService();
		return getLastObsDate(personService.getPerson(personId), conceptService
				.getConcept(conceptName), conceptService
				.getConcept(conceptValue));
	}

	public Date getLastDoseObsDate(Integer personId, String conceptName,
			Integer doseNumber) {
		ConceptService conceptService = contextService.getConceptService();
		PersonService personService = contextService.getPersonService();
		ObsService obsService = contextService.getObsService();
		List<Obs> matchingObs = obsService.getObservationsByPersonAndConcept(
				personService.getPerson(personId), conceptService
						.getConcept(conceptName));
		for (Obs obs : matchingObs) {
			Double value = obs.getValueNumeric();
			if (value != null && doseNumber.intValue() == value.intValue()) {
				return obs.getObsDatetime();
			}
		}
		return null;
	}

	public Date getLastDoseObsDateInActivePregnancy(Integer patientId,
			String conceptName, Integer doseNumber) {
		PersonService personService = contextService.getPersonService();
		ConceptService conceptService = contextService.getConceptService();
		Obs pregnancy = getActivePregnancy(patientId);
		if (pregnancy != null) {
			Integer pregnancyObsId = pregnancy.getObsId();
			List<Obs> matchingObs = getMatchingObs(personService
					.getPerson(patientId), conceptService
					.getConcept(conceptName), null, pregnancyObsId, null, null);
			for (Obs obs : matchingObs) {
				Double value = obs.getValueNumeric();
				if (value != null && doseNumber.intValue() == value.intValue()) {
					return obs.getObsDatetime();
				}
			}
		}
		return null;
	}

	public Obs getActivePregnancyDueDateObs(Integer patientId, Obs pregnancy) {
		PersonService personService = contextService.getPersonService();
		if (pregnancy != null) {
			Integer pregnancyObsId = pregnancy.getObsId();
			List<Obs> dueDateObsList = getMatchingObs(personService
					.getPerson(patientId), getDueDateConcept(), null,
					pregnancyObsId, null, null);
			if (dueDateObsList.size() > 0) {
				return dueDateObsList.get(0);
			}
		}
		return null;
	}

	public Date getActivePregnancyDueDate(Integer patientId) {
		Obs pregnancy = getActivePregnancy(patientId);
		Obs dueDateObs = getActivePregnancyDueDateObs(patientId, pregnancy);
		if (dueDateObs != null) {
			return dueDateObs.getValueDatetime();
		}
		return null;
	}

	public Date getLastPregnancyEndDate(Integer patientId) {
		PersonService personService = contextService.getPersonService();
		List<Obs> pregnancyStatusObsList = getMatchingObs(personService
				.getPerson(patientId), getPregnancyStatusConcept(), null, null,
				null, null);
		for (Obs pregnancyStatusObs : pregnancyStatusObsList) {
			Boolean status = pregnancyStatusObs.getValueAsBoolean();
			if (Boolean.FALSE.equals(status)) {
				return pregnancyStatusObs.getObsDatetime();
			}
		}
		return null;
	}

	public Date getLastObsValue(Integer personId, String conceptName) {
		ConceptService conceptService = contextService.getConceptService();
		PersonService personService = contextService.getPersonService();
		return getLastObsValue(personService.getPerson(personId),
				conceptService.getConcept(conceptName));
	}

	public int getNumberOfObs(Person person, Concept concept, Concept value) {

		List<Obs> obsList = getMatchingObs(person, concept, value, null, null,
				null);
		return obsList.size();
	}

	public Date getLastObsCreationDate(Person person, Concept concept,
			Concept value) {

		Date latestObsDate = null;

		// List default sorted by Obs datetime
		List<Obs> obsList = getMatchingObs(person, concept, value, null, null,
				null);

		if (obsList.size() > 0) {
			latestObsDate = obsList.get(obsList.size() - 1).getDateCreated();
		} else if (log.isDebugEnabled()) {
			log.debug("No matching Obs: person id: " + person.getPersonId()
					+ ", concept: " + concept.getConceptId() + ", value: "
					+ (value != null ? value.getConceptId() : "null"));
		}
		return latestObsDate;
	}

	public Date getLastObsDate(Person person, Concept concept, Concept value) {

		Date latestObsDate = null;

		// List default sorted by Obs datetime
		List<Obs> obsList = getMatchingObs(person, concept, value, null, null,
				null);

		if (obsList.size() > 0) {
			latestObsDate = obsList.get(0).getObsDatetime();
		} else if (log.isDebugEnabled()) {
			log.debug("No matching Obs: person id: " + person.getPersonId()
					+ ", concept: " + concept.getConceptId() + ", value: "
					+ (value != null ? value.getConceptId() : "null"));
		}
		return latestObsDate;
	}

	public Date getLastObsValue(Person person, Concept concept) {
		Date lastestObsValue = null;

		List<Obs> obsList = getMatchingObs(person, concept, null, null, null,
				null);
		if (obsList.size() > 0) {
			lastestObsValue = obsList.get(0).getValueDatetime();
		} else if (log.isDebugEnabled()) {
			log.debug("No matching Obs: person id: " + person.getPersonId()
					+ ", concept: " + concept.getConceptId());
		}
		return lastestObsValue;
	}

	public Date getObsValue(Integer obsId) {
		ObsService obsService = contextService.getObsService();

		Date result = null;
		if (obsId != null) {
			Obs obs = obsService.getObs(obsId);
			if (obs != null) {
				result = obs.getValueDatetime();
			}
		}
		return result;
	}

	public Integer getObsId(Integer personId, String conceptName,
			String conceptValue, Date earliest, Date latest) {
		PersonService personService = contextService.getPersonService();
		ConceptService conceptService = contextService.getConceptService();

		List<Obs> observations = getMatchingObs(personService
				.getPerson(personId), conceptService.getConcept(conceptName),
				conceptService.getConcept(conceptValue), null, earliest, latest);
		if (observations.size() > 0) {
			observations.get(0).getObsId();
		}
		return null;
	}

	public Integer getObsId(Integer personId, String conceptName,
			Integer doseNumber, Date earliest, Date latest) {
		PersonService personService = contextService.getPersonService();
		ConceptService conceptService = contextService.getConceptService();

		List<Obs> observations = getMatchingObs(personService
				.getPerson(personId), conceptService.getConcept(conceptName),
				null, null, earliest, latest);
		for (Obs obs : observations) {
			Double value = obs.getValueNumeric();
			if (value != null && value.intValue() >= doseNumber.intValue()) {
				return obs.getObsId();
			}
		}
		return null;
	}

	public Integer getEncounterId(Integer patientId, String encounterType,
			Date earliest, Date latest) {
		PatientService patientService = contextService.getPatientService();
		EncounterService encounterService = contextService
				.getEncounterService();

		List<EncounterType> encounterTypes = new ArrayList<EncounterType>();
		encounterTypes.add(encounterService.getEncounterType(encounterType));

		List<Encounter> encounters = encounterService.getEncounters(
				patientService.getPatient(patientId), null, earliest, latest,
				null, encounterTypes, null, false);
		if (encounters.size() > 0) {
			return encounters.get(0).getEncounterId();
		}
		return null;
	}

	/* PatientObsService methods end */

	/* MessageSchedulerImpl methods start */
	public void scheduleMessage(String messageKey,
			MessageProgramEnrollment enrollment, Date messageDate,
			boolean userPreferenceBased) {
		// Return existing message definition
		MessageDefinition messageDefinition = this
				.getMessageDefinition(messageKey);

		// TODO: Assumes recipient is person in enrollment
		Integer messageRecipientId = enrollment.getPersonId();

		Date scheduledMessageDate;
		if (!userPreferenceBased) {
			scheduledMessageDate = messageDate;
		} else {
			scheduledMessageDate = this.determineUserPreferredMessageDate(
					messageRecipientId, messageDate);
		}

		// Cancel any unsent messages for the same enrollment and not matching
		// the message to schedule
		this.removeUnsentMessages(messageRecipientId, enrollment,
				messageDefinition, scheduledMessageDate);

		// Create new scheduled message (with pending attempt) for enrollment
		// if none matching already exist
		this.createScheduledMessage(messageRecipientId, messageDefinition,
				enrollment, scheduledMessageDate);
	}

	public ScheduledMessage scheduleCareMessage(String messageKey,
			MessageProgramEnrollment enrollment, Date messageDate,
			boolean userPreferenceBased, String care) {
		// Return existing message definition
		MessageDefinition messageDefinition = this
				.getMessageDefinition(messageKey);

		// TODO: Assumes recipient is person in enrollment
		Integer messageRecipientId = enrollment.getPersonId();

		Date scheduledMessageDate;
		if (!userPreferenceBased) {
			scheduledMessageDate = messageDate;
		} else {
			scheduledMessageDate = this.determineUserPreferredMessageDate(
					messageRecipientId, messageDate);
		}

		// Create new scheduled message (with pending attempt) for enrollment
		// Does not check if one already exists
		return this.createCareScheduledMessage(messageRecipientId,
				messageDefinition, enrollment, scheduledMessageDate, care);
	}

	private MessageDefinition getMessageDefinition(String messageKey) {
		MotechService motechService = contextService.getMotechService();
		MessageDefinition messageDefinition = motechService
				.getMessageDefinition(messageKey);
		if (messageDefinition == null) {
			log.error("Invalid message key for message definition: "
					+ messageKey);
		}
		return messageDefinition;
	}

	protected void removeUnsentMessages(Integer recipientId,
			MessageProgramEnrollment enrollment,
			MessageDefinition messageDefinition, Date messageDate) {
		MotechService motechService = contextService.getMotechService();
		// Get Messages matching the recipient, enrollment, and status, but
		// not matching the message definition and message date
		List<Message> unsentMessages = motechService.getMessages(recipientId,
				enrollment, messageDefinition, messageDate,
				MessageStatus.SHOULD_ATTEMPT);
		log.debug("Unsent messages found during scheduling: "
				+ unsentMessages.size());

		for (Message unsentMessage : unsentMessages) {
			unsentMessage.setAttemptStatus(MessageStatus.CANCELLED);
			motechService.saveMessage(unsentMessage);

			log.debug("Message cancelled to schedule new: Id: "
					+ unsentMessage.getId());
		}
	}

	public void removeUnsentMessages(List<ScheduledMessage> scheduledMessages) {
		MotechService motechService = contextService.getMotechService();

		for (ScheduledMessage scheduledMessage : scheduledMessages) {
			for (Message unsentMessage : scheduledMessage.getMessageAttempts()) {
				if (MessageStatus.SHOULD_ATTEMPT == unsentMessage
						.getAttemptStatus()) {

					unsentMessage.setAttemptStatus(MessageStatus.CANCELLED);
					motechService.saveMessage(unsentMessage);

					log
							.debug("Message cancelled: Id: "
									+ unsentMessage.getId());
				}
			}
		}
	}

	public void addMessageAttempt(ScheduledMessage scheduledMessage,
			Date attemptDate) {
		MotechService motechService = contextService.getMotechService();
		MessageDefinition messageDefinition = scheduledMessage.getMessage();

		Message message = messageDefinition.createMessage(scheduledMessage);
		message.setAttemptDate(attemptDate);
		scheduledMessage.getMessageAttempts().add(message);

		motechService.saveScheduledMessage(scheduledMessage);
	}

	public void removeAllUnsentMessages(MessageProgramEnrollment enrollment) {
		MotechService motechService = contextService.getMotechService();
		List<Message> unsentMessages = motechService.getMessages(enrollment,
				MessageStatus.SHOULD_ATTEMPT);
		log.debug("Unsent messages found to cancel: " + unsentMessages.size()
				+ ", for enrollment: " + enrollment.getId());

		for (Message unsentMessage : unsentMessages) {
			unsentMessage.setAttemptStatus(MessageStatus.CANCELLED);
			motechService.saveMessage(unsentMessage);

			log.debug("Message cancelled: Id: " + unsentMessage.getId());
		}
	}

	private Date determineUserPreferredMessageDate(Integer recipientId,
			Date messageDate) {
		PersonService personService = contextService.getPersonService();
		Person recipient = personService.getPerson(recipientId);

		return determineMessageStartDate(recipient, messageDate);
	}

	private void createScheduledMessage(Integer recipientId,
			MessageDefinition messageDefinition,
			MessageProgramEnrollment enrollment, Date messageDate) {

		MotechService motechService = contextService.getMotechService();

		List<ScheduledMessage> scheduledMessages = motechService
				.getScheduledMessages(recipientId, messageDefinition,
						enrollment, messageDate);

		if (scheduledMessages.size() == 0) {
			if (log.isDebugEnabled()) {
				log.debug("Creating ScheduledMessage: recipient: "
						+ recipientId + ", enrollment: " + enrollment.getId()
						+ ", message key: " + messageDefinition.getMessageKey()
						+ ", date: " + messageDate);
			}

			ScheduledMessage scheduledMessage = new ScheduledMessage();
			scheduledMessage.setScheduledFor(messageDate);
			scheduledMessage.setRecipientId(recipientId);
			scheduledMessage.setMessage(messageDefinition);
			scheduledMessage.setEnrollment(enrollment);

			Message message = messageDefinition.createMessage(scheduledMessage);
			message.setAttemptDate(messageDate);
			scheduledMessage.getMessageAttempts().add(message);

			motechService.saveScheduledMessage(scheduledMessage);
		}
	}

	private ScheduledMessage createCareScheduledMessage(Integer recipientId,
			MessageDefinition messageDefinition,
			MessageProgramEnrollment enrollment, Date messageDate, String care) {

		MotechService motechService = contextService.getMotechService();

		if (log.isDebugEnabled()) {
			log.debug("Creating ScheduledMessage: recipient: " + recipientId
					+ ", enrollment: " + enrollment.getId() + ", message key: "
					+ messageDefinition.getMessageKey() + ", date: "
					+ messageDate);
		}

		ScheduledMessage scheduledMessage = new ScheduledMessage();
		scheduledMessage.setScheduledFor(messageDate);
		scheduledMessage.setRecipientId(recipientId);
		scheduledMessage.setMessage(messageDefinition);
		scheduledMessage.setEnrollment(enrollment);
		// Set care field on scheduled message (not set on informational
		// messages)
		scheduledMessage.setCare(care);

		Message message = messageDefinition.createMessage(scheduledMessage);
		message.setAttemptDate(messageDate);
		scheduledMessage.getMessageAttempts().add(message);

		return motechService.saveScheduledMessage(scheduledMessage);
	}

	/* MessageSchedulerImpl methods end */

	/* Activator methods start */
	public void addInitialData() {

		UserService userService = contextService.getUserService();
		User admin = userService.getUser(1);

		log.info("Verifying Person Attributes Exist");
		createPersonAttributeType(MotechConstants.PERSON_ATTRIBUTE_CHPS_ID,
				"A nurse's CHPS ID.", String.class.getName(), admin);
		createPersonAttributeType(
				MotechConstants.PERSON_ATTRIBUTE_PHONE_NUMBER,
				"A person's phone number.", String.class.getName(), admin);
		createPersonAttributeType(MotechConstants.PERSON_ATTRIBUTE_NHIS_NUMBER,
				"A person's NHIS number.", String.class.getName(), admin);
		createPersonAttributeType(
				MotechConstants.PERSON_ATTRIBUTE_NHIS_EXP_DATE,
				"A person's NHIS expiration date.", AttributableDate.class
						.getName(), admin);
		createPersonAttributeType(MotechConstants.PERSON_ATTRIBUTE_LANGUAGE,
				"A person's language preference for messages.", String.class
						.getName(), admin);
		createPersonAttributeType(
				MotechConstants.PERSON_ATTRIBUTE_PHONE_TYPE,
				"A person's phone ownership type (PERSONAL, HOUSEHOLD, or PUBLIC).",
				String.class.getName(), admin);
		createPersonAttributeType(MotechConstants.PERSON_ATTRIBUTE_MEDIA_TYPE,
				"A person's media type preference for messages.", String.class
						.getName(), admin);
		createPersonAttributeType(
				MotechConstants.PERSON_ATTRIBUTE_DELIVERY_DAY,
				"A person's preferred delivery day (SUNDAY to SATURDAY).",
				String.class.getName(), admin);
		createPersonAttributeType(
				MotechConstants.PERSON_ATTRIBUTE_DELIVERY_TIME,
				"A person's preferred delivery time (HH:mm).", String.class
						.getName(), admin);
		createPersonAttributeType(MotechConstants.PERSON_ATTRIBUTE_INSURED,
				"Is person insured? (TRUE OR FALSE)", String.class.getName(),
				admin);
		createPersonAttributeType(MotechConstants.PERSON_ATTRIBUTE_HOW_LEARNED,
				"How person found out about services.", String.class.getName(),
				admin);
		createPersonAttributeType(
				MotechConstants.PERSON_ATTRIBUTE_WHY_INTERESTED,
				"Why person is interested in services "
						+ "(IN_HOUSEHOLD_PREGNANCY, OUT_HOUSEHOLD_PREGNANCY, or IN_HOUSEHOLD_BIRTH).",
				String.class.getName(), admin);
		createPersonAttributeType(MotechConstants.PERSON_ATTRIBUTE_COMMUNITY,
				"A person's community (as Location id).", Location.class
						.getName(), admin);

		log.info("Verifying Patient Identifier Exist");
		PatientIdentifierType motechIDType = createPatientIdentifierType(
				MotechConstants.PATIENT_IDENTIFIER_MOTECH_ID,
				"Patient Id for MoTeCH system.",
				MotechIdVerhoeffValidator.class.getName(), admin);

		log.info("Verifying Patient Identifier Generator Exists");
		createSequentialPatientIdentifierGenerator(
				MotechConstants.IDGEN_SEQ_ID_GEN_MOTECH_ID, motechIDType,
				MotechIdVerhoeffValidator.ALLOWED_CHARS,
				MotechConstants.IDGEN_SEQ_ID_GEN_FIRST_MOTECH_ID_BASE,
				MotechIdVerhoeffValidator.VERHOEFF_ID_LENGTH);

		log.info("Verifying Locations Exist");
		Location ghana = createLocation(MotechConstants.LOCATION_GHANA,
				"Republic of Ghana, Country, Root in hierarchy", null, admin);
		Location upperEast = createLocation(
				MotechConstants.LOCATION_UPPER_EAST,
				"Upper East Region in Ghana", ghana, admin);
		createLocation(MotechConstants.LOCATION_KASSENA_NANKANA,
				"Kassena-Nankana District in Upper East Region, Ghana",
				upperEast, admin);
		Location kassenaNankanaWest = createLocation(
				MotechConstants.LOCATION_KASSENA_NANKANA_WEST,
				"Kassena-Nankana West District in Upper East Region, Ghana",
				upperEast, admin);
		Location westTest = createLocation(
				"West Test Community",
				"Test Community in Kassena-Nankana West District, Upper East Region, Ghana",
				kassenaNankanaWest, admin);
		createLocation(
				"West Test Clinic",
				"Test Clinic in West Test Community, Kassena-Nankana West District, Upper East Region, Ghana",
				westTest, admin);

		log.info("Verifying Encounter Types Exist");
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_ANCVISIT,
				"Ghana Antenatal Care (ANC) Visit", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_PREGREGVISIT,
				"Ghana Pregnancy Registration Visit", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_PREGTERMVISIT,
				"Ghana Pregnancy Termination Visit", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_PREGDELVISIT,
				"Ghana Pregnancy Delivery Visit", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_GENERALVISIT,
				"Ghana General Visit", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_TTVISIT,
				"Ghana Tetanus outside Pregnancy Visit", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_CWCVISIT,
				"Ghana Child Immunization Visit", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_PNCMOTHERVISIT,
				"Ghana Mother Postnatal Care (PNC) Visit", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_PNCCHILDVISIT,
				"Ghana Child Postnatal Care (PNC) Visit", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_PREGDELNOTIFYVISIT,
				"Ghana Pregnancy Delivery Notification", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_ANCREGVISIT,
				"Ghana Antental Care (ANC) Registration", admin);
		createEncounterType(MotechConstants.ENCOUNTER_TYPE_CWCREGVISIT,
				"Ghana Child Immunization Registration", admin);

		log.info("Verifying Concepts Exist");
		createConcept(MotechConstants.CONCEPT_VISIT_NUMBER, "Visit Number",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_TETANUS_TOXOID_DOSE,
				"Dose Number for Tetanus Toxoid Vaccination",
				MotechConstants.CONCEPT_CLASS_DRUG,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(
				MotechConstants.CONCEPT_INTERMITTENT_PREVENTATIVE_TREATMENT_DOSE,
				"Dose Number for Malaria Treatment",
				MotechConstants.CONCEPT_CLASS_DRUG,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(
				MotechConstants.CONCEPT_HIV_TEST_RESULT,
				"Question: \"What is the patient's text coded HIV test result?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_TEXT, admin);
		createConcept(MotechConstants.CONCEPT_TERMINATION_TYPE,
				"Numeric coded pregnancy termination reason",
				MotechConstants.CONCEPT_CLASS_FINDING,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_TERMINATION_COMPLICATION,
				"Numeric coded pregnancy termination complication",
				MotechConstants.CONCEPT_CLASS_FINDING,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(
				MotechConstants.CONCEPT_INTERMITTENT_PREVENTATIVE_TREATMENT_INFANTS,
				"Malaria Treatment for infants.",
				MotechConstants.CONCEPT_CLASS_DRUG,
				MotechConstants.CONCEPT_DATATYPE_N_A, admin);
		createConcept(
				MotechConstants.CONCEPT_INSECTICIDE_TREATED_NET_USAGE,
				"Question on encounter form: \"Does the patient use insecticide-treated nets?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(MotechConstants.CONCEPT_ORAL_POLIO_VACCINATION_DOSE,
				"Dose Number for child Oral Polio vaccination.",
				MotechConstants.CONCEPT_CLASS_DRUG,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_PENTA_VACCINATION_DOSE,
				"Dose Number for child Penta vaccination.",
				MotechConstants.CONCEPT_CLASS_DRUG,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(
				MotechConstants.CONCEPT_CEREBRO_SPINAL_MENINGITIS_VACCINATION,
				"Vaccination against Cerebro-Spinal Meningitis.",
				MotechConstants.CONCEPT_CLASS_DRUG,
				MotechConstants.CONCEPT_DATATYPE_N_A, admin);
		createConcept(MotechConstants.CONCEPT_VITAMIN_A,
				"Supplement for Vitamin A.",
				MotechConstants.CONCEPT_CLASS_DRUG,
				MotechConstants.CONCEPT_DATATYPE_N_A, admin);
		createConcept(
				MotechConstants.CONCEPT_DATE_OF_CONFINEMENT_CONFIRMED,
				"Question: \"Is the pregnancy due date confirmed by the CHW?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(MotechConstants.CONCEPT_ENROLLMENT_REFERENCE_DATE,
				"Reference Date for Message Program Enrollment",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_DATETIME, admin);
		createConcept(MotechConstants.CONCEPT_CAUSE_OF_DEATH,
				"Numeric coded cause of patient death",
				MotechConstants.CONCEPT_CLASS_DIAGNOSIS,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_MATERNAL_CAUSE_OF_DEATH,
				"Numeric coded maternal cause of patient death",
				MotechConstants.CONCEPT_CLASS_DIAGNOSIS,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_SERIAL_NUMBER,
				"Patient register serial number",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_TEXT, admin);
		createConcept(MotechConstants.CONCEPT_NEW_CASE,
				"Question: \"Is this a new case?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(MotechConstants.CONCEPT_REFERRED,
				"Question: \"Was patient referred?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(MotechConstants.CONCEPT_PRIMARY_DIAGNOSIS,
				"Numeric coded primary diagnosis",
				MotechConstants.CONCEPT_CLASS_DIAGNOSIS,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_SECONDARY_DIAGNOSIS,
				"Numeric coded secondary diagnosis",
				MotechConstants.CONCEPT_CLASS_DIAGNOSIS,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_DELIVERY_METHOD,
				"Numeric coded method of delivery",
				MotechConstants.CONCEPT_CLASS_FINDING,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_DELIVERY_LOCATION,
				"Numeric coded place of delivery",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_DELIVERED_BY,
				"Numeric coded who performed delivery",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_DELIVERY_OUTCOME,
				"Numeric coded outcome of delivery",
				MotechConstants.CONCEPT_CLASS_FINDING,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_BIRTH_OUTCOME,
				"Text coded birth outcome",
				MotechConstants.CONCEPT_CLASS_FINDING,
				MotechConstants.CONCEPT_DATATYPE_TEXT, admin);
		createConcept(MotechConstants.CONCEPT_MALARIA_RAPID_TEST,
				"Rapid diagnostic test for malaria",
				MotechConstants.CONCEPT_CLASS_TEST,
				MotechConstants.CONCEPT_DATATYPE_CODED, admin);
		createConcept(
				MotechConstants.CONCEPT_VDRL_TREATMENT,
				"Question on encounter form: \"Was the patient given treatment for syphilis?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(MotechConstants.CONCEPT_URINE_PROTEIN_TEST,
				"Test for protein in urine",
				MotechConstants.CONCEPT_CLASS_TEST,
				MotechConstants.CONCEPT_DATATYPE_CODED, admin);
		createConcept(MotechConstants.CONCEPT_URINE_GLUCOSE_TEST,
				"Test for glucose in urine",
				MotechConstants.CONCEPT_CLASS_TEST,
				MotechConstants.CONCEPT_DATATYPE_CODED, admin);
		createConcept(MotechConstants.CONCEPT_FETAL_HEART_RATE,
				"Fetal heart rate (in bpm)",
				MotechConstants.CONCEPT_CLASS_TEST,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_FUNDAL_HEIGHT,
				"Measurement of uterus (in cm)",
				MotechConstants.CONCEPT_CLASS_TEST,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_VVF_REPAIR,
				"Numeric coded value for Vesico Vaginal Fistula (VVF) repair",
				MotechConstants.CONCEPT_CLASS_FINDING,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(
				MotechConstants.CONCEPT_DEWORMER,
				"Question on encounter form: \"Was dewormer given to patient?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(MotechConstants.CONCEPT_PMTCT,
				"Preventing Mother-to-Child Transmission (PMTCT) of HIV",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(
				MotechConstants.CONCEPT_PMTCT_TREATMENT,
				"Question on encounter form: \"Was PMTCT HIV treatment given to patient?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(
				MotechConstants.CONCEPT_ACT_TREATMENT,
				"Question on encounter form: \"Was Artemisinin-based Combination Therapy (ACT) for Malaria given to patient?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(
				MotechConstants.CONCEPT_HIV_PRE_TEST_COUNSELING,
				"Question on encounter form: \"Was counseling done before HIV test?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(
				MotechConstants.CONCEPT_HIV_POST_TEST_COUNSELING,
				"Question on encounter form: \"Was counseling done after HIV test?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(MotechConstants.CONCEPT_DELIVERY_COMPLICATION,
				"Numeric coded value for delivery or post-partum complication",
				MotechConstants.CONCEPT_CLASS_FINDING,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(
				MotechConstants.CONCEPT_POST_ABORTION_FP_COUNSELING,
				"Question on encounter form: \"Was family planning counseling done after abortion?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(
				MotechConstants.CONCEPT_POST_ABORTION_FP_ACCEPTED,
				"Question on encounter form: \"Did the patient accept family planning after abortion?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(
				MotechConstants.CONCEPT_IPT_REACTION,
				"Intermittent Preventive Treatment (IPT) with Sulphadoxine-Pyrimethamine (SP)",
				MotechConstants.CONCEPT_CLASS_TEST,
				MotechConstants.CONCEPT_DATATYPE_CODED, admin);
		createConcept(MotechConstants.CONCEPT_LOCHIA_COLOUR,
				"Numeric coded colour of post-partum vaginal discharge",
				MotechConstants.CONCEPT_CLASS_FINDING,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_LOCHIA_EXCESS_AMOUNT,
				"Is amount of post-partum vaginal discharge in excess?",
				MotechConstants.CONCEPT_CLASS_FINDING,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(MotechConstants.CONCEPT_MIDDLE_UPPER_ARM_CIRCUMFERENCE,
				"Circumference of Middle Upper Arm (MUAC) in cm",
				MotechConstants.CONCEPT_CLASS_FINDING,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(
				MotechConstants.CONCEPT_MATERNAL_DEATH,
				"Question on encounter form: \"Death of patient during delivery or abortion procedure?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(MotechConstants.CONCEPT_TERMINATION_PROCEDURE,
				"Numeric coded procedure used to terminate pregnancy",
				MotechConstants.CONCEPT_CLASS_PROCEDURE,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(
				MotechConstants.CONCEPT_CORD_CONDITION,
				"Question on encounter form: \"Is the condition of the umbilical cord normal?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(
				MotechConstants.CONCEPT_CONDITION_OF_BABY,
				"Question on encounter form: \"Is the condition of the baby good?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(
				MotechConstants.CONCEPT_NEXT_ANC_DATE,
				"Question on encounter form: \"What is the date of the next antenatal care visit for the patient?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_DATETIME, admin);
		createConcept(
				MotechConstants.CONCEPT_MALE_INVOLVEMENT,
				"Question on encounter form: \"Was the male household member involved in patient's care?\"",
				MotechConstants.CONCEPT_CLASS_QUESTION,
				MotechConstants.CONCEPT_DATATYPE_BOOLEAN, admin);
		createConcept(MotechConstants.CONCEPT_COMMUNITY,
				"Community details on the location of care",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_TEXT, admin);
		createConcept(MotechConstants.CONCEPT_HOUSE,
				"House details on the location of care",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_TEXT, admin);
		createConcept(MotechConstants.CONCEPT_ANC_PNC_LOCATION,
				"Numeric coded location of ANC or PNC care",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_CWC_LOCATION,
				"Numeric coded location of CWC care",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_NUMERIC, admin);
		createConcept(MotechConstants.CONCEPT_COMMENTS, "Comments",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_TEXT, admin);
		createConcept(MotechConstants.CONCEPT_ANC_REG_NUMBER,
				"Ghana ANC Registration Number",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_TEXT, admin);
		createConcept(MotechConstants.CONCEPT_CWC_REG_NUMBER,
				"Ghana CWC Registration Number",
				MotechConstants.CONCEPT_CLASS_MISC,
				MotechConstants.CONCEPT_DATATYPE_TEXT, admin);

		log.info("Verifying Concepts Exist as Answers");
		// TODO: Add IPT to proper Concept as an Answer, not an immunization
		addConceptAnswers(
				MotechConstants.CONCEPT_IMMUNIZATIONS_ORDERED,
				new String[] {
						MotechConstants.CONCEPT_YELLOW_FEVER_VACCINATION,
						MotechConstants.CONCEPT_INTERMITTENT_PREVENTATIVE_TREATMENT_INFANTS,
						MotechConstants.CONCEPT_VITAMIN_A,
						MotechConstants.CONCEPT_CEREBRO_SPINAL_MENINGITIS_VACCINATION },
				admin);
		addConceptAnswers(MotechConstants.CONCEPT_MALARIA_RAPID_TEST,
				new String[] { MotechConstants.CONCEPT_POSITIVE,
						MotechConstants.CONCEPT_NEGATIVE }, admin);
		addConceptAnswers(MotechConstants.CONCEPT_URINE_PROTEIN_TEST,
				new String[] { MotechConstants.CONCEPT_POSITIVE,
						MotechConstants.CONCEPT_NEGATIVE }, admin);
		addConceptAnswers(MotechConstants.CONCEPT_URINE_GLUCOSE_TEST,
				new String[] { MotechConstants.CONCEPT_POSITIVE,
						MotechConstants.CONCEPT_NEGATIVE }, admin);
		addConceptAnswers(MotechConstants.CONCEPT_IPT_REACTION, new String[] {
				MotechConstants.CONCEPT_REACTIVE,
				MotechConstants.CONCEPT_NON_REACTIVE }, admin);

		log.info("Verifying Task Exists and is Scheduled");
		// TODO: Task should start automatically on startup, Boolean.TRUE
		Map<String, String> immProps = new HashMap<String, String>();
		immProps.put(MotechConstants.TASK_PROPERTY_SEND_IMMEDIATE, Boolean.TRUE
				.toString());
		createTask(MotechConstants.TASK_IMMEDIATE_NOTIFICATION,
				"Task to send out immediate SMS notifications", new Date(),
				new Long(30), Boolean.FALSE, NotificationTask.class.getName(),
				admin, immProps);
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		Map<String, String> dailyProps = new HashMap<String, String>();
		dailyProps.put(MotechConstants.TASK_PROPERTY_TIME_OFFSET,
				new Long(3600).toString());
		createTask(MotechConstants.TASK_DAILY_NOTIFICATION,
				"Task to send out SMS notifications for next day", calendar
						.getTime(), new Long(86400), Boolean.FALSE,
				NotificationTask.class.getName(), admin, dailyProps);
		createTask(MotechConstants.TASK_MESSAGEPROGRAM_UPDATE,
				"Task to update message program state for patients",
				new Date(), new Long(30), Boolean.FALSE,
				MessageProgramUpdateTask.class.getName(), admin, null);

		Map<String, String> dailyNurseProps = new HashMap<String, String>();
		dailyNurseProps.put(MotechConstants.TASK_PROPERTY_SEND_UPCOMING,
				Boolean.TRUE.toString());
		String[] dailyGroups = { "PPC", "PNC" };
		String dailyGroupsProperty = StringUtils.join(dailyGroups,
				MotechConstants.TASK_PROPERTY_CARE_GROUPS_DELIMITER);
		dailyNurseProps.put(MotechConstants.TASK_PROPERTY_CARE_GROUPS,
				dailyGroupsProperty);
		dailyNurseProps.put(MotechConstants.TASK_PROPERTY_DELIVERY_TIME_OFFSET,
				new Long(36000).toString());
		createTask(MotechConstants.TASK_DAILY_NURSE_CARE_MESSAGING,
				"Task to send out nurse SMS care messages for next day",
				calendar.getTime(), new Long(86400), Boolean.FALSE,
				NurseCareMessagingTask.class.getName(), admin, dailyNurseProps);

		calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
		Map<String, String> weeklyNurseProps = new HashMap<String, String>();
		String[] weeklyGroups = { "ANC", "TT", "IPT", "BCG", "OPV", "Penta",
				"YellowFever", "Measles", "IPTI", "VitaA" };
		String weeklyGroupsProperty = StringUtils.join(weeklyGroups,
				MotechConstants.TASK_PROPERTY_CARE_GROUPS_DELIMITER);
		weeklyNurseProps.put(MotechConstants.TASK_PROPERTY_CARE_GROUPS,
				weeklyGroupsProperty);
		weeklyNurseProps.put(
				MotechConstants.TASK_PROPERTY_DELIVERY_TIME_OFFSET, new Long(
						36000).toString());
		createTask(MotechConstants.TASK_WEEKLY_NURSE_CARE_MESSAGING,
				"Task to send out nurse SMS care messages for week", calendar
						.getTime(), new Long(604800), Boolean.FALSE,
				NurseCareMessagingTask.class.getName(), admin, weeklyNurseProps);
	}

	private void createPersonAttributeType(String name, String description,
			String format, User creator) {
		PersonService personService = contextService.getPersonService();
		PersonAttributeType attrType = personService
				.getPersonAttributeTypeByName(name);
		if (attrType == null) {
			log.info(name + " PersonAttributeType Does Not Exist - Creating");
			attrType = new PersonAttributeType();
		}
		attrType.setName(name);
		attrType.setDescription(description);
		attrType.setFormat(format);
		attrType.setCreator(creator);
		personService.savePersonAttributeType(attrType);
	}

	private String generateMotechId() {
		String motechId = null;
		PatientIdentifierType motechIdType = getMotechPatientIdType();
		try {
			IdentifierSourceService idSourceService = contextService
					.getIdentifierSourceService();

			SequentialIdentifierGenerator idGenerator = getSeqIdGenerator(
					MotechConstants.IDGEN_SEQ_ID_GEN_MOTECH_ID, motechIdType);
			motechId = idSourceService.generateIdentifier(idGenerator,
					MotechConstants.IDGEN_SEQ_ID_GEN_MOTECH_ID_GEN_COMMENT);

		} catch (Exception e) {
			log.error("Error generating Motech Id using Idgen module", e);
		}
		return motechId;
	}

	private void excludeIdForGenerator(String motechId) {
		PatientIdentifierType motechIdType = getMotechPatientIdType();
		try {
			IdentifierSourceService idSourceService = contextService
					.getIdentifierSourceService();

			SequentialIdentifierGenerator idGenerator = getSeqIdGenerator(
					MotechConstants.IDGEN_SEQ_ID_GEN_MOTECH_ID, motechIdType);

			// Persisted only if match for source and id doesn't already exist
			LogEntry newLog = new LogEntry();
			newLog.setSource(idGenerator);
			newLog.setIdentifier(motechId);
			newLog.setDateGenerated(new Date());
			newLog.setGeneratedBy(contextService.getAuthenticatedUser());
			newLog
					.setComment(MotechConstants.IDGEN_SEQ_ID_GEN_MOTECH_ID_MANUAL_COMMENT);
			idSourceService.saveLogEntry(newLog);

		} catch (Exception e) {
			log.error("Error verifying Motech Id in Log of Idgen module", e);
		}
	}

	private SequentialIdentifierGenerator getSeqIdGenerator(String name,
			PatientIdentifierType identifierType) {

		SequentialIdentifierGenerator idGenerator = null;
		try {
			IdentifierSourceService idSourceService = contextService
					.getIdentifierSourceService();

			List<IdentifierSource> idSources = idSourceService
					.getAllIdentifierSources(false);

			for (IdentifierSource idSource : idSources) {
				if (idSource instanceof SequentialIdentifierGenerator
						&& idSource.getName().equals(name)
						&& idSource.getIdentifierType().equals(identifierType)) {
					idGenerator = (SequentialIdentifierGenerator) idSource;
					break;
				}
			}
		} catch (Exception e) {
			log.error("Error retrieving Patient Id generator in Idgen module",
					e);
		}
		return idGenerator;
	}

	private void createSequentialPatientIdentifierGenerator(String name,
			PatientIdentifierType identifierType, String baseCharacterSet,
			String firstIdentifierBase, Integer length) {
		try {
			IdentifierSourceService idSourceService = contextService
					.getIdentifierSourceService();

			SequentialIdentifierGenerator idGenerator = getSeqIdGenerator(name,
					identifierType);

			if (idGenerator == null) {
				idGenerator = new SequentialIdentifierGenerator();
				log
						.info(name
								+ " Sequential Patient Id Generator Does Not Exist - Creating");
			}

			idGenerator.setName(name);
			idGenerator.setIdentifierType(identifierType);
			idGenerator.setBaseCharacterSet(baseCharacterSet);
			idGenerator.setFirstIdentifierBase(firstIdentifierBase);
			idGenerator.setLength(length);

			idSourceService.saveIdentifierSource(idGenerator);

		} catch (Exception e) {
			log.error("Error creating Patient Id generator in Idgen module", e);
		}
	}

	private PatientIdentifierType createPatientIdentifierType(String name,
			String description, String validator, User creator) {
		PatientService patientService = contextService.getPatientService();
		PatientIdentifierType idType = patientService
				.getPatientIdentifierTypeByName(name);
		if (idType == null) {
			log.info(name + " PatientIdentifierType Does Not Exist - Creating");
			idType = new PatientIdentifierType();
		}
		idType.setName(name);
		idType.setDescription(description);
		idType.setCreator(creator);
		idType.setValidator(validator);
		return patientService.savePatientIdentifierType(idType);
	}

	private Location createLocation(String name, String description,
			Location parent, User creator) {
		LocationService locationService = contextService.getLocationService();
		Location location = locationService.getLocation(name);
		if (location == null) {
			log.info(name + " Location Does Not Exist - Creating");
			location = new Location();
		}
		location.setName(name);
		location.setDescription(description);
		location.setCreator(creator);

		copyParentHierarchy(parent, location);

		locationService.saveLocation(location);

		if (parent != null
				&& (parent.getChildLocations() == null || !parent
						.getChildLocations().contains(location))) {
			parent.addChildLocation(location);
			locationService.saveLocation(parent);
		}
		return location;
	}

	private void copyParentHierarchy(Location parent, Location child) {
		child.setCountry(null);
		child.setRegion(null);
		child.setCountyDistrict(null);
		child.setCityVillage(null);
		child.setNeighborhoodCell(null);

		if (parent == null) {
			child.setCountry(child.getName());
		} else {
			String country = parent.getCountry();
			String region = parent.getRegion();
			String district = parent.getCountyDistrict();
			String community = parent.getCityVillage();

			if (country != null) {
				child.setCountry(country);
				if (region != null) {
					child.setRegion(region);
					if (district != null) {
						child.setCountyDistrict(district);
						if (community != null) {
							child.setCityVillage(community);
							child.setNeighborhoodCell(child.getName());
						} else {
							child.setCityVillage(child.getName());
						}
					} else {
						child.setCountyDistrict(child.getName());
					}
				} else {
					child.setRegion(child.getName());
				}
			} else {
				child.setCountry(child.getName());
			}
		}
	}

	private void createEncounterType(String name, String description,
			User creator) {
		EncounterService encounterService = contextService
				.getEncounterService();
		EncounterType encType = encounterService.getEncounterType(name);
		if (encType == null) {
			log.info(name + " EncounterType Does Not Exist - Creating");
			encType = new EncounterType();
			encType.setName(name);
			encType.setDescription(description);
			encType.setCreator(creator);
			encounterService.saveEncounterType(encType);
		}
	}

	private Concept createConcept(String name, String description,
			String className, String dataTypeName, User creator) {
		ConceptService conceptService = contextService.getConceptService();
		// Default "en" Locale matching other existing concepts
		Locale defaultLocale = Locale.ENGLISH;
		Concept concept = conceptService.getConcept(name);
		ConceptNameTag prefTag = conceptService
				.getConceptNameTagByName(ConceptNameTag.PREFERRED);
		if (concept == null) {
			log.info(name + " Concept Does Not Exist - Creating");
			concept = new Concept();
			ConceptName conceptName = new ConceptName(name, defaultLocale);
			conceptName.addTag(prefTag);
			conceptName.setCreator(creator);
			// AddTag is workaround since the following results in
			// "preferred_en" instead of "preferred"
			// itn.setPreferredName(defaultLocale, conceptName)
			concept.addName(conceptName);
			ConceptDescription conceptDescription = new ConceptDescription(
					description, defaultLocale);
			conceptDescription.setCreator(creator);
			concept.addDescription(conceptDescription);
			concept.setConceptClass(conceptService
					.getConceptClassByName(className));
			concept.setDatatype(conceptService
					.getConceptDatatypeByName(dataTypeName));
			concept.setCreator(creator);
			concept = conceptService.saveConcept(concept);
		} else {
			log.info(name + " Concept Exists");
		}
		return concept;
	}

	private void addConceptAnswers(String conceptName, String[] answerNames,
			User creator) {
		ConceptService conceptService = contextService.getConceptService();
		Concept concept = conceptService.getConcept(conceptName);
		Set<Integer> currentAnswerIds = new HashSet<Integer>();
		for (ConceptAnswer answer : concept.getAnswers()) {
			currentAnswerIds.add(answer.getAnswerConcept().getConceptId());
		}
		boolean changed = false;
		for (String answerName : answerNames) {
			Concept answer = conceptService.getConcept(answerName);
			if (!currentAnswerIds.contains(answer.getConceptId())) {
				log.info("Adding Concept Answer " + answerName + " to "
						+ conceptName);
				changed = true;
				ConceptAnswer conceptAnswer = new ConceptAnswer(answer);
				conceptAnswer.setCreator(creator);
				conceptAnswer.setDateCreated(new Date());
				concept.addAnswer(conceptAnswer);
			}
		}
		if (changed) {
			conceptService.saveConcept(concept);
		}
	}

	private void createTask(String name, String description, Date startDate,
			Long repeatSeconds, Boolean startOnStartup, String taskClass,
			User creator, Map<String, String> properties) {
		SchedulerService schedulerService = contextService
				.getSchedulerService();
		TaskDefinition task = schedulerService.getTaskByName(name);
		if (task == null) {
			task = new TaskDefinition();
			task.setName(name);
			task.setDescription(description);
			task.setStartTime(startDate);
			task.setRepeatInterval(repeatSeconds);
			if (properties != null)
				task.setProperties(properties);
			task.setTaskClass(taskClass);
			task.setStartOnStartup(startOnStartup);
			task.setCreator(creator);
			schedulerService.saveTask(task);
			task = schedulerService.getTaskByName(name);
		}

		try {
			schedulerService.scheduleTask(task);
		} catch (SchedulerException e) {
			log.error("Cannot schedule task" + name, e);
		}

	}

	private void removeTask(String name) {
		SchedulerService schedulerService = contextService
				.getSchedulerService();
		TaskDefinition task = schedulerService.getTaskByName(name);
		if (task != null) {
			// Only shutdown if task has not already been shutdown
			if (task.getStarted()) {
				try {
					schedulerService.shutdownTask(task);
				} catch (SchedulerException e) {
					log.error("Cannot shutdown task: " + name, e);
				}
			}
			schedulerService.deleteTask(task.getId());
		}
	}

	public void removeAllTasks() {
		log.info("Removing Scheduled Tasks");

		removeTask(MotechConstants.TASK_IMMEDIATE_NOTIFICATION);
		removeTask(MotechConstants.TASK_DAILY_NOTIFICATION);
		removeTask(MotechConstants.TASK_MESSAGEPROGRAM_UPDATE);
		removeTask(MotechConstants.TASK_DAILY_NURSE_CARE_MESSAGING);
		removeTask(MotechConstants.TASK_WEEKLY_NURSE_CARE_MESSAGING);
	}

	/* Activator methods end */

	/* SaveObsAdvice method */
	public void updateMessageProgramState(Integer personId, String conceptName) {

		// Only determine message program state for active enrolled programs
		// concerned with an observed concept and matching the concept of this
		// obs

		MotechService motechService = contextService.getMotechService();

		List<MessageProgramEnrollment> patientActiveEnrollments = motechService
				.getActiveMessageProgramEnrollments(personId, null, null);

		for (MessageProgramEnrollment enrollment : patientActiveEnrollments) {
			MessageProgram program = this.getMessageProgram(enrollment
					.getProgram());

			if (program.getConceptName() != null) {
				if (program.getConceptName().equals(conceptName)) {
					log
							.debug("Save Obs - Obs matches Program concept, update Program: "
									+ enrollment.getProgram());

					program.determineState(enrollment);
				}
			}
		}
	}

	/* MessageProgramUpdateTask method */
	public void updateAllMessageProgramsState() {

		MotechService motechService = contextService.getMotechService();

		List<MessageProgramEnrollment> activeEnrollments = motechService
				.getActiveMessageProgramEnrollments(null, null, null);

		for (MessageProgramEnrollment enrollment : activeEnrollments) {
			MessageProgram program = this.getMessageProgram(enrollment
					.getProgram());

			log.debug("MessageProgram Update - Update State: enrollment: "
					+ enrollment.getId());

			program.determineState(enrollment);
		}
	}

	public void sendNurseCareMessages(Date startDate, Date endDate,
			Date deliveryDate, String[] careGroups, boolean sendUpcoming) {

		UserService userService = contextService.getUserService();
		List<User> users = userService.getAllUsers();

		for (User user : users) {
			String phoneNumber = getPersonPhoneNumber(user);
			if (phoneNumber == null) {
				// Skip nurses without a phone number
				continue;
			}
			// All nurse messages sent as SMS
			MediaType mediaType = MediaType.TEXT;
			// No corresponding message stored for nurse care messages
			String messageId = null;

			WebServiceModelConverterImpl modelConverter = new WebServiceModelConverterImpl();

			// Send Defaulted Care Message
			List<ExpectedEncounter> defaultedEncounters = getDefaultedExpectedEncounters(
					careGroups, startDate);
			List<ExpectedObs> defaultedObs = getDefaultedExpectedObs(
					careGroups, startDate);
			if (!defaultedEncounters.isEmpty() || !defaultedObs.isEmpty()) {
				Care[] defaultedCares = modelConverter
						.defaultedToWebServiceCares(defaultedEncounters,
								defaultedObs);
				sendNurseDefaultedCareMessage(messageId, phoneNumber,
						mediaType, deliveryDate, null, defaultedCares);
			}

			if (sendUpcoming) {
				// Send Upcoming Care Messages
				List<ExpectedEncounter> upcomingEncounters = getUpcomingExpectedEncounters(
						careGroups, startDate, endDate);
				for (ExpectedEncounter upcomingEncounter : upcomingEncounters) {
					org.motechproject.ws.Patient patient = modelConverter
							.upcomingEncounterToWebServicePatient(upcomingEncounter);

					sendNurseUpcomingCareMessage(messageId, phoneNumber,
							mediaType, deliveryDate, null, patient);
				}
				List<ExpectedObs> upcomingObs = getUpcomingExpectedObs(
						careGroups, startDate, endDate);
				for (ExpectedObs upcomingObservation : upcomingObs) {
					org.motechproject.ws.Patient patient = modelConverter
							.upcomingObsToWebServicePatient(upcomingObservation);

					sendNurseUpcomingCareMessage(messageId, phoneNumber,
							mediaType, deliveryDate, null, patient);
				}
			}
		}
	}

	/* NotificationTask methods start */
	public void sendMessages(Date startDate, Date endDate, boolean sendImmediate) {
		try {
			MotechService motechService = contextService.getMotechService();

			List<Message> shouldAttemptMessages = motechService.getMessages(
					startDate, endDate, MessageStatus.SHOULD_ATTEMPT);

			if (log.isDebugEnabled()) {
				log
						.debug("Notification Task executed, Should Attempt Messages found: "
								+ shouldAttemptMessages.size());
			}

			for (Message shouldAttemptMessage : shouldAttemptMessages) {
				sendMessage(shouldAttemptMessage, sendImmediate);
			}
		} catch (Exception e) {
			log.error(e);
		}
	}

	public void sendMessage(Message message, boolean sendImmediate) {
		MotechService motechService = contextService.getMotechService();
		PersonService personService = contextService.getPersonService();
		PatientService patientService = contextService.getPatientService();
		UserService userService = contextService.getUserService();

		org.motechproject.server.model.Log motechLog = new org.motechproject.server.model.Log();
		motechLog.setDate(new Date());

		Long notificationType = message.getSchedule().getMessage()
				.getPublicId();
		Integer recipientId = message.getSchedule().getRecipientId();
		Person person = personService.getPerson(recipientId);

		String phoneNumber = getPersonPhoneNumber(person);

		// Cancel message if phone number is considered troubled
		if (isPhoneTroubled(phoneNumber)) {
			motechLog.setMessage("Attempt to send to Troubled Phone, Phone: "
					+ phoneNumber + ", Notification cancelled: "
					+ notificationType);
			motechLog.setType(LogType.FAILURE);

			message.setAttemptStatus(MessageStatus.CANCELLED);

		} else {
			motechLog.setMessage("Scheduled Message Notification, Phone: "
					+ phoneNumber + ", Notification: " + notificationType);

			String messageId = message.getPublicId();
			MediaType mediaType = getPersonMediaType(person);
			String languageCode = getPersonLanguageCode(person);
			NameValuePair[] personalInfo = new NameValuePair[0];

			Date messageStartDate = null;
			Date messageEndDate = null;

			if (!sendImmediate) {
				messageStartDate = message.getAttemptDate();
			}

			Patient patient = patientService.getPatient(recipientId);
			User nurse = userService.getUser(recipientId);

			boolean sendMessageSuccess = false;
			if (patient != null) {
				ContactNumberType contactNumberType = getPersonPhoneType(person);
				String motechId = patient.getPatientIdentifier(
						MotechConstants.PATIENT_IDENTIFIER_MOTECH_ID)
						.getIdentifier();

				sendMessageSuccess = sendPatientMessage(messageId,
						personalInfo, motechId, phoneNumber, languageCode,
						mediaType, notificationType, messageStartDate,
						messageEndDate, contactNumberType);
			} else if (nurse != null) {
				org.motechproject.ws.Patient[] patients = new org.motechproject.ws.Patient[0];

				sendMessageSuccess = sendNurseMessage(messageId, personalInfo,
						phoneNumber, languageCode, mediaType, notificationType,
						messageStartDate, messageEndDate, patients);
			} else {
				log.error("Attempt to send to Person not patient or nurse: "
						+ recipientId);
			}
			if (sendMessageSuccess) {
				message.setAttemptStatus(MessageStatus.ATTEMPT_PENDING);
				motechLog.setType(LogType.SUCCESS);
			} else {
				message.setAttemptStatus(MessageStatus.ATTEMPT_FAIL);
				motechLog.setType(LogType.FAILURE);
			}
		}
		motechService.saveLog(motechLog);

		motechService.saveMessage(message);
	}

	public boolean sendPatientMessage(String messageId,
			NameValuePair[] personalInfo, String motechId, String phoneNumber,
			String languageCode, MediaType mediaType, Long notificationType,
			Date messageStartDate, Date messageEndDate,
			ContactNumberType contactType) {

		try {
			org.motechproject.ws.MessageStatus messageStatus = mobileService
					.sendPatientMessage(messageId, personalInfo, phoneNumber,
							contactType, languageCode, mediaType,
							notificationType, messageStartDate, messageEndDate,
							motechId);

			return messageStatus != org.motechproject.ws.MessageStatus.FAILED;
		} catch (Exception e) {
			log.error("Mobile WS patient message failure", e);
			return false;
		}
	}

	public boolean sendNurseMessage(String messageId,
			NameValuePair[] personalInfo, String phoneNumber,
			String languageCode, MediaType mediaType, Long notificationType,
			Date messageStartDate, Date messageEndDate,
			org.motechproject.ws.Patient[] patients) {

		try {
			org.motechproject.ws.MessageStatus messageStatus = mobileService
					.sendCHPSMessage(messageId, personalInfo, phoneNumber,
							patients, languageCode, mediaType,
							notificationType, messageStartDate, messageEndDate);

			return messageStatus != org.motechproject.ws.MessageStatus.FAILED;
		} catch (Exception e) {
			log.error("Mobile WS nurse message failure", e);
			return false;
		}
	}

	public boolean sendNurseDefaultedCareMessage(String messageId,
			String phoneNumber, MediaType mediaType, Date messageStartDate,
			Date messageEndDate, Care[] cares) {

		try {
			org.motechproject.ws.MessageStatus messageStatus = mobileService
					.sendDefaulterMessage(messageId, phoneNumber, cares,
							mediaType, messageStartDate, messageEndDate);

			return messageStatus != org.motechproject.ws.MessageStatus.FAILED;
		} catch (Exception e) {
			log.error("Mobile WS nurse defaulted care message failure", e);
			return false;
		}
	}

	public boolean sendNurseUpcomingCareMessage(String messageId,
			String phoneNumber, MediaType mediaType, Date messageStartDate,
			Date messageEndDate, org.motechproject.ws.Patient patient) {

		try {
			org.motechproject.ws.MessageStatus messageStatus = mobileService
					.sendUpcomingCaresMessage(messageId, phoneNumber, patient,
							mediaType, messageStartDate, messageEndDate);

			return messageStatus != org.motechproject.ws.MessageStatus.FAILED;
		} catch (Exception e) {
			log.error("Mobile WS nurse upcoming care message failure", e);
			return false;
		}
	}

	/* NotificationTask methods end */

	/* Factored out methods start */
	public String[] getActiveMessageProgramEnrollmentNames(Patient patient) {
		MotechService motechService = contextService.getMotechService();

		List<MessageProgramEnrollment> enrollments = motechService
				.getActiveMessageProgramEnrollments(patient.getPatientId(),
						null, null);

		List<String> enrollmentNames = new ArrayList<String>();
		for (MessageProgramEnrollment enrollment : enrollments) {
			enrollmentNames.add(enrollment.getProgram());
		}
		return enrollmentNames.toArray(new String[enrollmentNames.size()]);
	}

	public void addMessageProgramEnrollment(Integer personId, String program,
			Integer obsId) {
		MotechService motechService = contextService.getMotechService();

		List<MessageProgramEnrollment> enrollments = motechService
				.getActiveMessageProgramEnrollments(personId, program, obsId);
		if (enrollments.size() == 0) {
			MessageProgramEnrollment enrollment = new MessageProgramEnrollment();
			enrollment.setPersonId(personId);
			enrollment.setProgram(program);
			enrollment.setStartDate(new Date());
			enrollment.setObsId(obsId);
			motechService.saveMessageProgramEnrollment(enrollment);
		}
	}

	public void removeMessageProgramEnrollment(
			MessageProgramEnrollment enrollment) {

		MotechService motechService = contextService.getMotechService();
		removeAllUnsentMessages(enrollment);
		if (enrollment.getEndDate() == null) {
			enrollment.setEndDate(new Date());
			motechService.saveMessageProgramEnrollment(enrollment);
		}
	}

	public void removeMessageProgramEnrollment(Integer personId,
			String program, Integer obsId) {
		MotechService motechService = contextService.getMotechService();

		List<MessageProgramEnrollment> enrollments = motechService
				.getActiveMessageProgramEnrollments(personId, program, obsId);
		for (MessageProgramEnrollment enrollment : enrollments) {
			removeMessageProgramEnrollment(enrollment);
		}
	}

	private void removeAllMessageProgramEnrollments(Integer personId) {
		MotechService motechService = contextService.getMotechService();

		List<MessageProgramEnrollment> enrollments = motechService
				.getActiveMessageProgramEnrollments(personId, null, null);

		for (MessageProgramEnrollment enrollment : enrollments) {
			removeMessageProgramEnrollment(enrollment);
		}
	}

	public Obs createNumericValueObs(Date date, Concept concept, Person person,
			Location location, Integer value, Encounter encounter, User creator) {

		Double doubleValue = new Double(value);
		return createNumericValueObs(date, concept, person, location,
				doubleValue, encounter, creator);
	}

	public Obs createNumericValueObs(Date date, Concept concept, Person person,
			Location location, Double value, Encounter encounter, User creator) {

		Obs obs = createObs(date, concept, person, location, encounter, creator);
		obs.setValueNumeric(value);
		return obs;
	}

	public Obs createBooleanValueObs(Date date, Concept concept, Person person,
			Location location, Boolean value, Encounter encounter, User creator) {

		Double doubleValue = null;
		// Boolean currently stored as Numeric 1 or 0
		if (Boolean.TRUE.equals(value)) {
			doubleValue = 1.0;
		} else {
			doubleValue = 0.0;
		}
		return createNumericValueObs(date, concept, person, location,
				doubleValue, encounter, creator);
	}

	public Obs createDateValueObs(Date date, Concept concept, Person person,
			Location location, Date value, Encounter encounter, User creator) {

		Obs obs = createObs(date, concept, person, location, encounter, creator);
		obs.setValueDatetime(value);
		return obs;
	}

	public Obs createConceptValueObs(Date date, Concept concept, Person person,
			Location location, Concept value, Encounter encounter, User creator) {

		Obs obs = createObs(date, concept, person, location, encounter, creator);
		obs.setValueCoded(value);
		return obs;
	}

	public Obs createTextValueObs(Date date, Concept concept, Person person,
			Location location, String value, Encounter encounter, User creator) {

		Obs obs = createObs(date, concept, person, location, encounter, creator);
		obs.setValueText(value);
		return obs;
	}

	public Obs createObs(Date date, Concept concept, Person person,
			Location location, Encounter encounter, User creator) {

		Obs obs = new Obs();
		obs.setObsDatetime(date);
		obs.setConcept(concept);
		obs.setPerson(person);
		obs.setLocation(location);
		if (encounter != null) {
			obs.setEncounter(encounter);
		}
		if (creator != null) {
			obs.setCreator(creator);
		}
		return obs;
	}

	public Patient getPatientByMotechId(String motechId) {
		PatientService patientService = contextService.getPatientService();
		PatientIdentifierType motechIdType = getMotechPatientIdType();
		List<PatientIdentifierType> idTypes = new ArrayList<PatientIdentifierType>();
		idTypes.add(motechIdType);

		// Parameters are Name, Id, Id type, match exactly boolean
		List<Patient> patients = patientService.getPatients(null, motechId,
				idTypes, true);
		if (patients.size() > 0) {
			if (patients.size() > 1) {
				log.warn("Multiple Patients found for Motech ID: " + motechId);
			}
			return patients.get(0);
		}
		return null;
	}

	public User getNurseByCHPSId(String chpsId) {
		MotechService motechService = contextService.getMotechService();
		UserService userService = contextService.getUserService();

		PersonAttributeType chpsAttributeType = getNurseIdAttributeType();
		List<Integer> matchingUsers = motechService
				.getUserIdsByPersonAttribute(chpsAttributeType, chpsId);
		if (matchingUsers.size() > 0) {
			if (matchingUsers.size() > 1) {
				log.warn("Multiple Nurses found for CHPS ID: " + chpsId);
			}
			// If more than one user matches chps ID, first user in list is
			// returned
			Integer userId = matchingUsers.get(0);
			return userService.getUser(userId);
		}
		log.warn("No Nurse found for CHPS ID: " + chpsId);
		return null;
	}

	public String getPersonPhoneNumber(Person person) {
		PersonAttribute phoneNumberAttr = person
				.getAttribute(MotechConstants.PERSON_ATTRIBUTE_PHONE_NUMBER);
		if (phoneNumberAttr != null) {
			return phoneNumberAttr.getValue();
		}
		log
				.warn("No phone number found for Person id: "
						+ person.getPersonId());
		return null;
	}

	public String getPersonLanguageCode(Person person) {
		PersonAttribute languageAttr = person
				.getAttribute(MotechConstants.PERSON_ATTRIBUTE_LANGUAGE);
		if (languageAttr != null) {
			return languageAttr.getValue();
		}
		log.debug("No language found for Person id: " + person.getPersonId());
		return null;
	}

	public ContactNumberType getPersonPhoneType(Person person) {
		PersonAttribute phoneTypeAttr = person
				.getAttribute(MotechConstants.PERSON_ATTRIBUTE_PHONE_TYPE);
		if (phoneTypeAttr != null && phoneTypeAttr.getValue() != null) {
			return ContactNumberType.valueOf(phoneTypeAttr.getValue());
		}
		log.debug("No contact number type found for Person id: "
				+ person.getPersonId());
		return null;
	}

	public MediaType getPersonMediaType(Person person) {
		PersonAttribute mediaTypeAttr = person
				.getAttribute(MotechConstants.PERSON_ATTRIBUTE_MEDIA_TYPE);
		if (mediaTypeAttr != null && mediaTypeAttr.getValue() != null) {
			return MediaType.valueOf(mediaTypeAttr.getValue());
		}
		log.debug("No media type found for Person id: " + person.getPersonId());
		return null;
	}

	public boolean isPhoneTroubled(String phoneNumber) {
		TroubledPhone troubledPhone = contextService.getMotechService()
				.getTroubledPhone(phoneNumber);
		Integer maxFailures = getMaxPhoneNumberFailures();
		if (maxFailures == null) {
			return false;
		}
		return troubledPhone != null
				&& troubledPhone.getSendFailures() >= maxFailures;
	}

	public Integer getMaxPhoneNumberFailures() {
		String troubledPhoneProperty = getTroubledPhoneProperty();
		if (troubledPhoneProperty != null) {
			return Integer.parseInt(troubledPhoneProperty);
		}
		log.error("Troubled Phone Property not found");
		return null;
	}

	public Integer getMaxPatientCareReminders() {
		String careRemindersProperty = getPatientCareRemindersProperty();
		if (careRemindersProperty != null) {
			return Integer.parseInt(careRemindersProperty);
		}
		log.error("Patient Care Reminders Property not found");
		return null;
	}

	public DayOfWeek getPersonMessageDayOfWeek(Person person) {
		PersonAttribute dayAttr = person
				.getAttribute(MotechConstants.PERSON_ATTRIBUTE_DELIVERY_DAY);
		DayOfWeek day = null;
		if (dayAttr != null && dayAttr.getValue() != null) {
			try {
				day = DayOfWeek.valueOf(dayAttr.getValue());
			} catch (Exception e) {
				log.error("Invalid Patient Day of Week Attribute: "
						+ dayAttr.getValue(), e);
			}
		} else {
			log.debug("No day of week found for Person id: "
					+ person.getPersonId());
		}
		return day;
	}

	public Date getPersonMessageTimeOfDay(Person person) {
		PersonAttribute timeAttr = person
				.getAttribute(MotechConstants.PERSON_ATTRIBUTE_DELIVERY_TIME);
		Date time = null;
		if (timeAttr != null && timeAttr.getValue() != null) {
			SimpleDateFormat timeFormat = new SimpleDateFormat(
					MotechConstants.TIME_FORMAT_PERSON_ATTRIBUTE_DELIVERY_TIME);
			try {
				time = timeFormat.parse(timeAttr.getValue());
			} catch (Exception e) {
				log.error("Invalid Patient Time of Day Attribute: "
						+ timeAttr.getValue(), e);
			}
		} else {
			log.debug("No time of day found for Person id: "
					+ person.getPersonId());
		}
		return time;
	}

	public DayOfWeek getDefaultPatientDayOfWeek() {
		String dayProperty = getPatientDayOfWeekProperty();
		DayOfWeek day = null;
		try {
			day = DayOfWeek.valueOf(dayProperty);
		} catch (Exception e) {
			log
					.error("Invalid Patient Day of Week Property: "
							+ dayProperty, e);
		}
		return day;
	}

	public Date getDefaultPatientTimeOfDay() {
		String timeProperty = getPatientTimeOfDayProperty();
		SimpleDateFormat timeFormat = new SimpleDateFormat(
				MotechConstants.TIME_FORMAT_PERSON_ATTRIBUTE_DELIVERY_TIME);
		Date time = null;
		try {
			time = timeFormat.parse(timeProperty);
		} catch (Exception e) {
			log.error("Invalid Patient Time of Day Property: " + timeProperty,
					e);
		}
		return time;
	}

	public Date determineMessageStartDate(Person person, Date messageDate) {
		Calendar calendar = Calendar.getInstance();
		Date currentDate = calendar.getTime();
		calendar.setTime(messageDate);

		Date time = getPersonMessageTimeOfDay(person);
		if (time == null) {
			time = getDefaultPatientTimeOfDay();
		}
		if (time != null) {
			Calendar timeCalendar = Calendar.getInstance();
			timeCalendar.setTime(time);
			calendar.set(Calendar.HOUR_OF_DAY, timeCalendar
					.get(Calendar.HOUR_OF_DAY));
			calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE));
		}
		calendar.set(Calendar.SECOND, 0);

		DayOfWeek day = getPersonMessageDayOfWeek(person);
		if (day == null) {
			day = getDefaultPatientDayOfWeek();
		}
		if (day != null) {
			calendar.set(Calendar.DAY_OF_WEEK, day.getCalendarValue());
			if (calendar.getTime().before(currentDate)) {
				// Add a week if date in past after setting the day of week
				calendar.add(Calendar.DATE, 7);
			}
		}

		return calendar.getTime();
	}

	public PatientIdentifierType getMotechPatientIdType() {
		return contextService.getPatientService()
				.getPatientIdentifierTypeByName(
						MotechConstants.PATIENT_IDENTIFIER_MOTECH_ID);
	}

	public PersonAttributeType getNurseIdAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_CHPS_ID);
	}

	public PersonAttributeType getCommunityAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_COMMUNITY);
	}

	public PersonAttributeType getPhoneNumberAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_PHONE_NUMBER);
	}

	public PersonAttributeType getNHISNumberAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_NHIS_NUMBER);
	}

	public PersonAttributeType getNHISExpirationDateAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_NHIS_EXP_DATE);
	}

	public PersonAttributeType getPhoneTypeAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_PHONE_TYPE);
	}

	public PersonAttributeType getLanguageAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_LANGUAGE);
	}

	public PersonAttributeType getMediaTypeAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_MEDIA_TYPE);
	}

	public PersonAttributeType getDeliveryTimeAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_DELIVERY_TIME);
	}

	public PersonAttributeType getInsuredAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_INSURED);
	}

	public PersonAttributeType getHowLearnedAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_HOW_LEARNED);
	}

	public PersonAttributeType getWhyInterestedAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_WHY_INTERESTED);
	}

	public PersonAttributeType getDeliveryDayAttributeType() {
		return contextService.getPersonService().getPersonAttributeTypeByName(
				MotechConstants.PERSON_ATTRIBUTE_DELIVERY_DAY);
	}

	public Location getGhanaLocation() {
		return contextService.getLocationService().getLocation(
				MotechConstants.LOCATION_GHANA);
	}

	public EncounterType getANCVisitEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_ANCVISIT);
	}

	public EncounterType getPregnancyRegistrationVisitEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_PREGREGVISIT);
	}

	public EncounterType getPregnancyTerminationVisitEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_PREGTERMVISIT);
	}

	public EncounterType getPregnancyDeliveryVisitEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_PREGDELVISIT);
	}

	public EncounterType getPregnancyDeliveryNotificationEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_PREGDELNOTIFYVISIT);
	}

	public EncounterType getGeneralVisitEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_GENERALVISIT);
	}

	public EncounterType getTTVisitEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_TTVISIT);
	}

	public EncounterType getCWCVisitEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_CWCVISIT);
	}

	public EncounterType getMotherPNCVisitEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_PNCMOTHERVISIT);
	}

	public EncounterType getChildPNCVisitEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_PNCCHILDVISIT);
	}

	public EncounterType getANCRegistrationEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_ANCREGVISIT);
	}

	public EncounterType getCWCRegistrationEncounterType() {
		return contextService.getEncounterService().getEncounterType(
				MotechConstants.ENCOUNTER_TYPE_CWCREGVISIT);
	}

	public Concept getImmunizationsOrderedConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_IMMUNIZATIONS_ORDERED);
	}

	public Concept getTetanusDoseConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_TETANUS_TOXOID_DOSE);
	}

	public Concept getIPTDoseConcept() {
		return contextService
				.getConceptService()
				.getConcept(
						MotechConstants.CONCEPT_INTERMITTENT_PREVENTATIVE_TREATMENT_DOSE);
	}

	public Concept getHIVTestResultConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_HIV_TEST_RESULT);
	}

	public Concept getTerminationTypeConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_TERMINATION_TYPE);
	}

	public Concept getTerminationComplicationConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_TERMINATION_COMPLICATION);
	}

	public Concept getVitaminAConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_VITAMIN_A);
	}

	public Concept getITNConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_INSECTICIDE_TREATED_NET_USAGE);
	}

	public Concept getVisitNumberConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_VISIT_NUMBER);
	}

	public Concept getPregnancyConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_PREGNANCY);
	}

	public Concept getPregnancyStatusConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_PREGNANCY_STATUS);
	}

	public Concept getDueDateConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_ESTIMATED_DATE_OF_CONFINEMENT);
	}

	public Concept getParityConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_PARITY);
	}

	public Concept getGravidaConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_GRAVIDA);
	}

	public Concept getDueDateConfirmedConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_DATE_OF_CONFINEMENT_CONFIRMED);
	}

	public Concept getEnrollmentReferenceDateConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_ENROLLMENT_REFERENCE_DATE);
	}

	public Concept getDeathCauseConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_CAUSE_OF_DEATH);
	}

	public Concept getMaternalDeathCauseConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_MATERNAL_CAUSE_OF_DEATH);
	}

	public Concept getBCGConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_BCG_VACCINATION);
	}

	public Concept getOPVDoseConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_ORAL_POLIO_VACCINATION_DOSE);
	}

	public Concept getPentaDoseConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_PENTA_VACCINATION_DOSE);
	}

	public Concept getYellowFeverConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_YELLOW_FEVER_VACCINATION);
	}

	public Concept getCSMConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_CEREBRO_SPINAL_MENINGITIS_VACCINATION);
	}

	public Concept getMeaslesConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_MEASLES_VACCINATION);
	}

	public Concept getIPTiConcept() {
		return contextService
				.getConceptService()
				.getConcept(
						MotechConstants.CONCEPT_INTERMITTENT_PREVENTATIVE_TREATMENT_INFANTS);
	}

	public Concept getSerialNumberConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_SERIAL_NUMBER);
	}

	public Concept getNewCaseConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_NEW_CASE);
	}

	public Concept getReferredConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_REFERRED);
	}

	public Concept getPrimaryDiagnosisConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_PRIMARY_DIAGNOSIS);
	}

	public Concept getSecondaryDiagnosisConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_SECONDARY_DIAGNOSIS);
	}

	public Concept getDeliveryMethodConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_DELIVERY_METHOD);
	}

	public Concept getDeliveryLocationConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_DELIVERY_LOCATION);
	}

	public Concept getDeliveredByConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_DELIVERED_BY);
	}

	public Concept getDeliveryOutcomeConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_DELIVERY_OUTCOME);
	}

	public Concept getBirthOutcomeConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_BIRTH_OUTCOME);
	}

	public Concept getMalariaRDTConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_MALARIA_RAPID_TEST);
	}

	public Concept getVDRLTreatmentConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_VDRL_TREATMENT);
	}

	public Concept getUrineProteinTestConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_URINE_PROTEIN_TEST);
	}

	public Concept getUrineGlucoseTestConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_URINE_GLUCOSE_TEST);
	}

	public Concept getFetalHeartRateConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_FETAL_HEART_RATE);
	}

	public Concept getFundalHeightConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_FUNDAL_HEIGHT);
	}

	public Concept getVVFRepairConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_VVF_REPAIR);
	}

	public Concept getDewormerConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_DEWORMER);
	}

	public Concept getPMTCTConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_PMTCT);
	}

	public Concept getPMTCTTreatmentConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_PMTCT_TREATMENT);
	}

	public Concept getACTTreatmentConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_ACT_TREATMENT);
	}

	public Concept getPreHIVTestCounselingConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_HIV_PRE_TEST_COUNSELING);
	}

	public Concept getPostHIVTestCounselingConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_HIV_POST_TEST_COUNSELING);
	}

	public Concept getDeliveryComplicationConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_DELIVERY_COMPLICATION);
	}

	public Concept getPostAbortionFPCounselingConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_POST_ABORTION_FP_COUNSELING);
	}

	public Concept getPostAbortionFPAcceptedConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_POST_ABORTION_FP_ACCEPTED);
	}

	public Concept getIPTReactionConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_IPT_REACTION);
	}

	public Concept getLochiaColourConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_LOCHIA_COLOUR);
	}

	public Concept getLochiaExcessConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_LOCHIA_EXCESS_AMOUNT);
	}

	public Concept getMUACConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_MIDDLE_UPPER_ARM_CIRCUMFERENCE);
	}

	public Concept getMaternalDeathConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_MATERNAL_DEATH);
	}

	public Concept getTerminationProcedureConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_TERMINATION_PROCEDURE);
	}

	public Concept getCordConditionConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_CORD_CONDITION);
	}

	public Concept getConditionBabyConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_CONDITION_OF_BABY);
	}

	public Concept getNextANCDateConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_NEXT_ANC_DATE);
	}

	public Concept getMaleInvolvementConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_MALE_INVOLVEMENT);
	}

	public Concept getCommunityConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_COMMUNITY);
	}

	public Concept getHouseConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_HOUSE);
	}

	public Concept getANCPNCLocationConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_ANC_PNC_LOCATION);
	}

	public Concept getCWCLocationConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_CWC_LOCATION);
	}

	public Concept getCommentsConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_COMMENTS);
	}

	public Concept getVDRLConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_VDRL);
	}

	public Concept getRespiratoryRateConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_RESPIRATORY_RATE);
	}

	public Concept getDiastolicBloodPressureConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_DIASTOLIC_BLOOD_PRESSURE);
	}

	public Concept getSystolicBloodPressureConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_SYSTOLIC_BLOOD_PRESSURE);
	}

	public Concept getHemoglobinConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_HEMOGLOBIN);
	}

	public Concept getWeightConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_WEIGHT);
	}

	public Concept getHeightConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_HEIGHT);
	}

	public Concept getTemperatureConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_TEMPERATURE);
	}

	public Concept getReactiveConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_REACTIVE);
	}

	public Concept getNonReactiveConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_NON_REACTIVE);
	}

	public Concept getPositiveConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_POSITIVE);
	}

	public Concept getNegativeConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_NEGATIVE);
	}

	public Concept getANCRegistrationNumberConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_ANC_REG_NUMBER);
	}

	public Concept getCWCRegistrationNumberConcept() {
		return contextService.getConceptService().getConcept(
				MotechConstants.CONCEPT_CWC_REG_NUMBER);
	}

	public String getTroubledPhoneProperty() {
		return contextService.getAdministrationService().getGlobalProperty(
				MotechConstants.GLOBAL_PROPERTY_TROUBLED_PHONE);
	}

	public String getPatientCareRemindersProperty() {
		return contextService.getAdministrationService().getGlobalProperty(
				MotechConstants.GLOBAL_PROPERTY_CARE_REMINDERS);
	}

	public String getPatientDayOfWeekProperty() {
		return contextService.getAdministrationService().getGlobalProperty(
				MotechConstants.GLOBAL_PROPERTY_DAY_OF_WEEK);
	}

	public String getPatientTimeOfDayProperty() {
		return contextService.getAdministrationService().getGlobalProperty(
				MotechConstants.GLOBAL_PROPERTY_TIME_OF_DAY);
	}
	/* Factored out methods end */

}
