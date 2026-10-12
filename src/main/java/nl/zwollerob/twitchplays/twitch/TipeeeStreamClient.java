package nl.zwollerob.twitchplays.twitch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Donations from TipeeeStream, through their Socket.IO (v4) server, authenticated with the API key
 * from the TipeeeStream dashboard. See https://api.tipeeestream.com/api-doc/socketio
 * <p>
 * Socket.IO runs over a plain websocket here: "0" opens the connection, "40" joins the default
 * namespace, "2"/"3" are ping/pong and "42[name, data]" is an event.
 */
public final class TipeeeStreamClient {
	/** Tells where the socket server is; it has moved before. */
	private static final String SOCKET_INFO_URL = "https://api.tipeeestream.com/v2.0/site/socket";
	private static final String DEFAULT_HOST = "https://sso-cf.tipeeestream.com:443";
	/** Shown to TipeeeStream as who is listening; any name works. */
	private static final String LISTENER_NAME = "twitchplays";

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final Consumer<Alert> onAlert;
	private final Consumer<String> onStatus;
	/** An event can arrive twice, e.g. after a reconnect; only celebrate it once. */
	private final Set<String> seen = Collections.synchronizedSet(Collections.newSetFromMap(new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
			return size() > 2000;
		}
	}));

	private volatile String apiKey = "";
	private volatile boolean connected;
	private volatile WebSocket socket;
	private volatile long lastMessageAt;
	private volatile long silenceLimitMs = 60_000;
	private int generation;
	private int failures;

	public TipeeeStreamClient(Consumer<Alert> onAlert, Consumer<String> onStatus) {
		this.onAlert = onAlert;
		this.onStatus = onStatus;
	}

	public boolean connected() {
		return connected;
	}

	/** API keys are letters and digits; anything else is not something copied from the dashboard. */
	public static boolean looksLikeApiKey(String key) {
		return key != null && key.strip().matches("[A-Za-z0-9_-]{16,128}");
	}

	public synchronized void start(String key) {
		stop();
		if (!looksLikeApiKey(key)) {
			return;
		}
		useKey(key);
		int gen = generation;
		Scheduler.INSTANCE.execute(() -> open(gen));
	}

	/** Sets the key without connecting (the tests use this to play frames without a network). */
	void useKey(String key) {
		apiKey = key.strip();
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

	/** Asks TipeeeStream where the socket server is, then connects to it. */
	private void open(int gen) {
		String host = DEFAULT_HOST;
		try {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(SOCKET_INFO_URL))
					.timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				String found = hostFromInfo(response.body());
				if (found != null) {
					host = found;
				}
			}
		} catch (Exception e) {
			// Keep the known address; the connection itself will tell whether it still works.
		}
		connect(gen, host);
	}

	/** The socket server from /v2.0/site/socket, e.g. "https://sso-cf.tipeeestream.com:443"; null when it isn't TipeeeStream's own. */
	static String hostFromInfo(String body) {
		try {
			JsonObject datas = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("datas");
			URI uri = URI.create(string(datas, "host"));
			String port = string(datas, "port");
			String host = uri.getHost();
			// Only ever send the API key to TipeeeStream itself, over a secure connection.
			if (!"https".equals(uri.getScheme()) || host == null || !(host.equals("tipeeestream.com") || host.endsWith(".tipeeestream.com"))) {
				return null;
			}
			int portNumber = port.isEmpty() ? (uri.getPort() > 0 ? uri.getPort() : 443) : Integer.parseInt(port);
			if (portNumber <= 0 || portNumber > 65535) {
				return null;
			}
			return "https://" + host + ":" + portNumber;
		} catch (Exception e) {
			return null;
		}
	}

	/** The websocket address for a host from {@link #hostFromInfo}. */
	static String socketUrl(String host, String key) {
		return host.replaceFirst("^https://", "wss://") + "/socket.io/?EIO=4&transport=websocket&access_token="
				+ URLEncoder.encode(key, StandardCharsets.UTF_8);
	}

	private synchronized void connect(int gen, String host) {
		if (gen != generation) {
			return;
		}
		Listener listener = new Listener(gen);
		http.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(URI.create(socketUrl(host, apiKey)), listener)
				.whenComplete((ws, error) -> {
					if (error == null) {
						return;
					}
					Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
					int status = cause instanceof WebSocketHandshakeException handshake ? handshake.getResponse().statusCode() : 0;
					// A wrong key gets "400 Forbidden" from TipeeeStream; retrying won't help.
					if (status == 400 || status == 401 || status == 403) {
						onStatus.accept("TipeeeStream weigert de API-sleutel. Kopieer hem opnieuw en typ /twitchplays tipeeestream");
						stop();
					} else {
						listener.fail(status > 0 ? "HTTP " + status : "kan niet verbinden");
					}
				});
	}

	private synchronized void reconnect(int gen, String reason) {
		if (gen != generation) {
			return;
		}
		connected = false;
		WebSocket old = socket;
		socket = null;
		if (old != null) {
			old.abort();
		}
		failures++;
		long delay = Math.min(60, 1L << Math.min(failures, 6));
		onStatus.accept("TipeeeStream verbroken (" + reason + "), opnieuw over " + delay + "s");
		Scheduler.INSTANCE.schedule(() -> open(gen), delay, TimeUnit.SECONDS);
	}

	/** Reconnects when the server has been silent for longer than its own ping rhythm allows; one watch per connection. */
	private void watch(Listener listener, WebSocket ws) {
		synchronized (this) {
			if (listener.gen != generation || socket != ws) {
				return;
			}
		}
		if (System.currentTimeMillis() - lastMessageAt > silenceLimitMs) {
			listener.fail("geen ping");
			return;
		}
		Scheduler.INSTANCE.schedule(() -> watch(listener, ws), 10, TimeUnit.SECONDS);
	}

	private final class Listener implements WebSocket.Listener {
		private final int gen;
		private final StringBuilder buffer = new StringBuilder();
		private boolean failed;

		Listener(int gen) {
			this.gen = gen;
		}

		synchronized void fail(String reason) {
			if (!failed) {
				failed = true;
				reconnect(gen, reason);
			}
		}

		@Override
		public void onOpen(WebSocket ws) {
			synchronized (TipeeeStreamClient.this) {
				if (gen != generation) {
					ws.abort();
					return;
				}
				socket = ws;
			}
			lastMessageAt = System.currentTimeMillis();
			Scheduler.INSTANCE.schedule(() -> watch(this, ws), 10, TimeUnit.SECONDS);
			ws.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				String frame = buffer.toString();
				buffer.setLength(0);
				if (gen != generation) {
					ws.abort();
					return null;
				}
				lastMessageAt = System.currentTimeMillis();
				for (String reply : onFrame(frame)) {
					ws.sendText(reply, true);
				}
				if (frame.equals("1") || frame.startsWith("41")) {
					fail("server sloot de verbinding");
				}
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
			fail("code " + statusCode);
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			fail("verbindingsfout");
		}
	}

	/** Handles one Socket.IO frame from the server; returns the frames to send back. */
	List<String> onFrame(String frame) {
		List<String> replies = new ArrayList<>();
		try {
			if (frame.startsWith("0{")) {
				// Engine.IO open: the server says how often it pings. Then join the default namespace.
				JsonObject open = JsonParser.parseString(frame.substring(1)).getAsJsonObject();
				long interval = open.has("pingInterval") ? open.get("pingInterval").getAsLong() : 25_000;
				long timeout = open.has("pingTimeout") ? open.get("pingTimeout").getAsLong() : 20_000;
				silenceLimitMs = Math.clamp(interval + timeout + 5_000, 10_000, 300_000);
				replies.add("40");
			} else if (frame.startsWith("40")) {
				JsonObject join = new JsonObject();
				join.addProperty("room", apiKey);
				join.addProperty("username", LISTENER_NAME);
				JsonArray event = new JsonArray();
				event.add("join-room");
				event.add(join);
				replies.add("42" + event);
				if (!connected) {
					connected = true;
					failures = 0;
					onStatus.accept("TipeeeStream verbonden: donaties actief");
				}
			} else if (frame.startsWith("44")) {
				// Connect error from the server, for example a wrong key.
				onStatus.accept("TipeeeStream weigert de verbinding. Kopieer de API-sleutel opnieuw en typ /twitchplays tipeeestream");
				stop();
			} else if (frame.equals("2")) {
				replies.add("3");
			} else if (frame.startsWith("42")) {
				JsonArray event = JsonParser.parseString(frame.substring(2)).getAsJsonArray();
				if (event.size() >= 2 && event.get(0).getAsString().equals("new-event") && event.get(1).isJsonObject()) {
					Alert alert = toAlert(event.get(1).getAsJsonObject());
					if (alert != null) {
						onAlert.accept(alert);
					}
				}
			}
		} catch (RuntimeException e) {
			// A frame we don't understand is skipped; it may never take the connection down.
		}
		return replies;
	}

	/** Turns a "new-event" into a donation alert; null for other events (follows and subs come from Twitch itself). */
	Alert toAlert(JsonObject data) {
		JsonObject event = data.has("event") && data.get("event").isJsonObject() ? data.getAsJsonObject("event") : null;
		if (event == null || !string(event, "type").equals("donation")) {
			return null;
		}
		JsonObject parameters = event.has("parameters") && event.get("parameters").isJsonObject() ? event.getAsJsonObject("parameters") : new JsonObject();
		String id = string(event, "id");
		if (id.isEmpty()) {
			id = string(event, "ref");
		}
		if (id.isEmpty()) {
			id = string(event, "created_at") + "/" + string(parameters, "username") + "/" + string(parameters, "amount");
		}
		if (!seen.add(id)) {
			return null;
		}
		double amount;
		try {
			amount = Double.parseDouble(string(parameters, "amount").replace(',', '.'));
		} catch (NumberFormatException e) {
			amount = 0;
		}
		if (!Double.isFinite(amount) || amount < 0) {
			amount = 0;
		}
		String currency = string(parameters, "currency");
		if (currency.isEmpty() && event.has("user") && event.get("user").isJsonObject()) {
			JsonObject user = event.getAsJsonObject("user");
			if (user.has("currency") && user.get("currency").isJsonObject()) {
				currency = string(user.getAsJsonObject("currency"), "code");
			}
		}
		return Alert.donation(string(parameters, "username"), amount, currency.isEmpty() ? "EUR" : currency.toUpperCase(Locale.ROOT));
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object == null ? null : object.get(key);
		return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? "" : value.getAsString();
	}
}
