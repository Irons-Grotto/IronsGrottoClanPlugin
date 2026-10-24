package com.ironsgrotto.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ironsgrotto.session.AccountIdentity;
import com.ironsgrotto.session.AccountSession;
import com.ironsgrotto.tracker.ChatMessageParser;
import com.ironsgrotto.SyncExecutor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.util.Text;

/**
 * Decides when account progress is sent. It happens by itself, at the
 * moments that matter, except the full collection log, which the member
 * syncs from the side panel.
 *
 * - **Login** — a few ticks in (varbits arrive after the login tick): skills,
 *   diaries, combat achievements, quests and the collection log's counters.
 * - **Logout, and closing the client** — the latest reading again, so the
 *   session's progress lands without waiting for the next login. Game state
 *   is gone by the time the login screen shows, so the reading is the one
 *   taken quietly (in memory, not uploaded) every minute while playing.
 * - **Events that change standing** — a new collection log slot or a pet is
 *   sent straight away, and the panel refreshes when the server has it.
 * - **"Sync collection log"** in the side panel, with the log open — the
 *   whole item list (see {@link CollectionLogSync}).
 * - **Client settings** tracking depends on — on login and whenever the
 *   member changes one, so onboarding sees the fix straight away.
 * - **Game mode** — with the login and logout readings, never on its own. It
 *   changes about never, and its varbit reads 0 for a moment on a world hop,
 *   which the server would take for a main.
 *
 * A world hop or reconnect counts as a login: the varbits are cleared and
 * resent, so nothing is read until they settle again.
 *
 * Nothing unchanged is ever re-sent: the uploader skips a category identical
 * to its last upload. Test events from the developer tools never touch
 * progress.
 */
@Singleton
public class ProgressSync
{
	static final int FIRST_READ_TICK = 8;
	/** ~1 minute of 0.6s ticks between in-memory readings. */
	static final int CACHE_EVERY_TICKS = 100;
	/** The last-item varp can land a tick either side of the chat message. */
	private static final int CLOG_ITEM_WAIT_TICKS = 3;

	private final Client client;
	private final AccountSession session;
	private final ProgressCollector collector;
	private final ProgressUploader uploader;
	private final ItemManager itemManager;
	private final SyncExecutor executor;
	private final ClientThread clientThread;
	private final CollectionLogSync collectionLog;

	private AccountIdentity account;
	/** The account whose full log is on its way to the server, if one is. */
	private volatile AccountIdentity awaitingFullLog;
	private int ticksLoggedIn;
	/** The latest cheap reading, for logout; null until the first one. */
	private JsonObject lastReading;

	/**
	 * The game's "recently obtained" list, newest first. Two new slots in one
	 * tick (a raid drop and a pet) push the first one down to the second varp.
	 */
	private static final int[] CLOG_LAST_ITEM_VARPS = {
		VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM0, VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM1,
		VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM2, VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM3,
		VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM4, VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM5,
		VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM6, VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM7,
		VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM8, VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM9,
		VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM10, VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM11,
	};

	/** Names from "New item added to your collection log" not yet sent. */
	private final List<String> pendingClogItems = new ArrayList<>();
	private int pendingClogTicks;

