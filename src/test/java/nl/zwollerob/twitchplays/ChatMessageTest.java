package nl.zwollerob.twitchplays;

import nl.zwollerob.twitchplays.twitch.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageTest {
	@Test
	void parsesPrivmsgWithTags() {
		ChatMessage m = ChatMessage.parse("@badge-info=;badges=moderator/1,subscriber/12;bits=100;color=#FF0000;display-name=Kijker\\s1;"
				+ "mod=1;user-id=12345 :kijker1!kijker1@kijker1.tmi.twitch.tv PRIVMSG #streamer :Cheer100 !jump 2");
		assertEquals("12345", m.userId());
		assertEquals("kijker1", m.login());
		assertEquals("Kijker 1", m.displayName());
		assertEquals("Cheer100 !jump 2", m.text());
		assertEquals(100, m.bits());
		assertTrue(m.moderator());
		assertFalse(m.broadcaster());
	}

	@Test
	void broadcasterCountsAsModerator() {
		ChatMessage m = ChatMessage.parse("@badges=broadcaster/1;display-name=;user-id=1 :streamer!streamer@streamer.tmi.twitch.tv PRIVMSG #streamer :!democracy");
		assertTrue(m.broadcaster());
		assertTrue(m.moderator());
		assertEquals("streamer", m.displayName());
	}

	@Test
	void handlesActionMessagesAndColonsInText() {
		ChatMessage m = ChatMessage.parse(":a!a@a.tmi.twitch.tv PRIVMSG #c :\u0001ACTION !w :)\u0001");
		assertEquals("!w :)", m.text());
		assertEquals(0, m.bits());
	}

	@Test
	void ignoresOtherLines() {
		assertNull(ChatMessage.parse("PING :tmi.twitch.tv"));
		assertNull(ChatMessage.parse(":tmi.twitch.tv 001 justinfan123 :Welcome, GLHF!"));
		assertNull(ChatMessage.parse("@emote-only=0 :tmi.twitch.tv ROOMSTATE #c"));
		assertNull(ChatMessage.parse(""));
	}
}
