package com.ironsgrotto.chat;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;

public class BroadcastPollerTest
{
	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	private static final Instant NOW = Instant.parse("2026-11-06T14:00:00Z");

	@Test
	public void fillsInTheEndWithItsDay()
	{
		assertEquals("Live until Fri 5:00am.", BroadcastPoller.withEndTime("Live until {end}.",
			"2026-11-13T10:00:00Z", NEW_YORK, NOW));
	}

	@Test
	public void leavesOutTheDayForToday()
	{
		assertEquals("Live until 11:00pm.", BroadcastPoller.withEndTime("Live until {end}.",
			"2026-11-07T04:00:00Z", NEW_YORK, NOW));
	}

	@Test
	public void leavesAMessageWithNoEndAlone()
	{
		assertEquals("Party at 8.", BroadcastPoller.withEndTime("Party at 8.", null, NEW_YORK, NOW));
	}
}
