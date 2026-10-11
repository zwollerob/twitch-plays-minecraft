package nl.zwollerob.twitchplays.game;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.component.FireworkExplosion;
import nl.zwollerob.twitchplays.Config;
import nl.zwollerob.twitchplays.twitch.Alert;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shows alerts one after another: a big title, a sound and a firework show.
 * The fireworks are only particles on this client, like the ones a rocket leaves behind,
 * so they cannot hurt anyone or change the world.
 */
public final class AlertPlayer {
	private static final int TITLE_IN = 10;
	private static final int TITLE_STAY = 60;
	private static final int TITLE_OUT = 20;
	/** Ticks between two alerts. */
	private static final int ALERT_TICKS = TITLE_IN + TITLE_STAY + TITLE_OUT + 10;
	private static final int ROCKET_FLIGHT_TICKS = 12;
	private static final int MAX_QUEUE = 25;

	private static final FireworkExplosion.Shape[] SHAPES = {
			FireworkExplosion.Shape.LARGE_BALL, FireworkExplosion.Shape.SMALL_BALL,
			FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.BURST};

	/** A rocket on its way up: a trail of sparks for a few ticks, then the explosion. */
	private static final class Rocket {
		final double x, y, z, topY;
		final IntList colors;
		int delay;
		int age;

