package nl.zwollerob.twitchplays.twitch;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/**
 * Something worth celebrating on stream: a sub, follow, donation, raid or cheer.
 *
 * @param user   who did it (for a gift: the gifter)
 * @param detail extra text, such as the tier, the gift recipient or the donated amount
 * @param amount months, gift count, viewers, bits or donated amount; bigger means more fireworks
 * @param gifter login of the gifter, used to fold the single gifts of a gift bomb into one alert
 */
public record Alert(Type type, String user, String detail, int amount, String gifter) {

	public enum Type {
		SUB("subs"),
		RESUB("subs"),
		GIFT("gifts"),
		GIFT_BOMB("gifts"),
		FOLLOW("follows"),
		DONATION("donations"),
		RAID("raids"),
		BITS("bits"),
		/** A viewer gets exclusive control; detail is the duration ("10 minuten"). */
		TAKEOVER("takeover");

		/** The config switch (alerts.subs, alerts.gifts, ...) that turns this type on or off. */
		public final String setting;

		Type(String setting) {
			this.setting = setting;
		}
	}

	public static Alert of(Type type, String user, String detail, int amount) {
		return new Alert(type, user, detail, amount, "");
	}

	/** A donation, with the amount written the Dutch way ("€ 4,20"). An unnamed donor is "Anoniem". */
	public static Alert donation(String user, double amount, String currency) {
		String name = user == null || user.isBlank() ? "Anoniem" : user;
		return of(Type.DONATION, name, money(amount, currency), (int) Math.round(amount));
	}

	static String money(double amount, String currency) {
		NumberFormat format = NumberFormat.getCurrencyInstance(Locale.of("nl", "NL"));
		try {
			format.setCurrency(Currency.getInstance(currency.toUpperCase(Locale.ROOT)));
		} catch (Exception e) {
			return String.format(Locale.ROOT, "%.2f %s", amount, currency).strip();
		}
		return format.format(amount).replace(' ', ' ');
	}

	/**
	 * Turns a USERNOTICE (sub, resub, gift, gift bomb, raid) into an alert.
	 * Returns null for other notices, such as announcements or shared chat events from other channels.
	 */
	public static Alert fromUserNotice(IrcLine irc) {
		if (irc == null || !irc.command().equals("USERNOTICE") || irc.fromOtherRoom()) {
			return null;
		}
		String user = irc.displayName();
		String gifter = irc.tag("login");
		return switch (irc.tag("msg-id")) {
			case "sub" -> of(Type.SUB, user, plan(irc.tag("msg-param-sub-plan")), 1);
			case "resub" -> {
				int months = Math.max(1, irc.intTag("msg-param-cumulative-months"));
				yield of(Type.RESUB, user, months + " maanden", months);
			}
			case "subgift", "anonsubgift" -> new Alert(Type.GIFT, user, irc.tag("msg-param-recipient-display-name"), 1, gifter);
			case "submysterygift", "anonsubmysterygift" -> {
				int count = Math.max(1, irc.intTag("msg-param-mass-gift-count"));
				yield new Alert(Type.GIFT_BOMB, user, "", count, gifter);
			}
			case "raid" -> {
				int viewers = irc.intTag("msg-param-viewerCount");
				String raider = irc.tag("msg-param-displayName");
				yield of(Type.RAID, raider.isEmpty() ? user : raider, "", viewers);
			}
			default -> null;
		};
	}

	private static String plan(String plan) {
		return switch (plan.toLowerCase(Locale.ROOT)) {
			case "prime" -> "Prime";
			case "2000" -> "Tier 2";
			case "3000" -> "Tier 3";
			default -> "Tier 1";
		};
	}
}
