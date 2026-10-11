package nl.zwollerob.twitchplays.twitch;

/** A chat message from Twitch IRC, with the tags we use. */
public record ChatMessage(String userId, String login, String displayName, String text, int bits,
		boolean moderator, boolean broadcaster) {

	/** Parses one raw IRC line. Returns null for anything that is not a PRIVMSG. */
	public static ChatMessage parse(String line) {
		IrcLine irc = IrcLine.parse(line);
		return irc == null ? null : from(irc);
	}

	public static ChatMessage from(IrcLine irc) {
		if (!irc.command().equals("PRIVMSG")) {
			return null;
		}
		String text = irc.text();
		// /me messages arrive as CTCP ACTION
		if (text.startsWith("\u0001ACTION ") && text.endsWith("\u0001")) {
			text = text.substring(8, text.length() - 1);
		}
		String badges = irc.tag("badges");
		// In a shared chat, the broadcaster and moderators of the other channel have no say here.
		boolean ownRoom = !irc.fromOtherRoom();
		boolean broadcaster = ownRoom && badges.contains("broadcaster/");
		boolean moderator = broadcaster || ownRoom && ("1".equals(irc.tag("mod")) || badges.contains("moderator/"));
		String userId = irc.tag("user-id");
		// Bits cheered in another channel of a shared chat were not paid to this streamer.
		int bits = irc.fromOtherRoom() ? 0 : irc.intTag("bits");
		return new ChatMessage(userId.isEmpty() ? irc.login() : userId, irc.login(), irc.displayName(), text, bits,
				moderator, broadcaster);
	}
}
