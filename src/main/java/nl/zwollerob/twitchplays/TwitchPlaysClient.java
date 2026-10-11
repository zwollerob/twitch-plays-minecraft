package nl.zwollerob.twitchplays;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import nl.zwollerob.twitchplays.hud.Overlay;
import nl.zwollerob.twitchplays.twitch.Alert;
import nl.zwollerob.twitchplays.twitch.StreamElementsClient;
import nl.zwollerob.twitchplays.twitch.TwitchAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class TwitchPlaysClient implements ClientModInitializer {
	public static final String MOD_ID = "twitchplays";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static TwitchPlays twitch;
	private static KeyMapping toggleKey;
	private static KeyMapping modeKey;

	/** The running instance; null until the client has started. */
	public static TwitchPlays get() {
		return twitch;
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.twitchplays.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, category));
		modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.twitchplays.mode", InputConstants.Type.KEYBOARD, InputConstants.KEY_F9, category));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			twitch = new TwitchPlays();
			HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "overlay"), new Overlay(twitch)::render);
			twitch.start();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (twitch != null) {
				twitch.irc.disconnect();
				twitch.eventSub.stop();
				twitch.streamElements.stop();
				twitch.saveTakeover();
			}
		});

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (twitch == null) {
				return;
			}
			try {
				twitch.tick(client);
			} catch (RuntimeException e) {
				// An error here would otherwise crash the game mid-stream; let go of all keys and keep playing.
				LOGGER.error("Twitch Plays tick failed", e);
				twitch.controller.stopAll();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (twitch == null) {
				return;
			}
			while (toggleKey.consumeClick()) {
				twitch.setEnabled(!twitch.config.enabled);
			}
			while (modeKey.consumeClick()) {
				twitch.setMode(!twitch.config.democracy());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> registerCommands(dispatcher));
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("twitchplays")
				.executes(ctx -> status())
				.then(ClientCommands.literal("status").executes(ctx -> status()))
				.then(ClientCommands.literal("on").executes(ctx -> run(() -> twitch.setEnabled(true))))
				.then(ClientCommands.literal("off").executes(ctx -> run(() -> twitch.setEnabled(false))))
				.then(ClientCommands.literal("mode")
						.then(ClientCommands.literal("anarchy").executes(ctx -> run(() -> twitch.setMode(false))))
						.then(ClientCommands.literal("democracy").executes(ctx -> run(() -> twitch.setMode(true)))))
				.then(ClientCommands.literal("channel")
						.then(ClientCommands.argument("name", StringArgumentType.word())
								.executes(ctx -> run(() -> twitch.setChannel(StringArgumentType.getString(ctx, "name"))))))
				.then(ClientCommands.literal("clientid")
						.then(ClientCommands.argument("id", StringArgumentType.word())
								.executes(TwitchPlaysClient::setClientId)))
				.then(ClientCommands.literal("login").executes(ctx -> login()))
				.then(ClientCommands.literal("logout").executes(ctx -> run(() -> {
					twitch.eventSub.stop();
					twitch.auth.logout();
					TwitchPlays.message(Component.literal("Uitgelogd. Kanaalpunten staan uit; chat en bits werken gewoon door."));
				})))
				.then(ClientCommands.literal("overlay").executes(ctx -> run(() -> {
					twitch.config.showOverlay = !twitch.config.showOverlay;
					twitch.config.save();
				})))
				.then(ClientCommands.literal("commands").executes(ctx -> run(() ->
						TwitchPlays.message(Component.literal(TwitchPlays.commandList(twitch.parser().prefix())).withStyle(ChatFormatting.WHITE)))))
				.then(ClientCommands.literal("test")
						.then(ClientCommands.argument("command", StringArgumentType.greedyString())
								.executes(ctx -> run(() -> twitch.simulate(StringArgumentType.getString(ctx, "command"))))))
				.then(ClientCommands.literal("reload").executes(ctx -> reload()))
				.then(ClientCommands.literal("streamelements")
						.executes(ctx -> streamElements())
						.then(ClientCommands.literal("off").executes(ctx -> run(() -> {
							try {
								twitch.setStreamElementsToken("");
								TwitchPlays.message(Component.literal("StreamElements ontkoppeld."));
							} catch (IOException e) {
								TwitchPlays.message(Component.literal("Kon het token niet verwijderen: " + e.getMessage()).withStyle(ChatFormatting.RED));
							}
						}))))
				.then(ClientCommands.literal("alerts")
						.then(ClientCommands.literal("on").executes(ctx -> setAlerts(true)))
						.then(ClientCommands.literal("off").executes(ctx -> setAlerts(false))))
				.then(alertTests())
				.then(ClientCommands.literal("takeover")
						.then(ClientCommands.literal("stop").executes(ctx -> run(() -> twitch.stopTakeover())))
						// Pretend a viewer cheered, to see a takeover without spending bits.
						.then(ClientCommands.literal("test")
								.executes(ctx -> run(() -> twitch.offerTakeover("local-takeover-test", "TestKijker", 1000)))
								.then(ClientCommands.argument("bits", IntegerArgumentType.integer(1))
										.executes(ctx -> run(() -> twitch.offerTakeover("local-takeover-test", "TestKijker",
												IntegerArgumentType.getInteger(ctx, "bits"))))))));
	}

	/** /twitchplays alert <type>: shows an example alert so the streamer can see what it looks like. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> alertTests() {
		Map<String, Alert> examples = new LinkedHashMap<>();
		examples.put("sub", Alert.of(Alert.Type.SUB, "TestKijker", "Tier 1", 1));
		examples.put("resub", Alert.of(Alert.Type.RESUB, "TestKijker", "12 maanden", 12));
		examples.put("gift", new Alert(Alert.Type.GIFT, "TestKijker", "Gelukkige", 1, "testkijker"));
		examples.put("giftbomb", new Alert(Alert.Type.GIFT_BOMB, "TestKijker", "", 10, "testkijker"));
		examples.put("follow", Alert.of(Alert.Type.FOLLOW, "TestKijker", "", 1));
		examples.put("donation", Alert.of(Alert.Type.DONATION, "TestKijker", "€ 5,00", 5));
		examples.put("raid", Alert.of(Alert.Type.RAID, "TestStreamer", "", 42));
		examples.put("bits", Alert.of(Alert.Type.BITS, "TestKijker", "", 500));
		examples.put("takeover", Alert.of(Alert.Type.TAKEOVER, "TestKijker", "10 minuten", 10));
		LiteralArgumentBuilder<FabricClientCommandSource> alert = ClientCommands.literal("alert");
		examples.forEach((name, example) -> alert.then(ClientCommands.literal(name).executes(ctx -> run(() -> twitch.testAlert(example)))));
		return alert;
	}

	private static int setAlerts(boolean on) {
		return run(() -> {
			twitch.config.alerts.enabled = on;
			twitch.config.save();
			TwitchPlays.message(Component.literal(on ? "Meldingen aan." : "Meldingen uit."));
		});
	}

	/** The JWT token is longer than Minecraft's chat allows, so it is read from the clipboard. */
	private static int streamElements() {
		return run(() -> {
			Minecraft mc = Minecraft.getInstance();
			String token = mc.keyboardHandler.getClipboard().strip();
			if (!StreamElementsClient.looksLikeJwt(token)) {
				TwitchPlays.message(Component.literal("Kopieer eerst je JWT-token: ")
						.append(TwitchPlays.link("streamelements.com/dashboard/account/channels", "https://streamelements.com/dashboard/account/channels"))
						.append(" → Show secrets → JWT Token. Typ daarna opnieuw /twitchplays streamelements"));
				return;
			}
			try {
				twitch.setStreamElementsToken(token);
				mc.keyboardHandler.setClipboard("");
				TwitchPlays.message(Component.literal("Token opgeslagen en van je klembord gehaald. Verbinden met StreamElements..."));
			} catch (IOException e) {
				TwitchPlays.message(Component.literal("Kon het token niet opslaan: " + e.getMessage()).withStyle(ChatFormatting.RED));
			}
		});
	}

	private static int run(Runnable action) {
		if (twitch != null) {
			action.run();
		}
		return 1;
	}

	private static int status() {
		return run(() -> {
			Config c = twitch.config;
			TwitchAuth.Token token = twitch.auth.token();
			TwitchPlays.message(Component.literal(
					"Kanaal: " + (c.channel.isBlank() ? "(geen)" : "#" + c.channel) + " - " + twitch.irc.state()
							+ "\nBesturing: " + (c.enabled ? "aan" : "uit") + ", modus: " + (c.democracy() ? "democratie" : "anarchie")
							+ "\nKanaalpunten: " + (token == null ? "niet ingelogd" : "ingelogd als " + token.login()
							+ (twitch.eventSub.active() ? "" : " (niet actief)"))
							+ "\nStreamElements: " + (twitch.streamElementsToken().isEmpty() ? "niet gekoppeld"
							: twitch.streamElements.connected() ? "verbonden" : "niet verbonden")
							+ "\nMeldingen: " + (c.alerts.enabled ? "aan" : "uit")
							+ "\nConfig: config/twitchplays.json").withStyle(ChatFormatting.WHITE));
		});
	}

	private static int setClientId(CommandContext<FabricClientCommandSource> ctx) {
		return run(() -> {
			twitch.config.clientId = StringArgumentType.getString(ctx, "id");
			twitch.config.save();
			TwitchPlays.message(Component.literal("Client ID opgeslagen. Log nu in met /twitchplays login."));
		});
	}

	private static int login() {
		return run(() -> {
			Config c = twitch.config;
			if (c.clientId.isBlank()) {
				TwitchPlays.message(Component.literal("Stel eerst je Client ID in: /twitchplays clientid <id>. Maak die aan op ")
						.append(TwitchPlays.link("dev.twitch.tv/console/apps", "https://dev.twitch.tv/console/apps"))
						.append(" (type: Public)."));
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			TwitchPlays.message(Component.literal("Login starten..."));
			twitch.auth.login(c.clientId,
					code -> mc.execute(() -> {
						mc.keyboardHandler.setClipboard(code.userCode());
						TwitchPlays.message(Component.literal("Open ")
								.append(TwitchPlays.link(code.verificationUri(), code.verificationUri()))
								.append(" en voer code ")
								.append(Component.literal(code.userCode()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
								.append(" in (staat al op je klembord). Log in met het account van je kanaal."));
					}),
					token -> mc.execute(() -> {
						TwitchPlays.message(Component.literal("Ingelogd als " + token.login() + ".").withStyle(ChatFormatting.GREEN));
						if (c.channel.isBlank()) {
							twitch.setChannel(token.login());
						}
						twitch.eventSub.start(c.clientId);
					}),
					error -> mc.execute(() -> TwitchPlays.message(Component.literal(error).withStyle(ChatFormatting.RED))));
		});
	}

	private static int reload() {
		return run(() -> {
			twitch.reloadConfig();
			TwitchPlays.message(Component.literal("Config opnieuw geladen."));
		});
	}
}
