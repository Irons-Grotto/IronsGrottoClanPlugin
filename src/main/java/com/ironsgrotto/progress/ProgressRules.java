package com.ironsgrotto.progress;

/** The pure decisions behind the progress sync, kept apart so they are tested. */
public final class ProgressRules
{
	private ProgressRules()
	{
	}

	/** The ironman varbit's values, in order, as the server names them. */
	private static final String[] ACCOUNT_TYPES = {
		"main",
		"ironman",
		"ultimate_ironman",
		"hardcore_ironman",
		"group_ironman",
		"hardcore_group_ironman",
		"unranked_group_ironman",
	};

	/** The game mode for the ironman varbit's value, or null for one the game has added since. */
	public static String accountType(int varbitValue)
	{
		return varbitValue >= 0 && varbitValue < ACCOUNT_TYPES.length ? ACCOUNT_TYPES[varbitValue] : null;
	}

	/** Highest diary tier whose completion varbit is set, or "None". */
	public static String diaryTier(int[] completionValues)
	{
		String tier = "None";
		for (int i = 0; i < completionValues.length && i < GameIds.DIARY_TIERS.length; i++)
		{
			if (completionValues[i] > 0)
			{
				tier = GameIds.DIARY_TIERS[i];
			}
		}
		return tier;
	}

	/**
	 * Highest combat achievement tier the points reach. A threshold of 0 means
	 * the game has not sent it yet, and never counts as reached.
	 */
	public static String combatAchievementTier(int points, int[] thresholds)
	{
		String tier = "None";
		for (int i = 0; i < thresholds.length && i < GameIds.CA_TIERS.length; i++)
		{
			if (thresholds[i] > 0 && points >= thresholds[i])
			{
				tier = GameIds.CA_TIERS[i];
			}
		}
		return tier;
	}

	/**
	 * Whether the game's "new collection log item" setting includes the chat
	 * message: bit 1 of the varbit is chat, bit 2 the popup.
	 */
	public static boolean collectionLogChatEnabled(int settingValue)
	{
		return (settingValue & 1) != 0;
	}
}
