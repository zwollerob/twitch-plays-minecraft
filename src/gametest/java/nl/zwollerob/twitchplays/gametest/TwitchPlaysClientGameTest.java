package nl.zwollerob.twitchplays.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import nl.zwollerob.twitchplays.SecretStore;
import nl.zwollerob.twitchplays.TwitchPlays;
import nl.zwollerob.twitchplays.TwitchPlaysClient;
import nl.zwollerob.twitchplays.twitch.Alert;
import nl.zwollerob.twitchplays.twitch.ChatMessage;
import nl.zwollerob.twitchplays.twitch.EventSubClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plays the mod like chat would, in a real singleplayer world.
 * Run with: build.cmd runClientGameTest
 */
public class TwitchPlaysClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		// Tokens are encrypted with Windows DPAPI through Minecraft's bundled JNA; check that works inside the game.
		try {
			Path secret = Files.createTempFile("twitchplays", ".txt");
			SecretStore.write(secret, "geheim-token");
			check(Files.readString(secret).startsWith("dpapi:"), "token should be encrypted on disk");
			check(SecretStore.read(secret).equals("geheim-token"), "token should decrypt");
			Files.delete(secret);
		} catch (IOException e) {
			throw new AssertionError(e);
		}

		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set day");
			server.runCommand("give @a minecraft:cobblestone 32");
			server.runCommand("give @a minecraft:bread 5");
			server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate");
			ctx.waitTicks(20);

			// Anarchy: one chatter walks forward for 2 seconds.
			Vec3 start = ctx.computeOnClient(mc -> mc.player.position());
			chat(ctx, "kijker1", "!forward 2", 0);
			ctx.waitTicks(50);
			double walked = ctx.computeOnClient(mc -> mc.player.position().subtract(start).horizontalDistance());
			check(walked > 3, "!forward 2 should walk several blocks, walked " + walked);
			ctx.takeScreenshot("twitchplays-anarchy");

			// Normal chat is ignored.
			chat(ctx, "kijker2", "ik ga forward", 0);
			ctx.waitTicks(5);
			check(feedTop(ctx).contains("forward 2"), "normal chat should not run a command: " + feedTop(ctx));

			// Looking around.
			ctx.runOnClient(mc -> mc.player.setXRot(0));
			float yaw = ctx.computeOnClient(mc -> mc.player.getYRot());
			chat(ctx, "kijker3", "!lookleft 90", 0);
			ctx.waitTicks(10);
			float turned = ctx.computeOnClient(mc -> mc.player.getYRot()) - yaw;
			check(Math.abs(turned + 90) < 0.5, "!lookleft 90 should turn -90 degrees, turned " + turned);

			// Hotbar and items. Cobblestone is in slot 1, bread in slot 2.
			chat(ctx, "kijker4", "!3", 0);
			ctx.waitTicks(3);
			check(selectedSlot(ctx) == 2, "!3 should select hotbar slot 3, selected " + (selectedSlot(ctx) + 1));
			chat(ctx, "kijker5", "!eet", 0);
			ctx.waitTicks(3);
			check(selectedSlot(ctx) == 1, "!eet should select the bread, selected " + (selectedSlot(ctx) + 1));
			chat(ctx, "kijker6", "!stop", 0);
			chat(ctx, "kijker7", "!1", 0);
			ctx.waitTicks(3);
			chat(ctx, "kijker8", "!drop 3", 0);
			world.getConnection().waitForServerboundPackets();
			ctx.waitTicks(5);
			int cobble = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(0).getCount());
			check(cobble == 29, "!drop 3 should drop 3 cobblestone, left " + cobble);

			// The whole inventory: fill the hotbar, so the pickaxe lands in the main inventory (slot 10).
			for (String item : new String[]{"dirt", "oak_planks", "torch", "stick", "sand", "gravel", "apple"}) {
				server.runCommand("give @a minecraft:" + item);
			}
			server.runCommand("give @a minecraft:diamond_pickaxe");
			world.getConnection().waitForClientboundPackets();
			ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> mc.player.getInventory().getItem(9).is(Items.DIAMOND_PICKAXE)), "setup: pickaxe should be in inventory slot 10");
			chat(ctx, "pakker1", "!pak diamond pickaxe", 0);
			world.getConnection().waitForServerboundPackets();
			ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> mc.player.getMainHandItem().is(Items.DIAMOND_PICKAXE)), "!pak diamond pickaxe should put the pickaxe in hand");
			check(ctx.computeOnClient(mc -> mc.player.getInventory().getItem(9).is(Items.COBBLESTONE)), "the cobblestone from the hand should move to where the pickaxe was");
			chat(ctx, "pakker2", "!pak torch", 0);
			ctx.waitTicks(3);
			check(ctx.computeOnClient(mc -> mc.player.getMainHandItem().is(Items.TORCH)), "!pak torch should select the torch in the hotbar");
			chat(ctx, "pakker3", "!pak zwaard", 0);
			ctx.waitTicks(3);
			check(feedTop(ctx).contains("niet gevonden"), "unknown item should say so: " + feedTop(ctx));

			// !inv shows the inventory for 3 seconds.
			chat(ctx, "kijkinv", "!inv", 0);
			ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen), "!inv should open the inventory");
			ctx.takeScreenshot("twitchplays-inventory");
			ctx.waitTicks(70);
			check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "the inventory should close by itself");

			// Compass directions: look exactly that way, straight ahead.
			ctx.runOnClient(mc -> mc.player.setXRot(25));
			chat(ctx, "kompas1", "!noord", 0);
			ctx.waitTicks(15);
			check(ctx.computeOnClient(mc -> Math.abs(Math.abs(Mth.wrapDegrees(mc.player.getYRot())) - 180) < 0.5 && Math.abs(mc.player.getXRot()) < 0.5),
					"!noord should look north, straight ahead");
			chat(ctx, "kompas2", "!kijk oost", 0);
			ctx.waitTicks(15);
			check(ctx.computeOnClient(mc -> Math.abs(Mth.wrapDegrees(mc.player.getYRot()) + 90) < 0.5), "!kijk oost should look east");

			// Walk to a tree 4 blocks east in exact steps, stop in front of it and chop the log at eye height.
			BlockPos walkStart = ctx.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos tree = walkStart.east(4);
			server.runCommand("fill " + tree.getX() + " " + tree.getY() + " " + tree.getZ() + " " + tree.getX() + " " + (tree.getY() + 2) + " " + tree.getZ() + " minecraft:oak_log");
			world.getConnection().waitForClientboundPackets();
			chat(ctx, "stapper1", "!stap 2", 0);
			ctx.waitTicks(40);
			BlockPos afterTwo = ctx.computeOnClient(mc -> mc.player.blockPosition());
			check(afterTwo.equals(walkStart.east(2)), "!stap 2 should end 2 blocks east: " + walkStart + " -> " + afterTwo);
			check(ctx.computeOnClient(mc -> Math.abs(mc.player.getX() - (afterTwo.getX() + 0.5)) < 0.3 && Math.abs(mc.player.getZ() - (afterTwo.getZ() + 0.5)) < 0.3),
					"!stap should end near the middle of the block");
			chat(ctx, "stapper2", "!stap 5", 0);
			ctx.waitTicks(50);
			BlockPos atTree = ctx.computeOnClient(mc -> mc.player.blockPosition());
			check(atTree.equals(walkStart.east(3)), "!stap 5 should stop in front of the tree: " + atTree);
			chat(ctx, "houthakker", "!mine 5", 0);
			ctx.waitTicks(110);
			check(ctx.computeOnClient(mc -> mc.level.getBlockState(tree.above()).isAir()), "!mine 5 should chop the log at eye height");
			ctx.takeScreenshot("twitchplays-tree");

			// Jumping forward onto a step: face south and build a 1 block high platform right in front.
			server.runCommand("tp @a ~ ~ ~ 0 0");
			server.runCommand("execute at @p run fill ~-2 ~ ~1 ~2 ~ ~7 minecraft:stone");
			world.getConnection().waitForClientboundPackets();
			ctx.waitTicks(5);
			double groundY = ctx.computeOnClient(mc -> mc.player.getY());
			Vec3 jumpStart = ctx.computeOnClient(mc -> mc.player.position());
			chat(ctx, "springer1", "!jumpforward", 0);
			ctx.waitTicks(30);
			double stepY = ctx.computeOnClient(mc -> mc.player.getY());
			String debug = ctx.computeOnClient(mc -> " feed=" + feedTop(mc) + " moved=" + mc.player.position().subtract(jumpStart)
					+ " yaw=" + mc.player.getYRot() + " block ahead=" + mc.level.getBlockState(mc.player.blockPosition().south()));
			check(Math.abs(stepY - (groundY + 1)) < 0.01, "!jumpforward should land on the step one block up: from " + groundY + " to " + stepY + debug);

			// Sprint jump goes further than a walking jump.
			Vec3 sprintStart = ctx.computeOnClient(mc -> mc.player.position());
			chat(ctx, "springer2", "!sprintjump", 0);
			ctx.waitTicks(20);
			double sprinted = ctx.computeOnClient(mc -> mc.player.position().subtract(sprintStart).horizontalDistance());
			check(sprinted > 2.5, "!sprintjump should cover more than 2.5 blocks, did " + sprinted);

			// Pillar up 3 blocks with dirt; the torch in hand is not a full block, so the dirt gets picked.
			server.runCommand("give @a minecraft:dirt 10");
			world.getConnection().waitForClientboundPackets();
			ctx.waitTicks(15);
			ctx.runOnClient(mc -> mc.player.setXRot(10));
			double pillarStartY = ctx.computeOnClient(mc -> mc.player.getY());
			chat(ctx, "bouwer", "!pillar 3", 0);
			ctx.waitTicks(60);
			world.getConnection().waitForServerboundPackets();
			double pillarY = ctx.computeOnClient(mc -> mc.player.getY());
			check(Math.abs(pillarY - (pillarStartY + 3)) < 0.01, "!pillar 3 should end 3 blocks higher: from " + pillarStartY + " to " + pillarY);
			check(ctx.computeOnClient(mc -> mc.player.getMainHandItem().is(Items.DIRT) && mc.player.getMainHandItem().getCount() == 8),
					"pillar should use 3 dirt from the hand");
			float pitchAfter = ctx.computeOnClient(mc -> mc.player.getXRot());
			check(Math.abs(pitchAfter - 10) < 0.5, "after the pillar the player should look like before, pitch " + pitchAfter);
			ctx.takeScreenshot("twitchplays-pillar");

			// From the top of the pillar, bridge 4 blocks south without falling.
			BlockPos bridgeFloor = ctx.computeOnClient(mc -> mc.player.blockPosition().below());
			Vec3 bridgeStart = ctx.computeOnClient(mc -> mc.player.position());
			chat(ctx, "bruggenbouwer", "!bridge 4", 0);
			ctx.waitTicks(90);
			world.getConnection().waitForServerboundPackets();
			ctx.waitTicks(5);
			ctx.takeScreenshot("twitchplays-bridge");
			double bridgeY = ctx.computeOnClient(mc -> mc.player.getY());
			double bridged = ctx.computeOnClient(mc -> mc.player.position().subtract(bridgeStart).horizontalDistance());
			check(Math.abs(bridgeY - pillarY) < 0.01, "bridging must not fall off: from " + pillarY + " to " + bridgeY);
			check(bridged > 3, "!bridge 4 should walk out over the bridge, walked " + bridged);
			for (int i = 1; i <= 4; i++) {
				BlockPos pos = bridgeFloor.south(i);
				check(ctx.computeOnClient(mc -> mc.level.getBlockState(pos).is(Blocks.DIRT)), "bridge block " + i + " should be dirt at " + pos);
			}
			check(ctx.computeOnClient(mc -> mc.player.getMainHandItem().getCount() == 4), "the bridge should use 4 dirt");
			check(ctx.computeOnClient(mc -> Math.abs(mc.player.getXRot() - 10) < 0.5), "after the bridge the player should look like before");

			// Paid commands: free chat can't, a big enough cheer can.
			chat(ctx, "kijker9", "!unequip", 0);
			ctx.waitTicks(5);
			check(wearsChestplate(ctx), "free !unequip must be ignored (paidOnly)");
			chat(ctx, "kijker10", "Cheer100 !unequip", 100);
			ctx.waitTicks(5);
			check(wearsChestplate(ctx), "100 bits is below the 300 bits cost of unequip");
			chat(ctx, "kijker11", "Cheer300 !unequip", 300);
			world.getConnection().waitForServerboundPackets();
			ctx.waitTicks(10);
			check(!wearsChestplate(ctx), "Cheer300 !unequip should take the chestplate off");
			check(feedTop(ctx).equals("!unequip  kijker11 [300 bits]"), "cheer should be in the feed: " + feedTop(ctx));

			// Channel points: a fixed reward and a reward that uses the viewer's text.
			ctx.runOnClient(mc -> TwitchPlaysClient.get().onRedemption(new EventSubClient.Redemption("puntenkijker", "Hussel mijn inventory", "")));
			ctx.waitTicks(5);
			check(feedTop(ctx).contains("shuffle"), "reward should shuffle: " + feedTop(ctx));
			ctx.runOnClient(mc -> mc.player.setXRot(0));
			ctx.runOnClient(mc -> TwitchPlaysClient.get().onRedemption(new EventSubClient.Redemption("puntenkijker", "twitch bestuurt mij", "lookup 45")));
			ctx.waitTicks(10);
			float pitch = ctx.computeOnClient(mc -> mc.player.getXRot());
			check(Math.abs(pitch + 45) < 0.5, "reward with text 'lookup 45' should look up 45 degrees, pitch " + pitch);

			// Democracy: the most popular command wins after the round.
			ctx.runOnClient(mc -> TwitchPlaysClient.get().setMode(true));
			chat(ctx, "stem1", "!jump", 0);
			chat(ctx, "stem2", "!spring", 0);
			chat(ctx, "stem3", "!j", 0);
			chat(ctx, "stem4", "!back", 0);
			chat(ctx, "stem4", "!back 2", 0);
			ctx.waitTicks(20);
			ctx.takeScreenshot("twitchplays-democracy");
			ctx.waitFor(mc -> feedTop(mc).contains("jump"), 200);
			check(feedTop(ctx).equals("!jump  [3/4 stemmen]"), "jump should win the vote: " + feedTop(ctx));

			// Moderators can switch back to anarchy from chat; F8 (off) blocks everything.
			ctx.runOnClient(mc -> TwitchPlaysClient.get().onChat(new ChatMessage("99", "mod", "Mod", "!anarchie", 0, true, false)));
			check(ctx.computeOnClient(mc -> !TwitchPlaysClient.get().config.democracy()), "mod !anarchie should switch to anarchy");
			ctx.runOnClient(mc -> TwitchPlaysClient.get().setEnabled(false));
			Vec3 before = ctx.computeOnClient(mc -> mc.player.position());
			chat(ctx, "kijker12", "!forward 2", 0);
			ctx.waitTicks(30);
			double moved = ctx.computeOnClient(mc -> mc.player.position().subtract(before).horizontalDistance());
			check(moved < 0.1, "control off: chat must not move the player, moved " + moved);
			ctx.takeScreenshot("twitchplays-off");
			ctx.runOnClient(mc -> TwitchPlaysClient.get().setEnabled(true));

			// Alerts: the cheers above already queued bits alerts; let those finish first.
			ctx.waitFor(mc -> TwitchPlaysClient.get().alerts.idle(), 600);
			ctx.runOnClient(mc -> {
				TwitchPlays twitch = TwitchPlaysClient.get();
				twitch.onAlert(new Alert(Alert.Type.GIFT_BOMB, "Gulle", "", 3, "gulle"));
				for (int i = 0; i < 3; i++) {
					twitch.onAlert(new Alert(Alert.Type.GIFT, "Gulle", "Kijker" + i, 1, "gulle"));
				}
				// A fourth single gift after the bomb is a gift of its own.
				twitch.onAlert(new Alert(Alert.Type.GIFT, "Gulle", "Extra", 1, "gulle"));
			});
			int queued = ctx.computeOnClient(mc -> TwitchPlaysClient.get().alerts.queued());
			check(queued == 2, "a gift bomb of 3 plus its 3 gifts should be one alert, plus the extra gift; queued " + queued);
			ctx.waitTicks(30);
			ctx.takeScreenshot("twitchplays-alert-giftbomb");
			float health = ctx.computeOnClient(mc -> mc.player.getHealth());
			ctx.waitTicks(80);
			check(ctx.computeOnClient(mc -> mc.player.getHealth()) >= health, "fireworks must not do damage");
			ctx.runOnClient(mc -> TwitchPlaysClient.get().onAlert(Alert.of(Alert.Type.DONATION, "Donateur", "€ 10,00", 10)));
			ctx.waitFor(mc -> TwitchPlaysClient.get().alerts.queued() == 0, 200);
			ctx.waitTicks(30);
			ctx.takeScreenshot("twitchplays-alert-donation");

			// Takeover: one cheer of 1000+ bits gives exclusive control.
			ctx.runOnClient(mc -> TwitchPlaysClient.get().setMode(false));
			chat(ctx, "klein", "Cheer999", 999);
			check(ctx.computeOnClient(mc -> !TwitchPlaysClient.get().takeover.active()), "999 bits is not enough for a takeover");
			chat(ctx, "baas", "Cheer1000 overname!", 1000);
			check(ctx.computeOnClient(mc -> TwitchPlaysClient.get().takeover.controls("baas")), "1000 bits should give control");
			long tenMinutes = ctx.computeOnClient(mc -> TwitchPlaysClient.get().takeover.current().remainingMs());
			check(tenMinutes > 9.9 * 60_000 && tenMinutes <= 10 * 60_000, "1000 bits = 10 minutes, got " + tenMinutes);

			Vec3 beforeOthers = ctx.computeOnClient(mc -> mc.player.position());
			chat(ctx, "ander", "!back 2", 0);
			ctx.waitTicks(25);
			check(ctx.computeOnClient(mc -> mc.player.position().distanceTo(beforeOthers)) < 0.1, "during a takeover others can't move the player");
			check(!feedTop(ctx).contains("ander"), "commands of others must not run during a takeover: " + feedTop(ctx));
			chat(ctx, "baas", "!back 1", 0);
			chat(ctx, "baas", "!unequip", 0);
			ctx.waitTicks(25);
			check(ctx.computeOnClient(mc -> mc.player.position().distanceTo(beforeOthers)) > 0.5, "the viewer who took over can move the player");
			check(feedTop(ctx).startsWith("!unequip") && feedTop(ctx).contains("baas [overname]"), "paid commands are free during a takeover: " + feedTop(ctx));

			// Others who cheer enough wait in line; the same viewer cheering again gets more time (max 30 minutes).
			chat(ctx, "tweede", "Cheer2000", 2000);
			chat(ctx, "baas", "Cheer1000", 1000);
			ctx.runOnClient(mc -> TwitchPlaysClient.get().onBitsUsed(new EventSubClient.BitsUse("pu", "PowerUpper", 1500, "power_up")));
			ctx.runOnClient(mc -> TwitchPlaysClient.get().onBitsUsed(new EventSubClient.BitsUse("dubbel", "Dubbel", 5000, "cheer")));
			check(ctx.computeOnClient(mc -> TwitchPlaysClient.get().takeover.waiting()) == 2, "the second cheerer and the Power-up wait; a cheer via EventSub is not counted twice");
			long twentyMinutes = ctx.computeOnClient(mc -> TwitchPlaysClient.get().takeover.current().remainingMs());
			check(twentyMinutes > 19.9 * 60_000, "cheering again adds 10 minutes, got " + twentyMinutes);
			// The 999 bits alert comes first; wait for the takeover title.
			ctx.waitFor(mc -> TwitchPlaysClient.get().alerts.queued() == 0, 300);
			ctx.waitTicks(30);
			ctx.takeScreenshot("twitchplays-takeover");

			// The clock stops while control is off.
			ctx.runOnClient(mc -> TwitchPlaysClient.get().setEnabled(false));
			long pausedAt = ctx.computeOnClient(mc -> TwitchPlaysClient.get().takeover.current().remainingMs());
			ctx.waitTicks(30);
			check(ctx.computeOnClient(mc -> TwitchPlaysClient.get().takeover.current().remainingMs()) == pausedAt, "the takeover clock must wait while control is off");
			ctx.runOnClient(mc -> TwitchPlaysClient.get().setEnabled(true));

			// A timeout ends the turn, a moderator can stop the next one, then everyone plays again.
			ctx.runOnClient(mc -> TwitchPlaysClient.get().onUserRemoved("baas"));
			check(ctx.computeOnClient(mc -> TwitchPlaysClient.get().takeover.controls("tweede")), "after a timeout the next in line gets control");
			ctx.runOnClient(mc -> TwitchPlaysClient.get().onChat(new ChatMessage("77", "mod", "Mod", "!takeover stop", 0, true, false)));
			check(ctx.computeOnClient(mc -> TwitchPlaysClient.get().takeover.controls("pu")), "!takeover stop by a moderator goes to the next in line");
			ctx.runOnClient(mc -> TwitchPlaysClient.get().stopTakeover());
			check(ctx.computeOnClient(mc -> !TwitchPlaysClient.get().takeover.active()), "the queue should be empty now");
			// Terrain at this point of the test can block walking, so check that the command ran.
			chat(ctx, "ander2", "!forward 1", 0);
			ctx.waitTicks(3);
			check(feedTop(ctx).equals("!forward  ander2"), "after the takeover everyone can play again: " + feedTop(ctx));

			// Crafting: small recipes in the 2x2 grid of the inventory, without a crafting table.
			server.runCommand("clear @a");
			server.runCommand("recipe give @a *");
			server.runCommand("give @a minecraft:oak_log 4");
			server.runCommand("give @a minecraft:coal 2");
			server.runCommand("give @a minecraft:beef 3");
			server.runCommand("give @a minecraft:raw_iron 1");
			server.runCommand("give @a minecraft:diamond 3");
			world.getConnection().waitForClientboundPackets();
			ctx.waitTicks(10);
			chat(ctx, "smid1", "!craft planks 2", 0);
			ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen && mc.player.inventoryMenu.getResultSlot().hasItem(), 20);
			ctx.waitTicks(2);
			ctx.takeScreenshot("twitchplays-craft-inventory");
			ctx.waitFor(mc -> mc.gui.screen() == null && !TwitchPlaysClient.get().controller.workshop.busy(), 100);
			check(count(ctx, Items.OAK_PLANKS) == 8 && count(ctx, Items.OAK_LOG) == 2, "!craft planks 2 should turn 2 logs into 8 planks: " + feedTop(ctx)
					+ " planks=" + count(ctx, Items.OAK_PLANKS) + " logs=" + count(ctx, Items.OAK_LOG));
			// The test game is in English; a plural finds the item too.
			chat(ctx, "smid2", "!maak sticks", 0);
			ctx.waitTicks(3);
			ctx.waitFor(mc -> !TwitchPlaysClient.get().controller.workshop.busy(), 100);
			check(count(ctx, Items.STICK) == 4 && count(ctx, Items.OAK_PLANKS) == 6, "!maak sticks should make 4 sticks from 2 planks: " + feedTop(ctx)
					+ " sticks=" + count(ctx, Items.STICK) + " planks=" + count(ctx, Items.OAK_PLANKS));

			// Big recipes need a crafting table within reach.
			chat(ctx, "smid3", "!craft wooden pickaxe", 0);
			ctx.waitTicks(3);
			check(feedTop(ctx).contains("geen werkbank"), "without a crafting table, say so: " + feedTop(ctx));
			BlockPos feet = ctx.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos table = feet.east(2);
			BlockPos furnace = feet.west(2);
			BlockPos smoker = feet.north(2);
			server.runCommand("setblock " + table.getX() + " " + table.getY() + " " + table.getZ() + " minecraft:crafting_table");
			server.runCommand("setblock " + furnace.getX() + " " + furnace.getY() + " " + furnace.getZ() + " minecraft:furnace");
			server.runCommand("setblock " + smoker.getX() + " " + smoker.getY() + " " + smoker.getZ() + " minecraft:smoker");
			world.getConnection().waitForClientboundPackets();
			ctx.waitTicks(5);
			chat(ctx, "smid4", "!craft wooden pickaxe", 0);
			ctx.waitFor(mc -> mc.player.containerMenu instanceof CraftingMenu menu && menu.getResultSlot().hasItem(), 40);
			ctx.waitTicks(2);
			ctx.takeScreenshot("twitchplays-craft-table");
			ctx.waitFor(mc -> mc.gui.screen() == null && !TwitchPlaysClient.get().controller.workshop.busy(), 100);
			check(count(ctx, Items.WOODEN_PICKAXE) == 1 && count(ctx, Items.OAK_PLANKS) == 3 && count(ctx, Items.STICK) == 2,
					"!craft wooden pickaxe should use the crafting table: " + feedTop(ctx));

			// Diamonds are paid: free chat can't, 100 bits can.
			chat(ctx, "smid5", "!craft diamond pickaxe", 0);
			ctx.waitTicks(3);
			check(feedTop(ctx).contains("duur") && count(ctx, Items.DIAMOND) == 3, "free chat must not craft with diamonds: " + feedTop(ctx));
			chat(ctx, "smid6", "Cheer100 !craft diamond pickaxe", 100);
			ctx.waitTicks(3);
			ctx.waitFor(mc -> !TwitchPlaysClient.get().controller.workshop.busy(), 100);
			check(count(ctx, Items.DIAMOND_PICKAXE) == 1 && count(ctx, Items.DIAMOND) == 0, "Cheer100 may craft with diamonds: " + feedTop(ctx));

			// Smelting: meat goes in the smoker (faster than the furnace), with coal as fuel.
			chat(ctx, "kok", "!bak vlees 3", 0);
			ctx.waitFor(mc -> mc.player.containerMenu instanceof AbstractFurnaceMenu menu && menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).hasItem(), 60);
			ctx.waitTicks(6);
			ctx.takeScreenshot("twitchplays-smoker");
			ctx.waitFor(mc -> !TwitchPlaysClient.get().controller.workshop.busy(), 100);
			ItemStack smokerInput = slot(server, smoker, 0);
			check(smokerInput.is(Items.BEEF) && count(ctx, Items.BEEF) == 0, "!bak vlees should put all beef in the smoker: " + feedTop(ctx) + " input=" + smokerInput);
			// The smoker burns the coal right away, so it is gone from the fuel slot too.
			check(count(ctx, Items.COAL) == 1, "one coal is enough for 3 beef: coal left=" + count(ctx, Items.COAL) + " fuel=" + slot(server, smoker, 1));
			chat(ctx, "smelter", "!smelt raw iron", 0);
			ctx.waitTicks(3);
			ctx.waitFor(mc -> !TwitchPlaysClient.get().controller.workshop.busy(), 100);
			check(slot(server, furnace, 0).is(Items.RAW_IRON) && count(ctx, Items.COAL) == 0, "raw iron goes in the furnace with the last coal: " + feedTop(ctx)
					+ " input=" + slot(server, furnace, 0) + " coal left=" + count(ctx, Items.COAL));
			// A smoker cooks one item per 5 seconds.
			ctx.waitTicks(320);
			chat(ctx, "ophaler", "!ophalen", 0);
			ctx.waitTicks(3);
			ctx.waitFor(mc -> !TwitchPlaysClient.get().controller.workshop.busy(), 300);
			check(count(ctx, Items.COOKED_BEEF) == 3, "!ophalen should take the 3 steaks out: " + feedTop(ctx) + " steaks=" + count(ctx, Items.COOKED_BEEF));
			check(count(ctx, Items.IRON_INGOT) == 1, "!ophalen also empties the furnace: ingots=" + count(ctx, Items.IRON_INGOT));
			chat(ctx, "ophaler2", "!ophalen", 0);
			ctx.waitTicks(3);
			ctx.waitFor(mc -> !TwitchPlaysClient.get().controller.workshop.busy(), 300);
			check(feedTop(ctx).contains("nog niets klaar"), "nothing left to collect: " + feedTop(ctx));
		}
	}

	private static int count(ClientGameTestContext ctx, Item item) {
		return ctx.computeOnClient(mc -> mc.player.getInventory().countItem(item));
	}

	private static ItemStack slot(TestServerContext server, BlockPos pos, int slot) {
		return server.computeOnServer(s -> ((Container) s.overworld().getBlockEntity(pos)).getItem(slot).copy());
	}

	private static void chat(ClientGameTestContext ctx, String user, String text, int bits) {
		ctx.runOnClient(mc -> TwitchPlaysClient.get().onChat(new ChatMessage(user, user, user, text, bits, false, false)));
	}

	private static String feedTop(ClientGameTestContext ctx) {
		return ctx.computeOnClient(TwitchPlaysClientGameTest::feedTop);
	}

	private static String feedTop(Minecraft mc) {
		TwitchPlays.FeedEntry top = TwitchPlaysClient.get().feed().peekFirst();
		return top == null ? "" : top.text();
	}

	private static int selectedSlot(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
	}

	private static boolean wearsChestplate(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> !mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
