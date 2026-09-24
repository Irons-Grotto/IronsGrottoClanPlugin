package com.ironsgrotto.tracker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ironsgrotto.ledger.LedgerEventType;
import com.ironsgrotto.ledger.LedgerRecorder;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Every drop, as RuneLite's loot events report it.
 *
 * NPC and PvP kills come from the core loot manager, which runs whether or not
 * the Loot Tracker plugin is on. Everything else — raids, clues, Barrows,
 * minigames, pickpockets — is only reported by the Loot Tracker plugin, so it
 * must be enabled for those. Its NPC and PLAYER events are skipped, because
 * the core events above already cover them.
 */
@Singleton
public class LootEventTracker
{
	private final LedgerRecorder recorder;
	private final ItemManager itemManager;

	/** Set by the developer tools around a loot event they post. */
	private volatile boolean simulating;

	@Inject
	LootEventTracker(LedgerRecorder recorder, ItemManager itemManager)
	{
		this.recorder = recorder;
		this.itemManager = itemManager;
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		record(npc.getName(), LootRecordType.NPC, npc.getCombatLevel(), event.getItems());
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		Player player = event.getPlayer();
		record(player.getName(), LootRecordType.PLAYER, player.getCombatLevel(), event.getItems());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER)
		{
			return;
		}
		record(event.getName(), event.getType(), event.getCombatLevel(), event.getItems());
	}

	/** Posts a loot event marked as a test; called on the client thread. */
	public void simulate(Runnable post)
	{
		simulating = true;
		try
		{
			post.run();
		}
		finally
		{
			simulating = false;
		}
	}

	private void record(String source, LootRecordType type, int combatLevel, Collection<ItemStack> stacks)
	{
		if (source == null || stacks == null || stacks.isEmpty())
		{
			return;
		}

		// Stacks of the same item arrive separately (e.g. two piles of bones).
		Map<Integer, Integer> quantities = new LinkedHashMap<>();
		for (ItemStack stack : stacks)
		{
			int id = itemManager.canonicalize(stack.getId());
			quantities.merge(id, stack.getQuantity(), Integer::sum);
		}

		JsonArray items = new JsonArray();
		long total = 0;
		for (Map.Entry<Integer, Integer> entry : quantities.entrySet())
		{
			int id = entry.getKey();
			int quantity = entry.getValue();
			int price = itemManager.getItemPrice(id);

			JsonObject item = new JsonObject();
			item.addProperty("id", id);
			// The members name: on a free world `getName()` is the F2P display
			// name, e.g. "Skeletal visage (Members)".
			item.addProperty("name", itemManager.getItemComposition(id).getMembersName());
			item.addProperty("quantity", quantity);
			item.addProperty("price", price);
			items.add(item);
			total += (long) price * quantity;
		}

		JsonObject payload = new JsonObject();
		payload.addProperty("source", source);
		payload.addProperty("sourceType", type.name());
		if (combatLevel > 0)
		{
			payload.addProperty("combatLevel", combatLevel);
		}
		payload.add("items", items);
		payload.addProperty("totalValue", total);

		recorder.record(LedgerEventType.LOOT, payload, simulating);
	}
}
