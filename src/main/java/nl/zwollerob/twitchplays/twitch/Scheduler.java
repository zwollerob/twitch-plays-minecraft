package nl.zwollerob.twitchplays.twitch;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** One daemon thread for reconnects, token polling and HTTP calls, so Minecraft never waits on Twitch. */
final class Scheduler {
	static final ScheduledExecutorService INSTANCE = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "TwitchPlays");
		thread.setDaemon(true);
		return thread;
	});

	private Scheduler() {
	}
}
