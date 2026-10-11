package nl.zwollerob.twitchplays.twitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertParsingTest {
	private static Alert notice(String tags, String text) {
		return Alert.fromUserNotice(IrcLine.parse("@" + tags + " :tmi.twitch.tv USERNOTICE #streamer" + (text == null ? "" : " :" + text)));
	}

	@Test
	void sub() {
		Alert alert = notice("display-name=Kijker;login=kijker;msg-id=sub;msg-param-sub-plan=Prime;room-id=1", null);
		assertEquals(Alert.Type.SUB, alert.type());
		assertEquals("Kijker", alert.user());
		assertEquals("Prime", alert.detail());
	}

	@Test
	void resubWithMessage() {
		Alert alert = notice("display-name=Kijker;login=kijker;msg-id=resub;msg-param-cumulative-months=14;msg-param-sub-plan=2000", "Al 14 maanden!");
		assertEquals(Alert.Type.RESUB, alert.type());
		assertEquals("14 maanden", alert.detail());
		assertEquals(14, alert.amount());
	}

	@Test
	void giftAndGiftBomb() {
		Alert bomb = notice("display-name=Gulle;login=gulle;msg-id=submysterygift;msg-param-mass-gift-count=5", null);
		assertEquals(Alert.Type.GIFT_BOMB, bomb.type());
		assertEquals(5, bomb.amount());
		assertEquals("gulle", bomb.gifter());

		Alert gift = notice("display-name=Gulle;login=gulle;msg-id=subgift;msg-param-recipient-display-name=Bofkont", null);
		assertEquals(Alert.Type.GIFT, gift.type());
		assertEquals("Bofkont", gift.detail());
		assertEquals("gulle", gift.gifter());
	}

	@Test
	void raid() {
		Alert alert = notice("display-name=raider;login=raider;msg-id=raid;msg-param-displayName=Raider;msg-param-viewerCount=123", null);
		assertEquals(Alert.Type.RAID, alert.type());
		assertEquals("Raider", alert.user());
		assertEquals(123, alert.amount());
	}

	@Test
	void ignoresOtherNoticesAndSharedChat() {
		assertNull(notice("display-name=Mod;msg-id=announcement", "Hallo!"));
		assertNull(notice("display-name=Ander;msg-id=sub;room-id=1;source-room-id=2", null));
		assertNull(Alert.fromUserNotice(IrcLine.parse(":a!a@a.tmi.twitch.tv PRIVMSG #c :hoi")));
	}

	@Test
	void timeoutNamesTheViewer() {
		IrcLine timeout = IrcLine.parse("@ban-duration=600;room-id=1;target-user-id=42;tmi-sent-ts=1 :tmi.twitch.tv CLEARCHAT #streamer :trol");
		assertEquals("CLEARCHAT", timeout.command());
		assertEquals("42", timeout.tag("target-user-id"));
		IrcLine clearAll = IrcLine.parse("@room-id=1 :tmi.twitch.tv CLEARCHAT #streamer");
		assertEquals("", clearAll.tag("target-user-id"), "clearing the whole chat has no target");
	}

	@Test
	void sharedChatBitsDoNotCount() {
		ChatMessage own = ChatMessage.parse("@bits=100;room-id=1;user-id=5 :a!a@a.tmi.twitch.tv PRIVMSG #c :Cheer100");
		ChatMessage other = ChatMessage.parse("@bits=100;room-id=1;source-room-id=2;user-id=5 :a!a@a.tmi.twitch.tv PRIVMSG #c :Cheer100");
		assertEquals(100, own.bits());
		assertEquals(0, other.bits());
	}

	@Test
	void streamElementsTip() {
		StreamElementsClient client = new StreamElementsClient(a -> { }, s -> { });
		JsonObject data = JsonParser.parseString("""
				{"donation":{"user":{"username":"Styler"},"message":"Top stream!","amount":4.2,"currency":"EUR"},
				 "_id":"67b5","approved":"allowed","status":"success"}""").getAsJsonObject();
		Alert alert = client.toAlert("channel.tips", data);
		assertEquals(Alert.Type.DONATION, alert.type());
		assertEquals("Styler", alert.user());
		assertEquals("€ 4,20", alert.detail());
		assertEquals(4, alert.amount());
		assertNull(client.toAlert("channel.tips", data), "the same tip twice must alert once");

		JsonObject pending = JsonParser.parseString("""
				{"donation":{"user":{"username":"X"},"amount":1,"currency":"USD"},"_id":"2","status":"pending"}""").getAsJsonObject();
		assertNull(client.toAlert("channel.tips", pending));
	}

	@Test
	void streamElementsFollowOncePerUser() {
		StreamElementsClient client = new StreamElementsClient(a -> { }, s -> { });
		JsonObject follow = JsonParser.parseString("""
				{"type":"follow","provider":"twitch","data":{"username":"jtv","displayName":"JTV"}}""").getAsJsonObject();
		Alert alert = client.toAlert("channel.activities", follow);
		assertEquals(Alert.Type.FOLLOW, alert.type());
		assertEquals("JTV", alert.user());
		assertNull(client.toAlert("channel.activities", follow));
		assertNull(client.toAlert("channel.activities", JsonParser.parseString("""
				{"type":"subscriber","data":{"username":"a"}}""").getAsJsonObject()));
	}

	@Test
	void jwtHelpers() {
		String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"channel\":\"603abc\",\"role\":\"owner\"}".getBytes(StandardCharsets.UTF_8));
		String jwt = "eyJhbGciOiJIUzI1NiJ9." + payload + ".c2lnbmF0dXJl";
		assertTrue(StreamElementsClient.looksLikeJwt(jwt));
		assertEquals("603abc", StreamElementsClient.channelFromJwt(jwt));
		assertFalse(StreamElementsClient.looksLikeJwt("geen token"));
		assertEquals("", StreamElementsClient.channelFromJwt("a.b.c"));
	}
}
