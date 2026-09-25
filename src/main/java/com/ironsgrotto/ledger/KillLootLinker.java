package com.ironsgrotto.ledger;

import com.google.gson.JsonObject;
import com.ironsgrotto.outbox.OutboxEntry;
import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Ties a kill count to the loot from that same kill or chest, so the ledger
 * can answer "a boss kill worth 1M+" or "a ToB chest with a purple".
 *
 * The two arrive in either order: a boss's kill count message comes as it
 * dies, before its loot lands; a raid's completion count comes when the raid
 * ends and the chest may be opened minutes later; Barrows says its count as
 * the chest opens. So whichever arrives second carries the link to the first:
 *
 * - loot after a kill count gets {@code kc}, {@code kcBoss}, {@code kcEventId};
 * - a kill count after loot gets {@code lootEventId}, {@code lootValue}.
 *
 * Each kill and each loot is linked at most once.
 */
@Singleton
public class KillLootLinker
{
	/** Loot this long after a kill count still belongs to it (raid chests). */
	static final long KC_BEFORE_LOOT_MS = 15 * 60_000;
	/** A kill count this long after loot still belongs to it (chests that count on opening). */
	static final long LOOT_BEFORE_KC_MS = 10_000;

	private final Clock clock;
	private final Map<String, Pending> kills = new HashMap<>();
	private final Map<String, Pending> loots = new HashMap<>();

	@Inject
	KillLootLinker()
	{
		this(Clock.systemUTC());
	}

	KillLootLinker(Clock clock)
	{
		this.clock = clock;
	}

	private static class Pending
	{
		final OutboxEntry entry;
		final long at;

		Pending(OutboxEntry entry, long at)
		{
			this.entry = entry;
			this.at = at;
		}
	}

	/** Adds link fields to the entry's payload, before it is queued. */
	public synchronized void link(OutboxEntry entry)
	{
		long now = clock.millis();
		JsonObject payload = entry.getPayload();

		if (LedgerEventType.BOSS_KC.equals(entry.getType()))
		{
			String key = normalise(payload.get("boss").getAsString());
			Pending loot = take(loots, key, now, LOOT_BEFORE_KC_MS);

			if (loot != null)
			{
				payload.addProperty("lootEventId", loot.entry.getId());
				payload.addProperty("lootValue", loot.entry.getPayload().get("totalValue").getAsLong());
			}
			else
			{
				kills.put(key, new Pending(entry, now));
			}
		}
		else if (LedgerEventType.LOOT.equals(entry.getType()))
		{
			String key = normalise(payload.get("source").getAsString());
			Pending kill = take(kills, key, now, KC_BEFORE_LOOT_MS);

			if (kill != null)
			{
				JsonObject killPayload = kill.entry.getPayload();
				payload.addProperty("kc", killPayload.get("kc").getAsInt());
				payload.addProperty("kcBoss", killPayload.get("boss").getAsString());
				payload.addProperty("kcEventId", kill.entry.getId());
			}
			else
			{
				loots.put(key, new Pending(entry, now));
			}
		}
	}

	private static Pending take(Map<String, Pending> pending, String key, long now, long window)
	{
		Pending candidate = pending.get(key);
		if (candidate == null || now - candidate.at > window)
		{
			return null;
		}
		pending.remove(key);
		return candidate;
	}

	/**
	 * One key for a boss's kill count name and its loot source name, which
	 * differ in places: "Theatre of Blood: Hard Mode" completes into a
	 * "Theatre of Blood" chest, "Chambers of Xeric Challenge Mode" into
	 * "Chambers of Xeric", "Gauntlet" is looted as "The Gauntlet".
	 */
	static String normalise(String name)
	{
		String key = name.toLowerCase(Locale.ROOT).trim();
		int colon = key.indexOf(':');
		if (colon > 0)
		{
			key = key.substring(0, colon);
		}
		key = key.replace(" challenge mode", "");
		if (key.startsWith("the "))
		{
			key = key.substring(4);
		}
		return key.trim();
	}
}