		Rocket(double x, double y, double z, double topY, IntList colors, int delay) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.topY = topY;
			this.colors = colors;
			this.delay = delay;
		}
	}

	private final Deque<Alert> queue = new ArrayDeque<>();
	private final List<Rocket> rockets = new ArrayList<>();
	private int cooldown;

	public void add(Alert alert) {
		if (queue.size() < MAX_QUEUE) {
			queue.addLast(alert);
		}
	}

	public int queued() {
		return queue.size();
	}

	/** Nothing waiting and the last alert has finished. */
	public boolean idle() {
		return queue.isEmpty() && cooldown == 0;
	}

	public void tick(Minecraft mc, Config.Alerts settings) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			rockets.clear();
			return;
		}
		tickRockets(level);
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		// Wait with the next alert while a menu is open, so it isn't missed.
		if (queue.isEmpty() || mc.gui.screen() != null) {
			return;
		}
		show(mc, player, queue.removeFirst(), settings);
		cooldown = ALERT_TICKS;
	}

	private void show(Minecraft mc, LocalPlayer player, Alert alert, Config.Alerts settings) {
		Look look = Look.of(alert);
		if (settings.title) {
			mc.gui.hud.setTimes(TITLE_IN, TITLE_STAY, TITLE_OUT);
			mc.gui.hud.setSubtitle(Component.literal(look.subtitle).withStyle(Style.EMPTY.withColor(0xFFFFFF)));
			mc.gui.hud.setTitle(Component.literal(look.title).withStyle(Style.EMPTY.withColor(look.color).withBold(true)));
		}
		if (settings.sound) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f));
		}
		if (settings.fireworks) {
			launch(player, look, fireworkCount(alert));
		}
	}

	/** Bigger support, bigger show. */
	static int fireworkCount(Alert alert) {
		int amount = Math.max(1, alert.amount());
		int count = switch (alert.type()) {
			case FOLLOW -> 3;
			case SUB, GIFT -> 6;
			case RESUB -> 6 + amount / 3;
			case GIFT_BOMB -> 6 + amount * 2;
			case DONATION -> 5 + amount;
			case RAID -> 6 + amount / 5;
			case BITS -> 4 + amount / 100;
			case TAKEOVER -> 10 + amount;
		};
		return Math.min(count, 40);
	}

	private void launch(LocalPlayer player, Look look, int count) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < count; i++) {
			// In front of the player (within 70 degrees of where they look), so it is on stream,
			// and at least 7 blocks away so the flash does not fill the screen.
			double angle = Math.toRadians(player.getYRot()) + random.nextDouble(-1.2, 1.2);
			double distance = random.nextDouble(7, 14);
			double x = player.getX() - Math.sin(angle) * distance;
			double z = player.getZ() + Math.cos(angle) * distance;
			double y = player.getY();
			double topY = player.getEyeY() + random.nextDouble(3, 8);
			IntList colors = IntList.of(look.fireworkColors[random.nextInt(look.fireworkColors.length)],
					look.fireworkColors[random.nextInt(look.fireworkColors.length)]);
			// Spread the rockets over about 3 seconds.
			int delay = count == 1 ? 0 : i * Math.min(10, 60 / count) + random.nextInt(4);
			rockets.add(new Rocket(x, y, z, topY, colors, delay));
		}
	}

	private void tickRockets(ClientLevel level) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Iterator<Rocket> it = rockets.iterator();
		while (it.hasNext()) {
			Rocket rocket = it.next();
			if (rocket.delay > 0) {
				rocket.delay--;
				continue;
			}
			if (rocket.age == 0) {
				level.playLocalSound(rocket.x, rocket.y, rocket.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 2.0f, 1.0f, false);
			}
			double progress = (double) rocket.age / ROCKET_FLIGHT_TICKS;
			double y = rocket.y + (rocket.topY - rocket.y) * progress;
			level.addParticle(ParticleTypes.FIREWORK, rocket.x, y, rocket.z,
					random.nextGaussian() * 0.02, -0.1, random.nextGaussian() * 0.02);
			if (rocket.age >= ROCKET_FLIGHT_TICKS) {
				FireworkExplosion explosion = new FireworkExplosion(SHAPES[random.nextInt(SHAPES.length)],
						rocket.colors, IntList.of(0xFFFFFF), random.nextBoolean(), random.nextBoolean());
				level.createFireworks(rocket.x, rocket.topY, rocket.z, 0, 0, 0, List.of(explosion), true);
				it.remove();
			} else {
				rocket.age++;
			}
		}
	}

	/** Title, subtitle and colors for each kind of alert. */
	record Look(String title, String subtitle, int color, int[] fireworkColors) {
		private static final int PURPLE = 0x9146FF;

		static Look of(Alert alert) {
			String user = alert.user();
			return switch (alert.type()) {
				case SUB -> new Look("NIEUWE SUB!", user + " (" + alert.detail() + ")", PURPLE,
						new int[]{PURPLE, 0xBF94FF, 0xFFFFFF});
				case RESUB -> new Look("RESUB!", user + " - " + alert.detail(), PURPLE,
						new int[]{PURPLE, 0xBF94FF, 0xFFD166});
				case GIFT -> new Look("GIFT SUB!", user + " geeft " + alert.detail() + " een sub", 0xFF6EC7,
						new int[]{0xFF6EC7, PURPLE, 0xFFFFFF});
				case GIFT_BOMB -> new Look(alert.amount() + " GIFT SUBS!", "van " + user, 0xFF6EC7,
						new int[]{0xFF6EC7, PURPLE, 0xFFD166, 0x7FD4FF});
				case FOLLOW -> new Look("NIEUWE FOLLOWER!", user, 0x5CE65C,
						new int[]{0x5CE65C, 0x7FD4FF, 0xFFFFFF});
				case DONATION -> new Look("DONATIE!", user + " - " + alert.detail(), 0xFFD166,
						new int[]{0xFFD166, 0xFFA500, 0x5CE65C});
				case RAID -> new Look("RAID!", user + " met " + alert.amount() + " kijkers", 0xFF6B6B,
						new int[]{0xFF6B6B, 0xFFA500, 0xFFD166});
				case BITS -> new Look(alert.amount() + " BITS!", user, 0xC77DFF,
						new int[]{0xC77DFF, 0x7FD4FF, 0x5CE65C});
				case TAKEOVER -> new Look("OVERNAME!", user + " bestuurt alleen - " + alert.detail(), 0xFFD166,
						new int[]{0xFFD166, 0xFFA500, 0x9146FF, 0xFFFFFF});
			};
		}
	}
}
