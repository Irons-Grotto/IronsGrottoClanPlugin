package com.ironsgrotto.screenshot;

import com.ironsgrotto.api.model.PluginPolicy;
import com.ironsgrotto.ledger.LedgerEventType;
import com.ironsgrotto.outbox.OutboxEntry;

/** Which ledger events earn a screenshot — thresholds come from the server. */
public final class ScreenshotPolicy
{
	private ScreenshotPolicy()
	{
	}

	public static boolean shouldCapture(OutboxEntry entry, PluginPolicy policy)
	{
		switch (entry.getType())
		{
			case LedgerEventType.LOOT:
				return entry.getPayload().get("totalValue").getAsLong() >= policy.getMinScreenshotLootValue();
			case LedgerEventType.COLLECTION_LOG_ITEM:
				return policy.isScreenshotCollectionLog();
			case LedgerEventType.PET:
				return policy.isScreenshotPets();
			default:
				return false;
		}
	}
}
