package nl.zwollerob.twitchplays.twitch;

import java.util.HashMap;
import java.util.Map;

/**
 * One raw Twitch IRC line, split into its parts.
 * Example: {@code @badges=moderator/1;display-name=Foo :foo!foo@foo.tmi.twitch.tv PRIVMSG #chan :hello}
 */
public record IrcLine(Map<String, String> tags, String login, String command, String text) {

	public static IrcLine parse(String line) {
		if (line == null || line.isEmpty()) {
			return null;
		}
		String rest = line;
		Map<String, String> tags = new HashMap<>();
		if (rest.startsWith("@")) {
			int space = rest.indexOf(' ');
			if (space < 0) {
				return null;
			}
			for (String tag : rest.substring(1, space).split(";")) {
				int eq = tag.indexOf('=');
				if (eq > 0) {
					tags.put(tag.substring(0, eq), unescape(tag.substring(eq + 1)));
				}
			}
			rest = rest.substring(space + 1);
		}
		String login = "";
		if (rest.startsWith(":")) {
			int space = rest.indexOf(' ');
			if (space < 0) {
				return null;
			}
			String source = rest.substring(1, space);
			int bang = source.indexOf('!');
			login = bang > 0 ? source.substring(0, bang) : source;
			rest = rest.substring(space + 1);
		}
		int space = rest.indexOf(' ');
		String command = space < 0 ? rest : rest.substring(0, space);
		int textStart = rest.indexOf(" :");
		String text = textStart < 0 ? "" : rest.substring(textStart + 2);
		return new IrcLine(tags, login, command, text);
	}

	public String tag(String name) {
		return tags.getOrDefault(name, "");
	}

	public int intTag(String name) {
		try {
			return Integer.parseInt(tag(name));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Display name, or the login when the display name is empty. */
	public String displayName() {
		String name = tag("display-name");
		return name.isEmpty() ? (login.isEmpty() ? tag("login") : login) : name;
	}

	/** In a shared chat session Twitch also sends the other channels' messages; those have a different source room. */
	public boolean fromOtherRoom() {
		String source = tag("source-room-id");
		return !source.isEmpty() && !source.equals(tag("room-id"));
	}

	private static String unescape(String value) {
		if (value.indexOf('\\') < 0) {
			return value;
		}
		StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c != '\\') {
				out.append(c);
				continue;
			}
			if (i + 1 >= value.length()) {
				break;
			}
			char next = value.charAt(++i);
			switch (next) {
				case 's' -> out.append(' ');
				case ':' -> out.append(';');
				case 'r' -> out.append('\r');
				case 'n' -> out.append('\n');
				default -> out.append(next);
			}
		}
		return out.toString();
	}
}
