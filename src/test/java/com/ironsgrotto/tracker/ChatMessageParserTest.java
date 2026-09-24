package com.ironsgrotto.tracker;

import java.util.Optional;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ChatMessageParserTest
{
	private static void assertKc(String message, String boss, int kc)
	{
		Optional<ChatMessageParser.KillCount> parsed = ChatMessageParser.killCount(message);
		assertEquals(message, Optional.of(new ChatMessageParser.KillCount(boss, kc)), parsed);
	}

	@Test
	public void readsKillCounts()
	{
		assertKc("Your Vorkath kill count is: 123.", "Vorkath", 123);
		assertKc("Your TzTok-Jad kill count is: 1.", "TzTok-Jad", 1);
		assertKc("Your completed Chambers of Xeric count is: 5.", "Chambers of Xeric", 5);
		assertKc("Your completed Theatre of Blood: Hard Mode count is: 12.", "Theatre of Blood: Hard Mode", 12);
		assertKc("Your Barrows chest count is: 1,234.", "Barrows", 1234);
		assertKc("Your subdued Wintertodt count is: 10.", "Wintertodt", 10);
		assertKc("Your Gauntlet completion count is: 3.", "Gauntlet", 3);
		assertKc("Your Tombs of Amascut: Expert Mode total completion count is: 7.", "Tombs of Amascut: Expert Mode", 7);
	}

	@Test
	public void ignoresLapsAndOtherMessages()
	{
		assertFalse(ChatMessageParser.killCount("Your Ardougne Rooftop lap count is: 4.").isPresent());
		assertFalse(ChatMessageParser.killCount("Fight duration: 1:23. Personal best: 1:10").isPresent());
		assertFalse(ChatMessageParser.killCount("Your Vorkath kill count is: 0.").isPresent());
	}

	@Test
	public void readsCollectionLogSlots()
	{
		assertEquals(Optional.of("Vorki"), ChatMessageParser.collectionLogItem("New item added to your collection log: Vorki"));
		assertEquals(Optional.of("Dragon warhammer"), ChatMessageParser.collectionLogItem("New item added to your collection log: Dragon warhammer."));
		assertFalse(ChatMessageParser.collectionLogItem("Welcome to Old School RuneScape.").isPresent());
	}

	@Test
	public void readsPetMessages()
	{
		assertEquals(Optional.of("follower"), ChatMessageParser.petVariant("You have a funny feeling like you're being followed."));
		assertEquals(Optional.of("backpack"), ChatMessageParser.petVariant("You feel something weird sneaking into your backpack."));
		assertEquals(Optional.of("duplicate"), ChatMessageParser.petVariant("You have a funny feeling like you would have been followed..."));
		assertFalse(ChatMessageParser.petVariant("You feel a funny feeling.").isPresent());
	}

	@Test
	public void readsClueCounts()
	{
		assertEquals(Optional.of(new ChatMessageParser.ClueCount("Hard", 12)), ChatMessageParser.clueCount("You have completed 12 hard Treasure Trails."));
		assertEquals(Optional.of(new ChatMessageParser.ClueCount("Beginner", 1)), ChatMessageParser.clueCount("You have completed 1 beginner Treasure Trail."));
		assertEquals(Optional.of(new ChatMessageParser.ClueCount("Master", 1234)), ChatMessageParser.clueCount("You have completed 1,234 master Treasure Trails."));
		assertFalse(ChatMessageParser.clueCount("You have completed the Treasure Trail.").isPresent());
	}
}
