package nl.zwollerob.twitchplays;

import nl.zwollerob.twitchplays.command.Action;
import nl.zwollerob.twitchplays.command.CommandParser;
import nl.zwollerob.twitchplays.command.ParsedCommand;
import nl.zwollerob.twitchplays.command.VoteRound;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandParserTest {
	private final CommandParser parser = new CommandParser("!", 5, Map.of("loop", "forward"));

	private ParsedCommand parse(String text) {
		return parser.parse(text).orElseThrow(() -> new AssertionError("not parsed: " + text));
	}

	@Test
	void parsesCommandsWithDefaultsAndAmounts() {
		assertEquals(new ParsedCommand(Action.FORWARD, 1), parse("!forward"));
		assertEquals(new ParsedCommand(Action.FORWARD, 3), parse("!w 3"));
		assertEquals(new ParsedCommand(Action.FORWARD, 2.5), parse("!vooruit 2,5"));
		assertEquals(new ParsedCommand(Action.LOOK_LEFT, 90), parse("!look left 90"));
		assertEquals(new ParsedCommand(Action.LOOK_UP, 30), parse("  !Kijk   Omhoog "));
		assertEquals(new ParsedCommand(Action.SLOT, 4), parse("!4"));
		assertEquals(new ParsedCommand(Action.SLOT, 7), parse("!slot 7"));
		assertEquals(new ParsedCommand(Action.FORWARD, 1), parse("!loop"));
	}

	@Test
	void clampsAmounts() {
		assertEquals(5, parse("!forward 999").amount());
		assertEquals(0.1, parse("!jump 0").amount());
		assertEquals(180, parse("!lookleft 720").amount());
		assertEquals(10, parse("!attack 50").amount());
		assertEquals(1, parse("!attack -3").amount());
	}

	@Test
	void ignoresNormalChat() {
		for (String text : new String[]{"forward", "!", "!forward please", "!forward 1 2", "!slot", "!slot 12", "!turn 3",
				"!fly", "hallo !forward", "!0", "!forward NaN", "!forward Infinity"}) {
			assertEquals(Optional.empty(), parser.parse(text), text);
		}
	}

	@Test
	void parseBareAcceptsTextWithOrWithoutPrefix() {
		assertEquals(new ParsedCommand(Action.SHUFFLE, 0), parser.parseBare("shuffle").orElseThrow());
		assertEquals(new ParsedCommand(Action.SHUFFLE, 0), parser.parseBare("!shuffle").orElseThrow());
	}

	@Test
	void emptyPrefixUsesBareWords() {
		CommandParser bare = new CommandParser("", 5, Map.of());
		assertEquals(new ParsedCommand(Action.JUMP, 0.3), bare.parse("jump").orElseThrow());
		assertTrue(bare.parse("i will jump now").isEmpty());
	}

	@Test
	void canonicalFormGroupsVotes() {
		assertEquals("forward", parse("!w").canonical());
		assertEquals("forward", parse("!forward 1").canonical());
		assertEquals("forward 3", parse("!forward 3").canonical());
		assertEquals("slot 2", parse("!2").canonical());
		assertEquals("jump 1.5", parse("!jump 1.5").canonical());
	}

	@Test
	void voteRoundCountsOneVotePerUser() {
		VoteRound round = new VoteRound();
		round.vote("a", parse("!forward"));
		round.vote("b", parse("!w"));
		round.vote("c", parse("!jump"));
		round.vote("d", parse("!jump"));
		round.vote("d", parse("!forward")); // d changes their mind
		round.vote("e", parse("!jump"));

		assertEquals(5, round.voters());
		VoteRound.Tally winner = round.winner().orElseThrow();
		assertEquals(Action.FORWARD, winner.command().action());
		assertEquals(3, winner.votes());
	}

	@Test
	void voteTieGoesToFirstVotedCommand() {
		VoteRound round = new VoteRound();
		round.vote("a", parse("!jump"));
		round.vote("b", parse("!back"));
		assertEquals(Action.JUMP, round.winner().orElseThrow().command().action());
		assertTrue(new VoteRound().winner().isEmpty());
	}
}
