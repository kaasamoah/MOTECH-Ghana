package org.motech.ws;

import static org.easymock.EasyMock.aryEq;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.logging.LogManager;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.motech.svc.RegistrarBean;
import org.motechproject.ws.ContactNumberType;
import org.motechproject.ws.DeliveryTime;
import org.motechproject.ws.Gender;
import org.motechproject.ws.LogType;
import org.motechproject.ws.MediaType;
import org.motechproject.ws.server.RegistrarService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RegistrarServiceTest {

	static ApplicationContext ctx;
	static RegistrarService regWs;
	static RegistrarBean registrarBean;

	@BeforeClass
	public static void setUpClass() throws Exception {
		LogManager.getLogManager().readConfiguration(
				RegistrarServiceTest.class
						.getResourceAsStream("/jul-test.properties"));
		registrarBean = createMock(RegistrarBean.class);
		ctx = new ClassPathXmlApplicationContext("test-context.xml");
		RegistrarWebService regService = (RegistrarWebService) ctx
				.getBean("registrarService");
		regService.setRegistrarBean(registrarBean);
		regWs = (RegistrarService) ctx.getBean("registrarClient");
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		ctx = null;
		regWs = null;
		registrarBean = null;
		LogManager.getLogManager().readConfiguration();
	}

	@Before
	public void setup() {
	}

	@After
	public void tearDown() throws Exception {
		reset(registrarBean);
	}

	@Test
	public void testRegisterClinic() {
		String clinicName = "A-Test-Clinic-Name";

		registrarBean.registerClinic(clinicName);

		replay(registrarBean);

		regWs.registerClinic(clinicName);

		verify(registrarBean);
	}

	@Test
	public void testRegisterNurse() {
		String name = "Sally", phone = "12075555555", clinic = "C-Clinic";

		registrarBean.registerNurse(name, phone, clinic);

		replay(registrarBean);

		regWs.registerNurse(name, phone, clinic);

		verify(registrarBean);
	}

	@Test
	public void testRegisterPatient() {
		String nPhone = "12075551212", serialId = "387946894", name = "Francis", community = "somepeople", location = "somewhere", pPhone = "120755512525";
		Date dob = new Date();
		Gender gender = Gender.FEMALE;
		Integer nhis = 3;
		ContactNumberType phoneType = ContactNumberType.PERSONAL;
		String language = "English";
		MediaType mediaType = MediaType.TEXT;
		DeliveryTime deliveryTime = DeliveryTime.ANYTIME;
		String[] regimen = new String[2];
		regimen[0] = "Example Regimen 1";
		regimen[1] = "Example Regimen 2";

		registrarBean.registerPatient(eq(nPhone), eq(serialId), eq(name),
				eq(community), eq(location), eq(dob), eq(gender), eq(nhis),
				eq(pPhone), eq(phoneType), eq(language), eq(mediaType),
				eq(deliveryTime), aryEq(regimen));

		replay(registrarBean);

		regWs.registerPatient(nPhone, serialId, name, community, location, dob,
				gender, nhis, pPhone, phoneType, language, mediaType,
				deliveryTime, regimen);

		verify(registrarBean);
	}

	@Test
	public void testRegisterPregnancy() {
		String nPhone = "12075551212", serialId = "387946894";
		Date date = new Date(), dueDate = new Date();
		Integer parity = 3;
		Double hemo = 2.0;

		registrarBean.registerPregnancy(nPhone, date, serialId, dueDate,
				parity, hemo);

		replay(registrarBean);

		regWs.registerPregnancy(nPhone, date, serialId, dueDate, parity, hemo);

		verify(registrarBean);
	}

	@Test
	public void testRecordMaternalVisit() {
		String nPhone = "12077778383", serialId = "ghj347956y";
		Date date = new Date();
		Boolean tetanus = true, ipt = false, itn = true, onARV = false, prePMTCT = true, testPMTCT = false, postPMTCT = true;
		Integer visitNum = 3;
		Double hemo = 378.34;

		registrarBean.recordMaternalVisit(nPhone, date, serialId, tetanus, ipt,
				itn, visitNum, onARV, prePMTCT, testPMTCT, postPMTCT, hemo);

		replay(registrarBean);

		regWs.recordMaternalVisit(nPhone, date, serialId, tetanus, ipt, itn,
				visitNum, onARV, prePMTCT, testPMTCT, postPMTCT, hemo);

		verify(registrarBean);
	}

	@Test
	public void testLog() {
		LogType type = LogType.SUCCESS;
		String msg = "logging over ws is slow";

		registrarBean.log(type, msg);

		replay(registrarBean);

		regWs.log(type, msg);

		verify(registrarBean);
	}

	@Test
	public void testSetMessageStatus() {
		String messageId = "12345678-1234-1234-1234-123456789012";
		Boolean success = true;

		replay(registrarBean);

		try {
			regWs.setMessageStatus(messageId, success);
			fail("Expected org.motech.messaging.MessageNotFoundException");
		} catch (Exception e) {
		}

		verify(registrarBean);
	}

	@Test
	public void testRegistrarBeanProperty() throws SecurityException,
			NoSuchFieldException, IllegalArgumentException,
			IllegalAccessException {
		RegistrarWebService regWs = new RegistrarWebService();

		Field regBeanField = regWs.getClass().getDeclaredField("registrarBean");
		regBeanField.setAccessible(true);

		regWs.setRegistrarBean(registrarBean);
		assertEquals(registrarBean, regBeanField.get(regWs));

		regWs.setRegistrarBean(null);
		assertEquals(null, regBeanField.get(regWs));
	}
}
