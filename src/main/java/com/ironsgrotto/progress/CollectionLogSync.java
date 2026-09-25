package com.ironsgrotto.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Reads the whole collection log when the member presses the Grotto button on
 * the log ({@link CollectionLogButton}).
 *
 * The log only draws the page being viewed, so this briefly toggles the log's
 * own search, which draws every obtained item at once, then closes it again.
 * Each drawn item runs a client script whose arguments are the item id and
 * quantity; those are collected until they stop coming, and sent as one
 * complete snapshot. WikiSync and TempleOSRS read the log the same way, from
 * their own buttons in the same spot.
 *
 * ⚠️ Only ever from the member's click, never by itself on opening the log.
 */
@Slf4j
@Singleton
public class CollectionLogSync
{
	/** Ticks without a new item before the capture is taken as finished. */
	private static final int QUIET_TICKS = 2;
	/** Give up on a capture that never produced anything. */
	private static final int MAX_TICKS = 10;

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;

	private final Map<Integer, Integer> captured = new LinkedHashMap<>();
	private boolean capturing;
	private int ticksSinceLastItem;
	private int ticksCapturing;
	/** From the click until the server has the log, or it failed. */
	private volatile boolean syncing;
	private volatile Consumer<JsonObject> onSnapshot = snapshot -> { };
	private volatile Consumer<String> onMessage = message -> { };

	@Inject
	CollectionLogSync(Client client, ClientThread clientThread, ItemManager itemManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
	}

	public void setOnSnapshot(Consumer<JsonObject> onSnapshot)
	{
		this.onSnapshot = onSnapshot;
	}

	/** Called with a line for the member's chat about how the sync went. */
	public void setOnMessage(Consumer<String> onMessage)
	{
		this.onMessage = onMessage;
	}

	/** The member pressed the button. Client thread, with the log open. */
	public void requestSync()
	{
		if (syncing)
		{
			onMessage.accept("Your collection log is already syncing.");
			return;
		}
		if (client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE) == null)
		{
			return;
		}

		captured.clear();
		capturing = true;
		syncing = true;
		ticksSinceLastItem = 0;
		ticksCapturing = 0;
		onMessage.accept("Syncing your collection log…");

		// Open the search (which draws every obtained item), then close it again.
		client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
		client.runScript(GameIds.SCRIPT_COLLECTION_SEARCH_CLOSE);
	}

	/** The server accepted the complete log. Any thread. */
	public void onSent()
	{
		if (syncing)
		{
			syncing = false;
			onMessage.accept("Collection log synced.");
		}
	}

	/** The log could not be delivered: no account, or the server refused it. Any thread. */
	public void onNotSent()
	{
		if (syncing)
		{
			syncing = false;
			onMessage.accept("Couldn't sync your collection log. Try again.");
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			// The next account starts fresh; a log still uploading lands on its own.
			capturing = false;
			syncing = false;
		}
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
			syncing = false;
			onMessage.accept("Couldn't read your collection log. Try again.");
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
