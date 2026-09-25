package com.ironsgrotto.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ironsgrotto.IronsGrottoConfig;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Reads the whole collection log when the member presses "Sync collection log"
 * in the side panel, with the log open.
 *
 * The log only draws the page being viewed, so this briefly toggles the log's
 * own search, which draws every obtained item at once, then closes it again.
 * Each drawn item runs a client script whose arguments are the item id and
 * quantity; those are collected until they stop coming, and sent as one
 * complete snapshot. WikiSync reads the log the same way, also from a button.
 *
 * ⚠️ Only ever from the member's click. Opening the log by itself does
 * nothing: firing the search toggle without the member asking is exactly the
 * kind of automation Plugin Hub review questions.
 */
@Slf4j
@Singleton
public class CollectionLogSync
{
	/** RS-profile config key: when this account's full log last reached the server (epoch ms). */
	static final String SYNCED_AT_KEY = "collectionLogSyncedAt";
	/** Ticks without a new item before the capture is taken as finished. */
	private static final int QUIET_TICKS = 2;
	/** Give up on a capture that never produced anything. */
	private static final int MAX_TICKS = 10;

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ConfigManager configManager;

	private final Map<Integer, Integer> captured = new LinkedHashMap<>();
	private boolean capturing;
	private int ticksSinceLastItem;
	private int ticksCapturing;
	private volatile boolean logOpen;
	private volatile CollectionLogSyncState.Phase phase = CollectionLogSyncState.Phase.IDLE;
	private volatile Consumer<JsonObject> onSnapshot = snapshot -> { };
	private volatile Consumer<CollectionLogSyncState> onState = state -> { };

	@Inject
	CollectionLogSync(Client client, ClientThread clientThread, ItemManager itemManager, ConfigManager configManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.configManager = configManager;
	}

	public void setOnSnapshot(Consumer<JsonObject> onSnapshot)
	{
		this.onSnapshot = onSnapshot;
	}

	/** Called with the new state whenever it changes, from any thread. */
	public void setOnState(Consumer<CollectionLogSyncState> onState)
	{
		this.onState = onState;
	}

	public CollectionLogSyncState state()
	{
		return new CollectionLogSyncState(logOpen, phase, lastSyncedAt());
	}

	/** Sends the current state to the listener, e.g. after the panel was rebuilt. */
	public void publish()
	{
		onState.accept(state());
	}

	/** The member pressed Sync. Ignored unless the log is open and nothing is running. */
	public void requestSync()
	{
		clientThread.invoke(() ->
		{
			if (!logOpen || capturing || phase == CollectionLogSyncState.Phase.SYNCING)
			{
				return;
			}

			captured.clear();
			capturing = true;
			ticksSinceLastItem = 0;
			ticksCapturing = 0;
			setPhase(CollectionLogSyncState.Phase.SYNCING);

			// Open the search (which draws every obtained item), then close it again.
			client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
			client.runScript(GameIds.SCRIPT_COLLECTION_SEARCH_CLOSE);
		});
	}

	/** The server accepted a complete log from the account logged in now. */
	public void onSent()
	{
		configManager.setRSProfileConfiguration(IronsGrottoConfig.GROUP, SYNCED_AT_KEY, System.currentTimeMillis());
		setPhase(CollectionLogSyncState.Phase.SYNCED);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.COLLECTION)
		{
			return;
		}

		logOpen = true;
		// A fresh look at the log: last time's result no longer applies.
		if (phase == CollectionLogSyncState.Phase.SYNCED || phase == CollectionLogSyncState.Phase.FAILED)
		{
			phase = CollectionLogSyncState.Phase.IDLE;
		}
		publish();
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION)
		{
			logOpen = false;
			publish();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			// The log is gone, and the next account starts from its own state.
			logOpen = false;
			capturing = false;
			phase = CollectionLogSyncState.Phase.IDLE;
		}
	}

	@Nullable
	private Instant lastSyncedAt()
	{
		Long at = configManager.getRSProfileConfiguration(IronsGrottoConfig.GROUP, SYNCED_AT_KEY, Long.class);
		return at == null ? null : Instant.ofEpochMilli(at);
	}

	/** The read could not be delivered (no account, or the server refused it). */
	public void onNotSent()
	{
		setPhase(CollectionLogSyncState.Phase.FAILED);
	}

	private void setPhase(CollectionLogSyncState.Phase next)
	{
		phase = next;
		publish();
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (!capturing || event.getScriptId() != GameIds.SCRIPT_COLLECTION_DRAW_ITEM || event.getScriptEvent() == null)
		{
			return;
		}

		Object[] args = event.getScriptEvent().getArguments();
		if (args == null || args.length < 3 || !(args[1] instanceof Integer) || !(args[2] instanceof Integer))
		{
			return;
		}

		int itemId = (Integer) args[1];
		int quantity = (Integer) args[2];
		if (itemId > 0 && quantity > 0)
		{
			captured.merge(itemId, quantity, Math::max);
			ticksSinceLastItem = 0;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!capturing)
		{
			return;
		}

		ticksCapturing++;
		ticksSinceLastItem++;

		boolean settled = !captured.isEmpty() && ticksSinceLastItem >= QUIET_TICKS;
		if (!settled && ticksCapturing < MAX_TICKS)
		{
			return;
		}

		capturing = false;
		if (captured.isEmpty())
		{
			log.debug("Collection log sync drew no items");
			setPhase(CollectionLogSyncState.Phase.FAILED);
			return;
		}

		JsonArray items = new JsonArray();
		for (Map.Entry<Integer, Integer> entry : captured.entrySet())
		{
			JsonObject item = new JsonObject();
			item.addProperty("id", entry.getKey());
			item.addProperty("name", itemManager.getItemComposition(entry.getKey()).getMembersName());
			item.addProperty("quantity", entry.getValue());
			items.add(item);
		}

		JsonObject snapshot = new JsonObject();
		snapshot.add("items", items);
		snapshot.addProperty("complete", true);

		// Still SYNCING: done once the server has it (onSent).
		onSnapshot.accept(snapshot);
	}
}
