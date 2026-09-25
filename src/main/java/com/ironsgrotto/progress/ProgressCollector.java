package com.ironsgrotto.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;

/**
 * Reads account progress straight from the game. Every method must run on
 * the client thread.
 */
@Singleton
public class ProgressCollector
{
	private final Client client;
	private final PluginManager pluginManager;

	@Inject
	ProgressCollector(Client client, PluginManager pluginManager)
	{
		this.client = client;
		this.pluginManager = pluginManager;
	}

	/**
	 * The client settings tracking depends on, so onboarding can ask the
	 * member to fix them: the game's chat message for a new collection log
	 * slot (how new slots are seen), and RuneLite's Loot Tracker plugin
	 * (which posts raid, clue and other non-NPC loot).
	 */
	public JsonObject settings()
	{
		JsonObject settings = new JsonObject();
		settings.addProperty("collectionLogChat",
			ProgressRules.collectionLogChatEnabled(client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM)));
		settings.addProperty("lootTracker", isLootTrackerEnabled());
		return settings;
	}

	private boolean isLootTrackerEnabled()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof LootTrackerPlugin)
			{
				return pluginManager.isPluginEnabled(plugin);
			}
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	public JsonObject skills()
	{
		JsonObject skills = new JsonObject();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("level", client.getRealSkillLevel(skill));
			entry.addProperty("xp", client.getSkillExperience(skill));
			skills.add(skill.getName(), entry);
		}

		JsonObject result = new JsonObject();
		result.addProperty("totalLevel", client.getTotalLevel());
		result.addProperty("totalXp", client.getOverallExperience());
		result.add("skills", skills);
		return result;
	}

	public JsonObject diaries()
	{
		JsonObject diaries = new JsonObject();
		for (Map.Entry<String, int[]> diary : GameIds.DIARIES.entrySet())
		{
			int[] varbits = diary.getValue();
			int[] values = new int[varbits.length];
			for (int i = 0; i < varbits.length; i++)
			{
				values[i] = client.getVarbitValue(varbits[i]);
			}
			diaries.addProperty(diary.getKey(), ProgressRules.diaryTier(values));
		}
		return diaries;
	}

	/** Null until the game has sent the thresholds (they arrive shortly after login). */
	public JsonObject combatAchievements()
	{
		int points = client.getVarbitValue(VarbitID.CA_POINTS);
		int[] thresholds = new int[GameIds.CA_THRESHOLDS.length];
		for (int i = 0; i < thresholds.length; i++)
		{
			thresholds[i] = client.getVarbitValue(GameIds.CA_THRESHOLDS[i]);
		}
		if (thresholds[0] == 0)
		{
			return null;
		}

		JsonObject result = new JsonObject();
		result.addProperty("points", points);
		result.addProperty("tier", ProgressRules.combatAchievementTier(points, thresholds));
		return result;
	}

	public JsonObject quests()
	{
		JsonArray completed = new JsonArray();
		for (Quest quest : Quest.values())
		{
			if (quest.getState(client) == QuestState.FINISHED)
			{
				completed.add(quest.getName());
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("questPoints", client.getVarpValue(VarPlayerID.QP));
		result.add("completed", completed);
		return result;
	}

	/** The game's own slot counters; null until it has sent them. */
	public JsonObject collectionLogCounts()
	{
		int obtained = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
		int total = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
		if (total <= 0)
		{
			return null;
		}

		JsonObject result = new JsonObject();
		result.addProperty("obtained", obtained);
		result.addProperty("total", total);
		return result;
	}
}
