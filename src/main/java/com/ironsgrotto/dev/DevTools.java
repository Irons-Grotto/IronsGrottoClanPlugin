package com.ironsgrotto.dev;

import com.ironsgrotto.tracker.LootEventTracker;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Spawns fake game events, for testing the ledger without killing anything.
 *
 * They go in the way real ones do — a chat message the game "sent", a loot
 * event on the event bus — so the parsing, the trackers, the outbox and the
 * server are all exercised. Every one is marked as a test: chat messages are
 * signed with {@link #SENDER} (real game messages have no sender) and loot is
 * posted inside {@link LootEventTracker#simulate}. The server flags them
 * `test` and consumers never count them.
 */
@Singleton
public class DevTools
{
	public static final String SENDER = "IronsGrottoDevTools";

	private static final int SKELETAL_VISAGE = 22006;
	private static final int DRAGON_BONES = 536;
	private static final int BLUE_DRAGONHIDE = 1751;

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final LootEventTracker lootTracker;

	@Inject
	DevTools(Client client, ClientThread clientThread, EventBus eventBus, LootEventTracker lootTracker)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.lootTracker = lootTracker;
	}

	public void spawnKillCount()
	{
		int kc = ThreadLocalRandom.current().nextInt(1, 5000);
		gameMessage("Your Vorkath kill count is: <col=ff0000>" + kc + "</col>.");
	}

	/** A kill count then its loot, as a real boss kill arrives — they get linked. */
	public void spawnKillWithDrop()
	{
		spawnKillCount();
		spawnDrop();
	}

	public void spawnCollectionLog()
	{
		gameMessage("New item added to your collection log: <col=ef1020>Vorki</col>");
	}

	public void spawnPet()
	{
		gameMessage("You have a funny feeling like you're being followed.");
	}

	public void spawnDrop()
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			LootReceived loot = new LootReceived(
				"Vorkath",
				732,
				// EVENT, not NPC: the tracker takes NPC drops from the core
				// loot manager, which needs a real NPC.
				LootRecordType.EVENT,
				Arrays.asList(
					new ItemStack(SKELETAL_VISAGE, 1),
					new ItemStack(DRAGON_BONES, 2),
					new ItemStack(BLUE_DRAGONHIDE, 2)),
				1,
				null);
			lootTracker.simulate(() -> eventBus.post(loot));
		});
	}

	private void gameMessage(String message)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, SENDER, message, null);
			}
		});
	}
}
