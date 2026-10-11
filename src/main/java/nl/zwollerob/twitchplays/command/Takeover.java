package nl.zwollerob.twitchplays.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Exclusive control bought with one big cheer: while someone holds control, only their
 * commands count. Others who cheer enough wait in a queue; the holder cheering again extends.
 * Time only runs while the game is being played (see {@link #tick}).
 */
public final class Takeover {
	/** One viewer's turn. */
	public static final class Turn {
		public final String userId;
		public final String name;
		long remainingMs;

		public Turn(String userId, String name, long remainingMs) {
			this.userId = userId;
			this.name = name;
			this.remainingMs = remainingMs;
		}

		public long remainingMs() {
			return remainingMs;
		}
	}

	/** What happened, so the caller can show alerts and send chat messages. */
	public interface Listener {
		void started(Turn turn);

		void queued(Turn turn, int position);

		void extended(Turn turn, long addedMs);

		void ended(Turn turn);
	}

	private final Listener listener;
	private final Deque<Turn> queue = new ArrayDeque<>();
	private Turn current;

	public Takeover(Listener listener) {
		this.listener = listener;
	}

	/** Minutes of control for a cheer: one minute per {@code bitsPerMinute}, 0 below the minimum, capped. */
	public static long durationMs(int bits, int minBits, int bitsPerMinute, int maxMinutes) {
		if (bits < minBits || bitsPerMinute <= 0) {
			return 0;
		}
		long ms = (long) bits * 60_000L / bitsPerMinute;
		return Math.min(ms, maxMinutes * 60_000L);
	}

	/**
	 * Adds a cheer. A viewer who already has a turn (current or queued) gets the time added,
	 * but never more than {@code maxMs} in total, so nobody can hold control forever.
	 */
	public void add(String userId, String name, long ms, long maxMs) {
		if (ms <= 0) {
			return;
		}
		Turn existing = find(userId);
		if (existing != null) {
			long before = existing.remainingMs;
			existing.remainingMs = Math.min(maxMs, existing.remainingMs + ms);
			listener.extended(existing, existing.remainingMs - before);
			return;
		}
		Turn turn = new Turn(userId, name, Math.min(ms, maxMs));
		if (current == null) {
			current = turn;
			listener.started(turn);
		} else {
			queue.addLast(turn);
			listener.queued(turn, queue.size());
		}
	}

	/** Lets time pass; only when {@code running}, so paused games and menus don't eat paid time. */
	public void tick(long elapsedMs, boolean running) {
		if (current == null || !running || elapsedMs <= 0) {
			return;
		}
		current.remainingMs -= elapsedMs;
		if (current.remainingMs <= 0) {
			current.remainingMs = 0;
			next();
		}
	}

	/** Ends the current turn early (a moderator's !takeover stop); the next in line starts. */
	public Turn stop() {
		Turn stopped = current;
		if (stopped != null) {
			next();
		}
		return stopped;
	}

	/** Removes a viewer completely, e.g. after a timeout or ban. Returns whether they had a turn. */
	public boolean remove(String userId) {
		if (current != null && current.userId.equals(userId)) {
			next();
			return true;
		}
		return queue.removeIf(turn -> turn.userId.equals(userId));
	}

	private void next() {
		Turn ended = current;
		current = queue.pollFirst();
		if (ended != null) {
			listener.ended(ended);
		}
		if (current != null) {
			listener.started(current);
		}
	}

	private Turn find(String userId) {
		if (current != null && current.userId.equals(userId)) {
			return current;
		}
		for (Turn turn : queue) {
			if (turn.userId.equals(userId)) {
				return turn;
			}
		}
		return null;
	}

	public boolean active() {
		return current != null;
	}

	/** Whether this viewer has control right now. */
	public boolean controls(String userId) {
		return current != null && current.userId.equals(userId);
	}

	public Turn current() {
		return current;
	}

	public int waiting() {
		return queue.size();
	}

	/** Current turn first, then the queue; for saving across restarts. */
	public List<Turn> turns() {
		List<Turn> all = new ArrayList<>();
		if (current != null) {
			all.add(current);
		}
		all.addAll(queue);
		return all;
	}

	/** Restores saved turns without announcing them again. */
	public void restore(List<Turn> turns) {
		queue.clear();
		current = null;
		for (Turn turn : turns) {
			if (turn.remainingMs <= 0) {
				continue;
			}
			if (current == null) {
				current = turn;
			} else {
				queue.addLast(turn);
			}
		}
	}
}
