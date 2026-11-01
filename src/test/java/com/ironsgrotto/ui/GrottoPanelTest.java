package com.ironsgrotto.ui;

import com.ironsgrotto.api.model.EventWinner;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GrottoPanelTest
{
	@Test
	public void tokenLinkNamesTheAccount()
	{
		assertEquals("https://ironsgrotto.xyz/plugin?name=Eclipse%20Goon",
			GrottoPanel.tokenUrlFor("https://ironsgrotto.xyz/plugin", "Eclipse Goon"));
	}

	@Test
	public void onlyAWholeTokenIsChecked()
	{
		String token = "igp_" + "a".repeat(40) + "_-9";
		assertTrue(GrottoPanel.looksLikeToken(token));
		assertFalse(GrottoPanel.looksLikeToken(token.substring(0, 30)));
		assertFalse(GrottoPanel.looksLikeToken("abc_" + "a".repeat(43)));
		assertFalse(GrottoPanel.looksLikeToken(token + "x"));
	}

	@Test
	public void shortGpReadsLikeOsrsValues()
	{
		assertEquals("950", GrottoPanel.shortGp(950));
		assertEquals("1K", GrottoPanel.shortGp(1_000));
		assertEquals("952K", GrottoPanel.shortGp(952_409));
		assertEquals("999K", GrottoPanel.shortGp(999_999));
		assertEquals("1.25M", GrottoPanel.shortGp(1_255_777));
		assertEquals("42.8M", GrottoPanel.shortGp(42_820_578));
		assertEquals("42M", GrottoPanel.shortGp(42_065_000));
		assertEquals("2.14B", GrottoPanel.shortGp(2_147_483_647L));
	}

	@Test
	public void winnerScoreShortensXpButNotKills()
	{
		assertEquals("3.56M xp", GrottoPanel.winnerScore("sotw", 3_566_550));
		assertEquals("932K xp", GrottoPanel.winnerScore("sotw", 932_400));
		assertEquals("1,204 kc", GrottoPanel.winnerScore("botw", 1_204));
	}

	private static EventWinner winner(String type, String name, long gained)
	{
		EventWinner winner = new EventWinner();
		winner.setType(type);
		winner.setPlayerName(name);
		winner.setGained(gained);
		return winner;
	}

	@Test
	public void lastWinnersKeepsTheNewestOfEachType()
	{
		assertEquals(
			java.util.List.of("Last BOTW: Lucky Fe, 147 kc", "Last SOTW: Slow Alpaca, 3.02M xp"),
			GrottoPanel.lastWinnerLines(java.util.List.of(
				winner("botw", "Lucky Fe", 147),
				winner("sotw", "Slow Alpaca", 3_027_955),
				winner("botw", "Push Myself", 435),
				winner("sotw", "Elfyrr", 3_566_550))));
	}

	@Test
	public void agoIsOneShortWord()
	{
		java.time.Instant now = java.time.Instant.parse("2026-10-02T12:00:00Z");
		assertEquals("now", GrottoPanel.ago("2026-10-02T11:59:30Z", now));
		assertEquals("45m", GrottoPanel.ago("2026-10-02T11:15:00Z", now));
		assertEquals("3h", GrottoPanel.ago("2026-10-02T09:00:00Z", now));
		assertEquals("2d", GrottoPanel.ago("2026-09-30T11:00:00Z", now));
		assertEquals("3w", GrottoPanel.ago("2026-09-10T12:00:00Z", now));
		assertEquals("", GrottoPanel.ago("not a date", now));
	}
}
