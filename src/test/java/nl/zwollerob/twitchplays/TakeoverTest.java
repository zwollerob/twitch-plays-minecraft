package nl.zwollerob.twitchplays;

import nl.zwollerob.twitchplays.command.Takeover;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TakeoverTest {
	private static final long MIN = 60_000;
	private static final long MAX = 30 * MIN;

	private final List<String> events = new ArrayList<>();
	private final Takeover takeover = new Takeover(new Takeover.Listener() {
		@Override
		public void started(Takeover.Turn turn) {
			events.add("start " + turn.name + " " + turn.remainingMs() / MIN);
		}

		@Override
		public void queued(Takeover.Turn turn, int position) {
			events.add("queue " + turn.name + " #" + position);
		}

		@Override
		public void extended(Takeover.Turn turn, long addedMs) {
			events.add("extend " + turn.name + " +" + addedMs / MIN);
		}

		@Override
		public void ended(Takeover.Turn turn) {
			events.add("end " + turn.name);
		}
	});

	@Test
	void durationIsOneMinutePer100BitsFrom1000UpTo30Minutes() {
		assertEquals(0, Takeover.durationMs(999, 1000, 100, 30));
		assertEquals(10 * MIN, Takeover.durationMs(1000, 1000, 100, 30));
		assertEquals(15 * MIN, Takeover.durationMs(1500, 1000, 100, 30));
		assertEquals(20 * MIN, Takeover.durationMs(2000, 1000, 100, 30));
		assertEquals(30 * MIN, Takeover.durationMs(10_000, 1000, 100, 30));
	}

	@Test
	void secondCheererWaitsInLine() {
		takeover.add("1", "Anna", 10 * MIN, MAX);
		takeover.add("2", "Bram", 20 * MIN, MAX);
		assertTrue(takeover.controls("1"));
		assertFalse(takeover.controls("2"));
		assertEquals(1, takeover.waiting());

		takeover.tick(10 * MIN, true);
		assertTrue(takeover.controls("2"));
		assertEquals(20 * MIN, takeover.current().remainingMs());
		takeover.tick(20 * MIN, true);
		assertFalse(takeover.active());
		assertEquals(List.of("start Anna 10", "queue Bram #1", "end Anna", "start Bram 20", "end Bram"), events);
	}

	@Test
	void cheeringAgainExtendsUpToTheMaximum() {
		takeover.add("1", "Anna", 20 * MIN, MAX);
		takeover.add("1", "Anna", 20 * MIN, MAX);
		assertEquals(MAX, takeover.current().remainingMs());
		assertEquals(0, takeover.waiting());
		assertEquals(List.of("start Anna 20", "extend Anna +10"), events);
	}

	@Test
	void timeOnlyRunsWhilePlaying() {
		takeover.add("1", "Anna", 10 * MIN, MAX);
		takeover.tick(5 * MIN, false);
		assertEquals(10 * MIN, takeover.current().remainingMs());
		takeover.tick(MIN, true);
		assertEquals(9 * MIN, takeover.current().remainingMs());
	}

	@Test
	void stopAndRemoveGoToTheNextInLine() {
		takeover.add("1", "Anna", 10 * MIN, MAX);
		takeover.add("2", "Bram", 10 * MIN, MAX);
		takeover.add("3", "Cas", 10 * MIN, MAX);
		assertEquals("Anna", takeover.stop().name);
		assertTrue(takeover.controls("2"));
		assertTrue(takeover.remove("3"), "a timed out viewer leaves the queue");
		assertTrue(takeover.remove("2"), "a timed out viewer loses control");
		assertFalse(takeover.active());
		assertFalse(takeover.remove("4"));
		assertNull(takeover.stop());
	}

	@Test
	void restoresSavedTurnsWithoutAnnouncing() {
		takeover.restore(List.of(new Takeover.Turn("1", "Anna", 4 * MIN), new Takeover.Turn("2", "Bram", 0), new Takeover.Turn("3", "Cas", 10 * MIN)));
		assertTrue(takeover.controls("1"));
		assertEquals(1, takeover.waiting(), "a turn without time left is dropped");
		assertTrue(events.isEmpty());
	}
}