	@Inject
	ProgressSync(Client client, AccountSession session, ProgressCollector collector, ProgressUploader uploader,
		CollectionLogSync collectionLog, ItemManager itemManager, SyncExecutor executor, ClientThread clientThread)
	{
		this.clientThread = clientThread;
		this.client = client;
		this.session = session;
		this.collector = collector;
		this.uploader = uploader;
		this.itemManager = itemManager;
		this.executor = executor;
		this.collectionLog = collectionLog;
		collectionLog.setOnSnapshot(this::onCollectionLog);
		uploader.setOnSent(this::onProgressSent);
		uploader.setOnDropped(this::onProgressDropped);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		AccountIdentity current = session.getIdentity();
		if (current == null)
		{
			return;
		}

		if (!current.equals(account))
		{
			account = current;
			ticksLoggedIn = 0;
			lastReading = null;
		}

		ticksLoggedIn++;
		if (ticksLoggedIn == FIRST_READ_TICK)
		{
			lastReading = readCheap();
			submit(current, lastReading);
			uploader.submit(current, "quests", collector.quests());
			uploader.submit(current, "settings", collector.settings());
		}
		else if (ticksLoggedIn > FIRST_READ_TICK && ticksLoggedIn % CACHE_EVERY_TICKS == 0)
		{
			lastReading = readCheap();
		}

		resolvePendingClogItem(current);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN && account != null)
		{
			// Logged out: send what the session ended on.
			submitLastReading();
			executor.execute(uploader::flush);
			account = null;
			lastReading = null;
		}
		else if (account != null && ProgressRules.resetsVarbits(event.getGameState()))
		{
			ticksLoggedIn = 0;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.OPTION_COLLECTION_NEW_ITEM)
		{
			submitSettings();
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (event.getPlugin() instanceof LootTrackerPlugin)
		{
			// Posted from whichever thread toggled it; the reading needs the client thread.
			clientThread.invoke(this::submitSettings);
		}
	}

