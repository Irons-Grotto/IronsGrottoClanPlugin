package com.ironsgrotto.tracker;

import com.google.gson.JsonObject;
import com.ironsgrotto.dev.DevTools;
import com.ironsgrotto.ledger.LedgerEventType;
import com.ironsgrotto.ledger.LedgerRecorder;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Kill counts, new collection log slots and pets, from the game's own chat
 * messages.
 *
 * The collection log message needs the in-game setting "Collection log — new
 * addition notification" set to chat (or both).
 */
@Singleton
public class ChatEventTracker
{
	private final LedgerRecorder recorder;

	@Inject
	ChatEventTracker(LedgerRecorder recorder)
	{
		this.recorder = recorder;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		// Real game messages have no sender; the developer tools sign theirs.
		boolean test = DevTools.SENDER.equals(event.getName());
		String message = Text.removeTags(event.getMessage());

		ChatMessageParser.killCount(message).ifPresent(kill ->
		{
			JsonObject payload = new JsonObject();
			payload.addProperty("boss", kill.getBoss());
			payload.addProperty("kc", kill.getKc());
			recorder.record(LedgerEventType.BOSS_KC, payload, test);
		});

		ChatMessageParser.collectionLogItem(message).ifPresent(item ->
		{
			JsonObject payload = new JsonObject();
			payload.addProperty("itemName", item);
			recorder.record(LedgerEventType.COLLECTION_LOG_ITEM, payload, test);
		});

		ChatMessageParser.petVariant(message).ifPresent(variant ->
		{
			JsonObject payload = new JsonObject();
			payload.addProperty("variant", variant);
			payload.addProperty("message", message);
			recorder.record(LedgerEventType.PET, payload, test);
		});
	}
}
