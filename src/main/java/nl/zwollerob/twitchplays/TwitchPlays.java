package nl.zwollerob.twitchplays;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import nl.zwollerob.twitchplays.command.Action;
import nl.zwollerob.twitchplays.command.CommandParser;
import nl.zwollerob.twitchplays.command.ParsedCommand;
import nl.zwollerob.twitchplays.command.Takeover;
import nl.zwollerob.twitchplays.command.VoteRound;
import nl.zwollerob.twitchplays.game.AlertPlayer;
import nl.zwollerob.twitchplays.game.Controller;
import nl.zwollerob.twitchplays.twitch.Alert;
import nl.zwollerob.twitchplays.twitch.ChatMessage;
import nl.zwollerob.twitchplays.twitch.ChatSender;
import nl.zwollerob.twitchplays.twitch.EventSubClient;
import nl.zwollerob.twitchplays.twitch.IrcClient;
import nl.zwollerob.twitchplays.twitch.StreamElementsClient;
import nl.zwollerob.twitchplays.twitch.TipeeeStreamClient;
import nl.zwollerob.twitchplays.twitch.TwitchAuth;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Decides what happens with each chat message, cheer and redemption. Everything here runs on the client thread. */
public final class TwitchPlays {
	public static final int COLOR_CHAT = 0xFFFFFFFF;
	public static final int COLOR_VOTE = 0xFF7FD4FF;
	public static final int COLOR_BITS = 0xFFC77DFF;
	public static final int COLOR_POINTS = 0xFFFFD166;
	public static final int COLOR_ERROR = 0xFFFF6B6B;
	public static final int COLOR_INFO = 0xFFAAAAAA;
	public static final int COLOR_ALERT = 0xFF5CE65C;
	public static final int COLOR_TAKEOVER = 0xFFFFB347;

	/** One line in the overlay: what happened, in color, followed by who did it, in grey. */
	public record FeedEntry(String what, String who, int color, long time) {
		public String text() {
			return who.isEmpty() ? what : what + "  " + who;
		}
	}

	private static final int FEED_SIZE = 6;

	public Config config;
	private final Path configPath;
	public final Controller controller = new Controller();
	public final IrcClient irc;
	public final TwitchAuth auth;
	public final EventSubClient eventSub;
	public final StreamElementsClient streamElements;
	public final TipeeeStreamClient tipeeeStream;
	public final AlertPlayer alerts = new AlertPlayer();
	public final ChatSender chatSender;
	public final Takeover takeover = new Takeover(new TakeoverEvents());
	private final Path takeoverPath;
	private long lastTakeoverTick;
	private long lastTakeoverSave;
	private final Path streamElementsTokenPath;
	private final Path tipeeeStreamKeyPath;
	/** Gift bombs arrive as one "N gifts" notice followed by N single gifts; those singles are folded into the first. */
	private final Map<String, Integer> pendingGifts = new HashMap<>();
	private final Map<String, Long> pendingGiftsSince = new HashMap<>();

	private CommandParser parser;
	private VoteRound round = new VoteRound();
	private long roundEndsAt;
	private final Map<String, Long> lastCommandAt = new HashMap<>();
	private final Deque<FeedEntry> feed = new ArrayDeque<>();
	private String status = "Niet verbonden";
	private boolean greeted;

