package nl.zwollerob.twitchplays.game;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nl.zwollerob.twitchplays.command.ItemMatcher;
import nl.zwollerob.twitchplays.command.ParsedCommand;
import nl.zwollerob.twitchplays.mixin.KeyMappingAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays commands as if someone pressed the real keys, so Minecraft handles them exactly
 * like player input. Runs on the client thread at the start of every tick.
 */
public final class Controller {
	private static final float MAX_TURN_PER_TICK = 20;
	private static final int EAT_TICKS = 40;
	private static final int INVENTORY_SHOW_TICKS = 60;
	/** Ticks from jumping off flat ground until landing again. */
	private static final int JUMP_TICKS = 12;
	/** Ticks per block when pillaring: you land on the new block a little sooner than on flat ground. */
	private static final int PILLAR_TICKS = 11;
	/** Ticks per block when bridging: sneaking walks about 1.3 blocks per second. */
	private static final int BRIDGE_TICKS = 16;
	/** How far down the player looks while bridging, so viewers see the blocks appear. */
	private static final float BRIDGE_PITCH = 70;
	/** Let go of forward this far before the middle of the target block; the player slides the rest. */
	private static final double WALK_STOP_DISTANCE = 0.25;

	private record Click(KeyMapping key, int delay) {
	}

	/** Walking to the middle of a block straight ahead, for !step. */
	private static final class Walk {
		final Direction direction;
		final double targetX;
		final double targetZ;
		int ticksLeft;
		int stuckTicks;

		Walk(Direction direction, BlockPos target, int ticksLeft) {
			this.direction = direction;
			this.targetX = target.getX() + 0.5;
			this.targetZ = target.getZ() + 0.5;
			this.ticksLeft = ticksLeft;
		}
	}

	/** A step of a combination (pillar, jump forward) that runs after a number of ticks. */
	private record Step(int delay, Runnable action) {
	}

	/** Keys chat is holding, with the ticks left. */
	private final Map<KeyMapping, Integer> holds = new HashMap<>();
	private final List<Click> clicks = new ArrayList<>();
	private final List<Step> steps = new ArrayList<>();
	private float pendingYaw;
	private float pendingPitch;
	/** The block-exact walk of !step, or null. */
	private Walk walk;
	/** Ticks left before the inventory that chat opened with !inv closes again; 0 when there is none. */
	private int inventoryTicks;
	/** Crafting and smelting, which work in a screen of their own. */
	public final Workshop workshop = new Workshop();
	/** Keep the player afloat in water and lava (set every tick from the config, only while chat has control). */
	public boolean floating;

	/** Whether chat input can be applied right now (in a world, no menu open, not paused). */
	public static boolean canControl(Minecraft mc) {
		return mc.player != null && mc.gameMode != null && mc.gui.screen() == null && !mc.isPaused();
	}

	public void tick(Minecraft mc) {
		// Before the check below: the workshop runs while its own screen is open.
		workshop.tick(mc);
		if (inventoryTicks > 0 && --inventoryTicks == 0) {
			closeChatInventory(mc);
		}
		if (!canControl(mc)) {
			// Opening a menu releases all keys in vanilla too; don't keep holding them behind it.
			stopAll();
			return;
		}
		LocalPlayer player = mc.player;

		// Steps can press keys and schedule clicks, so they run before the keys and clicks below.
		List<Step> due = new ArrayList<>();
		List<Step> waiting = new ArrayList<>();
		for (Step step : steps) {
			if (step.delay() <= 0) {
				due.add(step);
			} else {
				waiting.add(new Step(step.delay() - 1, step.action()));
			}
		}
		steps.clear();
		steps.addAll(waiting);
		due.forEach(step -> step.action().run());

		if (walk != null) {
			tickWalk(mc.options, player);
		}

		// Chat can't keep space pressed, so the player would sink and drown. Hold jump while in water or lava,
		// like a player does to stay up; sneaking (chat's !sneak or the streamer's own key) still dives.
		if (floating && player.isInLiquid() && !player.isPassenger() && !player.getAbilities().flying && !mc.options.keyShift.isDown()) {
			hold(mc.options.keyJump, 2);
		}

		Iterator<Map.Entry<KeyMapping, Integer>> it = holds.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<KeyMapping, Integer> entry = it.next();
			if (entry.getValue() <= 0) {
				release(entry.getKey());
				it.remove();
			} else {
				entry.getKey().setDown(true);
				entry.setValue(entry.getValue() - 1);
			}
		}

