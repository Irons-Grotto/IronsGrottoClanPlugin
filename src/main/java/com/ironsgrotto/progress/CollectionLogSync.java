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
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Reads the whole collection log when the player opens it.
 *
 * The log only draws the page being viewed, so on opening it this briefly
 * toggles the log's own search — which draws every obtained item at once —
 * then closes it again. Each drawn item runs a client script whose arguments
 * are the item id and quantity; those are collected until they stop coming,
 * and sent as one complete snapshot. The WikiSync plugin reads the log the
 * same way.
 *
 * At most once per {@link #MIN_INTERVAL_MS} unless the obtained count moved.
 */
@Slf4j
@Singleton
public class CollectionLogSync
{
	static final long MIN_INTERVAL_MS = 30 * 60_000;
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
	private long lastSyncAt;
	private int lastSyncObtained = -1;
	private volatile Consumer<JsonObject> onSnapshot = snapshot -> { };

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

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.COLLECTION || capturing)
		{
			return;
		}

		int obtained = client.getVarpValue(net.runelite.api.gameval.VarPlayerID.COLLECTION_COUNT);
		boolean recent = System.currentTimeMillis() - lastSyncAt < MIN_INTERVAL_MS;
		if (recent && obtained == lastSyncObtained)
		{
			return;
		}

		captured.clear();
		capturing = true;
		ticksSinceLastItem = 0;
		ticksCapturing = 0;

		// After the interface has finished building: open the search (which
		// draws every obtained item), then close it again.
		clientThread.invokeLater(() ->
		{
			client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
			client.runScript(GameIds.SCRIPT_COLLECTION_SEARCH_CLOSE);
		});
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

		lastSyncAt = System.currentTimeMillis();
		lastSyncObtained = client.getVarpValue(net.runelite.api.gameval.VarPlayerID.COLLECTION_COUNT);
		onSnapshot.accept(snapshot);
	}
}
