package com.ironsgrotto.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ironsgrotto.session.AccountIdentity;
import com.ironsgrotto.session.AccountSession;
import com.ironsgrotto.tracker.ChatMessageParser;
import com.ironsgrotto.SyncExecutor;
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
 * Decides when account progress is sent. There is no button: it happens by
 * itself, at the moments that matter.
 *
 * - **Login** — a few ticks in (varbits arrive after the login tick): skills,
 *   diaries, combat achievements, quests and the collection log's counters.
 * - **Logout, and closing the client** — the latest reading again, so the
 *   session's progress lands without waiting for the next login. Game state
 *   is gone by the time the login screen shows, so the reading is the one
 *   taken quietly (in memory, not uploaded) every minute while playing.
 * - **Events that change standing** — a new collection log slot or a pet is
 *   sent straight away, and the panel refreshes when the server has it.
 * - **Opening the collection log** — the whole item list (see
 *   {@link CollectionLogSync}).
 * - **Client settings** tracking depends on — on login and whenever the
 *   member changes one, so onboarding sees the fix straight away.
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

	private AccountIdentity account;
	private int ticksLoggedIn;
	/** The latest cheap reading, for logout; null until the first one. */
	private JsonObject lastReading;

	private String pendingClogItem;
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
		collectionLog.setOnSnapshot(this::onCollectionLog);
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
			pendingClogItem = item;
			pendingClogTicks = 0;
			resolvePendingClogItem(current);
		});

		// A pet always shows in the collection log counters (a new one) or
		// not at all (a duplicate); sending the counters now is what moves
		// the member's standing without waiting for logout.
		ChatMessageParser.petVariant(message).ifPresent(variant -> submitCounters(current));
	}

	/**
	 * A new collection log slot, with its item id: the game's "last obtained"
	 * varp names the item, and is checked against the chat message's name so
	 * a stale value is never sent as the new one.
	 */
	private void resolvePendingClogItem(AccountIdentity current)
	{
		if (pendingClogItem == null)
		{
			return;
		}

		int itemId = client.getVarpValue(VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM0);
		boolean matches = itemId > 0
			&& pendingClogItem.equalsIgnoreCase(itemManager.getItemComposition(itemId).getMembersName());

		if (!matches && ++pendingClogTicks < CLOG_ITEM_WAIT_TICKS)
		{
			return;
		}

		JsonObject log = collector.collectionLogCounts();
		if (log == null)
		{
			log = new JsonObject();
		}
		if (matches)
		{
			JsonObject item = new JsonObject();
			item.addProperty("id", itemId);
			item.addProperty("name", pendingClogItem);
			item.addProperty("quantity", 1);
			JsonArray items = new JsonArray();
			items.add(item);
			log.add("items", items);
		}
		// Unmatched: the counters still move; the item itself arrives with the
		// next full read of the log.
		if (log.size() > 0)
		{
			uploader.submit(current, "collectionLog", log);
		}
		pendingClogItem = null;
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
			return;
		}

		JsonObject counts = collector.collectionLogCounts();
		if (counts != null)
		{
			snapshot.add("obtained", counts.get("obtained"));
			snapshot.add("total", counts.get("total"));
		}
		uploader.submit(current, "collectionLog", snapshot);
	}
}
