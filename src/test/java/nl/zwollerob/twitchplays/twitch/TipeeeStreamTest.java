package nl.zwollerob.twitchplays.twitch;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipeeeStreamTest {
	private static final String KEY = "abcdef0123456789abcdef0123456789";
	/** Shaped like a real donation from TipeeeStream's socket. */
	private static final String DONATION = """
			{"appKey":"####","event":{"id":4242,"ref":"59e83874d287b","type":"donation","created_at":"2017-10-19T07:30:28+0200",
			 "user":{"username":"streamer","currency":{"code":"EUR","symbol":"€"}},
			 "parameters":{"username":"anonym5","amount":"50","currency":"EUR","message":"take my moneeeeey!!"},
			 "formattedAmount":"€50.00"}}""";

	private final List<Alert> alerts = new ArrayList<>();
	private final List<String> statuses = new ArrayList<>();
	private final TipeeeStreamClient client = new TipeeeStreamClient(alerts::add, statuses::add);

	@Test
	void handshakeJoinsTheRoomWithTheKey() {
		client.useKey(KEY);
		assertEquals(List.of("40"), client.onFrame("0{\"sid\":\"x\",\"upgrades\":[],\"pingInterval\":25000,\"pingTimeout\":20000}"));
		List<String> join = client.onFrame("40{\"sid\":\"y\"}");
		assertEquals(1, join.size());
		assertTrue(join.getFirst().startsWith("42"));
		JsonArray event = JsonParser.parseString(join.getFirst().substring(2)).getAsJsonArray();
		assertEquals("join-room", event.get(0).getAsString());
		assertEquals(KEY, event.get(1).getAsJsonObject().get("room").getAsString());
		assertTrue(client.connected());
		assertTrue(statuses.getLast().contains("verbonden"));
		assertEquals(List.of("3"), client.onFrame("2"), "every ping gets a pong");
	}

	@Test
	void donationBecomesAnAlertOnce() {
		client.onFrame("42[\"new-event\"," + DONATION + "]");
		assertEquals(1, alerts.size());
		Alert alert = alerts.getFirst();
		assertEquals(Alert.Type.DONATION, alert.type());
		assertEquals("anonym5", alert.user());
		assertEquals("€ 50,00", alert.detail());
		assertEquals(50, alert.amount());
		client.onFrame("42[\"new-event\"," + DONATION + "]");
		assertEquals(1, alerts.size(), "the same donation twice must alert once");
	}

	@Test
	void otherEventsAreIgnored() {
		// Follows, subs and cheers come from Twitch itself; through TipeeeStream they would show twice.
		client.onFrame("42[\"new-event\",{\"event\":{\"id\":1,\"type\":\"follow\",\"parameters\":{\"username\":\"a\"}}}]");
		client.onFrame("42[\"new-event\",{\"event\":{\"id\":2,\"type\":\"cheer\",\"parameters\":{\"username\":\"a\",\"amount\":\"100\"}}}]");
		client.onFrame("42[\"something-else\"," + DONATION + "]");
		assertTrue(alerts.isEmpty());
	}

	@Test
	void oddDonationsStillWork() {
		// No name, amount as a number with a comma-free decimal, currency only on the streamer's account.
		client.onFrame("42[\"new-event\",{\"event\":{\"id\":7,\"type\":\"donation\",\"user\":{\"currency\":{\"code\":\"usd\"}},"
				+ "\"parameters\":{\"amount\":2.5}}}]");
		assertEquals(1, alerts.size());
		assertEquals("Anoniem", alerts.getFirst().user());
		assertEquals("US$ 2,50", alerts.getFirst().detail());
		// A broken amount does not lose the alert.
		client.onFrame("42[\"new-event\",{\"event\":{\"id\":8,\"type\":\"donation\",\"parameters\":{\"username\":\"b\",\"amount\":\"veel\"}}}]");
		assertEquals(2, alerts.size());
		assertEquals(0, alerts.getLast().amount());
	}

	@Test
	void serverRefusalStopsAndSaysSo() {
		client.useKey(KEY);
		client.onFrame("44{\"message\":\"Forbidden\"}");
		assertFalse(client.connected());
		assertTrue(statuses.getLast().contains("/twitchplays tipeeestream"));
	}

	@Test
	void keyOnlyGoesToTipeeeStream() {
		assertEquals("https://sso-cf.tipeeestream.com:443",
				TipeeeStreamClient.hostFromInfo("{\"code\":200,\"message\":\"success\",\"datas\":{\"port\":\"443\",\"host\":\"https:\\/\\/sso-cf.tipeeestream.com\"}}"));
		assertNull(TipeeeStreamClient.hostFromInfo("{\"datas\":{\"port\":\"443\",\"host\":\"https://evil.example\"}}"));
		assertNull(TipeeeStreamClient.hostFromInfo("{\"datas\":{\"port\":\"443\",\"host\":\"https://tipeeestream.com.evil.example\"}}"));
		assertNull(TipeeeStreamClient.hostFromInfo("{\"datas\":{\"port\":\"443\",\"host\":\"https://eviltipeeestream.com\"}}"));
		assertNull(TipeeeStreamClient.hostFromInfo("{\"datas\":{\"port\":\"80\",\"host\":\"http://sso.tipeeestream.com\"}}"), "no unencrypted connection");
		assertNull(TipeeeStreamClient.hostFromInfo("{\"datas\":{\"port\":\"99999\",\"host\":\"https://sso.tipeeestream.com\"}}"));
		assertNull(TipeeeStreamClient.hostFromInfo("geen json"));
		String url = TipeeeStreamClient.socketUrl("https://sso-cf.tipeeestream.com:443", KEY);
		assertEquals("wss://sso-cf.tipeeestream.com:443/socket.io/?EIO=4&transport=websocket&access_token=" + KEY, url);
	}

	@Test
	void apiKeyShape() {
		assertTrue(TipeeeStreamClient.looksLikeApiKey(KEY));
		assertFalse(TipeeeStreamClient.looksLikeApiKey("kort"));
		assertFalse(TipeeeStreamClient.looksLikeApiKey("https://www.tipeeestream.com/dashboard/api-key"));
		assertFalse(TipeeeStreamClient.looksLikeApiKey(null));
	}

	@Test
	void garbageFramesNeverThrow() {
		client.useKey(KEY);
		String[] hostile = {"", "0", "0{", "40", "42", "42[", "42[]", "42[\"new-event\"]", "42[\"new-event\",null]", "42[\"new-event\",[]]",
				"42[\"new-event\",{\"event\":[]}]", "42[\"new-event\",{\"event\":{\"type\":\"donation\",\"parameters\":[]}}]",
				"42[\"new-event\",{\"event\":{\"type\":\"donation\",\"parameters\":{\"amount\":\"1e400\"}}}]",
				"42[\"new-event\",{\"event\":{\"type\":\"donation\",\"parameters\":{\"amount\":\"-5\"}}}]",
				"42[\"new-event\",{\"event\":{\"type\":\"donation\",\"parameters\":{\"amount\":\"NaN\"}}}]",
				"0{\"pingInterval\":\"x\"}", "6", "3", "1", "41"};
		for (String frame : hostile) {
			client.onFrame(frame);
		}
		for (Alert alert : alerts) {
			assertTrue(alert.amount() >= 0, alert.toString());
		}
		Random random = new Random(12);
		String alphabet = "042[]{}\":,eventdonationparametersamount-1.5 ";
		for (int i = 0; i < 20_000; i++) {
			StringBuilder frame = new StringBuilder(random.nextBoolean() ? "42[\"new-event\"," : "");
			for (int j = random.nextInt(60); j > 0; j--) {
				frame.append(alphabet.charAt(random.nextInt(alphabet.length())));
			}
			client.onFrame(frame.toString());
		}
	}
}
