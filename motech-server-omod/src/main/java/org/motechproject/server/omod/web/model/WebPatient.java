package org.motechproject.server.omod.web.model;

import java.util.Date;

import org.motechproject.ws.ContactNumberType;
import org.motechproject.ws.Gender;
import org.motechproject.ws.InterestReason;
import org.motechproject.ws.MediaType;

public class WebPatient {

	private Integer id;
	private Integer motechId;
	private String firstName;
	private String middleName;
	private String lastName;
	private String prefName;
	private Date birthDate;
	private Boolean birthDateEst;
	private Gender sex;
	private Boolean registeredGHS;
	private String regNumberGHS;
	private String motherMotechId;
	private Boolean insured;
	private String nhis;
	private Date nhisExpDate;
	private String region;
	private String district;
	private String community;
	private String address;
	private Integer clinic;
	private Date dueDate;
	private Boolean dueDateConfirmed;
	private Integer gravida;
	private Integer parity;
	private Boolean registerPregProgram;
	private Boolean termsConsent;
	private String phoneNumber;
	private ContactNumberType phoneType;
	private MediaType mediaType;
	private String language;
	private String religion;
	private String occupation;
	private String howLearned;
	private InterestReason interestReason;
	private Integer messagesStartWeek;

	public WebPatient() {
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getMotechId() {
		return motechId;
	}

	public void setMotechId(Integer motechId) {
		this.motechId = motechId;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getMiddleName() {
		return middleName;
	}

	public void setMiddleName(String middleName) {
		this.middleName = middleName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getPrefName() {
		return prefName;
	}

	public void setPrefName(String prefName) {
		this.prefName = prefName;
	}

	public Date getBirthDate() {
		return birthDate;
	}

	public void setBirthDate(Date birthDate) {
		this.birthDate = birthDate;
	}

	public Boolean getBirthDateEst() {
		return birthDateEst;
	}

	public void setBirthDateEst(Boolean birthDateEst) {
		this.birthDateEst = birthDateEst;
	}

	public Gender getSex() {
		return sex;
	}

	public void setSex(Gender sex) {
		this.sex = sex;
	}

	public Boolean getRegisteredGHS() {
		return registeredGHS;
	}

	public void setRegisteredGHS(Boolean registeredGHS) {
		this.registeredGHS = registeredGHS;
	}

	public String getRegNumberGHS() {
		return regNumberGHS;
	}

	public void setRegNumberGHS(String regNumberGHS) {
		this.regNumberGHS = regNumberGHS;
	}

	public String getMotherMotechId() {
		return motherMotechId;
	}

	public void setMotherMotechId(String motherMotechId) {
		this.motherMotechId = motherMotechId;
	}

	public Boolean getInsured() {
		return insured;
	}

	public void setInsured(Boolean insured) {
		this.insured = insured;
	}

	public String getNhis() {
		return nhis;
	}

	public void setNhis(String nhis) {
		this.nhis = nhis;
	}

	public Date getNhisExpDate() {
		return nhisExpDate;
	}

	public void setNhisExpDate(Date nhisExpDate) {
		this.nhisExpDate = nhisExpDate;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getDistrict() {
		return district;
	}

	public void setDistrict(String district) {
		this.district = district;
	}

	public String getCommunity() {
		return community;
	}

	public void setCommunity(String community) {
		this.community = community;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public Integer getClinic() {
		return clinic;
	}

	public void setClinic(Integer clinic) {
		this.clinic = clinic;
	}

	public Date getDueDate() {
		return dueDate;
	}

	public void setDueDate(Date dueDate) {
		this.dueDate = dueDate;
	}

	public Boolean getDueDateConfirmed() {
		return dueDateConfirmed;
	}

	public void setDueDateConfirmed(Boolean dueDateConfirmed) {
		this.dueDateConfirmed = dueDateConfirmed;
	}

	public Integer getGravida() {
		return gravida;
	}

	public void setGravida(Integer gravida) {
		this.gravida = gravida;
	}

	public Integer getParity() {
		return parity;
	}

	public void setParity(Integer parity) {
		this.parity = parity;
	}

	public Boolean getRegisterPregProgram() {
		return registerPregProgram;
	}

	public void setRegisterPregProgram(Boolean registerPregProgram) {
		this.registerPregProgram = registerPregProgram;
	}

	public Boolean getTermsConsent() {
		return termsConsent;
	}

	public void setTermsConsent(Boolean termsConsent) {
		this.termsConsent = termsConsent;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public ContactNumberType getPhoneType() {
		return phoneType;
	}

	public void setPhoneType(ContactNumberType phoneType) {
		this.phoneType = phoneType;
	}

	public MediaType getMediaType() {
		return mediaType;
	}

	public void setMediaType(MediaType mediaType) {
		this.mediaType = mediaType;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getReligion() {
		return religion;
	}

	public void setReligion(String religion) {
		this.religion = religion;
	}

	public String getOccupation() {
		return occupation;
	}

	public void setOccupation(String occupation) {
		this.occupation = occupation;
	}

	public String getHowLearned() {
		return howLearned;
	}

	public void setHowLearned(String howLearned) {
		this.howLearned = howLearned;
	}

	public InterestReason getInterestReason() {
		return interestReason;
	}

	public void setInterestReason(InterestReason interestReason) {
		this.interestReason = interestReason;
	}

	public Integer getMessagesStartWeek() {
		return messagesStartWeek;
	}

	public void setMessagesStartWeek(Integer messagesStartWeek) {
		this.messagesStartWeek = messagesStartWeek;
	}

}
