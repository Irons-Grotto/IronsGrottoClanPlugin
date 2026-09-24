package com.ironsgrotto.ledger;

import com.google.gson.JsonObject;
import com.ironsgrotto.outbox.OutboxEntry;
import com.ironsgrotto.session.AccountIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class KillLootLinkerTest
{
	private static final AccountIdentity ACCOUNT = new AccountIdentity("1", "Iron Dude");

	private long now = 1_000_000;
	private final KillLootLinker linker = new KillLootLinker(new Clock()
	{
		@Override
		public ZoneId getZone()
		{
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone)
		{
			return this;
		}

		@Override
		public Instant instant()
		{
			return Instant.ofEpochMilli(now);
		}
	});

	private static OutboxEntry kc(String boss, int kc)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("boss", boss);
		payload.addProperty("kc", kc);
		return OutboxEntry.create(LedgerEventType.BOSS_KC, payload, ACCOUNT, Instant.EPOCH);
	}

	private static OutboxEntry loot(String source, long value)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("source", source);
		payload.addProperty("totalValue", value);
		return OutboxEntry.create(LedgerEventType.LOOT, payload, ACCOUNT, Instant.EPOCH);
	}

	@Test
	public void lootAfterAKillCarriesTheKill()
	{
		OutboxEntry kill = kc("Vorkath", 101);
		OutboxEntry drop = loot("Vorkath", 4_000_000);

		linker.link(kill);
		now += 600;
		linker.link(drop);

		assertEquals(101, drop.getPayload().get("kc").getAsInt());
		assertEquals("Vorkath", drop.getPayload().get("kcBoss").getAsString());
		assertEquals(kill.getId(), drop.getPayload().get("kcEventId").getAsString());
	}

	@Test
	public void aRaidChestOpenedLaterStillLinksAcrossModes()
	{
		OutboxEntry kill = kc("Theatre of Blood: Hard Mode", 12);
		OutboxEntry chest = loot("Theatre of Blood", 1_500_000);

		linker.link(kill);
		now += 5 * 60_000;
		linker.link(chest);

		assertEquals("Theatre of Blood: Hard Mode", chest.getPayload().get("kcBoss").getAsString());
	}

	@Test
	public void aCountAfterTheChestCarriesTheLoot()
	{
		OutboxEntry chest = loot("Barrows", 250_000);
		OutboxEntry kill = kc("Barrows", 50);

		linker.link(chest);
		now += 100;
		linker.link(kill);

		assertEquals(chest.getId(), kill.getPayload().get("lootEventId").getAsString());
		assertEquals(250_000, kill.getPayload().get("lootValue").getAsLong());
	}

	@Test
	public void linksEachKillOnlyOnce()
	{
		OutboxEntry kill = kc("Vorkath", 101);
		OutboxEntry first = loot("Vorkath", 10);
		OutboxEntry second = loot("Vorkath", 20);

		linker.link(kill);
		linker.link(first);
		linker.link(second);

		assertFalse(second.getPayload().has("kc"));
	}

	@Test
	public void ignoresAKillFromTooLongAgo()
	{
		OutboxEntry kill = kc("Vorkath", 101);
		OutboxEntry drop = loot("Vorkath", 10);

		linker.link(kill);
		now += KillLootLinker.KC_BEFORE_LOOT_MS + 1;
		linker.link(drop);

		assertFalse(drop.getPayload().has("kc"));
	}

	@Test
	public void doesNotLinkDifferentBosses()
	{
		OutboxEntry kill = kc("Vorkath", 101);
		OutboxEntry drop = loot("Zulrah", 10);

		linker.link(kill);
		linker.link(drop);

		assertFalse(drop.getPayload().has("kc"));
	}

	@Test
	public void normalisesNamesAcrossKcAndLoot()
	{
		assertEquals("gauntlet", KillLootLinker.normalise("The Gauntlet"));
		assertEquals("gauntlet", KillLootLinker.normalise("Gauntlet"));
		assertEquals("chambers of xeric", KillLootLinker.normalise("Chambers of Xeric Challenge Mode"));
		assertEquals("tombs of amascut", KillLootLinker.normalise("Tombs of Amascut: Expert Mode"));
	}
}
