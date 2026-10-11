package nl.zwollerob.twitchplays.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import nl.zwollerob.twitchplays.TwitchPlays;
import nl.zwollerob.twitchplays.command.Takeover;
import nl.zwollerob.twitchplays.command.VoteRound;
import nl.zwollerob.twitchplays.game.Controller;
import nl.zwollerob.twitchplays.twitch.IrcClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Top-left panel that shows viewers what chat is doing: mode, vote countdown and recent commands. */
public final class Overlay {
	private static final long FEED_VISIBLE_MS = 12_000;
	private static final int PADDING = 4;
	private static final int BACKGROUND = 0x90000000;
	private static final int TITLE = 0xFF9146FF;

	private record Line(String text, int color, String suffix) {
		Line(String text, int color) {
			this(text, color, "");
		}

		String full() {
			return suffix.isEmpty() ? text : text + "  " + suffix;
		}
	}

	private final TwitchPlays twitch;

	public Overlay(TwitchPlays twitch) {
		this.twitch = twitch;
	}

	private static String compassName(Direction direction) {
		return switch (direction) {
			case NORTH -> "noord";
			case EAST -> "oost";
			case SOUTH -> "zuid";
			case WEST -> "west";
			default -> direction.getName();
		};
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!twitch.config.showOverlay) {
			return;
		}
		Font font = mc.font;
		List<Line> lines = new ArrayList<>();

		String state;
		int stateColor;
		if (!twitch.config.enabled) {
			state = "UIT";
			stateColor = TwitchPlays.COLOR_ERROR;
		} else if (twitch.config.democracy()) {
			state = "DEMOCRATIE";
			stateColor = TwitchPlays.COLOR_VOTE;
		} else {
			state = "ANARCHIE";
			stateColor = 0xFFFF9F43;
		}
		lines.add(new Line("Twitch Plays  " + state, stateColor));
		Takeover.Turn takeover = twitch.takeover.current();
		if (takeover != null) {
			String paused = twitch.takeoverRunning(mc) ? "" : "  (pauze)";
			lines.add(new Line("Overname " + TwitchPlays.clock(takeover.remainingMs()) + paused, TwitchPlays.COLOR_TAKEOVER, takeover.name));
			if (twitch.takeover.waiting() > 0) {
				lines.add(new Line("Wachtrij: " + twitch.takeover.waiting(), TwitchPlays.COLOR_TAKEOVER));
			}
		}
		if (mc.player != null) {
			// So chat knows which way !north, !step and !bridge go.
			lines.add(new Line("Kijkt naar: " + compassName(mc.player.getDirection()), TwitchPlays.COLOR_INFO));
		}

		if (twitch.irc.state() != IrcClient.State.CONNECTED) {
			lines.add(new Line(twitch.status(), TwitchPlays.COLOR_INFO));
		} else if (twitch.config.enabled && !Controller.canControl(mc)) {
			lines.add(new Line("gepauzeerd (menu open)", TwitchPlays.COLOR_INFO));
		}

		if (twitch.config.enabled && twitch.config.democracy()) {
			VoteRound round = twitch.round();
			lines.add(new Line(String.format(Locale.ROOT, "Stemmen: %.1fs  (%d)", twitch.millisLeftInRound() / 1000.0, round.voters()), TwitchPlays.COLOR_VOTE));
			List<VoteRound.Tally> tally = round.tally();
			for (int i = 0; i < Math.min(3, tally.size()); i++) {
				VoteRound.Tally t = tally.get(i);
				lines.add(new Line("  " + twitch.parser().prefix() + t.command().canonical() + "  " + t.votes(), 0xFFDDDDDD));
			}
		}

		long now = System.currentTimeMillis();
		for (TwitchPlays.FeedEntry entry : twitch.feed()) {
			if (now - entry.time() < FEED_VISIBLE_MS) {
				lines.add(new Line(entry.what(), entry.color(), entry.who()));
			}
		}

		int width = 0;
		for (Line line : lines) {
			width = Math.max(width, font.width(line.full()));
		}
		int maxWidth = Math.max(160, graphics.guiWidth() / 3);
		width = Math.min(width, maxWidth);
		int lineHeight = font.lineHeight + 1;
		int x = PADDING;
		int y = PADDING;
		graphics.fill(x - 2, y - 2, x + width + 2, y + lines.size() * lineHeight + 1, BACKGROUND);
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			if (i == 0) {
				graphics.text(font, "Twitch Plays", x, y, TITLE, true);
				graphics.text(font, state, x + font.width("Twitch Plays  "), y, line.color(), true);
			} else {
				// Truncate from the end, so the command stays readable and a long name gets cut instead.
				String text = font.width(line.full()) > maxWidth ? font.plainSubstrByWidth(line.full(), maxWidth - 6) + "..." : line.full();
				String head = text.length() > line.text().length() ? line.text() : text;
				graphics.text(font, head, x, y, line.color(), true);
				if (text.length() > head.length()) {
					graphics.text(font, text.substring(head.length()), x + font.width(head), y, TwitchPlays.COLOR_INFO, true);
				}
			}
			y += lineHeight;
		}
	}
}
