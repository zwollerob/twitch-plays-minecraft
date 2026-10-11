package nl.zwollerob.twitchplays.command;

import java.util.Locale;

/** A command with its amount, and for {@link Action.Kind#TEXT} and {@link Action.Kind#ITEM_COUNT} commands its text (lower case). */
public record ParsedCommand(Action action, double amount, String text) {
	public ParsedCommand(Action action, double amount) {
		this(action, amount, "");
	}

	/** The form chat would type, used to group democracy votes ("forward", "forward 3", "grab bread"). */
	public String canonical() {
		if (action.kind == Action.Kind.TEXT) {
			return action.id() + " " + text;
		}
		if (action.kind == Action.Kind.ITEM_COUNT) {
			return action.id() + " " + text + (amount == action.defaultAmount ? "" : " " + formatAmount());
		}
		if (action.kind == Action.Kind.NONE || action.kind != Action.Kind.SLOT && amount == action.defaultAmount) {
			return action.id();
		}
		return action.id() + " " + formatAmount();
	}

	public String formatAmount() {
		if (amount == Math.rint(amount)) {
			return Long.toString((long) amount);
		}
		return String.format(Locale.ROOT, "%.1f", amount);
	}

	public int ticks() {
		return (int) Math.round(amount * 20);
	}

	public int count() {
		return (int) amount;
	}
}
