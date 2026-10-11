package nl.zwollerob.twitchplays.command;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a chat message into a command. The whole message must be a command
 * ("!forward 3"), so normal chat that happens to contain a command word is ignored.
 */
public final class CommandParser {
	private final String prefix;
	private final double maxHoldSeconds;
	/** Longest item name chat may type after !grab; longer text is not a command. */
	static final int MAX_TEXT_LENGTH = 40;

	private final Map<String, Action> lookup = new HashMap<>();

	public CommandParser(String prefix, double maxHoldSeconds, Map<String, String> extraAliases) {
		this.prefix = prefix == null ? "" : prefix.strip();
		this.maxHoldSeconds = maxHoldSeconds;
		for (Action action : Action.values()) {
			for (String alias : action.aliases) {
				lookup.put(alias, action);
			}
		}
		if (extraAliases != null) {
			extraAliases.forEach((alias, id) -> {
				Action action = Action.byId(id);
				if (action != null) {
					lookup.put(normalize(alias), action);
				}
			});
		}
	}

	public String prefix() {
		return prefix;
	}

	/** Parses a chat message, which must start with the prefix. */
	public Optional<ParsedCommand> parse(String message) {
		if (message == null) {
			return Optional.empty();
		}
		String text = message.strip();
		if (!prefix.isEmpty()) {
			if (!text.startsWith(prefix)) {
				return Optional.empty();
			}
			text = text.substring(prefix.length());
		}
		return parseBare(text);
	}

	/** Parses a command with or without prefix, e.g. from a channel point reward or the config. */
	public Optional<ParsedCommand> parseBare(String text) {
		if (text == null) {
			return Optional.empty();
		}
		text = text.strip();
		if (!prefix.isEmpty() && text.startsWith(prefix)) {
			text = text.substring(prefix.length());
		}
		String normalized = normalize(text);
		if (normalized.isEmpty()) {
			return Optional.empty();
		}
		String[] tokens = normalized.split(" ");

		Action action = null;
		int used = 0;
		if (tokens.length >= 2) {
			action = lookup.get(tokens[0] + " " + tokens[1]);
			used = 2;
		}
		if (action == null) {
			action = lookup.get(tokens[0]);
			used = 1;
		}
		if (action == null) {
			// "!3" selects hotbar slot 3
			if (tokens.length == 1 && tokens[0].matches("[1-9]")) {
				return Optional.of(new ParsedCommand(Action.SLOT, Integer.parseInt(tokens[0])));
			}
			return Optional.empty();
		}

		int rest = tokens.length - used;
		if (action.kind == Action.Kind.TEXT) {
			String itemText = String.join(" ", Arrays.copyOfRange(tokens, used, tokens.length));
			if (itemText.isEmpty() || itemText.length() > MAX_TEXT_LENGTH) {
				return Optional.empty();
			}
			return Optional.of(new ParsedCommand(action, 0, itemText));
		}
		if (tokens.length > 3 || rest > 1) {
			return Optional.empty();
		}
		Double amount = null;
		if (rest == 1) {
			if (action.kind == Action.Kind.NONE) {
				return Optional.empty();
			}
			try {
				amount = Double.parseDouble(tokens[used].replace(',', '.'));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
			if (amount.isNaN() || amount.isInfinite()) {
				return Optional.empty();
			}
		}
		return build(action, amount);
	}

	private Optional<ParsedCommand> build(Action action, Double amount) {
		return switch (action.kind) {
			case NONE -> Optional.of(new ParsedCommand(action, 0));
			case TEXT -> Optional.empty(); // handled in parseBare, which has the text
			case SLOT -> {
				if (amount == null || amount != Math.rint(amount) || amount < 1 || amount > 9) {
					yield Optional.empty();
				}
				yield Optional.of(new ParsedCommand(action, amount));
			}
			case SECONDS -> {
				double max = Math.max(0.1, Math.min(action.maxAmount, maxHoldSeconds));
				double value = amount == null ? action.defaultAmount : amount;
				value = Math.round(Math.clamp(value, 0.1, max) * 10) / 10.0;
				yield Optional.of(new ParsedCommand(action, value));
			}
			case DEGREES -> {
				double value = amount == null ? action.defaultAmount : Math.round(amount);
				yield Optional.of(new ParsedCommand(action, Math.clamp(value, 1, action.maxAmount)));
			}
			case COUNT -> {
				double value = amount == null ? action.defaultAmount : Math.floor(amount);
				yield Optional.of(new ParsedCommand(action, Math.clamp(value, 1, action.maxAmount)));
			}
		};
	}

	private static String normalize(String text) {
		return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}
}
