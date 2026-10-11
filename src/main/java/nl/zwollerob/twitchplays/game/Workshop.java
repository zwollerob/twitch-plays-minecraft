package nl.zwollerob.twitchplays.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nl.zwollerob.twitchplays.command.Action;
import nl.zwollerob.twitchplays.command.ItemMatcher;
import nl.zwollerob.twitchplays.command.ParsedCommand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Crafting and smelting for chat, done the way the recipe book does it: Minecraft itself moves the
 * ingredients from the inventory into the grid, so only real items are used and nothing comes for free.
 * Small recipes use the 2x2 grid of the inventory, big ones a crafting table within reach. Smelting
 * uses a furnace, smoker or blast furnace within reach and adds fuel from the inventory.
 */
public final class Workshop {
	/** Ticks a screen stays open after the work, so viewers see what happened. */
	private static final int SHOW_TICKS = 15;
	/** Ticks the filled crafting grid stays in view before the result is taken. */
	private static final int GRID_TICKS = 10;
	/** Ticks to wait for the server (a menu opening, items arriving) before giving up. */
	private static final int TIMEOUT_TICKS = 40;
	/** Ticks the furnace input must stay the same before adding fuel: placed items can arrive one by one. */
	private static final int SETTLE_TICKS = 3;
	private static final int MAX_STATIONS = 4;
	private static final List<Block> FURNACES = List.of(Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE);
	/** Fuel chat may burn, best first. Blaze rods and lava buckets are left alone: they are worth more for other things. */
	private static final List<Predicate<ItemStack>> FUEL_ORDER = List.of(
			s -> s.is(Items.CHARCOAL),
			s -> s.is(Items.COAL),
			s -> s.is(Items.DRIED_KELP_BLOCK),
			s -> s.is(ItemTags.PLANKS),
			s -> s.is(ItemTags.LOGS_THAT_BURN),
			s -> s.is(Items.STICK),
			s -> s.is(Items.COAL_BLOCK));

	/** Item ids (e.g. "diamond") that make a recipe paid: free chat can't use it. */
	public List<String> paidIngredients = List.of();
	/** Shown when free chat asks for a paid recipe. */
	public String paidHint = "alleen met bits of punten";
	/** Problems that show up after the command started, for the overlay. */
	public Consumer<String> onProblem = text -> {
	};

	private enum Kind { CRAFT, SMELT, COLLECT }

	private enum Phase { OPEN, WAIT_OPEN, WORK, PLACED, CLEANUP, SHOW }

	/** A recipe chat asked for. Small recipes fit the 2x2 grid of the inventory and need no station. */
	private record Choice(RecipeDisplayEntry entry, List<ItemStack> inputs, boolean small, List<BlockPos> stations,
			boolean craftable, boolean expensive) {
	}

	private static final class Job {
		final Kind kind;
		final String label;
		final RecipeDisplayEntry recipe;
		/** Smelting: the items the recipe accepts, to see whether a furnace is busy with something else. */
		final List<ItemStack> inputs;
		final int count;
		/** Blocks to open, nearest first; empty means the 2x2 grid of the inventory. */
		final List<BlockPos> stations;
		int station;
		Phase phase = Phase.OPEN;
		int ticks;
		AbstractContainerMenu menu;
		int before;
		int lastCount = -1;
		int stable;
		int collected;

		Job(Kind kind, String label, RecipeDisplayEntry recipe, List<ItemStack> inputs, int count, List<BlockPos> stations) {
			this.kind = kind;
			this.label = label;
			this.recipe = recipe;
			this.inputs = inputs;
			this.count = count;
			this.stations = stations;
		}
	}

	private Job job;

	public boolean busy() {
		return job != null;
	}

