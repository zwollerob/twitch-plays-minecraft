package nl.zwollerob.twitchplays;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** config/twitchplays.json. Field names are the JSON keys, so keep them stable. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Twitch channel to read chat from, without #. */
	public String channel = "";
	/** Client ID of your own app on dev.twitch.tv (type "Public"); only needed for channel points. */
	public String clientId = "";
	/** Chat commands start with this, e.g. "!forward". Empty means bare words ("forward"). */
	public String prefix = "!";
	public boolean enabled = true;
	/** "anarchy" or "democracy". */
	public String mode = "anarchy";
	/** Length of a democracy voting round. */
	public int voteSeconds = 5;
	/** In anarchy, how long one chatter must wait between commands. */
	public int userCooldownMs = 1500;
	/** Upper limit for "!forward 10"-style hold times. */
	public double maxHoldSeconds = 5;
	/** Twitch moderators and the broadcaster can type !anarchy / !democracy. */
	public boolean modsCanSwitchMode = true;
	public boolean showOverlay = true;

	/** Commands free chat cannot use; only bits and channel points can trigger them. */
	public List<String> paidOnly = new ArrayList<>(List.of("dropstack", "shuffle", "unequip"));
	/** Minimum bits for a cheer with this command, e.g. "Cheer100 !shuffle". Cheers skip voting and cooldown. */
	public Map<String, Integer> bitsCost = new LinkedHashMap<>(Map.of(
			"dropstack", 100,
			"unequip", 300,
			"shuffle", 500));
	/** Bits needed for commands not listed in bitsCost. */
	public int bitsDefaultCost = 1;
	/**
	 * Channel point reward title -> command. "*" uses the text the viewer typed in the reward
	 * as the command, so make that reward require user input.
	 */
	public Map<String, String> rewards = new LinkedHashMap<>(Map.of(
			"Twitch bestuurt mij", "*",
			"Hussel mijn inventory", "shuffle",
			"Trek mijn armor uit", "unequip"));
	/** Extra words for commands, e.g. "vooruitlopen": "forward". */
	public Map<String, String> aliases = new LinkedHashMap<>();
	/** Commands that are switched off completely. */
	public List<String> disabled = new ArrayList<>();
	/** Pop-ups in Minecraft for subs, follows, donations, raids and bits. */
	public Alerts alerts = new Alerts();
	/** One big cheer buys exclusive control for a while. */
	public TakeoverSettings takeover = new TakeoverSettings();

	public static final class TakeoverSettings {
		public boolean enabled = true;
		/** Smallest single cheer that buys control. */
		public int minBits = 1000;
		/** One minute of control per this many bits: 1000 bits = 10 minutes. */
		public int bitsPerMinute = 100;
		/** Most control one viewer can have at once, also after cheering again. */
		public int maxMinutes = 30;
		/** Confirm in Twitch chat (needs /twitchplays login). */
		public boolean chatMessages = true;
	}

	public static final class Alerts {
		public boolean enabled = true;
		public boolean subs = true;
		public boolean gifts = true;
		/** Needs StreamElements (/twitchplays streamelements). */
		public boolean follows = true;
		/** Needs StreamElements (/twitchplays streamelements). */
		public boolean donations = true;
		public boolean raids = true;
		public boolean bits = true;
		public boolean takeover = true;
		/** Smaller cheers don't get an alert. */
		public int bitsMinimum = 100;
		public boolean title = true;
		public boolean sound = true;
		public boolean fireworks = true;

		/** Whether alerts of this kind are switched on, by the setting name from {@link nl.zwollerob.twitchplays.twitch.Alert.Type#setting}. */
		public boolean allows(String setting) {
			if (!enabled) {
				return false;
			}
			return switch (setting) {
				case "subs" -> subs;
				case "gifts" -> gifts;
				case "follows" -> follows;
				case "donations" -> donations;
				case "raids" -> raids;
				case "bits" -> bits;
				case "takeover" -> takeover;
				default -> false;
			};
		}
	}

	private transient Path path;

	public static Config load(Path path) {
		Config config = null;
		try {
			if (Files.exists(path)) {
				config = GSON.fromJson(Files.readString(path), Config.class);
			}
		} catch (Exception e) {
			TwitchPlaysClient.LOGGER.error("Could not read {}, using defaults", path, e);
		}
		if (config == null) {
			config = new Config();
		}
		config.path = path;
		config.fillMissing();
		config.save();
		return config;
	}

	/** Gson leaves fields null when an older config file lacks them. */
	private void fillMissing() {
		Config defaults = new Config();
		if (channel == null) channel = defaults.channel;
		if (clientId == null) clientId = defaults.clientId;
		if (prefix == null) prefix = defaults.prefix;
		if (mode == null) mode = defaults.mode;
		if (paidOnly == null) paidOnly = defaults.paidOnly;
		if (bitsCost == null) bitsCost = defaults.bitsCost;
		if (rewards == null) rewards = defaults.rewards;
		if (aliases == null) aliases = defaults.aliases;
		if (disabled == null) disabled = defaults.disabled;
		if (alerts == null) alerts = defaults.alerts;
		if (takeover == null) takeover = defaults.takeover;
		voteSeconds = Math.max(1, voteSeconds);
		userCooldownMs = Math.max(0, userCooldownMs);
		maxHoldSeconds = Math.max(0.1, maxHoldSeconds);
	}

	public boolean democracy() {
		return "democracy".equalsIgnoreCase(mode);
	}

	public void save() {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (Exception e) {
			TwitchPlaysClient.LOGGER.error("Could not save {}", path, e);
		}
	}
}