		List<Click> next = new ArrayList<>();
		for (Click click : clicks) {
			if (click.delay() <= 0) {
				KeyMappingAccessor accessor = (KeyMappingAccessor) click.key();
				accessor.twitchplays$setClickCount(accessor.twitchplays$getClickCount() + 1);
			} else {
				next.add(new Click(click.key(), click.delay() - 1));
			}
		}
		clicks.clear();
		clicks.addAll(next);

		if (pendingYaw != 0) {
			float step = Mth.clamp(pendingYaw, -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
			player.setYRot(player.getYRot() + step);
			pendingYaw -= step;
		}
		if (pendingPitch != 0) {
			float step = Mth.clamp(pendingPitch, -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
			player.setXRot(Mth.clamp(player.getXRot() + step, -90, 90));
			pendingPitch -= step;
		}
	}

	/**
	 * Runs a command. Returns null when it worked, or a short reason why it could not.
	 * Paid means bits, channel points or a takeover: only then may crafting use paid ingredients.
	 */
	public String execute(ParsedCommand command, Minecraft mc, boolean paid) {
		if (!canControl(mc)) {
			return "niet in de wereld of menu open";
		}
		Options o = mc.options;
		LocalPlayer player = mc.player;
		Inventory inventory = player.getInventory();
		switch (command.action()) {
			case FORWARD -> hold(o.keyUp, command.ticks());
			case BACK -> hold(o.keyDown, command.ticks());
			case LEFT -> hold(o.keyLeft, command.ticks());
			case RIGHT -> hold(o.keyRight, command.ticks());
			case JUMP -> hold(o.keyJump, command.ticks());
			case SNEAK -> hold(o.keyShift, command.ticks());
			case SPRINT -> {
				hold(o.keySprint, command.ticks());
				hold(o.keyUp, command.ticks());
			}
			case LOOK_LEFT -> pendingYaw -= (float) command.amount();
			case LOOK_RIGHT -> pendingYaw += (float) command.amount();
			case LOOK_UP -> pendingPitch -= (float) command.amount();
			case LOOK_DOWN -> pendingPitch += (float) command.amount();
			case TURN -> pendingYaw += 180;
			case NORTH -> face(player, Direction.NORTH);
			case EAST -> face(player, Direction.EAST);
			case SOUTH -> face(player, Direction.SOUTH);
			case WEST -> face(player, Direction.WEST);
			case STEP -> step(player, command.count());
			case JUMP_FORWARD -> jumpForward(o, command.count(), false);
			case SPRINT_JUMP -> jumpForward(o, command.count(), true);
			case PILLAR -> {
				String error = pillar(mc, player, command.count());
				if (error != null) {
					return error;
				}
			}
			case BRIDGE -> {
				String error = bridge(mc, player, command.count());
				if (error != null) {
					return error;
				}
			}
			case ATTACK -> clicks(o.keyAttack, command.count(), 6);
			case MINE -> hold(o.keyAttack, command.ticks());
			case USE -> clicks(o.keyUse, command.count(), 5);
			case HOLD_USE -> hold(o.keyUse, command.ticks());
			case EAT -> {
				int slot = findFood(inventory);
				if (slot < 0) {
					return "geen eten in de hotbar";
				}
				inventory.setSelectedSlot(slot);
				hold(o.keyUse, EAT_TICKS);
			}
			case SLOT -> clicks(o.keyHotbarSlots[command.count() - 1], 1, 0);
			case NEXT_SLOT -> inventory.setSelectedSlot((inventory.getSelectedSlot() + 1) % Inventory.getSelectionSize());
			case PREV_SLOT -> inventory.setSelectedSlot((inventory.getSelectedSlot() + Inventory.getSelectionSize() - 1) % Inventory.getSelectionSize());
			case GRAB -> {
				String error = grab(mc, player, command.text());
				if (error != null) {
					return error;
				}
			}
			case INVENTORY -> {
				// Pressing the real inventory key lets vanilla pick the right screen (creative, riding a horse, ...).
				clicks(o.keyInventory, 1, 0);
				inventoryTicks = INVENTORY_SHOW_TICKS;
			}
			case DROP -> {
				if (player.isSpectator()) {
					return "spectator";
				}
				for (int i = 0; i < command.count(); i++) {
					mc.gameMode.dropItem(player, false);
				}
			}
			case DROP_STACK -> {
				if (player.isSpectator()) {
					return "spectator";
				}
				mc.gameMode.dropItem(player, true);
			}
			case SWAP -> clicks(o.keySwapOffhand, 1, 0);
			case SHUFFLE -> shuffle(mc, player);
			case UNEQUIP -> {
				if (!unequip(mc, player)) {
					return "geen armor aan";
				}
			}
			case CRAFT, SMELT, COLLECT -> {
				// Stand still while the screen is open; this also stops sneaking, which would place blocks instead.
				stopAll();
				String error = workshop.start(mc, command, paid);
				if (error != null) {
					return error;
				}
			}
			case STOP -> stopAll();
		}
		return null;
	}

	public void stopAll() {
		for (KeyMapping key : holds.keySet()) {
			release(key);
		}
		holds.clear();
		clicks.clear();
		steps.clear();
		walk = null;
		pendingYaw = 0;
		pendingPitch = 0;
	}

	private void hold(KeyMapping key, int ticks) {
		// Several chatters holding the same key extend it instead of stacking.
		holds.merge(key, ticks, Math::max);
		key.setDown(true);
	}

	private void later(int delay, Runnable action) {
		steps.add(new Step(delay, action));
	}

	/** Looks exactly north, east, south or west, straight ahead (not up or down). Returns the ticks the turn takes. */
	private int face(LocalPlayer player, Direction direction) {
		pendingYaw = Mth.wrapDegrees(direction.toYRot() - player.getYRot());
		pendingPitch = -player.getXRot();
		return (int) Math.ceil(Math.max(Math.abs(pendingYaw), Math.abs(pendingPitch)) / MAX_TURN_PER_TICK) + 1;
	}

	/** Walks exactly this many blocks in the direction the player faces most, ending in the middle of a block. */
	private void step(LocalPlayer player, int blocks) {
		Direction direction = player.getDirection();
		BlockPos target = player.blockPosition().relative(direction, blocks);
		// Turning first keeps the walk straight; walking speed is about 4 blocks per second.
		int turnTicks = face(player, direction);
		later(turnTicks, () -> walk = new Walk(direction, target, blocks * 8 + 20));
	}

	/** Holds forward until the middle of the target block, steering sideways, and stops when something is in the way. */
	private void tickWalk(Options o, LocalPlayer player) {
		Walk w = walk;
		double dx = w.targetX - player.getX();
		double dz = w.targetZ - player.getZ();
		double ahead = dx * w.direction.getStepX() + dz * w.direction.getStepZ();
		Direction left = w.direction.getCounterClockWise();
		double sideways = dx * left.getStepX() + dz * left.getStepZ();
		w.stuckTicks = player.horizontalCollision ? w.stuckTicks + 1 : 0;
		if (ahead <= WALK_STOP_DISTANCE || --w.ticksLeft <= 0 || w.stuckTicks > 3) {
			walk = null;
			return;
		}
		hold(o.keyUp, 1);
		if (sideways > 0.1) {
			hold(o.keyLeft, 1);
		} else if (sideways < -0.1) {
			hold(o.keyRight, 1);
		}
	}

	/** Walks (or sprints) forward while jumping: one jump per count, landing before the next. */
	private void jumpForward(Options o, int jumps, boolean sprint) {
		int moveTicks = jumps * JUMP_TICKS + 4;
		hold(o.keyUp, moveTicks);
		if (sprint) {
			hold(o.keySprint, moveTicks);
		}
		// Holding jump makes vanilla jump again on every landing; release it during the last jump.
		hold(o.keyJump, (jumps - 1) * JUMP_TICKS + 3);
	}

	/**
	 * Builds a pillar under the player, like a player holding space and right-clicking while looking
	 * straight down: per block, jump, and while in the air place a block under the feet.
	 */
	private String pillar(Minecraft mc, LocalPlayer player, int blocks) {
		if (!holdBlock(mc, player)) {
			return "geen blokken";
		}
		Options o = mc.options;
		float startPitch = player.getXRot();
		pendingPitch = 90 - startPitch;
		int turnTicks = (int) Math.ceil(Math.abs(pendingPitch) / MAX_TURN_PER_TICK) + 1;
		for (int i = 0; i < blocks; i++) {
			int start = turnTicks + i * PILLAR_TICKS;
			later(start, () -> hold(o.keyJump, 2));
			// Place once the feet are a block up; extra clicks after that find no room and do nothing.
			later(start + 4, () -> clicks(o.keyUse, 1, 0));
			later(start + 6, () -> clicks(o.keyUse, 1, 0));
			later(start + 8, () -> clicks(o.keyUse, 1, 0));
		}
		// Look the way the player looked before.
		later(turnTicks + blocks * PILLAR_TICKS, () -> pendingPitch = startPitch - player.getXRot());
		return null;
	}

	/**
	 * Builds a bridge straight ahead (north, east, south or west, whichever the player faces most):
	 * per block, place a block against the front of the one below, then sneak onto it. Sneaking the
	 * whole time keeps the player from walking off the edge, also when the blocks run out.
	 */
	private String bridge(Minecraft mc, LocalPlayer player, int blocks) {
		if (!holdBlock(mc, player)) {
			return "geen blokken";
		}
		BlockPos floor = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
		if (!player.onGround() || mc.level.getBlockState(floor).canBeReplaced()) {
			return "sta niet op een blok";
		}
		Options o = mc.options;
		Direction direction = player.getDirection();
		float startPitch = player.getXRot();
		pendingYaw = Mth.wrapDegrees(direction.toYRot() - player.getYRot());
		pendingPitch = BRIDGE_PITCH - startPitch;
		int turnTicks = (int) Math.ceil(Math.max(Math.abs(pendingYaw), Math.abs(pendingPitch)) / MAX_TURN_PER_TICK) + 1;
		int endTick = turnTicks + blocks * BRIDGE_TICKS;
		hold(o.keyShift, endTick + 2);
		for (int i = 0; i < blocks; i++) {
			BlockPos from = floor.relative(direction, i);
			int start = turnTicks + i * BRIDGE_TICKS;
			later(start, () -> placeAgainst(mc, player, from, direction));
			later(start + 2, () -> hold(o.keyUp, BRIDGE_TICKS - 2));
		}
		later(endTick, () -> pendingPitch = startPitch - player.getXRot());
		return null;
	}

	/** Places the block in hand against one side of a block, like right-clicking that side. */
	private static void placeAgainst(Minecraft mc, LocalPlayer player, BlockPos against, Direction side) {
		if (!mc.level.getBlockState(against.relative(side)).canBeReplaced()) {
			// Something is already there: just walk on.
			return;
		}
		if (!isFullBlock(player.getMainHandItem()) && !holdBlock(mc, player)) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		Vec3 hit = Vec3.atCenterOf(against).add(side.getUnitVec3().scale(0.5));
		InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, side, against, false));
		if (result.consumesAction()) {
			player.swing(InteractionHand.MAIN_HAND, stack.getInteractAnimation(), false);
		}
	}

	/** Makes sure a full block (not a torch or slab) is in hand: from the hand, the hotbar or the inventory. */
	private static boolean holdBlock(Minecraft mc, LocalPlayer player) {
		Inventory inventory = player.getInventory();
		if (isFullBlock(inventory.getItem(inventory.getSelectedSlot()))) {
			return true;
		}
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (!isFullBlock(inventory.getItem(slot))) {
				continue;
			}
			if (Inventory.isHotbarSlot(slot)) {
				inventory.setSelectedSlot(slot);
			} else {
				mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, inventory.getSelectedSlot(), ContainerInput.SWAP, player);
			}
			return true;
		}
		return false;
	}

	private static boolean isFullBlock(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock().defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	private void clicks(KeyMapping key, int count, int spacing) {
		for (int i = 0; i < count; i++) {
			clicks.add(new Click(key, i * spacing));
		}
	}

	/** Lets go of a key, unless the streamer is physically holding it. */
	private static void release(KeyMapping key) {
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		boolean physical = bound.getType() == InputConstants.Type.KEYBOARD
				&& bound.getValue() != InputConstants.UNKNOWN.getValue()
				&& InputConstants.isKeyDown(bound.getValue());
		key.setDown(physical);
	}

	private static int findFood(Inventory inventory) {
		int selected = inventory.getSelectedSlot();
		if (inventory.getItem(selected).has(DataComponents.FOOD)) {
			return selected;
		}
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (inventory.getItem(slot).has(DataComponents.FOOD)) {
				return slot;
			}
		}
		return -1;
	}

	/** Swaps random inventory slots with random hotbar slots, like pressing 1-9 over items. */
	private static void shuffle(Minecraft mc, LocalPlayer player) {
		InventoryMenu menu = player.inventoryMenu;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < 20; i++) {
			int slot = random.nextInt(InventoryMenu.INV_SLOT_START, InventoryMenu.USE_ROW_SLOT_END);
			int hotbarButton = random.nextInt(Inventory.getSelectionSize());
			mc.gameMode.handleContainerInput(menu.containerId, slot, hotbarButton, ContainerInput.SWAP, player);
		}
	}

	/**
	 * Puts the item that best matches chat's text in the player's hand: selects it when it is in the
	 * hotbar, otherwise swaps it from the inventory into the selected hotbar slot (like pressing 1-9 over it).
	 */
	private static String grab(Minecraft mc, LocalPlayer player, String query) {
		Inventory inventory = player.getInventory();
		int best = -1;
		int bestRank = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			int score = ItemMatcher.score(query, BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getHoverName().getString());
			// On equal names, prefer the hotbar: then nothing needs to move.
			int rank = score * 2 + (Inventory.isHotbarSlot(slot) ? 1 : 0);
			if (score > 0 && rank > bestRank) {
				best = slot;
				bestRank = rank;
			}
		}
		if (best < 0) {
			return "niet gevonden";
		}
		if (Inventory.isHotbarSlot(best)) {
			inventory.setSelectedSlot(best);
		} else {
			// Main inventory slots 9-35 have the same number in the inventory menu.
			mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, best, inventory.getSelectedSlot(), ContainerInput.SWAP, player);
		}
		return null;
	}

	private static void closeChatInventory(Minecraft mc) {
		Screen screen = mc.gui.screen();
		if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
			screen.onClose();
		}
	}

	/** Shift-clicks the armor slots, so armor moves into the inventory when there is room. */
	private static boolean unequip(Minecraft mc, LocalPlayer player) {
		InventoryMenu menu = player.inventoryMenu;
		boolean any = false;
		for (int slot = InventoryMenu.ARMOR_SLOT_START; slot < InventoryMenu.ARMOR_SLOT_END; slot++) {
			if (menu.getSlot(slot).hasItem()) {
				mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
				any = true;
			}
		}
		return any;
	}
}