	/** Sends the settings if they changed; only once the login reading has been taken. */
	private void submitSettings()
	{
		AccountIdentity current = session.getIdentity();
		if (current != null && current.equals(account) && ticksLoggedIn >= FIRST_READ_TICK)
		{
			uploader.submit(current, "settings", collector.settings());
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		if (account != null)
		{
			submitLastReading();
		}
		// RuneLite holds the exit briefly for tasks handed to it.
		event.waitFor(executor.submit(uploader::flush));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		AccountIdentity current = session.getIdentity();
		if (current == null || event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());

		ChatMessageParser.clueCount(message).ifPresent(clue ->
		{
			JsonObject clues = new JsonObject();
			clues.addProperty(clue.getTier(), clue.getCount());
			uploader.submit(current, "clues", clues);
		});

		ChatMessageParser.collectionLogItem(message).ifPresent(item ->
		{
			pendingClogItems.add(item);
			pendingClogTicks = 0;
			resolvePendingClogItem(current);
		});

		// A pet always shows in the collection log counters (a new one) or
		// not at all (a duplicate); sending the counters now is what moves
		// the member's standing without waiting for logout.
		ChatMessageParser.petVariant(message).ifPresent(variant -> submitCounters(current));
	}

	/**
	 * New collection log slots, with their item ids: the game's "recently
	 * obtained" varps name the items, and are checked against the chat
	 * messages' names so a stale value is never sent as a new one. Every slot
	 * from the same moment goes in one upload.
	 */
	private void resolvePendingClogItem(AccountIdentity current)
	{
		if (pendingClogItems.isEmpty())
		{
			return;
		}

		int[] recentIds = new int[CLOG_LAST_ITEM_VARPS.length];
		for (int i = 0; i < recentIds.length; i++)
		{
			recentIds[i] = client.getVarpValue(CLOG_LAST_ITEM_VARPS[i]);
		}
		Map<String, Integer> matched = matchClogItems(pendingClogItems, recentIds,
			id -> itemManager.getItemComposition(id).getMembersName());

		if (matched.size() < pendingClogItems.size() && ++pendingClogTicks < CLOG_ITEM_WAIT_TICKS)
		{
			return;
		}

		JsonObject log = collector.collectionLogCounts();
		if (log == null)
		{
			log = new JsonObject();
		}
		if (!matched.isEmpty())
		{
			JsonArray items = new JsonArray();
			matched.forEach((name, id) ->
			{
				JsonObject item = new JsonObject();
				item.addProperty("id", id);
				item.addProperty("name", name);
				item.addProperty("quantity", 1);
				items.add(item);
			});
			log.add("items", items);
		}
		// Unmatched: the counters still move; the item itself arrives with the
		// next full read of the log.
		if (log.size() > 0)
		{
			uploader.submit(current, "collectionLog", log);
		}
		pendingClogItems.clear();
	}

	/**
	 * Pairs each chat-message name with a distinct id from the recently
	 * obtained list (by item name, ignoring case). Names with no match are
	 * left out; the result keeps the names' order.
	 */
	static Map<String, Integer> matchClogItems(List<String> names, int[] recentIds, IntFunction<String> nameOf)
	{
		Map<String, Integer> matched = new LinkedHashMap<>();
		boolean[] used = new boolean[recentIds.length];
		for (String name : names)
		{
			for (int i = 0; i < recentIds.length; i++)
			{
				if (!used[i] && recentIds[i] > 0 && name.equalsIgnoreCase(nameOf.apply(recentIds[i])))
				{
					used[i] = true;
					matched.put(name, recentIds[i]);
					break;
				}
			}
		}
		return matched;
	}

	private void submitCounters(AccountIdentity current)
	{
		JsonObject counts = collector.collectionLogCounts();
		if (counts != null)
		{
			uploader.submit(current, "collectionLog", counts);
		}
	}

	private void submitLastReading()
	{
		if (lastReading != null)
		{
			submit(account, lastReading);
		}
	}

	/** Skills, diaries, CA and clog counters — cheap enough to take every minute. */
	private JsonObject readCheap()
	{
		JsonObject reading = new JsonObject();
		reading.add("skills", collector.skills());
		reading.add("diaries", collector.diaries());
		JsonObject ca = collector.combatAchievements();
		if (ca != null)
		{
			reading.add("combatAchievements", ca);
		}
		JsonObject counts = collector.collectionLogCounts();
		if (counts != null)
		{
			reading.add("collectionLog", counts);
		}
		String accountType = collector.accountType();
		if (accountType != null)
		{
			reading.addProperty("accountType", accountType);
		}
		return reading;
	}

	private void submit(AccountIdentity to, JsonObject reading)
	{
		reading.entrySet().forEach(entry -> uploader.submit(to, entry.getKey(), entry.getValue()));
	}

	private void onCollectionLog(JsonObject snapshot)
	{
		AccountIdentity current = session.getIdentity();
		if (current == null)
		{
			collectionLog.onNotSent();
			return;
		}

		JsonObject counts = collector.collectionLogCounts();
		if (counts != null)
		{
			snapshot.add("obtained", counts.get("obtained"));
			snapshot.add("total", counts.get("total"));
		}
		if (!uploader.submit(current, "collectionLog", snapshot))
		{
			// The same full log already reached the server this session.
			collectionLog.onSent(Collections.emptyList());
			return;
		}
		awaitingFullLog = current;
		executor.execute(uploader::flush);
	}

	private void onProgressSent(JsonObject body, JsonObject result)
	{
		AccountIdentity waiting = awaitingFullLog;
		if (waiting != null && isFullLog(body))
		{
			awaitingFullLog = null;
			// Only mark it synced for the account still logged in: the mark
			// is saved to the logged-in account's profile.
			if (waiting.equals(session.getIdentity()))
			{
				collectionLog.onSent(newCollectionLogItems(result));
			}
		}
	}

	private void onProgressDropped(JsonObject body)
	{
		if (awaitingFullLog != null && isFullLog(body))
		{
			awaitingFullLog = null;
			collectionLog.onNotSent();
		}
	}

	/**
	 * The collection log items the upload added to the member's clan record,
	 * or null if the server didn't say (not a member yet, or an older server).
	 */
	@Nullable
	static List<String> newCollectionLogItems(@Nullable JsonObject result)
	{
		if (result == null || !result.has("newCollectionLogItems") || !result.get("newCollectionLogItems").isJsonArray())
		{
			return null;
		}
		List<String> names = new ArrayList<>();
		result.getAsJsonArray("newCollectionLogItems").forEach(name -> names.add(name.getAsString()));
		return names;
	}

	/** Whether an upload carried a complete collection log, not just its counters or one slot. */
	static boolean isFullLog(JsonObject body)
	{
		JsonObject log = body.has("collectionLog") && body.get("collectionLog").isJsonObject()
			? body.getAsJsonObject("collectionLog") : null;
		return log != null && log.has("complete") && log.get("complete").getAsBoolean();
	}
}
