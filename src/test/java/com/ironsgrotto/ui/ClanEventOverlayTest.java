package com.ironsgrotto.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClanEventOverlayTest
{
	private static final int LINE = 16;

	@Test
	public void showsTheDefaultUntilResized()
	{
		assertEquals(ClanEventOverlay.DEFAULT_ROWS, ClanEventOverlay.rowsFor(0, LINE, 25));
	}

	@Test
	public void taller_showsMore()
	{
		int threeRows = ClanEventOverlay.rowsFor(10 + 5 * LINE, LINE, 25);
		int sevenRows = ClanEventOverlay.rowsFor(10 + 9 * LINE, LINE, 25);
		assertEquals(3, threeRows);
		assertEquals(7, sevenRows);
	}

	@Test
	public void neverMoreThanThereAre_neverNone()
	{
		assertEquals(4, ClanEventOverlay.rowsFor(2000, LINE, 4));
		assertEquals(1, ClanEventOverlay.rowsFor(20, LINE, 25));
		assertEquals(0, ClanEventOverlay.rowsFor(500, LINE, 0));
		assertEquals(2, ClanEventOverlay.rowsFor(0, LINE, 2));
	}
}
