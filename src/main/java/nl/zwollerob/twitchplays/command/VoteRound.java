package nl.zwollerob.twitchplays.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One democracy round: every chatter has one vote, the last one they typed counts. */
public final class VoteRound {
	public record Tally(ParsedCommand command, int votes) {
	}

	private final Map<String, ParsedCommand> votes = new LinkedHashMap<>();

	public void vote(String user, ParsedCommand command) {
		votes.remove(user);
		votes.put(user, command);
	}

	public int voters() {
		return votes.size();
	}

	/** Commands by number of votes; ties go to the command that was voted first. */
	public List<Tally> tally() {
		Map<String, ParsedCommand> commands = new LinkedHashMap<>();
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (ParsedCommand command : votes.values()) {
			String key = command.canonical();
			commands.putIfAbsent(key, command);
			counts.merge(key, 1, Integer::sum);
		}
		List<Tally> result = new ArrayList<>();
		counts.forEach((key, count) -> result.add(new Tally(commands.get(key), count)));
		result.sort(Comparator.comparingInt(Tally::votes).reversed());
		return result;
	}

	public Optional<Tally> winner() {
		List<Tally> tally = tally();
		return tally.isEmpty() ? Optional.empty() : Optional.of(tally.getFirst());
	}
}
