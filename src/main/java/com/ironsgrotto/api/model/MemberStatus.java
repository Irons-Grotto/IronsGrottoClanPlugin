package com.ironsgrotto.api.model;

import javax.annotation.Nullable;
import lombok.Data;

/** Where a clan member stands in the rank system. */
@Data
public class MemberStatus
{
	private String playerName;
	private String rank;
	private double points;
	@Nullable
	private String accountType;
	@Nullable
	private String staffRole;
	private double currentRankThreshold;
	@Nullable
	private String nextRank;
	@Nullable
	private Double nextRankThreshold;
}
