/**
 * MOTECH PLATFORM OPENSOURCE LICENSE AGREEMENT
 *
 * Copyright (c) 2010-11 The Trustees of Columbia University in the City of
 * New York and Grameen Foundation USA.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Grameen Foundation USA, Columbia University, or
 * their respective contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY GRAMEEN FOUNDATION USA, COLUMBIA UNIVERSITY
 * AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL GRAMEEN FOUNDATION
 * USA, COLUMBIA UNIVERSITY OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.motechproject.server.util;

public final class MotechConstants {

	/*
	 * Values added and used by Motech
	 */
	public static final String PERSON_ATTRIBUTE_PHONE_NUMBER = "Phone Number";
	public static final String PERSON_ATTRIBUTE_NHIS_NUMBER = "NHIS Number";
	public static final String PERSON_ATTRIBUTE_NHIS_EXP_DATE = "NHIS Expiration Date";
	public static final String PERSON_ATTRIBUTE_LANGUAGE = "Language";
	public static final String PERSON_ATTRIBUTE_PHONE_TYPE = "Phone Type";
	public static final String PERSON_ATTRIBUTE_MEDIA_TYPE = "Media Type";
	public static final String PERSON_ATTRIBUTE_DELIVERY_DAY = "Delivery Day";
	public static final String PERSON_ATTRIBUTE_DELIVERY_TIME = "Delivery Time";
	public static final String PERSON_ATTRIBUTE_INSURED = "Insured";
	public static final String PERSON_ATTRIBUTE_HOW_LEARNED = "How learned of service";
	public static final String PERSON_ATTRIBUTE_INTEREST_REASON = "Reason interested in service";
	public static final String PATIENT_IDENTIFIER_MOTECH_ID = "MoTeCH Id";
	public static final String PATIENT_IDENTIFIER_STAFF_ID = "MoTeCH Staff Id";
	public static final String LOCATION_GHANA = "Ghana";
	public static final String LOCATION_UPPER_EAST = "Upper East";
	public static final String LOCATION_KASSENA_NANKANA = "Kassena-Nankana";
	public static final String LOCATION_KASSENA_NANKANA_WEST = "Kassena-Nankana West";
	public static final String ENCOUNTER_TYPE_ANCVISIT = "ANCVISIT";
	public static final String ENCOUNTER_TYPE_PREGREGVISIT = "PREGREGVISIT";
	public static final String ENCOUNTER_TYPE_PREGTERMVISIT = "PREGTERMVISIT";
	public static final String ENCOUNTER_TYPE_PREGDELVISIT = "PREGDELVISIT";
	public static final String ENCOUNTER_TYPE_OUTPATIENTVISIT = "OUTPATIENTVISIT";
	public static final String ENCOUNTER_TYPE_PNCMOTHERVISIT = "PNCMOTHERVISIT";
	public static final String ENCOUNTER_TYPE_PNCCHILDVISIT = "PNCCHILDVISIT";
	public static final String ENCOUNTER_TYPE_PATIENTREGVISIT = "PATIENTREGVISIT";
	public static final String CONCEPT_VISIT_NUMBER = "VISIT NUMBER";
	public static final String CONCEPT_INTERMITTENT_PREVENTATIVE_TREATMENT_INFANTS_DOSE = "INTERMITTENT PREVENTATIVE TREATMENT INFANTS DOSE";
	public static final String CONCEPT_ORAL_POLIO_VACCINATION_DOSE = "ORAL POLIO VACCINATION DOSE";
	public static final String CONCEPT_PENTA_VACCINATION_DOSE = "PENTA VACCINATION DOSE";
	public static final String CONCEPT_INSECTICIDE_TREATED_NET_USAGE = "INSECTICIDE TREATED NET USAGE";
	public static final String CONCEPT_CEREBRO_SPINAL_MENINGITIS_VACCINATION = "CEREBRO-SPINAL MENINGITIS VACCINATION";
	public static final String CONCEPT_DATE_OF_CONFINEMENT_CONFIRMED = "DATE OF CONFINEMENT CONFIRMED";
	public static final String CONCEPT_ENROLLMENT_REFERENCE_DATE = "MESSAGE PROGRAM ENROLLMENT REFERENCE DATE";
	public static final String CONCEPT_TETANUS_TOXOID_DOSE = "TETANUS TOXOID DOSE";
	public static final String CONCEPT_INTERMITTENT_PREVENTATIVE_TREATMENT_DOSE = "INTERMITTENT PREVENTATIVE TREATMENT DOSE";
	public static final String CONCEPT_HIV_TEST_RESULT = "HIV TEST RESULT";
	public static final String CONCEPT_TERMINATION_TYPE = "TERMINATION TYPE";
	public static final String CONCEPT_TERMINATION_COMPLICATION = "TERMINATION COMPLICATION";
	public static final String CONCEPT_DELIVERY_MODE = "DELIVERY MODE";
	public static final String CONCEPT_DELIVERY_LOCATION = "DELIVERY LOCATION";
	public static final String CONCEPT_DELIVERED_BY = "DELIVERED BY";
	public static final String CONCEPT_DELIVERY_OUTCOME = "DELIVERY OUTCOME";
	public static final String CONCEPT_BIRTH_OUTCOME = "BIRTH OUTCOME";
	public static final String CONCEPT_CAUSE_OF_DEATH = "CAUSE OF DEATH";
	public static final String CONCEPT_SERIAL_NUMBER = "SERIAL NUMBER";
	public static final String CONCEPT_NEW_CASE = "NEW CASE";
	public static final String CONCEPT_REFERRED = "REFERRED";
    public static final String IDGEN_SEQ_ID_GEN_STAFF_ID = "MoTeCH Staff ID Generator";
	public static final String CONCEPT_PRIMARY_DIAGNOSIS = "PRIMARY DIAGNOSIS ";
	public static final String CONCEPT_SECONDARY_DIAGNOSIS = "SECONDARY DIAGNOSIS";
	public static final String CONCEPT_NEXT_ANC_DATE = "NEXT ANC DATE";
	public static final String TASK_PROPERTY_SEND_IMMEDIATE = "sendImmediate";
	public static final String TASK_PROPERTY_TIME_OFFSET = "timeOffset";
	public static final String TASK_MESSAGEPROGRAM_UPDATE = "MessageProgram Update Task";
	public static final String TASK_PROPERTY_DELIVERY_TIME = "deliveryTime";
	public static final String TASK_PROPERTY_SEND_UPCOMING = "sendUpcoming";
	public static final String TASK_PROPERTY_CARE_GROUPS = "careGroups";
	public static final String TASK_PROPERTY_CARE_GROUPS_DELIMITER = ",";
	public static final String TASK_PROPERTY_AVOID_BLACKOUT = "avoidBlackout";
	public static final String TASK_PROPERTY_BATCH_SIZE = "batchSize";
	public static final String TASK_PROPERTY_BATCH_PREVIOUS_ID = "batchPreviousId";
	public static final String TASK_PROPERTY_BATCH_MAX_ID = "batchMaxId";
	public static final String GLOBAL_PROPERTY_TROUBLED_PHONE = "motechmodule.troubled_phone_failures";
	public static final String GLOBAL_PROPERTY_CARE_REMINDERS = "motechmodule.patient_care_reminders";
	public static final String GLOBAL_PROPERTY_DAY_OF_WEEK = "motechmodule.patient_message_delivery_day_of_week";
	public static final String GLOBAL_PROPERTY_TIME_OF_DAY = "motechmodule.patient_message_delivery_time_of_day";
	public static final String GLOBAL_PROPERTY_MAX_QUERY_RESULTS = "motechmodule.max_query_results";
	public static final String IDGEN_SEQ_ID_GEN_MOTECH_ID_GEN_COMMENT = "AUTO GENERATED";
	public static final String TIME_FORMAT_DELIVERY_TIME = "HH:mm";
	public static final String PHONE_REGEX_PATTERN = "^0[0-9]{9}$";
	public static final int MAX_STRING_LENGTH_OPENMRS = 50;

	/*
	 * Values already existing in OpenMRS, used by Motech
	 */
	public static final String GENDER_MALE_OPENMRS = "M";
	public static final String GENDER_FEMALE_OPENMRS = "F";
	public static final String GENDER_UNKNOWN_OPENMRS = "?";
	public static final String DATE_FORMAT = "yyyy-MM-dd";
	public static final String CONCEPT_IMMUNIZATIONS_ORDERED = "IMMUNIZATIONS ORDERED";
	public static final String CONCEPT_PREGNANCY = "PREGNANCY";
	public static final String CONCEPT_PREGNANCY_STATUS = "PREGNANCY STATUS";
	public static final String CONCEPT_ESTIMATED_DATE_OF_CONFINEMENT = "ESTIMATED DATE OF CONFINEMENT";
	public static final String CONCEPT_GRAVIDA = "GRAVIDA";
	public static final String CONCEPT_PARITY = "PARITY";
	public static final String RELATIONSHIP_TYPE_PARENT_CHILD = "Parent/Child";
}
