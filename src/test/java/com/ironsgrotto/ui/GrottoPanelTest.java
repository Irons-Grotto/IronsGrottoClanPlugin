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
}
