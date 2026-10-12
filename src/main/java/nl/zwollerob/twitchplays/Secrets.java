package nl.zwollerob.twitchplays;

import java.util.regex.Pattern;

/** Recognises text that holds a token or key, so it never ends up in Minecraft's command history or chat history. */
public final class Secrets {
	/** "/twitchplays streamelements <something>" or "/twitchplays tipeeestream <something>", except "off". */
	private static final Pattern SECRET_COMMAND = Pattern.compile(
			"(?i)^/?\\s*twitchplays\\s+(streamelements|tipeeestream)\\s+(?!off\\s*$)\\S.*");
	/** A JWT (StreamElements token): base64 parts, the first one starting with {"... Also a cut-off one, as chat cuts at 256 characters. */
	private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(?:\\.[A-Za-z0-9_-]*)?");
	/** A Twitch chat password. */
	private static final Pattern OAUTH = Pattern.compile("(?i)oauth:[a-z0-9]{20,}");
	/** A token in a JSON reply or a URL: "access_token":"...", refresh_token=..., access_token=... */
	private static final Pattern ACCESS_TOKEN = Pattern.compile("(?i)((?:access|refresh)_token\"?\\s*[:=]\\s*\"?)[^\"&\\s,}]+");

	private Secrets() {
	}

	public static boolean isSensitive(String text) {
		if (text == null) {
			return false;
		}
		return SECRET_COMMAND.matcher(text).matches() || JWT.matcher(text).find() || OAUTH.matcher(text).find()
				|| ACCESS_TOKEN.matcher(text).find();
	}

	/** Replaces anything that looks like a token with "***", for text that goes to the log or the screen. */
	public static String mask(String text) {
		if (text == null) {
			return "";
		}
		String masked = JWT.matcher(text).replaceAll("***");
		masked = OAUTH.matcher(masked).replaceAll("oauth:***");
		return ACCESS_TOKEN.matcher(masked).replaceAll("$1***");
	}
}