	public TwitchPlays() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		configPath = configDir.resolve("twitchplays.json");
		config = Config.load(configPath);
		auth = new TwitchAuth(configDir.resolve("twitchplays-login.json"));
		streamElementsTokenPath = configDir.resolve("twitchplays-streamelements.txt");
		tipeeeStreamKeyPath = configDir.resolve("twitchplays-tipeeestream.txt");
		takeoverPath = configDir.resolve("twitchplays-takeover.json");
		Minecraft mc = Minecraft.getInstance();
		irc = new IrcClient(
				message -> mc.execute(() -> onChat(message)),
				alert -> mc.execute(() -> onAlert(alert)),
				userId -> mc.execute(() -> onUserRemoved(userId)),
				text -> mc.execute(() -> status(text)));
		chatSender = new ChatSender(auth, () -> config.clientId, text -> mc.execute(() -> status(text)));
		streamElements = new StreamElementsClient(
				alert -> mc.execute(() -> onAlert(alert)),
				text -> mc.execute(() -> status(text)));
		tipeeeStream = new TipeeeStreamClient(
				alert -> mc.execute(() -> onAlert(alert)),
				text -> mc.execute(() -> status(text)));
		eventSub = new EventSubClient(auth,
				redemption -> mc.execute(() -> onRedemption(redemption)),
				bits -> mc.execute(() -> onBitsUsed(bits)),
				text -> mc.execute(() -> status(text)));
		controller.workshop.onProblem = text -> feed(parser.prefix() + text, "", COLOR_ERROR);
		reloadParser();
		loadTakeover();
	}

	public void start() {
		if (!config.channel.isBlank()) {
			irc.connect(config.channel);
			status = "Verbinden met #" + config.channel + "...";
		} else {
			status = "Geen kanaal ingesteld";
		}
		if (auth.token() != null && !config.clientId.isBlank()) {
			eventSub.start(config.clientId);
		}
		String seToken = streamElementsToken();
		if (!seToken.isEmpty()) {
			streamElements.start(seToken);
		}
		String tipeeeKey = tipeeeStreamKey();
		if (!tipeeeKey.isEmpty()) {
			tipeeeStream.start(tipeeeKey);
		}
	}

	public String streamElementsToken() {
		return SecretStore.read(streamElementsTokenPath);
	}

	/** Saves the token and connects; an empty token disconnects and forgets it. */
	public void setStreamElementsToken(String token) throws IOException {
		if (token.isEmpty()) {
			streamElements.stop();
			Files.deleteIfExists(streamElementsTokenPath);
			return;
		}
		SecretStore.write(streamElementsTokenPath, token);
		streamElements.start(token);
	}

	public String tipeeeStreamKey() {
		return SecretStore.read(tipeeeStreamKeyPath);
	}

	/** Saves the TipeeeStream API key and connects; an empty key disconnects and forgets it. */
	public void setTipeeeStreamKey(String key) throws IOException {
		if (key.isEmpty()) {
			tipeeeStream.stop();
			Files.deleteIfExists(tipeeeStreamKeyPath);
			return;
		}
		SecretStore.write(tipeeeStreamKeyPath, key);
		tipeeeStream.start(key);
	}

	public void reloadParser() {
		parser = new CommandParser(config.prefix, config.maxHoldSeconds, config.aliases);
		controller.workshop.paidIngredients = config.paidIngredients;
		controller.workshop.paidHint = "duur: " + config.paidIngredientBits + " bits of punten";
	}

	public void reloadConfig() {
		String oldChannel = config.channel;
		config = Config.load(configPath);
		reloadParser();
		if (!config.channel.equals(oldChannel)) {
			setChannel(config.channel);
		}
	}

	public CommandParser parser() {
		return parser;
	}

	// ---- incoming events ----

	public void onChat(ChatMessage message) {
		// A takeover cheer and alerts also count while chat control is switched off; the takeover clock waits.
		boolean tookOver = message.bits() > 0 && offerTakeover(message.userId(), message.displayName(), message.bits());
		if (!tookOver && message.bits() >= config.alerts.bitsMinimum) {
			onAlert(Alert.of(Alert.Type.BITS, message.displayName(), "", message.bits()));
		}
		String text = message.text().strip();
		// Moderators can switch mode and stop a takeover also while the streamer has control off (F8).
		if (message.moderator() && handleModCommand(text)) {
			return;
		}
		if (!config.enabled) {
			return;
		}

		if (message.bits() > 0) {
			Optional<ParsedCommand> cheered = parseCheer(text);
			if (cheered.isPresent() && isAllowed(cheered.get())) {
				ParsedCommand command = cheered.get();
				int cost = config.bitsCost.getOrDefault(command.action().id(), config.bitsDefaultCost);
				if (message.bits() >= cost) {
					run(command, message.displayName() + " [" + message.bits() + " bits]", COLOR_BITS, message.bits() >= config.paidIngredientBits);
					return;
				}
			}
		}

		Optional<ParsedCommand> parsed = parser.parse(text);
		if (takeover.active()) {
			// Only the viewer who took over: no voting, no cooldown, paid commands included.
			if (takeover.controls(message.userId()) && parsed.isPresent() && isAllowed(parsed.get())) {
				run(parsed.get(), message.displayName() + " [overname]", COLOR_TAKEOVER, true);
			}
			return;
		}
		if (parsed.isEmpty() || !isAllowed(parsed.get()) || isPaidOnly(parsed.get())) {
			return;
		}
		ParsedCommand command = parsed.get();
		if (config.democracy()) {
			// A paid recipe can't win a free vote.
			if (!controller.workshop.needsPayment(Minecraft.getInstance(), command)) {
				round.vote(message.userId(), command);
			}
			return;
		}
		long now = System.currentTimeMillis();
		Long last = lastCommandAt.get(message.userId());
		if (last != null && now - last < config.userCooldownMs) {
			return;
		}
		lastCommandAt.put(message.userId(), now);
		run(command, message.displayName(), COLOR_CHAT, false);
	}

	public void onRedemption(EventSubClient.Redemption redemption) {
		if (!config.enabled) {
			return;
		}
		String mapped = null;
		for (Map.Entry<String, String> entry : config.rewards.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(redemption.rewardTitle().strip())) {
				mapped = entry.getValue();
				break;
			}
		}
		if (mapped == null) {
			return;
		}
		String commandText = mapped.strip().equals("*") ? redemption.userInput() : mapped;
		Optional<ParsedCommand> parsed = parser.parseBare(commandText);
		if (parsed.isEmpty() || !isAllowed(parsed.get())) {
			feed("onbekend: " + commandText, redemption.userName(), COLOR_ERROR);
			return;
		}
		run(parsed.get(), redemption.userName() + " [punten]", COLOR_POINTS, true);
	}

	/** Bits from EventSub. Cheers also arrive in chat and are handled there, so only Power-ups count here. */
	public void onBitsUsed(EventSubClient.BitsUse bits) {
		if (bits.type().equals("cheer")) {
			return;
		}
		if (!offerTakeover(bits.userId(), bits.userName(), bits.bits()) && bits.bits() >= config.alerts.bitsMinimum) {
			onAlert(Alert.of(Alert.Type.BITS, bits.userName(), "", bits.bits()));
		}
	}

	/** Gives control (or a place in line) for a big enough single cheer. Returns whether it did. */
	public boolean offerTakeover(String userId, String name, int bits) {
		Config.TakeoverSettings settings = config.takeover;
		if (!settings.enabled) {
			return false;
		}
		long ms = Takeover.durationMs(bits, settings.minBits, settings.bitsPerMinute, settings.maxMinutes);
		if (ms <= 0) {
			return false;
		}
		takeover.add(userId, clean(name), ms, settings.maxMinutes * 60_000L);
		return true;
	}

	/** A timeout or ban in chat also ends that viewer's takeover. */
	public void onUserRemoved(String userId) {
		Takeover.Turn current = takeover.current();
		String name = current != null && current.userId.equals(userId) ? current.name : "";
		if (takeover.remove(userId)) {
			feed("overname gestopt (timeout/ban)", name, COLOR_ERROR);
			saveTakeover();
		}
	}

	/** Reacts to takeover changes: alert, overlay line and a confirmation in Twitch chat. */
	private final class TakeoverEvents implements Takeover.Listener {
		@Override
		public void started(Takeover.Turn turn) {
			long minutes = Math.round(turn.remainingMs() / 60_000.0);
			onAlert(Alert.of(Alert.Type.TAKEOVER, turn.name, minutes + " minuten", (int) minutes));
			say("\uD83D\uDC51 @" + turn.name + " heeft nu " + minutes + " minuten alleen de controle over Minecraft! Andere commando's tellen even niet.");
			saveTakeover();
		}

		@Override
		public void queued(Takeover.Turn turn, int position) {
			long minutes = Math.round(turn.remainingMs() / 60_000.0);
			feed("wachtrij #" + position, turn.name, COLOR_TAKEOVER);
			say("@" + turn.name + " staat in de wachtrij voor een overname (plek " + position + ", " + minutes + " minuten).");
			saveTakeover();
		}

		@Override
		public void extended(Takeover.Turn turn, long addedMs) {
			long minutes = Math.round(addedMs / 60_000.0);
			feed("overname +" + minutes + " min", turn.name, COLOR_TAKEOVER);
			say("@" + turn.name + " krijgt er " + minutes + " minuten bij (nu " + clock(turn.remainingMs()) + ").");
			saveTakeover();
		}

		@Override
		public void ended(Takeover.Turn turn) {
			feed("overname voorbij", turn.name, COLOR_TAKEOVER);
			if (!takeover.active()) {
				say("De overname van @" + turn.name + " is voorbij. Iedereen mag weer meedoen!");
				round = new VoteRound();
				roundEndsAt = System.currentTimeMillis() + config.voteSeconds * 1000L;
			}
			saveTakeover();
		}
	}

	private void say(String message) {
		if (config.takeover.chatMessages) {
			chatSender.send(message);
		}
	}

	/**
	 * Makes text from viewers safe to show: removes control characters, Minecraft's § formatting
	 * codes (obfuscated or invisible text) and invisible Unicode that flips or hides text.
	 */
	public static String clean(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(text.length());
		text.codePoints().forEach(c -> {
			boolean control = c < 0x20 || (c >= 0x7F && c < 0xA0);
			boolean formatting = c == '\u00A7';
			boolean invisible = (c >= 0x200B && c <= 0x200F) || (c >= 0x202A && c <= 0x202E) || (c >= 0x2060 && c <= 0x206F) || c == 0xFEFF;
			if (!control && !formatting && !invisible) {
				out.appendCodePoint(c);
			}
		});
		return out.toString();
	}

	private static Alert cleaned(Alert alert) {
		return new Alert(alert.type(), clean(alert.user()), clean(alert.detail()), alert.amount(), alert.gifter());
	}

	/** Remaining time as m:ss. */
	public static String clock(long ms) {
		long seconds = Math.max(0, (ms + 999) / 1000);
		return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
	}

	/** Whether the takeover clock runs: control on, in game, no menu, not paused. */
	public boolean takeoverRunning(Minecraft mc) {
		return config.enabled && Controller.canControl(mc);
	}

	private void tickTakeover(Minecraft mc) {
		long now = System.currentTimeMillis();
		long elapsed = lastTakeoverTick == 0 ? 0 : now - lastTakeoverTick;
		lastTakeoverTick = now;
		takeover.tick(elapsed, takeoverRunning(mc));
		// Save now and then, so a crash or restart loses at most a few seconds of paid time.
		if (takeover.active() && now - lastTakeoverSave > 10_000) {
			saveTakeover();
		}
	}

	private record SavedTurn(String userId, String name, long remainingMs) {
	}

	public void saveTakeover() {
		lastTakeoverSave = System.currentTimeMillis();
		try {
			List<SavedTurn> turns = takeover.turns().stream().map(t -> new SavedTurn(t.userId, t.name, t.remainingMs())).toList();
			Files.writeString(takeoverPath, new Gson().toJson(turns));
		} catch (IOException e) {
			TwitchPlaysClient.LOGGER.warn("Could not save {}", takeoverPath, e);
		}
	}

	private void loadTakeover() {
		try {
			if (Files.exists(takeoverPath)) {
				SavedTurn[] saved = new Gson().fromJson(Files.readString(takeoverPath), SavedTurn[].class);
				if (saved != null) {
					takeover.restore(Arrays.stream(saved).map(t -> new Takeover.Turn(t.userId(), t.name(), t.remainingMs())).toList());
				}
			}
		} catch (Exception e) {
			TwitchPlaysClient.LOGGER.warn("Could not read {}", takeoverPath, e);
		}
	}

	public void onAlert(Alert alert) {
		alert = cleaned(alert);
		long now = System.currentTimeMillis();
		pendingGiftsSince.entrySet().removeIf(e -> {
			boolean expired = now - e.getValue() > 60_000;
			if (expired) {
				pendingGifts.remove(e.getKey());
			}
			return expired;
		});
		switch (alert.type()) {
			case GIFT_BOMB -> {
				pendingGifts.merge(alert.gifter(), alert.amount(), Integer::sum);
				pendingGiftsSince.put(alert.gifter(), now);
			}
			case GIFT -> {
				Integer remaining = pendingGifts.get(alert.gifter());
				if (remaining != null && remaining > 0) {
					pendingGifts.put(alert.gifter(), remaining - 1);
					return;
				}
			}
			default -> {
			}
		}
		if (!config.alerts.allows(alert.type().setting)) {
			return;
		}
		alerts.add(alert);
		feed(describe(alert), alert.user(), COLOR_ALERT);
	}

	/** For /twitchplays alert: always shows, whatever the alert settings are. */
	public void testAlert(Alert alert) {
		alert = cleaned(alert);
		alerts.add(alert);
		feed(describe(alert), alert.user(), COLOR_ALERT);
	}

	private static String describe(Alert alert) {
		return switch (alert.type()) {
			case SUB -> "sub (" + alert.detail() + ")";
			case RESUB -> "resub, " + alert.detail();
			case GIFT -> "gift sub aan " + alert.detail();
			case GIFT_BOMB -> alert.amount() + " gift subs";
			case FOLLOW -> "nieuwe follower";
			case DONATION -> "donatie " + alert.detail();
			case RAID -> "raid met " + alert.amount() + " kijkers";
			case BITS -> alert.amount() + " bits";
			case TAKEOVER -> "overname " + alert.detail();
		};
	}

	/** For /twitchplays test: behaves like a chat message from a viewer. */
	public void simulate(String text) {
		String withPrefix = text.strip().startsWith(parser.prefix()) ? text.strip() : parser.prefix() + text.strip();
		onChat(new ChatMessage("local-test", "test", "test", withPrefix, 0, false, false));
	}

	private boolean handleModCommand(String text) {
		String word = text.toLowerCase(Locale.ROOT);
		if (!parser.prefix().isEmpty()) {
			if (!word.startsWith(parser.prefix())) {
				return false;
			}
			word = word.substring(parser.prefix().length());
		}
		switch (word.strip().replaceAll("\\s+", " ")) {
			case "anarchy", "anarchie" -> {
				if (!config.modsCanSwitchMode) {
					return false;
				}
				setMode(false);
			}
			case "democracy", "democratie" -> {
				if (!config.modsCanSwitchMode) {
					return false;
				}
				setMode(true);
			}
			// Moderators can always end a takeover, e.g. when someone is trolling.
			case "takeover stop", "overname stop" -> stopTakeover();
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Ends the current takeover; the next viewer in line (if any) starts. */
	public void stopTakeover() {
		Takeover.Turn stopped = takeover.stop();
		if (stopped == null) {
			feed("geen overname bezig", "", COLOR_INFO);
		}
	}

	/** A cheer looks like "Cheer100 !jump": the command starts at the prefix, cheermotes are dropped. */
	private Optional<ParsedCommand> parseCheer(String text) {
		if (!parser.prefix().isEmpty()) {
			int start = text.indexOf(parser.prefix());
			return start < 0 ? Optional.empty() : parser.parse(text.substring(start));
		}
		return parser.parse(text.replaceAll("(?i)\\b[a-z]+\\d+\\b", " "));
	}

	private boolean isAllowed(ParsedCommand command) {
		return config.disabled.stream().noneMatch(id -> id.equalsIgnoreCase(command.action().id()));
	}

	private boolean isPaidOnly(ParsedCommand command) {
		return config.paidOnly.stream().anyMatch(id -> id.equalsIgnoreCase(command.action().id()));
	}

	// ---- running commands ----

	private void run(ParsedCommand command, String who, int color, boolean paid) {
		String error = controller.execute(command, Minecraft.getInstance(), paid);
		String what = parser.prefix() + command.canonical();
		if (error == null) {
			feed(what, who, color);
		} else {
			feed(what + " (" + error + ")", who, COLOR_ERROR);
		}
	}

	public void tick(Minecraft mc) {
		if (!greeted && mc.player != null) {
			greeted = true;
			greet();
		}
		controller.tick(mc);
		alerts.tick(mc, config.alerts);
		tickTakeover(mc);
		if (!config.democracy() || !config.enabled || takeover.active()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < roundEndsAt) {
			return;
		}
		VoteRound finished = round;
		round = new VoteRound();
		roundEndsAt = now + config.voteSeconds * 1000L;
		finished.winner().ifPresent(winner -> run(winner.command(),
				"[" + winner.votes() + "/" + finished.voters() + " stemmen]", COLOR_VOTE, false));
	}

	/** First time in a world: explain how to get started, since the overlay has no room for it. */
	private void greet() {
		if (config.channel.isBlank()) {
			message(Component.literal("Nog geen Twitch-kanaal ingesteld. Typ ")
					.append(Component.literal("/twitchplays channel <jouw kanaalnaam>").withStyle(ChatFormatting.YELLOW))
					.append(". Alle commando's: /twitchplays commands"));
		} else {
			message(Component.literal(status).withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- state for the commands and the overlay ----

	public void setEnabled(boolean enabled) {
		config.enabled = enabled;
		config.save();
		if (!enabled) {
			controller.stopAll();
		}
		round = new VoteRound();
		roundEndsAt = System.currentTimeMillis() + config.voteSeconds * 1000L;
		feed(enabled ? "Besturing aan" : "Besturing uit", "", COLOR_INFO);
	}

	public void setMode(boolean democracy) {
		config.mode = democracy ? "democracy" : "anarchy";
		config.save();
		round = new VoteRound();
		roundEndsAt = System.currentTimeMillis() + config.voteSeconds * 1000L;
		feed(democracy ? "Modus: democratie" : "Modus: anarchie", "", COLOR_INFO);
	}

	public void setChannel(String channel) {
		config.channel = channel.strip().toLowerCase(Locale.ROOT).replaceFirst("^#", "");
		config.save();
		irc.connect(config.channel);
		status = "Verbinden met #" + config.channel + "...";
	}

	public VoteRound round() {
		return round;
	}

	public long millisLeftInRound() {
		return Math.max(0, roundEndsAt - System.currentTimeMillis());
	}

	public Deque<FeedEntry> feed() {
		return feed;
	}

	public String status() {
		return status;
	}

	public void feed(String what, String who, int color) {
		feed.addFirst(new FeedEntry(clean(what), clean(who), color, System.currentTimeMillis()));
		while (feed.size() > FEED_SIZE) {
			feed.removeLast();
		}
	}

	public void status(String text) {
		// Status texts can hold replies from Twitch or other services; never let a token reach the log or the screen.
		text = Secrets.mask(text);
		status = text;
		TwitchPlaysClient.LOGGER.info(text);
		message(Component.literal(text).withStyle(ChatFormatting.GRAY));
	}

	public static void message(Component text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			MutableComponent line = Component.literal("[Twitch] ").withStyle(ChatFormatting.DARK_PURPLE).append(text);
			mc.gui.chatListener().handleSystemMessage(line, false);
		}
	}

	public static Component link(String text, String url) {
		return Component.literal(text).withStyle(Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));
	}

	public static String commandList(String prefix) {
		StringBuilder out = new StringBuilder();
		for (Action action : Action.values()) {
			if (!out.isEmpty()) {
				out.append(", ");
			}
			out.append(prefix).append(action.id());
			switch (action.kind) {
				case SECONDS -> out.append(" [sec]");
				case DEGREES -> out.append(" [graden]");
				case COUNT -> out.append(" [aantal]");
				case SLOT -> out.append(" <1-9>");
				case TEXT -> out.append(" <item>");
				case ITEM_COUNT -> out.append(" <item> [aantal]");
				case NONE -> {
				}
			}
		}
		return out.toString();
	}
}
