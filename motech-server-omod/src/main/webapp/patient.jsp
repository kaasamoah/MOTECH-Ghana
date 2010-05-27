<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
<%@ include file="/WEB-INF/template/include.jsp"%>
<openmrs:require privilege="Register MoTeCH Patient" otherwise="/login.htm" redirect="/module/motechmodule/patient.form" />
<%@ include file="/WEB-INF/template/header.jsp"%>

<openmrs:htmlInclude file="/moduleResources/motechmodule/patientform.css" />
<openmrs:htmlInclude file="/dwr/util.js" />
<openmrs:htmlInclude file="/dwr/interface/DWRMotechService.js"/>
<openmrs:htmlInclude file="/moduleResources/motechmodule/find_duplicates.js" />

<meta name="heading" content="Register Patient" />
<%@ include file="localHeader.jsp" %>
<h2>Register Patient</h2>
<div class="instructions">
	This form allows you to create a new patient record, optionally 
	including pregnancy information and enrollment in the information service.
</div>
<form:form method="post" modelAttribute="patient" onsubmit="return confirmRegistrationOnMatches()">
<span style="color:green;">
	<spring:message code="${successMsg}" text="" />
</span>
<form:errors cssClass="error" />
<fieldset><legend>Patient Registration</legend>
<table>
	<tr>
		<td class="labelcolumn"><label for="registrationMode">Registration mode:</label></td>
		<td>
			<form:select path="registrationMode">
				<form:option value="" label="Select Value" />
				<form:option value="USE_PREPRINTED_ID" label="Use pre-printed MoteCH ID" />
				<form:option value="AUTO_GENERATE_ID" label="Auto-generate MoTeCH ID" />
			</form:select>
		</td>
		<td><form:errors path="registrationMode" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="motechId">MoTeCH ID:</label></td>
		<td><form:input path="motechId" onchange="findDuplicates()" /></td>
		<td><form:errors path="motechId" cssClass="error" /></td>
	</tr>
 	<tr>
		<td class="labelcolumn"><label for="registrantType">Type of Patient:</label></td>
		<td>
			<form:select path="registrantType">
				<form:option value="" label="Select Value" />
				<form:option value="PREGNANT_MOTHER" label="Pregnant mother" />
				<form:option value="CHILD_UNDER_FIVE" label="Child (age less than 5)" />
				<form:option value="OTHER" label="Other" />
			</form:select>
		</td>
		<td><form:errors path="registrantType" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="firstName">First Name:</label></td>
		<td><form:input path="firstName" onchange="findDuplicates()" maxlength="50" /></td>
		<td><form:errors path="firstName" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="middleName">Middle Name:</label></td>
		<td><form:input path="middleName" maxlength="50" /></td>
		<td><form:errors path="middleName" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="lastName">Last Name:</label></td>
		<td><form:input path="lastName" onchange="findDuplicates()" maxlength="50" /></td>
		<td><form:errors path="lastName" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="prefName">Preferred Name:</label></td>
		<td><form:input path="prefName" onchange="findDuplicates()" maxlength="50" /></td>
		<td><form:errors path="prefName" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="birthDate">Date of Birth (DD/MM/YYYY):</label></td>
		<td><form:input path="birthDate" onchange="findDuplicates()" /></td>
		<td><form:errors path="birthDate" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="birthDateEst">Estimated Date of Birth:</label></td>
		<td>
			<form:select path="birthDateEst">
				<form:option value="" label="Select Value" />
				<form:option value="true" label="Yes" />
				<form:option value="false" label="No" />
			</form:select>
		</td>
		<td><form:errors path="birthDateEst" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="sex">Sex:</label></td>
		<td>
			<form:select path="sex">
				<form:option value="" label="Select Value" />
				<form:option value="FEMALE" label="Female"/>
				<form:option value="MALE" label="Male"/>
			</form:select>
		</td>
		<td><form:errors path="sex" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="insured">Insured:</label></td>
		<td>
			<form:select path="insured">
				<form:option value="" label="Select Value" />
				<form:option value="true" label="Yes" />
				<form:option value="false" label="No" />
			</form:select>
		</td>
		<td><form:errors path="insured" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="nhis">NHIS Number:</label></td>
		<td><form:input path="nhis" onchange="findDuplicates()" maxlength="50" /></td>
		<td><form:errors path="nhis" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="nhisExpDate">NHIS Expiration Date (DD/MM/YYYY):</label></td>
		<td><form:input path="nhisExpDate" /></td>
		<td><form:errors path="nhisExpDate" cssClass="error" /></td>
	</tr>
 	<tr>
		<td class="labelcolumn"><label for="motherMotechId">Mother's MoTeCH ID:</label></td>
		<td><form:input path="motherMotechId" /></td>
		<td><form:errors path="motherMotechId" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="region">Region:</label></td>
		<td>
			<form:select path="region">
				<form:option value="" label="Select Value" />
				<form:options items="${regions}" />
			</form:select>
		</td>
		<td><form:errors path="region" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="district">District:</label></td>
		<td>
			<form:select path="district">
				<form:option value="" label="Select Value" />
				<form:options items="${districts}" />
			</form:select>
		</td>
		<td><form:errors path="district" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="communityId">Community:</label></td>
		<td>
			<form:select path="communityId" onchange="findDuplicates()">
				<form:option value="" label="Select Value" />
				<form:options items="${communities}" itemValue="communityId" itemLabel="name" />
			</form:select>
		</td>
		<td><form:errors path="communityId" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="address">Address/household:</label></td>
		<td><form:input path="address" maxlength="50" /></td>
		<td><form:errors path="address" cssClass="error" /></td>
	</tr>
