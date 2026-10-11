package nl.zwollerob.twitchplays.twitch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Reads Twitch chat anonymously (as a "justinfan" guest), so no login is needed.
 * Bits arrive in the message tags, so cheers work without login too.
 * Callbacks run on a background thread.
 */
public final class IrcClient {
	private static final URI SERVER = URI.create("wss://irc-ws.chat.twitch.tv:443");

	public enum State { DISCONNECTED, CONNECTING, CONNECTED }

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final Consumer<ChatMessage> onMessage;
	private final Consumer<Alert> onAlert;
	private final Consumer<String> onStatus;

	private volatile State state = State.DISCONNECTED;
	private volatile String channel = "";
	private volatile WebSocket socket;
	private int generation;
	private int failures;

	public IrcClient(Consumer<ChatMessage> onMessage, Consumer<Alert> onAlert, Consumer<String> onStatus) {
		this.onMessage = onMessage;
		this.onAlert = onAlert;
		this.onStatus = onStatus;
	}

	public State state() {
		return state;
	}

	public String channel() {
		return channel;
	}

	public synchronized void connect(String channel) {
		disconnect();
		String name = channel == null ? "" : channel.strip().toLowerCase(Locale.ROOT).replaceFirst("^#", "");
		if (name.isEmpty()) {
			return;
		}
		this.channel = name;
		open(generation);
	}

	public synchronized void disconnect() {
		generation++;
		channel = "";
		failures = 0;
		WebSocket old = socket;
		socket = null;
		state = State.DISCONNECTED;
		if (old != null) {
			old.abort();
		}
	}

	private synchronized void open(int gen) {
		if (gen != generation || channel.isEmpty()) {
			return;
		}
		state = State.CONNECTING;
		Listener listener = new Listener(gen);
		http.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(SERVER, listener)
				.whenComplete((ws, error) -> {
					if (error != null) {
						listener.fail("kan niet verbinden: " + error.getMessage());
					}
				});
	}

	private synchronized void scheduleReconnect(int gen, String reason) {
		if (gen != generation) {
			return;
		}
		socket = null;
		state = State.CONNECTING;
		failures++;
		long delay = Math.min(60, 1L << Math.min(failures, 6));
		onStatus.accept("Twitch-chat verbroken (" + reason + "), opnieuw verbinden over " + delay + "s");
		Scheduler.INSTANCE.schedule(() -> open(gen), delay, TimeUnit.SECONDS);
	}

	private final class Listener implements WebSocket.Listener {
		private final int gen;
		private final StringBuilder buffer = new StringBuilder();
		private boolean failed;

		Listener(int gen) {
			this.gen = gen;
		}

		/** onClose, onError and a RECONNECT can all fire for one socket; reconnect only once. */
		synchronized void fail(String reason) {
			if (!failed) {
				failed = true;
				scheduleReconnect(gen, reason);
			}
		}

		@Override
		public void onOpen(WebSocket ws) {
			synchronized (IrcClient.this) {
				if (gen != generation) {
					ws.abort();
					return;
				}
				socket = ws;
			}
			ws.sendText("CAP REQ :twitch.tv/tags twitch.tv/commands", true);
			ws.sendText("PASS SCHMOOPIIE", true);
			ws.sendText("NICK justinfan" + ThreadLocalRandom.current().nextInt(10000, 99999), true);
			ws.sendText("JOIN #" + channel, true);
			ws.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				String chunk = buffer.toString();
				buffer.setLength(0);
				for (String line : chunk.split("\r\n")) {
					handleLine(ws, line);
				}
			}
			ws.request(1);
			return null;
		}

		private void handleLine(WebSocket ws, String line) {
			if (gen != generation || line.isEmpty()) {
				return;
			}
			if (line.startsWith("PING")) {
				ws.sendText("PONG" + line.substring(4), true);
				return;
			}
			IrcLine irc = IrcLine.parse(line);
			if (irc == null) {
				return;
			}
			switch (irc.command()) {
				case "PRIVMSG" -> onMessage.accept(ChatMessage.from(irc));
				case "USERNOTICE" -> {
					Alert alert = Alert.fromUserNotice(irc);
					if (alert != null) {
						onAlert.accept(alert);
					}
				}
				case "ROOMSTATE" -> {
					if (state != State.CONNECTED) {
						state = State.CONNECTED;
						failures = 0;
						onStatus.accept("Verbonden met de chat van #" + channel);
					}
				}
				case "RECONNECT" -> {
					fail("Twitch vroeg om opnieuw te verbinden");
					ws.abort();
				}
				case "NOTICE" -> onStatus.accept("Twitch: " + irc.text());
				default -> {
				}
			}
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
			fail("code " + statusCode);
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			fail(String.valueOf(error.getMessage()));
		}
	}
}
