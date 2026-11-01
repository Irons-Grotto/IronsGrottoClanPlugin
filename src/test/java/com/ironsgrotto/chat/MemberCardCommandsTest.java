package com.ironsgrotto.chat;

import com.ironsgrotto.api.model.MemberCard;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MemberCardCommandsTest
{
	private static MemberCard card(long points, Long next)
	{
		MemberCard card = new MemberCard();
		card.setPoints(points);
		card.setNextRankThreshold(next);
		return card;
	}

	@Test
	public void pointsShowAgainstTheNextRank()
	{
		assertEquals("1,800/3,000", MemberCardCommands.pointsText(card(1_800, 3_000L)));
	}

	@Test
	public void pointsAtTheTopHaveNoNextRank()
	{
		assertEquals("25,000", MemberCardCommands.pointsText(card(25_000, null)));
	}

	@Test
	public void positionOnlyShowsWhenTheServerSendsOne()
	{
		MemberCard card = card(16_877, 19_000L);
		assertNull(MemberCardCommands.positionText(card));

		card.setLeaderboardPosition(12);
		assertEquals("#12 in the Grotto", MemberCardCommands.positionText(card));
	}
}
