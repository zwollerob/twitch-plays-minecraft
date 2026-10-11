package nl.zwollerob.twitchplays.command;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Scores how well chat's text ("pickaxe", "diamanten houweel", "brood") matches an item,
 * by its internal id ("diamond_pickaxe") and by the name shown in game ("Diamanten houweel").
 */
public final class ItemMatcher {
	private ItemMatcher() {
	}

	/** 0 means no match; higher is better. */
	public static int score(String query, String id, String displayName) {
		String q = normalize(query);
		if (q.isEmpty()) {
			return 0;
		}
		return Math.max(scoreName(q, normalize(id)), scoreName(q, normalize(displayName)));
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
