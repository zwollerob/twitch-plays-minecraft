package nl.zwollerob.twitchplays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretsTest {
	private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJjaGFubmVsIjoiNjAzYWJjIn0.c2lnbmF0dXJlZGluZ2V0amU";

	@Test
	void keysAfterTheCommandStayOutOfTheHistory() {
		assertTrue(Secrets.isSensitive("/twitchplays streamelements " + JWT));
		assertTrue(Secrets.isSensitive("/twitchplays tipeeestream abcdef0123456789abcdef"));
		assertTrue(Secrets.isSensitive("/TwitchPlays  TipeeeStream  iets"));
		assertTrue(Secrets.isSensitive("twitchplays tipeeestream abc"), "client commands are stored without the slash");
		assertTrue(Secrets.isSensitive("ik plak per ongeluk " + JWT + " in de chat"));
		// Chat cuts long text at 256 characters, so a pasted token can lose its last part.
		assertTrue(Secrets.isSensitive("eyJhbGciOiJIUzI1NiJ9.eyJjaGFubmVsIjoiNjAzYW"));
		assertEquals("x *** y", Secrets.mask("x eyJhbGciOiJIUzI1NiJ9.eyJjaGFubmVsIjoiNjAzYW y"));
		assertTrue(Secrets.isSensitive("PASS oauth:abcdefghij0123456789klmn"));
	}

	@Test
	void normalCommandsStayInTheHistory() {
		assertFalse(Secrets.isSensitive("/twitchplays streamelements"));
		assertFalse(Secrets.isSensitive("/twitchplays tipeeestream"));
		assertFalse(Secrets.isSensitive("/twitchplays tipeeestream off"));
		assertFalse(Secrets.isSensitive("/twitchplays test forward 3"));
		assertFalse(Secrets.isSensitive("/gamemode creative"));
		assertFalse(Secrets.isSensitive("hallo chat"));
		assertFalse(Secrets.isSensitive(null));
	}

	@Test
	void masksTokensInText() {
		assertEquals("Fout: *** ongeldig", Secrets.mask("Fout: " + JWT + " ongeldig"));
		assertEquals("{\"access_token\":\"***\",\"refresh_token\":\"***\"}", Secrets.mask("{\"access_token\":\"abc123\",\"refresh_token\":\"def456\"}"));
		assertEquals("wss://x/socket.io/?EIO=4&access_token=***&y=1", Secrets.mask("wss://x/socket.io/?EIO=4&access_token=geheim&y=1"));
		assertEquals("oauth:***", Secrets.mask("oauth:abcdefghij0123456789klmn"));
		assertEquals("Twitch-chat verbroken (code 1006)", Secrets.mask("Twitch-chat verbroken (code 1006)"));
		assertEquals("", Secrets.mask(null));
	}
}
