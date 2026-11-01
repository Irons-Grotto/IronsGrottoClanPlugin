package com.ironsgrotto.chat;

import com.ironsgrotto.api.model.MemberCard;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MemberCardCommandsTest
{
	private static MemberCard card(long points, Long next)
	{
		MemberCard card = new MemberCard();
		card.setRankLabel("Corporal");
		card.setPoints(points);
		card.setNextRankThreshold(next);
		return card;
	}

	@Test
	public void rankShowsPointsAgainstTheNextRank()
	{
		assertEquals("Corporal 1,800/3,000", MemberCardCommands.rankText(card(1_800, 3_000L)));
	}

	@Test
	public void rankAtTheTopHasNoNextRank()
	{
		assertEquals("Corporal 25,000", MemberCardCommands.rankText(card(25_000, null)));
	}
}
