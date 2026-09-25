package com.ironsgrotto.ui;

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
	public void saysWhenTheLogWasLastSynced()
	{
		java.time.Instant now = java.time.Instant.now();
		assertEquals("", GrottoPanel.syncedAgo(null));
		assertEquals("Synced just now", GrottoPanel.syncedAgo(now.minusSeconds(20)));
		assertEquals("Synced 5m ago", GrottoPanel.syncedAgo(now.minusSeconds(5 * 60 + 5)));
		assertEquals("Synced 3h ago", GrottoPanel.syncedAgo(now.minusSeconds(3 * 3600 + 60)));
		assertEquals("Synced 2d ago", GrottoPanel.syncedAgo(now.minusSeconds(2 * 86400 + 60)));
	}
}
