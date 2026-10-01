package com.ironsgrotto.api.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** {@code GET /api/plugin/v1/top-loot}: one of the clan's biggest drops in the last 24 hours. */
@Data
public class TopLoot
{
	private String id;
	private String playerName;
	private String source;
	private long totalValue;
	/** ISO-8601 instant. */
	private String occurredAt;
	/** Most valuable stack first. */
	private List<Item> items = new ArrayList<>();

	@Data
	public static class Item
	{
		private int id;
		private String name;
		private int quantity;
		/** GE price per item when it dropped. */
		private long price;
	}
}
