package com.ironsgrotto.api.model;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;

/** {@code GET /api/plugin/v1/member-card}: what the chat commands show for a member. */
@Data
public class MemberCard
{
	private String playerName;
	/** Rank id, which names the icon. */
	private String rank;
	/** What members call it. */
	private String rankLabel;
	private long points;
	/** Null at the top of the ladder and for mains. */
	@Nullable
	private Long nextRankThreshold;
	private int wins;
	/** Rarest in the clan first. */
	private List<Item> showcase = new ArrayList<>();

	@Data
	public static class Item
	{
		private String name;
		private int itemId;
	}
}
