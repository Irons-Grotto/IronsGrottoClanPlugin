package com.ironsgrotto.ui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class GrottoPanelTest
{
	@Test
	public void tokenLinkNamesTheAccount()
	{
		assertEquals("https://ironsgrotto.xyz/plugin?name=Eclipse%20Goon",
			GrottoPanel.tokenUrlFor("https://ironsgrotto.xyz/plugin", "Eclipse Goon"));
	}
}