	/** Starts !craft, !smelt or !collect. Returns null when it started, or a short reason why it can't. */
	public String start(Minecraft mc, ParsedCommand command, boolean paid) {
		if (job != null) {
			return "even geduld, nog bezig";
		}
		LocalPlayer player = mc.player;
		if (player.isSpectator()) {
			return "spectator";
		}
		String label = command.canonical();
		if (command.action() == Action.COLLECT) {
			List<BlockPos> furnaces = nearby(mc, player, FURNACES);
			if (furnaces.isEmpty()) {
				return "geen oven in de buurt";
			}
			job = new Job(Kind.COLLECT, label, null, List.of(), 0, furnaces);
			return null;
		}
		boolean smelt = command.action() == Action.SMELT;
		Choice choice = choose(mc, player, command.text(), smelt, paid);
		if (choice == null) {
			return "onbekend recept";
		}
		if (!choice.craftable()) {
			return "mist ingrediënten";
		}
		if (choice.expensive() && !paid) {
			return paidHint;
		}
		if (!choice.small() && choice.stations().isEmpty()) {
			return smelt ? "geen oven in de buurt" : "geen werkbank in de buurt";
		}
		job = new Job(smelt ? Kind.SMELT : Kind.CRAFT, label, choice.entry(), choice.inputs(), command.count(), choice.stations());
		return null;
	}

	/** Whether free chat may not run this command because the recipe uses a paid ingredient. */
	public boolean needsPayment(Minecraft mc, ParsedCommand command) {
		Action action = command.action();
		if (mc.player == null || mc.level == null || action != Action.CRAFT && action != Action.SMELT) {
			return false;
		}
		Choice choice = choose(mc, mc.player, command.text(), action == Action.SMELT, false);
		return choice != null && choice.expensive();
	}

	/**
	 * Finds the recipe that best matches chat's text, among the recipes in the player's recipe book.
	 * Crafting goes by the name of the result; smelting also by the name of what goes in ("!bak vlees").
	 */
	private Choice choose(Minecraft mc, LocalPlayer player, String text, boolean smelt, boolean paid) {
		ContextMap context = SlotDisplayContext.fromLevel(mc.level);
		StackedItemContents contents = new StackedItemContents();
		player.getInventory().fillStackedContents(contents);
		ItemMatcher.FoodWord food = ItemMatcher.foodWord(text);
		Map<Block, List<BlockPos>> stationsByBlock = new HashMap<>();
		Choice best = null;
		long bestRank = 0;
		for (RecipeCollection collection : player.getRecipeBook().getCollections()) {
			for (RecipeDisplayEntry entry : collection.getRecipes()) {
				RecipeDisplay display = entry.display();
				List<SlotDisplay> ingredients;
				boolean small = false;
				List<ItemStack> inputs = List.of();
				if (!smelt && display instanceof ShapedCraftingRecipeDisplay shaped) {
					ingredients = shaped.ingredients();
					small = shaped.width() <= 2 && shaped.height() <= 2;
				} else if (!smelt && display instanceof ShapelessCraftingRecipeDisplay shapeless) {
					ingredients = shapeless.ingredients();
					small = ingredients.size() <= 4;
				} else if (smelt && display instanceof FurnaceRecipeDisplay furnace) {
					ingredients = List.of(furnace.ingredient());
					inputs = furnace.ingredient().resolveForStacks(context);
				} else {
					continue;
				}
				ItemStack result = display.result().resolveForFirstStack(context);
				int score = score(text, result);
				for (ItemStack input : inputs) {
					score = Math.max(score, score(text, input));
				}
				if (food != null && result.has(DataComponents.FOOD)
						&& inputs.stream().anyMatch(input -> input.has(DataComponents.FOOD) && ItemMatcher.inFoodGroup(food, id(input)))) {
					score = Math.max(score, 50);
				}
				if (score == 0) {
					continue;
				}
				List<BlockPos> stations = List.of();
				Block station = Blocks.AIR;
				if (!small) {
					station = Block.byItem(display.craftingStation().resolveForFirstStack(context).getItem());
					if (station == Blocks.AIR) {
						continue;
					}
					stations = stationsByBlock.computeIfAbsent(station, block -> nearby(mc, player, List.of(block)));
				}
				boolean craftable = entry.canCraft(contents);
				boolean expensive = ingredients.stream().anyMatch(slot -> isPaid(slot.resolveForStacks(context)));
				boolean reachable = small || !stations.isEmpty();
				boolean fast = station == Blocks.SMOKER || station == Blocks.BLAST_FURNACE;
				// Crafting goes by the name first ("stick" is not "sticky piston"); smelting by what is in the inventory first.
				long rank = (long) score * 32 + (craftable ? 16 : 0) + (reachable ? 8 : 0) + (!expensive || paid ? 4 : 0) + (fast ? 2 : 0) + 1;
				if (smelt && craftable) {
					rank += 1_000_000;
				}
				if (rank > bestRank) {
					bestRank = rank;
					best = new Choice(entry, inputs, small, stations, craftable, expensive);
				}
			}
		}
		return best;
	}

