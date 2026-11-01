package com.ironsgrotto.api.model;

import lombok.Data;

/** {@code GET /api/plugin/v1/event-winners}: who won a finished SOTW or BOTW. */
@Data
public class EventWinner
{
	private long eventId;
	/** {@code sotw} or {@code botw}. */
	private String type;
	private String metricName;
	/** ISO-8601 instant. */
	private String endsAt;
	private String playerName;
	/** Experience for a skill week, kills for a boss week. */
	private long gained;
}
