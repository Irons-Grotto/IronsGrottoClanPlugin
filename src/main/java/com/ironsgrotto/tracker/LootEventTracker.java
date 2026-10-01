package com.ironsgrotto.tracker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ironsgrotto.ledger.LedgerEventType;
import com.ironsgrotto.ledger.LedgerRecorder;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Every drop, as RuneLite's loot events report it.
 *
 * NPC loot is {@link ServerNpcLoot}: the game itself reports every NPC drop
 * through its loot-tracker script (7192), and RuneLite's core loot manager
 * passes it on whether or not the Loot Tracker plugin is on. It covers loot
 * that never touches the ground (the Maggot King) as well as ordinary drops.
 * RuneLite's own Loot Tracker uses only this for NPCs; the older ground-item
 * event ({@code NpcLootReceived}) is not used here, or every ground drop would
 * be recorded twice.
 *
 * PvP kills come from the core {@link PlayerLootReceived}. Everything that is
 * not an NPC or a player — raids, clues, Barrows, Wintertodt and other
 * minigames, pickpockets — is only reported by the Loot Tracker plugin, so it
 * must be enabled for those. Its NPC and PLAYER events are skipped, because
 * the core events above already cover them.
 */
@Singleton
public class LootEventTracker
{
	/**
	 * Sailing salvage, which the Loot Tracker reports as an event named
	 * "<Tier> salvage" every time some is sorted. Thousands a session and no
	 * clan event is decided by it, so it is never queued. The server drops it
	 * too, for plugins older than this check.
	 */
	private static final Pattern SALVAGE = Pattern.compile("^\\S+ salvage$", Pattern.CASE_INSENSITIVE);

	/** The game's message for a successful pickpocket, as the Loot Tracker matches it. */
	private static final Pattern PICKPOCKET = Pattern.compile("You pick (the )?(?<target>.+)'s? pocket.*");

	private final LedgerRecorder recorder;
	private final ItemManager itemManager;
	private final Client client;

	/**
	 * The tick of the last pickpocket. The game reports pickpocket loot through
	 * the same script as a kill, so on that tick the server's NPC loot is the
	 * pickpocket, which the Loot Tracker records as PICKPOCKET loot instead.
	 */
	private int pickpocketTick = -1;

	@Inject
	LootEventTracker(LedgerRecorder recorder, ItemManager itemManager, Client client)
	{
		this.recorder = recorder;
		this.itemManager = itemManager;
		this.client = client;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if ((event.getType() == ChatMessageType.SPAM || event.getType() == ChatMessageType.GAMEMESSAGE)
			&& isPickpocket(event.getMessage()))
		{
			pickpocketTick = client.getTickCount();
		}
	}

	/** Every NPC's loot, as the game reports it. */
	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		NPCComposition npc = event.getComposition();
		if (npc == null || npc.getName() == null || client.getTickCount() == pickpocketTick)
		{
			return;
		}
		record(Text.removeTags(npc.getName()), LootRecordType.NPC, npc.getCombatLevel(), event.getItems());
	}

	static boolean isPickpocket(String message)
	{
		return message != null && PICKPOCKET.matcher(Text.removeTags(message)).matches();
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
		if (event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER
			|| isIgnored(event.getType(), event.getName()))
		{
			return;
		}
		record(event.getName(), event.getType(), event.getCombatLevel(), event.getItems());
	}

	/** Loot that is never recorded: sailing salvage. */
	static boolean isIgnored(LootRecordType type, String source)
	{
		return type == LootRecordType.EVENT && source != null && SALVAGE.matcher(source).matches();
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
			long price = itemManager.getItemPrice(id);

			JsonObject item = new JsonObject();
			item.addProperty("id", id);
			// The members name: on a free world `getName()` is the F2P display
			// name, e.g. "Skeletal visage (Members)".
			item.addProperty("name", itemManager.getItemComposition(id).getMembersName());
			item.addProperty("quantity", quantity);
			item.addProperty("price", price);
			items.add(item);
			total += price * quantity;
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

		recorder.record(LedgerEventType.LOOT, payload);
	}
}
