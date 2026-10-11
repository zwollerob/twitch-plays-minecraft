package nl.zwollerob.twitchplays.twitch;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Posts a message in the streamer's own Twitch chat, as the logged-in streamer
 * (Helix "Send Chat Message", scope user:write:chat). Does nothing without that login.
 */
public final class ChatSender {
	/** Twitch's limit for one chat message. */
	static final int MAX_LENGTH = 500;

	private final TwitchAuth auth;
	private final Supplier<String> clientId;
	private final Consumer<String> onStatus;

	public ChatSender(TwitchAuth auth, Supplier<String> clientId, Consumer<String> onStatus) {
		this.auth = auth;
		this.clientId = clientId;
		this.onStatus = onStatus;
	}

	public boolean available() {
		TwitchAuth.Token token = auth.token();
		return token != null && token.has(TwitchAuth.SCOPE_CHAT) && !clientId.get().isBlank();
	}

	/** Sends in the background; failures only show up as a status line in game. */
	public void send(String message) {
		if (!available()) {
			return;
		}
		String text = message.length() > MAX_LENGTH ? message.substring(0, MAX_LENGTH) : message;
		Scheduler.INSTANCE.execute(() -> post(text, false));
	}

	private void post(String text, boolean retried) {
		TwitchAuth.Token token = auth.token();
		if (token == null) {
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("broadcaster_id", token.userId());
		body.addProperty("sender_id", token.userId());
		body.addProperty("message", text);
		try {
			HttpResponse<String> response = TwitchAuth.HTTP.send(HttpRequest.newBuilder(URI.create("https://api.twitch.tv/helix/chat/messages"))
					.header("Authorization", "Bearer " + token.accessToken())
					.header("Client-Id", clientId.get().strip())
					.header("Content-Type", "application/json")
					.timeout(Duration.ofSeconds(10))
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 401 && !retried) {
				auth.refresh(clientId.get().strip());
				post(text, true);
			} else if (response.statusCode() != 200 || response.body().contains("\"is_sent\":false")) {
				onStatus.accept("Bericht in Twitch-chat niet verstuurd (" + response.statusCode() + "): " + response.body());
			}
		} catch (Exception e) {
			onStatus.accept("Bericht in Twitch-chat niet verstuurd: " + e.getMessage());
		}
	}
}
