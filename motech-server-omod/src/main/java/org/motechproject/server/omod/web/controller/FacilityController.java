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

package org.motechproject.server.omod.web.controller;

import java.util.HashSet;
import java.util.List;

import org.motechproject.server.model.Facility;
import org.motechproject.server.omod.ContextService;
import org.motechproject.server.omod.web.model.WebFacility;
import org.openmrs.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.support.SessionStatus;

@Controller
public class FacilityController {

	@Autowired
	private ContextService contextService;

	@ModelAttribute("facilities")
	public List<Facility> getFacilities() {
		return contextService.getMotechService().getAllFacilities();
	}

	@RequestMapping(value = "/module/motechmodule/facility.form", method = RequestMethod.GET)
	public String viewFacilities() {
		return "/module/motechmodule/facility";
	}

    @RequestMapping(value = "/module/motechmodule/addfacility.form", method = RequestMethod.GET)
    public String viewAddFacilityForm(ModelMap modelMap){
        modelMap.addAttribute("facility", new WebFacility());
        return "/module/motechmodule/addfacility";
    }

    @RequestMapping(value = "/module/motechmodule/addfacility.form", method = RequestMethod.POST)
    public String submitAddFacility(@ModelAttribute("facility") WebFacility facility, Errors errors,ModelMap modelMap, SessionStatus status){
        if(facility.getName().isEmpty()){
            errors.rejectValue("name", "motechmodule.name.blank");
        }
        if(contextService.getMotechService().getLocationByName(facility.getName()) != null){
            errors.rejectValue("name","motechmodule.Facility.duplicate.location");
        }
        if(errors.hasErrors()){
            return "/module/motechmodule/addfacility";
        }
        contextService.getLocationService().saveLocation(facility.getFacility().getLocation());
        contextService.getRegistrarBean().saveNewFacility(facility.getFacility());
        return "redirect:/module/motechmodule/facility.form";
    }

    @ModelAttribute("locations")
    public List<Location> getLocations(){
        return contextService.getLocationService().getAllLocations(false);
    }

    @RequestMapping(value = "/module/motechmodule/jsonfacilitydata.form", method = RequestMethod.GET)
    public String getJSON(ModelMap modelMap){
        List<Location> locationList = contextService.getLocationService().getAllLocations();
        HashSet<String> countries = new HashSet<String>();
        HashSet<String> regions = new HashSet<String>();
        HashSet<String> districts = new HashSet<String>();
        HashSet<String> provinces = new HashSet<String>();
        for (Location list : locationList){
            countries.add("\"" + list.getCountry() + "\"");
            regions.add("\"" + list.getRegion() + "\"");
            districts.add("\"" + list.getCountyDistrict() + "\"");
            provinces.add("\"" + list.getStateProvince() + "\"");
        }
        modelMap.addAttribute("countries", countries.toString());
        modelMap.addAttribute("regions", regions.toString());
        modelMap.addAttribute("districts", districts.toString());
        modelMap.addAttribute("provinces", provinces.toString());
        return "/module/motechmodule/json_data_facilities";
    }
}
