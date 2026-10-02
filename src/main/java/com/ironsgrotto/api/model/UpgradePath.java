package com.ironsgrotto.api.model;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;

/** {@code GET /api/plugin/v1/upgrade-path}: the member's next unlocks. */
@Data
public class UpgradePath
{
	private Ranks ranks = new Ranks();
	/** Members compared with. */
	private int cohortSize;
	/** Most common first. */
	private List<Item> items = new ArrayList<>();

	@Data
	public static class Ranks
	{
		private String current;
		@Nullable
		private String next;
	}

	@Data
	public static class Item
	{
		private String name;
		/** For the icon; null when no member has logged it. */
		@Nullable
		private Integer itemId;
		private int owners;
		/** 0 to 1. */
		private double share;
	}
}
