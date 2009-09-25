package org.motech.model;

import junit.framework.TestCase;

import com.dreamoval.motech.omi.service.MessageType;

public class NotificationTypeTest extends TestCase {

	public void testToOpenMRSString() {
		assertEquals(MessageType.TEXT, NotificationType.text.toMessageType());
		assertEquals(MessageType.VOICE, NotificationType.voice.toMessageType());
	}

	public void testFromMessageType() {
		assertEquals(NotificationType.text, NotificationType
				.fromMessageType(MessageType.TEXT));
		assertEquals(NotificationType.voice, NotificationType
				.fromMessageType(MessageType.VOICE));
	}

}