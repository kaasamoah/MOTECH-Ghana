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
package org.motechproject.server.omod.web.controller;

import java.sql.Time;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.motechproject.server.model.Blackout;
import org.motechproject.server.model.TroubledPhone;
import org.motechproject.server.omod.ContextService;
import org.motechproject.server.omod.MotechService;
import org.motechproject.server.omod.xml.LocationXStream;
import org.motechproject.server.svc.RegistrarBean;
import org.openmrs.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A spring webmvc controller, defining operations for the administrative
 * actions for the OpenMRS admin links.
 * 
 * @see org.motechproject.server.omod.extension.html.AdminList
 */
@Controller
public class MotechModuleFormController {

	protected final Log log = LogFactory
			.getLog(MotechModuleFormController.class);

	@Autowired
	@Qualifier("registrarBean")
	private RegistrarBean registrarBean;

	private ContextService contextService;

	@Autowired
	public void setContextService(ContextService contextService) {
		this.contextService = contextService;
	}

	public void setRegistrarBean(RegistrarBean registrarBean) {
		this.registrarBean = registrarBean;
	}

	@RequestMapping(value = "/module/motechmodule/clinic", method = RequestMethod.GET)
	public String viewClinicForm(ModelMap model) {
		List<Location> locations = registrarBean.getAllLocations();

		LocationXStream xstream = new LocationXStream();
		String locationsXml = xstream.toLocationHierarchyXML(locations);

		model.addAttribute("locationsXml", locationsXml);
		model.addAttribute("locations", locations);

		return "/module/motechmodule/clinic";
	}

	@RequestMapping(value = "/module/motechmodule/nurse", method = RequestMethod.GET)
	public String viewNurseForm(ModelMap model) {
		model.addAttribute("clinics", registrarBean.getAllLocations());
		return "/module/motechmodule/nurse";
	}

	@RequestMapping(value = "/module/motechmodule/clinic", method = RequestMethod.POST)
	public String registerClinic(@RequestParam("name") String name,
			@RequestParam("parent") String parent) {
		log.debug("Register Clinic");
		Integer parentId = null;
		if (!parent.equals("")) {
			parentId = Integer.valueOf(parent);
		}
		registrarBean.registerClinic(name, parentId);
		return "redirect:/module/motechmodule/viewdata.form";
	}

	@RequestMapping(value = "/module/motechmodule/nurse", method = RequestMethod.POST)
	public String registerNurse(@RequestParam("name") String name,
			@RequestParam("nurseId") String nurseId,
			@RequestParam("nursePhone") String nursePhone,
			@RequestParam("clinic") Integer clinicId) {
		log.debug("Register Nurse");
		registrarBean.registerNurse(name, nurseId, nursePhone, clinicId);
		return "redirect:/module/motechmodule/viewdata.form";
	}

	@RequestMapping("/module/motechmodule/viewdata")
	public String viewData(ModelMap model) {

		model.addAttribute("allLocations", registrarBean.getAllLocations());
		model.addAttribute("allNurses", registrarBean.getAllNurses());
		model.addAttribute("allPatients", registrarBean.getAllPatients());
		model.addAttribute("allPregnancies", registrarBean.getAllPregnancies());
		model.addAttribute("allScheduledMessages", registrarBean
				.getAllScheduledMessages());

		return "/module/motechmodule/viewdata";
	}

	@RequestMapping("/module/motechmodule/blackout")
	public String viewBlackoutSettings(ModelMap model) {

		Blackout blackout = contextService.getMotechService()
				.getBlackoutSettings();

		if (blackout != null) {
			model.addAttribute("startTime", blackout.getStartTime());
			model.addAttribute("endTime", blackout.getEndTime());
		}

		return "/module/motechmodule/blackout";
	}

	@RequestMapping(value = "/module/motechmodule/blackout", method = RequestMethod.POST)
	public String saveBlackoutSettings(
			@RequestParam("startTime") String startTime,
			@RequestParam("endTime") String endTime, ModelMap model) {

		MotechService motechService = contextService.getMotechService();
		Blackout blackout = motechService.getBlackoutSettings();

		Time startTimeCvt = Time.valueOf(startTime);
		Time endTimeCvt = Time.valueOf(endTime);

		if (blackout != null) {
			blackout.setStartTime(startTimeCvt);
			blackout.setEndTime(endTimeCvt);
		} else {
			blackout = new Blackout(startTimeCvt, endTimeCvt);
		}

		motechService.setBlackoutSettings(blackout);

		model.addAttribute("startTime", blackout.getStartTime());
		model.addAttribute("endTime", blackout.getEndTime());

		return "/module/motechmodule/blackout";
	}

	@RequestMapping(value = "/module/motechmodule/troubledphone", method = RequestMethod.GET)
	public String handleTroubledPhone(
			@RequestParam(required = false, value = "phoneNumber") String phoneNumber,
			@RequestParam(required = false, value = "remove") Boolean remove,
			ModelMap model) {

		if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {

			MotechService motechService = contextService.getMotechService();

			TroubledPhone troubledPhone = motechService
					.getTroubledPhone(phoneNumber);

			if (remove == Boolean.TRUE) {
				motechService.removeTroubledPhone(phoneNumber);
				return "redirect:/module/motechmodule/troubledphone.form";
			} else if (troubledPhone != null)
				model.addAttribute(troubledPhone);
		}

		return "/module/motechmodule/troubledphone";
	}
}