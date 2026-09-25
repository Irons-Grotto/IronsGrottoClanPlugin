package com.ironsgrotto.progress;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * Game ids the progress sync reads that RuneLite does not name for us, and
 * the named ones grouped the way the server wants them. Kept in one place so
 * a game update is a one-file fix.
 */
public final class GameIds
{
	/** Runs once per obtained item as the collection log draws; args[1] item id, args[2] quantity. */
	public static final int SCRIPT_COLLECTION_DRAW_ITEM = 4100;
	/** Closes the collection log search after the sync has opened it. */
	public static final int SCRIPT_COLLECTION_SEARCH_CLOSE = 2240;
	/** Builds the collection log interface; custom buttons are added after it runs. */
	public static final int SCRIPT_COLLECTION_SETUP = 7797;

	/**
	 * The stone button frame WikiSync and TempleOSRS draw on the collection
	 * log: background, four corners, left, top, right and bottom edges. The
	 * names are the deprecated {@code net.runelite.api.SpriteID}'s.
	 */
	public static final int[] BUTTON_SPRITES = {
		297, // DIALOG_BACKGROUND
		929, 930, 931, 932, // WORLD_MAP_BUTTON_METAL_CORNER_TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
		933, 934, 935, 936, // WORLD_MAP_BUTTON_EDGE_LEFT, TOP, RIGHT, BOTTOM
	};
	/** {@link #BUTTON_SPRITES} under the mouse. */
	public static final int[] BUTTON_SPRITES_HOVERED = {
		897, // RESIZEABLE_MODE_SIDE_PANEL_BACKGROUND
		921, 922, 923, 924, // EQUIPMENT_BUTTON_METAL_CORNER_*_HOVERED
		925, 926, 927, 928, // EQUIPMENT_BUTTON_EDGE_*_HOVERED
	};

	/**
	 * Diary location (as the server names it) → completion varbits for Easy,
	 * Medium, Hard and Elite.
	 */
	public static final Map<String, int[]> DIARIES = new LinkedHashMap<>();

	static
	{
		DIARIES.put("Ardougne", new int[] {VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE, VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE});
		DIARIES.put("Desert", new int[] {VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE, VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE});
		DIARIES.put("Falador", new int[] {VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE, VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE});
		DIARIES.put("Fremennik", new int[] {VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE, VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE});
		DIARIES.put("Kandarin", new int[] {VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE});
		// Karamja predates the *_COMPLETE varbits for its first three tiers.
		DIARIES.put("Karamja", new int[] {VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE});
		DIARIES.put("Kourend & Kebos", new int[] {VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE, VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE});
		DIARIES.put("Lumbridge & Draynor", new int[] {VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE, VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE});
		DIARIES.put("Morytania", new int[] {VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE});
		DIARIES.put("Varrock", new int[] {VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE});
		DIARIES.put("Western Provinces", new int[] {VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE, VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE});
		DIARIES.put("Wilderness", new int[] {VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE});
	}

	/** Diary tier names, in the order of the varbit arrays above. */
	public static final String[] DIARY_TIERS = {"Easy", "Medium", "Hard", "Elite"};

	/** CA tier names and the varbits holding their point thresholds, lowest first. */
	public static final String[] CA_TIERS = {"Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"};
	public static final int[] CA_THRESHOLDS = {
		VarbitID.CA_THRESHOLD_EASY,
		VarbitID.CA_THRESHOLD_MEDIUM,
		VarbitID.CA_THRESHOLD_HARD,
		VarbitID.CA_THRESHOLD_ELITE,
		VarbitID.CA_THRESHOLD_MASTER,
		VarbitID.CA_THRESHOLD_GRANDMASTER,
	};

	private GameIds()
	{
	}
}
