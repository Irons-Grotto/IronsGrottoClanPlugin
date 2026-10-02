package com.ironsgrotto.tracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;

public class LootEventTrackerTest
{
	@Test
	public void skipsSailingSalvage()
	{
		assertTrue(LootEventTracker.isIgnored(LootRecordType.EVENT, "Opulent salvage"));
		assertTrue(LootEventTracker.isIgnored(LootRecordType.EVENT, "Fishy salvage"));
	}

	@Test
	public void keepsOtherLoot()
	{
		assertFalse(LootEventTracker.isIgnored(LootRecordType.EVENT, "Bird nest"));
		assertFalse(LootEventTracker.isIgnored(LootRecordType.EVENT, "Chambers of Xeric"));
		assertFalse(LootEventTracker.isIgnored(LootRecordType.NPC, "Opulent salvage"));
		assertFalse(LootEventTracker.isIgnored(LootRecordType.EVENT, null));
	}

	@Test
	public void recognisesPickpocketMessages()
	{
		assertTrue(LootEventTracker.isPickpocket("You pick the H.A.M. Member's pocket."));
		assertTrue(LootEventTracker.isPickpocket("You pick the Master Farmer's pocket."));
		assertFalse(LootEventTracker.isPickpocket("You fail to pick the Master Farmer's pocket."));
		assertFalse(LootEventTracker.isPickpocket("Your Maggot King kill count is: 673."));
	}
}
