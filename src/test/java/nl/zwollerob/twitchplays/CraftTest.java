package nl.zwollerob.twitchplays;

import nl.zwollerob.twitchplays.command.Action;
import nl.zwollerob.twitchplays.command.CommandParser;
import nl.zwollerob.twitchplays.command.ItemMatcher;
import nl.zwollerob.twitchplays.command.ParsedCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftTest {
	private final CommandParser parser = new CommandParser("!", 5, Map.of());

	@Test
	void parsesNameAndCount() {
		assertEquals(new ParsedCommand(Action.CRAFT, 1, "fakkel"), parser.parse("!craft fakkel").orElseThrow());
		assertEquals(new ParsedCommand(Action.CRAFT, 4, "fakkel"), parser.parse("!maak fakkel 4").orElseThrow());
		assertEquals(new ParsedCommand(Action.CRAFT, 4, "torch"), parser.parse("!craft 4 torch").orElseThrow());
		assertEquals(new ParsedCommand(Action.CRAFT, 1, "diamond pickaxe"), parser.parse("!craft Diamond Pickaxe").orElseThrow());
		assertEquals(new ParsedCommand(Action.SMELT, 8, "vlees"), parser.parse("!bak vlees 8").orElseThrow());
		assertEquals(new ParsedCommand(Action.SMELT, 1, "raw iron"), parser.parse("!smelt raw iron").orElseThrow());
		assertEquals(new ParsedCommand(Action.COLLECT, 0), parser.parse("!ophalen").orElseThrow());
	}

	@Test
	void countStaysWithinLimits() {
		assertEquals(16, parser.parse("!craft fakkel 100").orElseThrow().amount());
		assertEquals(16, parser.parse("!craft fakkel " + "9".repeat(400)).orElseThrow().amount());
		assertEquals(1, parser.parse("!craft fakkel 0").orElseThrow().amount());
		assertEquals(2, parser.parse("!craft fakkel 2,7").orElseThrow().amount());
		// A number alone is not an item name.
		assertEquals("4", parser.parse("!craft 4").orElseThrow().text());
		assertTrue(parser.parse("!craft").isEmpty(), "an item name is required");
		assertTrue(parser.parse("!craft " + "x".repeat(41)).isEmpty(), "very long text is not a command");
		assertTrue(parser.parse("!ophalen 3").isEmpty());
	}

	@Test
	void canonicalGroupsVotes() {
		assertEquals("craft fakkel", parser.parse("!maak fakkel 1").orElseThrow().canonical());
		assertEquals("craft fakkel 4", parser.parse("!craft 4 fakkel").orElseThrow().canonical());
		assertEquals("smelt vlees", parser.parse("!oven vlees").orElseThrow().canonical());
	}

	@Test
	void pluralsFindTheItem() {
		assertTrue(ItemMatcher.score("fakkels", "torch", "Fakkel") > 0);
		assertTrue(ItemMatcher.score("torches", "torch", "Torch") > 0);
		assertTrue(ItemMatcher.score("planken", "oak_planks", "Eikenhouten planken") > 0);
		assertTrue(ItemMatcher.score("stokken", "stick", "Stok") > 0);
		assertTrue(ItemMatcher.score("houten planken", "oak_planks", "Houten plank") > 0);
		// The exact name still wins from a plural guess.
		assertTrue(ItemMatcher.score("stick", "stick", "Stick") > ItemMatcher.score("sticks", "stick", "Stick"));
		assertEquals(0, ItemMatcher.score("bus", "bucket", "Emmer"), "short words are not cut down");
	}

	@Test
	void foodWords() {
		assertEquals(ItemMatcher.FoodWord.MEAT, ItemMatcher.foodWord("Vlees"));
		assertEquals(ItemMatcher.FoodWord.MEAT, ItemMatcher.foodWord("raw meat"));
		assertEquals(ItemMatcher.FoodWord.FISH, ItemMatcher.foodWord("vis"));
		assertEquals(ItemMatcher.FoodWord.ANY, ItemMatcher.foodWord("eten"));
		assertNull(ItemMatcher.foodWord("ijzer"));
		assertTrue(ItemMatcher.inFoodGroup(ItemMatcher.FoodWord.MEAT, "beef"));
		assertTrue(ItemMatcher.inFoodGroup(ItemMatcher.FoodWord.MEAT, "minecraft:porkchop"));
		assertFalse(ItemMatcher.inFoodGroup(ItemMatcher.FoodWord.MEAT, "cod"));
		assertTrue(ItemMatcher.inFoodGroup(ItemMatcher.FoodWord.FISH, "salmon"));
		assertFalse(ItemMatcher.inFoodGroup(ItemMatcher.FoodWord.FISH, "potato"));
	}
}
