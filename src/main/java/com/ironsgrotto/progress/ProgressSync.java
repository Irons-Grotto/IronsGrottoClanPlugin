package com.ironsgrotto.progress;

import com.google.gson.JsonObject;
import com.ironsgrotto.session.AccountIdentity;
import com.ironsgrotto.session.AccountSession;
import com.ironsgrotto.tracker.ChatMessageParser;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Decides when to read account progress and hands it to the uploader.
 *
 * - A few ticks after login (varbits arrive after the login tick), then every
 *   ~10 minutes while logged in: skills, diaries, combat achievements, quests
 *   and the collection log's counters. Unchanged categories are not re-sent.
 * - When the collection log is opened: every obtained item.
 * - As a clue is completed: that tier's new count.
 */
@Singleton
public class ProgressSync
{
	static final int FIRST_READ_TICK = 8;
	/** ~10 minutes of 0.6s ticks. */
	static final int READ_EVERY_TICKS = 1000;

	private final AccountSession session;
	private final ProgressCollector collector;
	private final ProgressUploader uploader;

	private AccountIdentity account;
	private int ticksLoggedIn;

	@Inject
	ProgressSync(AccountSession session, ProgressCollector collector, ProgressUploader uploader, CollectionLogSync collectionLog)
	{
		this.session = session;
		this.collector = collector;
		this.uploader = uploader;
		collectionLog.setOnSnapshot(this::onCollectionLog);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		AccountIdentity current = session.getIdentity();
		if (current == null)
		{
			account = null;
			return;
		}

		if (!current.equals(account))
		{
			account = current;
			ticksLoggedIn = 0;
		}

		ticksLoggedIn++;
		if (ticksLoggedIn == FIRST_READ_TICK || (ticksLoggedIn > FIRST_READ_TICK && ticksLoggedIn % READ_EVERY_TICKS == 0))
		{
			readAll(current);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		AccountIdentity current = session.getIdentity();
		if (current == null || event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		ChatMessageParser.clueCount(Text.removeTags(event.getMessage())).ifPresent(clue ->
		{
			JsonObject clues = new JsonObject();
			clues.addProperty(clue.getTier(), clue.getCount());
			uploader.submit(current, "clues", clues);
		});
	}

	/** Reads everything now, e.g. from the panel's "Sync now". Client thread only. */
	public void readAll(AccountIdentity current)
	{
		uploader.submit(current, "skills", collector.skills());
		uploader.submit(current, "diaries", collector.diaries());
		uploader.submit(current, "quests", collector.quests());

		JsonObject ca = collector.combatAchievements();
		if (ca != null)
		{
			uploader.submit(current, "combatAchievements", ca);
		}

		JsonObject counts = collector.collectionLogCounts();
		if (counts != null)
		{
			uploader.submit(current, "collectionLog", counts);
		}
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
