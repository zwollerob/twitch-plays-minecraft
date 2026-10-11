package nl.zwollerob.twitchplays.twitch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Base64;
import java.util.Currency;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Donations and follows from StreamElements' Astro websocket gateway, authenticated with the
 * JWT token from the StreamElements dashboard. See https://docs.streamelements.com/websockets
 */
public final class StreamElementsClient {
	private static final String URL = "wss://astro.streamelements.com";

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final Consumer<Alert> onAlert;
	private final Consumer<String> onStatus;
	/** Astro can deliver an event again after a reconnect; only celebrate it once. */
	private final Set<String> seen = Collections.synchronizedSet(Collections.newSetFromMap(new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
			return size() > 2000;
		}
	}));

	private volatile String token;
	private volatile boolean connected;
	private volatile WebSocket socket;
	private int generation;
	private int failures;

	public StreamElementsClient(Consumer<Alert> onAlert, Consumer<String> onStatus) {
		this.onAlert = onAlert;
		this.onStatus = onStatus;
	}

	public boolean connected() {
		return connected;
	}

	/** A JWT is three base64 parts separated by dots. */
	public static boolean looksLikeJwt(String token) {
		return token != null && token.strip().matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
	}

	/** The StreamElements channel id is in the JWT's payload; Astro uses it as the room. */
	static String channelFromJwt(String jwt) {
		try {
			String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
			JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
			return json.has("channel") ? json.get("channel").getAsString() : "";
		} catch (Exception e) {
			return "";
		}
	}

	public synchronized void start(String token) {
		stop();
		if (!looksLikeJwt(token)) {
			return;
		}
		this.token = token.strip();
		open(generation, URL);
	}

	public synchronized void stop() {
		generation++;
		connected = false;
		failures = 0;
		WebSocket old = socket;
		socket = null;
		if (old != null) {
			old.abort();
		}
	}

	private synchronized void open(int gen, String url) {
		if (gen != generation) {
			return;
		}
		Listener listener = new Listener(gen);
		http.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(URI.create(url), listener)
				.whenComplete((ws, error) -> {
					if (error != null) {
						listener.fail("kan niet verbinden: " + error.getMessage(), URL);
					}
				});
	}

	private synchronized void reconnect(int gen, String reason, String url, boolean immediately) {
		if (gen != generation) {
			return;
		}
		connected = false;
		socket = null;
		long delay = 0;
		if (!immediately) {
			failures++;
			delay = Math.min(60, 1L << Math.min(failures, 6));
			onStatus.accept("StreamElements verbroken (" + reason + "), opnieuw over " + delay + "s");
		}
		Scheduler.INSTANCE.schedule(() -> open(gen, url), delay, TimeUnit.SECONDS);
	}

	private final class Listener implements WebSocket.Listener {
		private final int gen;
		private final StringBuilder buffer = new StringBuilder();
		private boolean failed;

		Listener(int gen) {
			this.gen = gen;
		}

		synchronized void fail(String reason, String url) {
			if (!failed) {
				failed = true;
				reconnect(gen, reason, url, false);
			}
		}

		@Override
		public void onOpen(WebSocket ws) {
			synchronized (StreamElementsClient.this) {
				if (gen != generation) {
					ws.abort();
					return;
				}
				socket = ws;
			}
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
			switch (string(message, "type")) {
				case "welcome" -> {
					subscribe(ws, "channel.tips");
					subscribe(ws, "channel.activities");
				}
				case "response" -> {
					if (message.has("error") && !message.get("error").isJsonNull()) {
						String error = string(message, "error");
						String detail = message.has("data") ? string(message.getAsJsonObject("data"), "message") : "";
						if (error.contains("unauthorized")) {
							onStatus.accept("StreamElements weigert het token. Kopieer opnieuw je JWT-token en typ /twitchplays streamelements");
							stop();
						} else {
							onStatus.accept("StreamElements: " + error + " " + detail);
						}
					} else if (!connected) {
						connected = true;
						failures = 0;
						onStatus.accept("StreamElements verbonden: donaties en follows actief");
					}
				}
				case "message" -> {
					Alert alert = toAlert(string(message, "topic"), message.getAsJsonObject("data"));
					if (alert != null) {
						onAlert.accept(alert);
					}
				}
				case "reconnect" -> {
					String reconnectToken = string(message.getAsJsonObject("data"), "reconnect_token");
					failed = true;
					reconnect(gen, "server herstart", URL + "/?reconnect_token=" + URLEncoder.encode(reconnectToken, StandardCharsets.UTF_8), true);
					ws.abort();
				}
				default -> {
				}
			}
		}

		private void subscribe(WebSocket ws, String topic) {
			JsonObject data = new JsonObject();
			data.addProperty("topic", topic);
			String room = channelFromJwt(token);
			if (!room.isEmpty()) {
				data.addProperty("room", room);
			}
			data.addProperty("token", token);
			data.addProperty("token_type", "jwt");
			JsonObject request = new JsonObject();
			request.addProperty("type", "subscribe");
			request.addProperty("nonce", UUID.randomUUID().toString());
			request.add("data", data);
			ws.sendText(request.toString(), true);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
			fail("code " + statusCode, URL);
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			fail(String.valueOf(error.getMessage()), URL);
		}
	}

	/** Turns a channel.tips or channel.activities (follow) message into an alert; null for anything else. */
	Alert toAlert(String topic, JsonObject data) {
		if (data == null) {
			return null;
		}
		if (topic.equals("channel.tips")) {
			JsonObject donation = data.getAsJsonObject("donation");
			if (donation == null || !seen.add("tip:" + string(data, "_id"))) {
				return null;
			}
			String status = string(data, "status");
			String approved = string(data, "approved");
			if (!status.isEmpty() && !status.equals("success") || approved.equals("denied") || approved.equals("rejected")) {
				return null;
			}
			JsonObject user = donation.getAsJsonObject("user");
			String name = user == null ? "" : string(user, "username");
			double amount = donation.has("amount") ? donation.get("amount").getAsDouble() : 0;
			return Alert.of(Alert.Type.DONATION, name.isEmpty() ? "Anoniem" : name,
					formatMoney(amount, string(donation, "currency")), (int) Math.round(amount));
		}
		if (topic.equals("channel.activities") && string(data, "type").equals("follow")) {
			JsonObject user = data.getAsJsonObject("data");
			if (user == null) {
				return null;
			}
			String name = string(user, "displayName");
			if (name.isEmpty()) {
				name = string(user, "username");
			}
			// Follow, unfollow, follow again: only the first one counts.
			if (name.isEmpty() || !seen.add("follow:" + name.toLowerCase(Locale.ROOT))) {
				return null;
			}
			return Alert.of(Alert.Type.FOLLOW, name, "", 1);
		}
		return null;
	}

	static String formatMoney(double amount, String currency) {
		NumberFormat format = NumberFormat.getCurrencyInstance(Locale.of("nl", "NL"));
		try {
			format.setCurrency(Currency.getInstance(currency.toUpperCase(Locale.ROOT)));
		} catch (Exception e) {
			return String.format(Locale.ROOT, "%.2f %s", amount, currency).strip();
		}
		return format.format(amount).replace(' ', ' ');
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object == null ? null : object.get(key);
		return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? "" : value.getAsString();
	}
}
