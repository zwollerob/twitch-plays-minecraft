package nl.zwollerob.twitchplays;

import nl.zwollerob.twitchplays.command.Action;
import nl.zwollerob.twitchplays.command.CommandParser;
import nl.zwollerob.twitchplays.command.ItemMatcher;
import nl.zwollerob.twitchplays.command.ParsedCommand;
import nl.zwollerob.twitchplays.twitch.Alert;
import nl.zwollerob.twitchplays.twitch.ChatMessage;
import nl.zwollerob.twitchplays.twitch.IrcLine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything viewers type ends up in these parsers. Random and hostile input must never throw,
 * and must never become anything other than a known command with a bounded amount.
 */
class FuzzTest {
	private static final String ALPHABET = "!@#:;= \\\r\n\t\u0000\u0001§‮⁦abcdefwxyz0123456789-.,eE+NaInfity漢😀"
			+ "PRIVMSG USERNOTICE CLEARCHAT bits= display-name= ";
	private static final List<String> HOSTILE = List.of(
			"", " ", "!", "!!", "!" + "a".repeat(100_000), "!forward " + "9".repeat(400), "!forward 1e308", "!forward -1e308",
			"!forward NaN", "!forward Infinity", "!forward 0x10", "!slot 1.5", "!slot -0", "!pak " + "§k".repeat(30),
			"!pak ${jndi:ldap://evil/x}", "!pak ../../../../windows/system32", "!pak %00", "!look left 1e9",
			"!forward\u0000", "!‮forward", "!for​ward", "!FORWARD", "!forward 3 3", "!stap 2147483648",
			"@bits=1000000;mod=1 :evil!evil@evil PRIVMSG #x :!takeover stop",
			"@badges=broadcaster/1;bits=99999999999999999999 :a!a@a PRIVMSG #c :Cheer1",
			"@display-name=\\s\\:\\\\\\ :a!a@a PRIVMSG #c :x", "@", "@a", ":", ": PRIVMSG", "PRIVMSG", "@x=1 :a PRIVMSG",
			"@msg-id=resub;msg-param-cumulative-months=-5 :tmi.twitch.tv USERNOTICE #c",
			"@msg-id=submysterygift;msg-param-mass-gift-count=2147483647 :tmi.twitch.tv USERNOTICE #c",
			"@msg-id=raid;msg-param-viewerCount=abc :tmi.twitch.tv USERNOTICE #c");

	private final CommandParser parser = new CommandParser("!", 5, Map.of());

	@Test
	void hostileInputIsHandledSafely() {
		for (String text : HOSTILE) {
			assertDoesNotThrow(() -> check(text), text.length() > 80 ? text.substring(0, 80) : text);
		}
	}

	@Test
	void viewerTextIsCleanedForDisplay() {
		// Without the § the code letter is just a letter, so the formatting no longer works.
		assertEquals("kNaam", TwitchPlays.clean("§kNa\u0000am‮"));
		assertEquals("brood", TwitchPlays.clean("br​ood"));
		assertEquals("Gewone tekst met émojis 😀 en 漢字", TwitchPlays.clean("Gewone tekst met émojis 😀 en 漢字"));
		assertEquals("", TwitchPlays.clean(null));
	}

	@Test
	void randomInputIsHandledSafely() {
		Random random = new Random(20261011);
		for (int i = 0; i < 50_000; i++) {
			int length = random.nextInt(60);
			StringBuilder text = new StringBuilder(random.nextBoolean() ? "!" : "");
			for (int j = 0; j < length; j++) {
				text.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
			}
			String input = text.toString();
			assertDoesNotThrow(() -> check(input), input);
		}
	}

	@Test
	void prefixedRandomWordsOnlyGiveKnownCommands() {
		Random random = new Random(7);
		for (int i = 0; i < 20_000; i++) {
			Action action = Action.values()[random.nextInt(Action.values().length)];
			String alias = action.aliases.get(random.nextInt(action.aliases.size()));
			String argument = switch (random.nextInt(4)) {
				case 0 -> "";
				case 1 -> " " + (random.nextDouble() * 1e12 - 5e11);
				case 2 -> " " + random.nextInt();
				default -> " x" + random.nextInt(9);
			};
			check("!" + alias + argument);
		}
	}

	private void check(String text) {
		Optional<ParsedCommand> parsed = parser.parse(text);
		parsed.ifPresent(FuzzTest::assertBounded);
		parser.parseBare(text).ifPresent(FuzzTest::assertBounded);
		ItemMatcher.score(text, text, text);
		IrcLine line = IrcLine.parse(text);
		if (line != null) {
			ChatMessage.from(line);
			Alert alert = Alert.fromUserNotice(line);
			if (alert != null) {
				assertTrue(alert.amount() >= 0 || alert.type() == Alert.Type.RAID, "negative amount: " + alert);
			}
		}
		ChatMessage.parse(text);
		assertEquals(TwitchPlays.clean(TwitchPlays.clean(text)), TwitchPlays.clean(text));
		assertTrue(TwitchPlays.clean(text).chars().noneMatch(c -> c < 0x20 || c == 0x7F || c == '§' || (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069)));
	}

	/** A parsed command can only ever be one of the known actions, with an amount inside its limits. */
	private static void assertBounded(ParsedCommand command) {
		Action action = command.action();
		double amount = command.amount();
		assertTrue(!Double.isNaN(amount) && !Double.isInfinite(amount), command.toString());
		switch (action.kind) {
			case NONE, TEXT -> assertEquals(0, amount);
			case SLOT -> assertTrue(amount >= 1 && amount <= 9 && amount == Math.rint(amount), command.toString());
			case SECONDS -> assertTrue(amount >= 0.1 && amount <= action.maxAmount, command.toString());
			case DEGREES, COUNT -> assertTrue(amount >= 1 && amount <= action.maxAmount, command.toString());
		}
		if (action.kind == Action.Kind.TEXT) {
			assertTrue(!command.text().isEmpty() && command.text().length() <= 40, command.toString());
		}
	}
}
