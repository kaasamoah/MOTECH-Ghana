package org.motechproject.server.omod.web.controller;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.motechproject.server.omod.ContextService;
import org.motechproject.server.omod.web.model.WebModelConverter;
import org.motechproject.server.omod.web.model.WebPatient;
import org.motechproject.server.svc.OpenmrsBean;
import org.motechproject.server.svc.RegistrarBean;
import org.motechproject.ws.RegistrantType;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

@Controller
@RequestMapping("/module/motechmodule/mother")
@SessionAttributes("mother")
public class MotherController {

	private static Log log = LogFactory.getLog(MotherController.class);

	private WebModelConverter webModelConverter;

	@Autowired
	@Qualifier("registrarBean")
	private RegistrarBean registrarBean;

	@Autowired
	@Qualifier("openmrsBean")
	private OpenmrsBean openmrsBean;

	private ContextService contextService;

	@Autowired
	public void setContextService(ContextService contextService) {
		this.contextService = contextService;
	}

	public void setRegistrarBean(RegistrarBean registrarBean) {
		this.registrarBean = registrarBean;
	}

	public void setOpenmrsBean(OpenmrsBean openmrsBean) {
		this.openmrsBean = openmrsBean;
	}

	@Autowired
	public void setWebModelConverter(WebModelConverter webModelConverter) {
		this.webModelConverter = webModelConverter;
	}

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		String datePattern = "dd/MM/yyyy";
		SimpleDateFormat dateFormat = new SimpleDateFormat(datePattern);
		dateFormat.setLenient(false);

		binder.registerCustomEditor(Date.class, new CustomDateEditor(
				dateFormat, true, datePattern.length()));
		binder
				.registerCustomEditor(String.class, new StringTrimmerEditor(
						true));
	}

	@ModelAttribute("regions")
	public List<Location> getRegions() {
		return contextService.getMotechService().getAllRegions();
	}

	@ModelAttribute("districts")
	public List<Location> getDistricts() {
		return contextService.getMotechService().getAllDistricts();
	}

	@ModelAttribute("communities")
	public List<Location> getCommunities() {
		return contextService.getMotechService().getAllCommunities();
	}

	@ModelAttribute("clinics")
	public List<Location> getClinics() {
		return contextService.getMotechService().getAllClinics();
	}

	@RequestMapping(method = RequestMethod.GET)
	public void viewForm(@RequestParam(required = false) Integer id,
			ModelMap model) {
	}

	@ModelAttribute("mother")
	public WebPatient getWebMother(@RequestParam(required = false) Integer id) {
		WebPatient result = new WebPatient();
		if (id != null) {
			Patient patient = contextService.getPatientService().getPatient(id);

			if (patient != null) {
				webModelConverter.patientToWeb(patient, result);
			}
		}
		return result;
	}

	@RequestMapping(method = RequestMethod.POST)
	public String submitForm(@ModelAttribute("mother") WebPatient mother,
			Errors errors, ModelMap model, SessionStatus status) {

		log.debug("Register Pregnant Mother");

		if (mother.getMotechId() != null
				&& openmrsBean.getPatientByMotechId(mother.getMotechId()) != null) {
			errors.rejectValue("motechId", "motechmodule.motechId.nonunique");
		}
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "firstName",
				"motechmodule.firstName.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "lastName",
				"motechmodule.lastName.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "birthDate",
				"motechmodule.birthDate.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "birthDateEst",
				"motechmodule.birthDateEst.required");
		if (Boolean.TRUE.equals(mother.getRegisteredGHS())) {
			ValidationUtils.rejectIfEmpty(errors, "regNumberGHS",
					"motechmodule.regNumberGHS.required");
		}
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "insured",
				"motechmodule.insured.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "region",
				"motechmodule.region.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "district",
				"motechmodule.district.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "community",
				"motechmodule.community.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "address",
				"motechmodule.address.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "clinic",
				"motechmodule.clinic.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "dueDate",
				"motechmodule.dueDate.required");
		if (mother.getDueDate() != null) {
			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.MONTH, 9);
			if (mother.getDueDate().after(calendar.getTime())) {
				errors.rejectValue("dueDate",
						"motechmodule.dueDate.overninemonths");
			}
		}
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "dueDateConfirmed",
				"motechmodule.dueDateConfirmed.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "gravida",
				"motechmodule.gravida.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "parity",
				"motechmodule.parity.required");
		if (errors.getFieldErrorCount("parity") == 0
				&& errors.getFieldErrorCount("gravida") == 0) {
			if (mother.getParity() > mother.getGravida()) {
				errors.rejectValue("parity", "motechmodule.parity.range");
			}
		}
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "hivStatus",
				"motechmodule.hivStatus.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors,
				"registerPregProgram",
				"motechmodule.registerPregProgram.required");

		if (Boolean.TRUE.equals(mother.getRegisterPregProgram())) {
			if (!Boolean.TRUE.equals(mother.getTermsConsent())) {
				errors.rejectValue("termsConsent",
						"motechmodule.termsConsent.required");
			}
			ValidationUtils.rejectIfEmptyOrWhitespace(errors, "primaryPhone",
					"motechmodule.primaryPhone.required");
			ValidationUtils.rejectIfEmptyOrWhitespace(errors,
					"primaryPhoneType",
					"motechmodule.primaryPhoneType.required");
			ValidationUtils.rejectIfEmptyOrWhitespace(errors, "mediaTypeInfo",
					"motechmodule.mediaTypeInfo.required");
			ValidationUtils.rejectIfEmptyOrWhitespace(errors,
					"mediaTypeReminder",
					"motechmodule.mediaTypeReminder.required");
			ValidationUtils.rejectIfEmptyOrWhitespace(errors, "languageVoice",
					"motechmodule.languageVoice.required");
			ValidationUtils.rejectIfEmptyOrWhitespace(errors, "languageText",
					"motechmodule.languageText.required");
			ValidationUtils.rejectIfEmptyOrWhitespace(errors, "whoRegistered",
					"motechmodule.whoRegistered.required");
		}

		if (!errors.hasErrors()) {
			registrarBean.registerPatient(null, Integer.parseInt(mother
					.getMotechId()), RegistrantType.OTHER, mother
					.getFirstName(), mother.getMiddleName(), mother
					.getLastName(), mother.getPrefName(),
					mother.getBirthDate(), mother.getBirthDateEst(), mother
							.getSex(), mother.getInsured(), mother.getNhis(),
					mother.getNhisExpDate(), null, null, mother.getAddress(),
					Integer.parseInt(mother.getPrimaryPhone()), mother
							.getDueDate(), mother.getDueDateConfirmed(), mother
							.getGravida(), mother.getParity(), mother
							.getRegisterPregProgram(), mother
							.getRegisterPregProgram(), mother
							.getPrimaryPhoneType(), mother.getMediaTypeInfo(),
					mother.getLanguageText(), null, null, null, null, mother
							.getMessagesStartWeek());

			model.addAttribute("successMsg",
					"motechmodule.Mother.register.success");

			status.setComplete();

			return "redirect:/module/motechmodule/viewdata.form";
		}

		return "/module/motechmodule/mother";
	}
}