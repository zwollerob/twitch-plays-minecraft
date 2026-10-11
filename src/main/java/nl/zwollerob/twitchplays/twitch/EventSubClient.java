package nl.zwollerob.twitchplays.twitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Receives channel point redemptions through EventSub over WebSocket.
 * Twitch closes the socket if we don't subscribe within 10 seconds of the welcome,
 * and sends a keepalive at least every ~10 seconds while it is healthy.
 */
public final class EventSubClient {
	private static final String DEFAULT_URL = "wss://eventsub.wss.twitch.tv/ws";
	private static final String TYPE = "channel.channel_points_custom_reward_redemption.add";

	public record Redemption(String userName, String rewardTitle, String userInput) {
	}

	private final TwitchAuth auth;
	private final Consumer<Redemption> onRedemption;
	private final Consumer<String> onStatus;

	private volatile String clientId;
	private volatile boolean active;
	private volatile WebSocket socket;
	private volatile long lastMessage;
	private volatile long keepaliveMillis = 30_000;
	private int generation;
	private int failures;
	private ScheduledFuture<?> watchdog;

	public EventSubClient(TwitchAuth auth, Consumer<Redemption> onRedemption, Consumer<String> onStatus) {
		this.auth = auth;
		this.onRedemption = onRedemption;
		this.onStatus = onStatus;
	}

	public boolean active() {
		return active;
	}

	public synchronized void start(String clientId) {
		stop();
		if (clientId == null || clientId.isBlank() || auth.token() == null) {
			return;
		}
		this.clientId = clientId.strip();
		active = true;
		failures = 0;
		open(generation, DEFAULT_URL, true);
		watchdog = Scheduler.INSTANCE.scheduleAtFixedRate(this::checkAlive, 5, 5, TimeUnit.SECONDS);
	}

	public synchronized void stop() {
		generation++;
		active = false;
		if (watchdog != null) {
			watchdog.cancel(false);
			watchdog = null;
		}
		WebSocket old = socket;
		socket = null;
		if (old != null) {
			old.abort();
		}
	}

	private synchronized void open(int gen, String url, boolean subscribe) {
		if (gen != generation) {
			return;
		}
		lastMessage = System.currentTimeMillis();
		TwitchAuth.HTTP.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(URI.create(url), new Listener(gen, subscribe))
				.whenComplete((ws, error) -> {
					if (error != null) {
						reconnect(gen, "kan niet verbinden: " + error.getMessage());
					}
				});
	}

	private synchronized void reconnect(int gen, String reason) {
		if (gen != generation) {
			return;
		}
		int next = ++generation;
		WebSocket old = socket;
		socket = null;
		if (old != null) {
			old.abort();
		}
		failures++;
		long delay = Math.min(60, 1L << Math.min(failures, 6));
		onStatus.accept("Kanaalpunten verbroken (" + reason + "), opnieuw over " + delay + "s");
		Scheduler.INSTANCE.schedule(() -> open(next, DEFAULT_URL, true), delay, TimeUnit.SECONDS);
	}

	private void checkAlive() {
		int gen;
		synchronized (this) {
			gen = generation;
		}
		if (active && System.currentTimeMillis() - lastMessage > keepaliveMillis + 15_000) {
			reconnect(gen, "geen keepalive");
		}
	}

	private void subscribe(int gen, String sessionId, boolean retried) {
		TwitchAuth.Token token = auth.token();
		if (token == null || gen != generation) {
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("type", TYPE);
		body.addProperty("version", "1");
		JsonObject condition = new JsonObject();
		condition.addProperty("broadcaster_user_id", token.userId());
		body.add("condition", condition);
		JsonObject transport = new JsonObject();
		transport.addProperty("method", "websocket");
		transport.addProperty("session_id", sessionId);
		body.add("transport", transport);
		try {
			HttpResponse<String> response = TwitchAuth.HTTP.send(HttpRequest.newBuilder(URI.create("https://api.twitch.tv/helix/eventsub/subscriptions"))
					.header("Authorization", "Bearer " + token.accessToken())
					.header("Client-Id", clientId)
					.header("Content-Type", "application/json")
					.timeout(Duration.ofSeconds(10))
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build(), HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			if (status == 202 || status == 409) {
				failures = 0;
				onStatus.accept("Kanaalpunten actief voor " + token.login());
			} else if (status == 401 && !retried) {
				auth.refresh(clientId);
				subscribe(gen, sessionId, true);
			} else if (status == 403) {
				onStatus.accept("Kanaalpunten niet beschikbaar: alleen voor Affiliates/Partners, en log in met het account van het kanaal. " + response.body());
				stop();
			} else {
				onStatus.accept("Kanaalpunten aanmelden mislukt (" + status + "): " + response.body());
			}
		} catch (Exception e) {
			onStatus.accept("Kanaalpunten aanmelden mislukt: " + e.getMessage());
			if (auth.token() == null) {
				stop();
			}
		}
	}

	private final class Listener implements WebSocket.Listener {
		private final int gen;
		private final boolean subscribeOnWelcome;
		private final StringBuilder buffer = new StringBuilder();

		Listener(int gen, boolean subscribeOnWelcome) {
			this.gen = gen;
			this.subscribeOnWelcome = subscribeOnWelcome;
		}

		@Override
		public void onOpen(WebSocket ws) {
			ws.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				String text = buffer.toString();
				buffer.setLength(0);
				try {
					handle(ws, JsonParser.parseString(text).getAsJsonObject());
				} catch (Exception ignored) {
				}
			}
			ws.request(1);
			return null;
		}

		private void handle(WebSocket ws, JsonObject message) {
			if (gen != generation) {
				ws.abort();
				return;
			}
			lastMessage = System.currentTimeMillis();
			String type = message.getAsJsonObject("metadata").get("message_type").getAsString();
			JsonObject payload = message.getAsJsonObject("payload");
			switch (type) {
				case "session_welcome" -> {
					JsonObject session = payload.getAsJsonObject("session");
					if (session.has("keepalive_timeout_seconds") && !session.get("keepalive_timeout_seconds").isJsonNull()) {
						keepaliveMillis = session.get("keepalive_timeout_seconds").getAsLong() * 1000;
					}
					WebSocket old;
					synchronized (EventSubClient.this) {
						old = socket;
						socket = ws;
					}
					// After a session_reconnect the old socket stays open until the new one is welcomed.
					if (old != null && old != ws) {
						old.abort();
					}
					if (subscribeOnWelcome) {
						String sessionId = session.get("id").getAsString();
						Scheduler.INSTANCE.execute(() -> subscribe(gen, sessionId, false));
					}
				}
				case "session_reconnect" -> {
					String url = payload.getAsJsonObject("session").get("reconnect_url").getAsString();
					open(gen, url, false);
				}
				case "notification" -> {
					JsonObject event = payload.getAsJsonObject("event");
					String input = event.has("user_input") && !event.get("user_input").isJsonNull() ? event.get("user_input").getAsString() : "";
					onRedemption.accept(new Redemption(
							event.get("user_name").getAsString(),
							event.getAsJsonObject("reward").get("title").getAsString(),
							input));
				}
				case "revocation" -> onStatus.accept("Twitch heeft de kanaalpunten-koppeling ingetrokken. Log opnieuw in met /twitchplays login.");
				default -> {
				}
			}
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
			if (ws == socket || socket == null) {
				reconnect(gen, "code " + statusCode + " " + reason);
			}
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			if (ws == socket || socket == null) {
				reconnect(gen, String.valueOf(error.getMessage()));
			}
		}
	}
}
