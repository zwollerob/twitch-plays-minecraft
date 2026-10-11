package nl.zwollerob.twitchplays.command;

import java.util.List;

/**
 * Everything chat can make the player do. The first alias is the command's id,
 * which is also the name used in the config (paidOnly, bitsCost, rewards, disabled).
 */
public enum Action {
	FORWARD(Kind.SECONDS, 1, 5, "forward", "w", "f", "vooruit"),
	BACK(Kind.SECONDS, 1, 5, "back", "s", "b", "achteruit"),
	LEFT(Kind.SECONDS, 1, 5, "left", "a", "links"),
	RIGHT(Kind.SECONDS, 1, 5, "right", "d", "rechts"),
	JUMP(Kind.SECONDS, 0.3, 5, "jump", "j", "space", "spring"),
	SNEAK(Kind.SECONDS, 2, 10, "sneak", "crouch", "shift", "sluip"),
	SPRINT(Kind.SECONDS, 2, 5, "sprint", "run", "ren"),
	LOOK_LEFT(Kind.DEGREES, 45, 180, "lookleft", "look left", "ll", "kijklinks", "kijk links"),
	LOOK_RIGHT(Kind.DEGREES, 45, 180, "lookright", "look right", "lr", "kijkrechts", "kijk rechts"),
	LOOK_UP(Kind.DEGREES, 30, 90, "lookup", "look up", "lu", "kijkomhoog", "kijk omhoog"),
	LOOK_DOWN(Kind.DEGREES, 30, 90, "lookdown", "look down", "ld", "kijkomlaag", "kijk omlaag"),
	TURN(Kind.NONE, 0, 0, "turn", "turnaround", "draai", "omdraaien"),
	NORTH(Kind.NONE, 0, 0, "north", "noord", "look north", "kijk noord"),
	EAST(Kind.NONE, 0, 0, "east", "oost", "look east", "kijk oost"),
	SOUTH(Kind.NONE, 0, 0, "south", "zuid", "look south", "kijk zuid"),
	WEST(Kind.NONE, 0, 0, "west", "look west", "kijk west"),
	STEP(Kind.COUNT, 1, 20, "step", "stap", "walk", "steps", "stappen"),
	JUMP_FORWARD(Kind.COUNT, 1, 5, "jumpforward", "jf", "hop", "springvooruit", "spring vooruit"),
	SPRINT_JUMP(Kind.COUNT, 1, 5, "sprintjump", "sj", "renspring"),
	PILLAR(Kind.COUNT, 1, 5, "pillar", "tower", "toren", "pilaar", "bouwop"),
	BRIDGE(Kind.COUNT, 3, 10, "bridge", "brug", "bouwvooruit"),
	ATTACK(Kind.COUNT, 1, 10, "attack", "hit", "sla", "slaan"),
	MINE(Kind.SECONDS, 2, 10, "mine", "dig", "hak", "hakken"),
	USE(Kind.COUNT, 1, 10, "use", "place", "rightclick", "gebruik", "plaats"),
	HOLD_USE(Kind.SECONDS, 1, 5, "holduse", "hold", "charge", "vasthouden"),
	EAT(Kind.NONE, 0, 0, "eat", "eet"),
	SLOT(Kind.SLOT, 1, 9, "slot"),
	NEXT_SLOT(Kind.NONE, 0, 0, "next", "volgende"),
	PREV_SLOT(Kind.NONE, 0, 0, "prev", "previous", "vorige"),
	GRAB(Kind.TEXT, 0, 0, "grab", "pak", "take", "neem"),
	INVENTORY(Kind.NONE, 0, 0, "inv", "inventory"),
	CRAFT(Kind.ITEM_COUNT, 1, 16, "craft", "maak"),
	SMELT(Kind.ITEM_COUNT, 1, 16, "smelt", "bak", "cook", "oven", "smelten", "bakken"),
	COLLECT(Kind.NONE, 0, 0, "collect", "ophalen"),
	DROP(Kind.COUNT, 1, 10, "drop", "q", "gooi"),
	DROP_STACK(Kind.NONE, 0, 0, "dropstack", "dropall", "gooialles"),
	SWAP(Kind.NONE, 0, 0, "swap", "offhand", "wissel"),
	SHUFFLE(Kind.NONE, 0, 0, "shuffle", "hussel"),
	UNEQUIP(Kind.NONE, 0, 0, "unequip", "armor", "uitkleden"),
	STOP(Kind.NONE, 0, 0, "stop", "halt");

	public enum Kind {
		/** Hold a key for a number of seconds. */
		SECONDS,
		/** Turn the camera by a number of degrees. */
		DEGREES,
		/** Repeat a single action a number of times. */
		COUNT,
		/** Hotbar slot 1-9, required. */
		SLOT,
		/** Free text, such as an item name ("!pak diamond pickaxe"), required. */
		TEXT,
		/** An item name, required, with an optional count ("!craft torch 4"). */
		ITEM_COUNT,
		/** Takes no amount. */
		NONE
	}

	public final Kind kind;
	public final double defaultAmount;
	public final double maxAmount;
	public final List<String> aliases;

	Action(Kind kind, double defaultAmount, double maxAmount, String... aliases) {
		this.kind = kind;
		this.defaultAmount = defaultAmount;
		this.maxAmount = maxAmount;
		this.aliases = List.of(aliases);
	}

	public String id() {
		return aliases.getFirst();
	}

	public static Action byId(String id) {
		for (Action action : values()) {
			if (action.id().equalsIgnoreCase(id)) {
				return action;
			}
		}
		return null;
	}
}
