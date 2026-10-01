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
}
