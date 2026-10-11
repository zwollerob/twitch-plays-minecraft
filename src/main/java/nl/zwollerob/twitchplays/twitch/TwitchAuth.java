package nl.zwollerob.twitchplays.twitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.zwollerob.twitchplays.SecretStore;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Twitch login through the Device Code Flow: the streamer opens twitch.tv/activate,
 * enters a code and approves. Only needed for channel point rewards.
 * Requires the Client ID of a "Public" app registered on dev.twitch.tv.
 */
public final class TwitchAuth {
	public static final String SCOPES = "channel:read:redemptions";

	public record Token(String accessToken, String refreshToken, String userId, String login) {
	}

	public record DeviceCode(String userCode, String verificationUri) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private final Path file;
	private volatile Token token;
	private int loginAttempt;

	public TwitchAuth(Path file) {
		this.file = file;
		try {
			String json = SecretStore.read(file);
			token = json.isEmpty() ? null : GSON.fromJson(json, Token.class);
		} catch (Exception e) {
			token = null;
		}
	}

	public Token token() {
		return token;
	}

	public void logout() {
		loginAttempt++;
		token = null;
		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
		}
	}

	/** Starts a login. onCode gets the code to show; onDone gets the token or an error message. */
	public void login(String clientId, Consumer<DeviceCode> onCode, Consumer<Token> onDone, Consumer<String> onError) {
		int attempt = ++loginAttempt;
		Scheduler.INSTANCE.execute(() -> {
			try {
				HttpResponse<String> response = post("https://id.twitch.tv/oauth2/device",
						Map.of("client_id", clientId, "scopes", SCOPES));
				if (response.statusCode() != 200) {
					onError.accept("Twitch weigerde de login (" + response.statusCode() + "). Klopt de Client ID, en is de app 'Public'? " + response.body());
					return;
				}
				JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
				String deviceCode = json.get("device_code").getAsString();
				int interval = Math.max(1, json.get("interval").getAsInt());
				long expiresAt = System.currentTimeMillis() + json.get("expires_in").getAsLong() * 1000;
				onCode.accept(new DeviceCode(json.get("user_code").getAsString(), json.get("verification_uri").getAsString()));
				poll(attempt, clientId, deviceCode, interval, expiresAt, onDone, onError);
			} catch (Exception e) {
				onError.accept("Login mislukt: " + e.getMessage());
			}
		});
	}

	private void poll(int attempt, String clientId, String deviceCode, int interval, long expiresAt,
			Consumer<Token> onDone, Consumer<String> onError) {
		Scheduler.INSTANCE.schedule(() -> {
			if (attempt != loginAttempt) {
				return;
			}
			if (System.currentTimeMillis() > expiresAt) {
				onError.accept("De logincode is verlopen. Probeer /twitchplays login opnieuw.");
				return;
			}
			try {
				HttpResponse<String> response = post("https://id.twitch.tv/oauth2/token", Map.of(
						"client_id", clientId,
						"scopes", SCOPES,
						"device_code", deviceCode,
						"grant_type", "urn:ietf:params:oauth:grant-type:device_code"));
				if (response.statusCode() == 200) {
					onDone.accept(store(clientId, JsonParser.parseString(response.body()).getAsJsonObject()));
				} else if (response.body().contains("authorization_pending") || response.body().contains("slow_down")) {
					poll(attempt, clientId, deviceCode, interval, expiresAt, onDone, onError);
				} else {
					onError.accept("Login geweigerd of mislukt: " + response.body());
				}
			} catch (Exception e) {
				poll(attempt, clientId, deviceCode, interval, expiresAt, onDone, onError);
			}
		}, interval, TimeUnit.SECONDS);
	}

	/** Gets a new access token with the refresh token. Blocking; call it off the main thread. */
	public synchronized Token refresh(String clientId) throws IOException, InterruptedException {
		Token current = token;
		if (current == null || current.refreshToken() == null) {
			throw new IOException("niet ingelogd");
		}
		HttpResponse<String> response = post("https://id.twitch.tv/oauth2/token", Map.of(
				"client_id", clientId,
				"grant_type", "refresh_token",
				"refresh_token", current.refreshToken()));
		if (response.statusCode() != 200) {
			logout();
			throw new IOException("login verlopen, log opnieuw in met /twitchplays login");
		}
		return store(clientId, JsonParser.parseString(response.body()).getAsJsonObject());
	}

	private Token store(String clientId, JsonObject json) throws IOException, InterruptedException {
		String access = json.get("access_token").getAsString();
		String refresh = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
		HttpResponse<String> validate = HTTP.send(HttpRequest.newBuilder(URI.create("https://id.twitch.tv/oauth2/validate"))
				.header("Authorization", "OAuth " + access)
				.timeout(Duration.ofSeconds(10))
				.build(), HttpResponse.BodyHandlers.ofString());
		if (validate.statusCode() != 200) {
			throw new IOException("token ongeldig: " + validate.body());
		}
		JsonObject info = JsonParser.parseString(validate.body()).getAsJsonObject();
		Token result = new Token(access, refresh, info.get("user_id").getAsString(), info.get("login").getAsString());
		token = result;
		SecretStore.write(file, GSON.toJson(result));
		return result;
	}

	private static HttpResponse<String> post(String url, Map<String, String> form) throws IOException, InterruptedException {
		String body = new LinkedHashMap<>(form).entrySet().stream()
				.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
		return HTTP.send(HttpRequest.newBuilder(URI.create(url))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.timeout(Duration.ofSeconds(15))
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build(), HttpResponse.BodyHandlers.ofString());
	}
}
