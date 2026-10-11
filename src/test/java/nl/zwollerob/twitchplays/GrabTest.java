package nl.zwollerob.twitchplays;

import nl.zwollerob.twitchplays.command.Action;
import nl.zwollerob.twitchplays.command.CommandParser;
import nl.zwollerob.twitchplays.command.ItemMatcher;
import nl.zwollerob.twitchplays.command.ParsedCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrabTest {
	private final CommandParser parser = new CommandParser("!", 5, Map.of());

	@Test
	void parsesItemNames() {
		assertEquals(new ParsedCommand(Action.GRAB, 0, "diamond pickaxe"), parser.parse("!pak Diamond  Pickaxe").orElseThrow());
		assertEquals(new ParsedCommand(Action.GRAB, 0, "brood"), parser.parse("!grab brood").orElseThrow());
		assertEquals("grab diamanten houweel", parser.parse("!neem diamanten houweel").orElseThrow().canonical());
		assertTrue(parser.parse("!pak").isEmpty(), "an item name is required");
		assertTrue(parser.parse("!pak " + "x".repeat(41)).isEmpty(), "very long text is not a command");
		assertEquals(new ParsedCommand(Action.INVENTORY, 0), parser.parse("!inv").orElseThrow());
		assertTrue(parser.parse("!inv 3").isEmpty());
	}

	@Test
	void matchesByIdAndDisplayName() {
		// English id, Dutch name in game
		assertEquals(100, ItemMatcher.score("diamond pickaxe", "diamond_pickaxe", "Diamanten houweel"));
		assertEquals(100, ItemMatcher.score("diamanten houweel", "diamond_pickaxe", "Diamanten houweel"));
		assertTrue(ItemMatcher.score("houweel", "diamond_pickaxe", "Diamanten houweel") > 0);
		assertTrue(ItemMatcher.score("pickaxe", "diamond_pickaxe", "Diamanten houweel") > 0);
		assertTrue(ItemMatcher.score("pickaxe diamond", "diamond_pickaxe", "Diamond Pickaxe") > 0);
		assertEquals(0, ItemMatcher.score("zwaard", "diamond_pickaxe", "Diamanten houweel"));
		assertEquals(0, ItemMatcher.score("", "bread", "Brood"));
	}

	@Test
	void betterMatchesScoreHigher() {
		// "brood" should prefer bread over something that merely contains the word
		int bread = ItemMatcher.score("brood", "bread", "Brood");
		int other = ItemMatcher.score("brood", "spider_eye", "Broodspin");
		assertTrue(bread > other);
		// "stone" prefers stone over cobblestone
		assertTrue(ItemMatcher.score("stone", "stone", "Stone") > ItemMatcher.score("stone", "cobblestone", "Cobblestone"));
		// "planks" prefers a name ending in the word over one only containing it
		assertTrue(ItemMatcher.score("planks", "oak_planks", "Oak Planks") > 0);
	}

	@Test
	void ignoresAccentsAndCase() {
		assertEquals(100, ItemMatcher.score("cafe", "x", "Café"));
		assertEquals(100, ItemMatcher.score("OAK PLANKS", "minecraft:oak_planks", ""));
	}
}
