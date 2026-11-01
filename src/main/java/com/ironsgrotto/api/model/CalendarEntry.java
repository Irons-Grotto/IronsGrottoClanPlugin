package com.ironsgrotto.api.model;

import lombok.Data;

/** {@code GET /api/plugin/v1/calendar}: what is running or coming up. */
@Data
public class CalendarEntry
{
	private String title;
	/** {@code live}, {@code upcoming} or {@code expected}. */
	private String status;
	/** ISO-8601 instant. */
	private String startsAt;
	/** ISO-8601 instant. */
	private String endsAt;
}
