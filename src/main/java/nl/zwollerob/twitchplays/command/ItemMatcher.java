package nl.zwollerob.twitchplays.command;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scores how well chat's text ("pickaxe", "diamanten houweel", "brood") matches an item,
 * by its internal id ("diamond_pickaxe") and by the name shown in game ("Diamanten houweel").
 */
public final class ItemMatcher {
	private ItemMatcher() {
	}

	/** Words chat uses for a whole group of food: "!bak vlees" cooks whatever raw meat there is. */
	public enum FoodWord { MEAT, FISH, ANY }

	private static final Set<String> MEAT = Set.of("beef", "porkchop", "chicken", "mutton", "rabbit");
	private static final Set<String> FISH = Set.of("cod", "salmon");

	/** 0 means no match; higher is better. */
	public static int score(String query, String id, String displayName) {
		String q = normalize(query);
		if (q.isEmpty()) {
			return 0;
		}
		String normalizedId = normalize(id);
		String normalizedName = normalize(displayName);
		int score = Math.max(scoreName(q, normalizedId), scoreName(q, normalizedName));
		if (score > 0) {
			return score;
		}
		// "fakkels", "planken", "torches": try the singular, a little lower than an exact name.
		for (String singular : singulars(q)) {
			score = Math.max(score, Math.max(scoreName(singular, normalizedId), scoreName(singular, normalizedName)) - 5);
		}
		return Math.max(score, 0);
	}

	/** Dutch and English plurals of the last word: -s, -es, -en, and -en after a doubled consonant ("stokken"). */
	static List<String> singulars(String query) {
		int space = query.lastIndexOf(' ');
		String start = query.substring(0, space + 1);
		String word = query.substring(space + 1);
		List<String> out = new ArrayList<>();
		for (String ending : new String[]{"es", "s", "en"}) {
			if (word.length() - ending.length() >= 3 && word.endsWith(ending)) {
				String stem = word.substring(0, word.length() - ending.length());
				out.add(start + stem);
				int n = stem.length();
				if (ending.equals("en") && stem.charAt(n - 1) == stem.charAt(n - 2)) {
					out.add(start + stem.substring(0, n - 1));
				}
			}
		}
		return out;
	}

	/** Whether chat named a food group instead of an item, such as "vlees" or "fish". */
	public static FoodWord foodWord(String query) {
		return switch (normalize(query)) {
			case "vlees", "rauw vlees", "meat", "raw meat" -> FoodWord.MEAT;
			case "vis", "rauwe vis", "fish", "raw fish" -> FoodWord.FISH;
			case "eten", "food" -> FoodWord.ANY;
			default -> null;
		};
	}

	/** Whether an item (by id, e.g. "minecraft:beef") belongs to a food group; for ANY the caller checks that it is food. */
	public static boolean inFoodGroup(FoodWord word, String id) {
		String path = normalize(id);
		return switch (word) {
			case MEAT -> MEAT.contains(path);
			case FISH -> FISH.contains(path);
			case ANY -> true;
		};
	}

	private static int scoreName(String query, String name) {
		if (name.isEmpty()) {
			return 0;
		}
		if (name.equals(query)) {
			return 100;
		}
		if (name.startsWith(query + " ") || name.endsWith(" " + query)) {
			return 80;
		}
		if (name.startsWith(query)) {
			return 70;
		}
		if (name.contains(query)) {
			return 60;
		}
		// "pickaxe diamond" or "diamant houweel": every word occurs somewhere.
		String[] words = query.split(" ");
		if (words.length > 1) {
			for (String word : words) {
				if (!name.contains(word)) {
					return 0;
				}
			}
			return 40;
		}
		return 0;
	}

	/** Lower case, no accents, "minecraft:" and underscores removed: "minecraft:Oak_Planks" -> "oak planks". */
	static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return plain.toLowerCase(Locale.ROOT)
				.replaceFirst("^minecraft:", "")
				.replaceAll("[_\\-:]", " ")
				.replaceAll("\\s+", " ")
				.strip();
	}
}