	private static int score(String text, ItemStack stack) {
		return stack.isEmpty() ? 0 : ItemMatcher.score(text, id(stack), stack.getHoverName().getString());
	}

	private static String id(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}

	/** An ingredient is paid when every item that fits it is in the paid list (so "any planks" never is). */
	private boolean isPaid(List<ItemStack> options) {
		if (options.isEmpty()) {
			return false;
		}
		return options.stream().allMatch(stack -> paidIngredients.stream()
				.anyMatch(paid -> paid.strip().toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "").equals(id(stack))));
	}

	/** Blocks of these kinds that the player can reach, nearest first. */
	private static List<BlockPos> nearby(Minecraft mc, LocalPlayer player, List<Block> blocks) {
		int radius = (int) Math.ceil(player.blockInteractionRange()) + 1;
		BlockPos center = player.blockPosition();
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			Block block = mc.level.getBlockState(pos).getBlock();
			if (blocks.contains(block) && player.isWithinBlockInteractionRange(pos, 0)) {
				found.add(pos.immutable());
			}
		}
		Vec3 eye = player.getEyePosition();
		found.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
		return found.size() > MAX_STATIONS ? List.copyOf(found.subList(0, MAX_STATIONS)) : found;
	}

	// ---- running the job, one step per tick ----

	public void tick(Minecraft mc) {
		Job j = job;
		if (j == null) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null) {
			job = null;
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		j.ticks++;
		try {
			step(mc, player, j);
		} catch (RuntimeException e) {
			job = null;
			throw e;
		}
	}

	private void step(Minecraft mc, LocalPlayer player, Job j) {
		switch (j.phase) {
			case OPEN -> open(mc, player, j);
			case WAIT_OPEN -> {
				AbstractContainerMenu open = player.containerMenu;
				if (open != player.inventoryMenu && (open instanceof CraftingMenu || open instanceof AbstractFurnaceMenu)) {
					j.menu = open;
					next(j, Phase.WORK);
				} else if (j.ticks > TIMEOUT_TICKS) {
					fail(player, j, j.kind == Kind.CRAFT ? "werkbank ging niet open" : "oven ging niet open");
				}
			}
			default -> {
				// The streamer can close the screen (Esc) at any time; then the job stops.
				if (mc.gui.screen() == null || player.containerMenu != j.menu) {
					job = null;
					problem(j, "afgebroken");
					return;
				}
				if (j.phase == Phase.SHOW) {
					if (j.ticks >= SHOW_TICKS) {
						nextStation(player, j);
					}
					return;
				}
				switch (j.kind) {
					case CRAFT -> craft(mc, player, j, (AbstractCraftingMenu) j.menu);
					case SMELT -> smelt(mc, player, j, (AbstractFurnaceMenu) j.menu);
					case COLLECT -> collect(mc, player, j, (AbstractFurnaceMenu) j.menu);
				}
			}
		}
	}

	/** Opens the inventory (2x2 grid) or the next station, like right-clicking it. */
	private void open(Minecraft mc, LocalPlayer player, Job j) {
		if (mc.gui.screen() != null) {
			fail(player, j, "menu open");
			return;
		}
		// Sneaking would use the item in hand on the block instead of opening it; the keys were just released.
		if (player.isShiftKeyDown() && j.ticks < 10) {
			return;
		}
		if (j.stations.isEmpty()) {
			mc.gui.setScreen(new InventoryScreen(player));
			j.menu = player.inventoryMenu;
			next(j, Phase.WORK);
			return;
		}
		BlockPos pos = j.stations.get(j.station);
		Vec3 center = Vec3.atCenterOf(pos);
		// Look at it, so viewers see where the screen comes from.
		player.lookAt(EntityAnchorArgument.Anchor.EYES, center);
		Direction side = Direction.getApproximateNearest(player.getEyePosition().subtract(center));
		Vec3 hit = center.add(side.getUnitVec3().scale(0.5));
		mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, side, pos, false));
		next(j, Phase.WAIT_OPEN);
	}

	private void craft(Minecraft mc, LocalPlayer player, Job j, AbstractCraftingMenu menu) {
		switch (j.phase) {
			case WORK -> {
				// Each click adds one set of ingredients to the grid, like clicking the recipe in the recipe book.
				for (int i = 0; i < j.count; i++) {
					mc.gameMode.handlePlaceRecipe(menu.containerId, j.recipe.id(), false);
				}
				next(j, Phase.PLACED);
			}
			case PLACED -> {
				Slot result = menu.getResultSlot();
				// Leave the filled grid in view for a moment, so viewers see the recipe.
				if (result.hasItem() && j.ticks >= GRID_TICKS) {
					// Shift-clicking the result crafts everything in the grid, as a player would.
					click(mc, player, menu, result.index, 0, ContainerInput.QUICK_MOVE);
					next(j, Phase.CLEANUP);
				} else if (j.ticks > TIMEOUT_TICKS) {
					fail(player, j, "lukte niet");
				}
			}
			case CLEANUP -> {
				if (j.ticks < 3) {
					return;
				}
				// Anything left in the grid (a full inventory) goes back; what doesn't fit drops when the screen closes.
				for (Slot slot : menu.getInputGridSlots()) {
					if (slot.hasItem()) {
						click(mc, player, menu, slot.index, 0, ContainerInput.QUICK_MOVE);
					}
				}
				next(j, Phase.SHOW);
			}
			default -> {
			}
		}
	}

	private void smelt(Minecraft mc, LocalPlayer player, Job j, AbstractFurnaceMenu menu) {
		Slot input = menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT);
		switch (j.phase) {
			case WORK -> {
				// Give the furnace's contents a moment to arrive after it opened.
				if (j.ticks < 2) {
					return;
				}
				takeResult(mc, player, menu);
				ItemStack current = input.getItem();
				if (!current.isEmpty() && j.inputs.stream().noneMatch(accepted -> ItemStack.isSameItem(accepted, current))) {
					// Busy with something else: leave it alone and try the next one.
					if (j.station + 1 < j.stations.size()) {
						player.closeContainer();
						j.menu = null;
						j.station++;
						next(j, Phase.OPEN);
					} else {
						fail(player, j, "oven is bezig met " + current.getHoverName().getString());
					}
					return;
				}
				if (menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT).getItem().isEmpty() && !menu.isLit() && bestFuel(player) == null) {
					fail(player, j, "geen brandstof");
					return;
				}
				j.before = current.getCount();
				for (int i = 0; i < j.count; i++) {
					mc.gameMode.handlePlaceRecipe(menu.containerId, j.recipe.id(), false);
				}
				next(j, Phase.PLACED);
			}
			case PLACED -> {
				int now = input.getItem().getCount();
				j.stable = now == j.lastCount ? j.stable + 1 : 0;
				j.lastCount = now;
				if (now != j.before && j.stable >= SETTLE_TICKS) {
					addFuel(mc, player, menu, j);
					next(j, Phase.SHOW);
				} else if (j.ticks > TIMEOUT_TICKS) {
					fail(player, j, "past niet in de oven");
				}
			}
			default -> {
			}
		}
	}

	private void collect(Minecraft mc, LocalPlayer player, Job j, AbstractFurnaceMenu menu) {
		if (j.phase == Phase.WORK && j.ticks >= 2) {
			if (takeResult(mc, player, menu)) {
				j.collected++;
			}
			next(j, Phase.SHOW);
		}
	}

	/** Shift-clicks what the furnace made into the inventory (this also gives the experience). */
	private static boolean takeResult(Minecraft mc, LocalPlayer player, AbstractFurnaceMenu menu) {
		if (!menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).hasItem()) {
			return false;
		}
		click(mc, player, menu, AbstractFurnaceMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
		return true;
	}

	/** Adds enough fuel from the inventory for everything in the furnace, when it has too little. */
	private void addFuel(Minecraft mc, LocalPlayer player, AbstractFurnaceMenu menu, Job j) {
		ItemStack inSlot = menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT).getItem();
		// Whatever is burning now still smelts something.
		double have = menu.isLit() ? 1 : 0;
		if (!inSlot.isEmpty()) {
			double per = itemsPerFuel(inSlot);
			if (per == 0) {
				// Fuel we don't know: assume the streamer put in enough.
				return;
			}
			have += per * inSlot.getCount();
		}
		double missing = menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem().getCount() - have;
		if (missing <= 0) {
			return;
		}
		Item fuel = inSlot.isEmpty() ? bestFuel(player) : inSlot.getItem();
		if (fuel == null) {
			problem(j, "te weinig brandstof");
			return;
		}
		int needed = (int) Math.ceil(missing / itemsPerFuel(new ItemStack(fuel)));
		for (Slot slot : menu.slots) {
			if (needed <= 0) {
				break;
			}
			if (!(slot.container instanceof Inventory) || !slot.getItem().is(fuel)) {
				continue;
			}
			int take = Math.min(needed, slot.getItem().getCount());
			// Pick the stack up, right-click one at a time into the fuel slot, put the rest back.
			click(mc, player, menu, slot.index, 0, ContainerInput.PICKUP);
			for (int i = 0; i < take; i++) {
				click(mc, player, menu, AbstractFurnaceMenu.FUEL_SLOT, 1, ContainerInput.PICKUP);
			}
			click(mc, player, menu, slot.index, 0, ContainerInput.PICKUP);
			needed -= take;
		}
		if (needed > 0) {
			problem(j, "te weinig brandstof voor alles");
		}
	}

	private static Item bestFuel(LocalPlayer player) {
		Inventory inventory = player.getInventory();
		for (Predicate<ItemStack> kind : FUEL_ORDER) {
			for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (!stack.isEmpty() && kind.test(stack)) {
					return stack.getItem();
				}
			}
		}
		return null;
	}

	/** How many items one of this fuel smelts (the same in a furnace, smoker and blast furnace); 0 for fuel not listed. */
	static double itemsPerFuel(ItemStack stack) {
		if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return 8;
		}
		if (stack.is(Items.COAL_BLOCK)) {
			return 80;
		}
		if (stack.is(Items.DRIED_KELP_BLOCK)) {
			return 20;
		}
		if (stack.is(Items.BLAZE_ROD)) {
			return 12;
		}
		if (stack.is(Items.LAVA_BUCKET)) {
			return 100;
		}
		if (stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS_THAT_BURN)) {
			return 1.5;
		}
		if (stack.is(Items.STICK)) {
			return 0.5;
		}
		return 0;
	}

	private static void click(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
		mc.gameMode.handleContainerInput(menu.containerId, slot, button, input, player);
	}

	private static void next(Job j, Phase phase) {
		j.phase = phase;
		j.ticks = 0;
	}

	/** Closes the screen and goes on with the next furnace (for !collect), or ends the job. */
	private void nextStation(LocalPlayer player, Job j) {
		player.closeContainer();
		j.menu = null;
		if (j.kind == Kind.COLLECT && j.station + 1 < j.stations.size()) {
			j.station++;
			next(j, Phase.OPEN);
			return;
		}
		job = null;
		if (j.kind == Kind.COLLECT && j.collected == 0) {
			problem(j, "nog niets klaar");
		}
	}

	private void fail(LocalPlayer player, Job j, String reason) {
		problem(j, reason);
		if (j.menu != null) {
			player.closeContainer();
		}
		job = null;
	}

	private void problem(Job j, String reason) {
		onProblem.accept(j.label + " (" + reason + ")");
	}
}