</table>
</fieldset>
<fieldset><legend>Pregnancy Registration</legend>
<table>
	<tr>
		<td class="labelcolumn"><label for="dueDate">Expected Delivery Date (DD/MM/YYYY):</label></td>
		<td><form:input path="dueDate" /></td>
		<td><form:errors path="dueDate" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="dueDateConfirmed">Delivery Date confirmed by CHW:</label></td>
		<td>
			<form:select path="dueDateConfirmed">
				<form:option value="" label="Select Value" />
				<form:option value="true" label="Yes" />
				<form:option value="false" label="No" />
			</form:select>
		</td>
		<td><form:errors path="dueDateConfirmed" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="gravida">Previous Pregnancies (gravida):</label></td>
		<td><form:input path="gravida" /></td>
		<td><form:errors path="gravida" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="parity">Previous Births (parity):</label></td>
		<td><form:input path="parity" /></td>
		<td><form:errors path="parity" cssClass="error" /></td>
	</tr>
</table>
</fieldset>
<fieldset><legend>Information Service Enrollment</legend>
<table>
	<tr>
		<td class="labelcolumn"><label for="enroll">Register in Info Service:</label></td>
		<td>
			<form:select path="enroll">
				<form:option value="" label="Select Value" />
				<form:option value="true" label="Yes" />
				<form:option value="false" label="No" />
			</form:select>
		</td>
		<td><form:errors path="enroll" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="consent">Registrant has heard consent text and has consented to terms of enrollment:</label></td>
		<td><form:checkbox path="consent" /></td>
		<td><form:errors path="consent" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="phoneNumber">Phone Number:</label></td>
		<td><form:input path="phoneNumber" onchange="findDuplicates()" maxlength="50" /></td>
		<td><form:errors path="phoneNumber" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="phoneType">Phone Ownership:</label></td>
		<td>
			<form:select path="phoneType">
				<form:option value="" label="Select Value" />
				<form:option value="PERSONAL" label="Personal phone" />
				<form:option value="HOUSEHOLD" label="Owned by household" />
				<form:option value="PUBLIC" label="Public phone" />
			</form:select>
		</td>
		<td><form:errors path="phoneType" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="mediaType">Message Format:</label></td>
		<td>
			<form:select path="mediaType">
				<form:option value="" label="Select Value" />
				<form:option value="TEXT" label="Text" />
				<form:option value="VOICE" label="Voice" />
			</form:select>
		</td>
		<td><form:errors path="mediaType" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="language">Language for Messages:</label></td>
		<td>
			<form:select path="language">
				<form:option value="" label="Select Value" />
				<form:option value="en" label="English" />
				<form:option value="kas" label="Kassim" />
				<form:option value="nan" label="Nankam" />
			</form:select>
		</td>
		<td><form:errors path="language" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="dayOfWeek">Day of week to receive messages:</label></td>
		<td>
			<form:select path="dayOfWeek">
				<form:option value="" label="Select Value" />
				<form:option value="MONDAY" label="Monday" />
				<form:option value="TUESDAY" label="Tuesday" />
				<form:option value="WEDNESDAY" label="Wednesday" />
				<form:option value="THURSDAY" label="Thursday" />
				<form:option value="FRIDAY" label="Friday" />
				<form:option value="SATURDAY" label="Saturday" />
				<form:option value="SUNDAY" label="Sunday" />
			</form:select>
		</td>
		<td><form:errors path="dayOfWeek" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="timeOfDay">Time of day to receive messages (HH:MM):</label></td>
		<td><form:input path="timeOfDay" /></td>
		<td><form:errors path="timeOfDay" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="interestReason">Reason for interest in info service:</label></td>
		<td>
			<form:select path="interestReason">
				<form:option value="" label="Select Value" />
				<form:option value="CURRENTLY_PREGNANT" label="Currently pregnant" />
				<form:option value="RECENTLY_DELIVERED" label="Recently delivered" />
				<form:option value="FAMILY_FRIEND_PREGNANT" label="Family/ friend is pregnant" />
				<form:option value="FAMILY_FRIEND_DELIVERED" label="Family/friend recently delivered" />
				<form:option value="PLANNING_PREGNANCY_INFO" label="Thinking of getting pregnant and want more information" />
				<form:option value="KNOW_MORE_PREGNANCY_CHILDBIRTH" label="Want to know more about pregnancy and child birth" />
				<form:option value="WORK_WITH_WOMEN_NEWBORNS" label="I work with pregnant women and/or new borns" />
			</form:select>
		</td>
		<td><form:errors path="interestReason" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="howLearned">How they learned of the service:</label></td>
		<td>
			<form:select path="howLearned">
				<form:option value="" label="Select Value" />
				<form:option value="GHS_NURSE" label="GHS Nurse" />
				<form:option value="MOTECH_FIELD_AGENT" label="MoTeCH field agent" />
				<form:option value="FRIEND" label="Friend" />
				<form:option value="POSTERS_ADS" label="Posters/ads" />
				<form:option value="RADIO" label="Radio" />
			</form:select>
		</td>
		<td><form:errors path="howLearned" cssClass="error" /></td>
	</tr>
	<tr>
		<td class="labelcolumn"><label for="messagesStartWeek">Week to begin messages:</label></td>
		<td>
			<form:select path="messagesStartWeek">
				<form:option value="" label="Select Value" />
				<c:forEach var="i" begin="5" end="40">
					<form:option value="${i}" label="Pregnancy week ${i}" />
				</c:forEach>
				<c:forEach var="i" begin="1" end="4">
					<form:option value="${i + 40}" label="Newborn week ${i}" />
				</c:forEach>
			</form:select>
		</td>
		<td><form:errors path="messagesStartWeek" cssClass="error" /></td>
	</tr>
</table>
</fieldset>
<table>
	<tr>
		<td colspan="2"><input type="submit" /></td>
	</tr>
</table>
</form:form>
<div id="matchingPatientsSection" style="color:red;display:none;">
	<h3>Conflicting Patients</h3>
	<table id="matchingPatients">
		<thead>
			<tr>
				<th>MoTeCH ID</th>
				<th>First Name</th>
				<th>Last Name</th>
				<th>Birth Date</th>
				<th>Community</th>
				<th>NHIS Number</th>
				<th>Phone Number</th>
			</tr>
		</thead>
		<tbody id="matchingPatientsBody" />
	</table>
</div>

<%@ include file="/WEB-INF/template/footer.jsp"%>