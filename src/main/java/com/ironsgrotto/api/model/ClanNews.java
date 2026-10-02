package com.ironsgrotto.api.model;

import lombok.Data;

/** {@code GET /api/plugin/v1/news}: one line of clan news. */
@Data
public class ClanNews
{
	/** {@code joined}, {@code rank_up}, {@code item} or {@code accomplishment}. */
	private String kind;
	private String playerName;
	/** What happened, after the name: "joined the clan", "Hydra's claw". */
	private String text;
	/** ISO-8601 instant. */
	private String at;
}
