/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.motechmodule.advice;

import java.lang.reflect.Method;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.motech.model.FutureServiceDelivery;
import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.module.motechmodule.MotechService;
import org.springframework.aop.AfterReturningAdvice;

public class SaveEncounterAdvisor implements AfterReturningAdvice {
	
	private static Log log = LogFactory.getLog(SaveEncounterAdvisor.class);
	
	/**
	 * @see org.springframework.aop.AfterReturningAdvice#afterReturning(java.lang.Object,
	 *      java.lang.reflect.Method, java.lang.Object[], java.lang.Object)
	 */
	@Override
	public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {
		
		if (method.getName().equals("saveEncounter")) {
			Encounter encounter = (Encounter) returnValue;
			
			if (Context.getEncounterService().getEncounterType("MATERNALVISIT").equals(encounter.getEncounterType())) {
				
				log.debug("Scheduling FutureServiceDelivery");
				
				// Date 30 seconds in future
				long nextServiceTimeInSecs = 30;
				Date nextServiceDate = new Date(System.currentTimeMillis() + (nextServiceTimeInSecs * 1000));
				
				FutureServiceDelivery f = new FutureServiceDelivery();
				f.setDate(nextServiceDate);
				f.setUser(encounter.getProvider());
				f.setPatient(encounter.getPatient());
				f.setService(Context.getConceptService().getConcept("PREGNANCY VISIT NUMBER"));
				
				Context.getService(MotechService.class).saveFutureServiceDelivery(f);
			}
		}
	}
}
