package com.ironsgrotto.api.model;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** {@code GET /api/plugin/clan-events}: the running SOTW/BOTW and the next one. */
@Data
public class ClanEventStatus
{
	@Nullable
	private ActiveEvent active;
	@Nullable
	private EventSummary next;

	@Data
	public static class EventSummary
	{
		private long id;
		/** {@code sotw} or {@code botw}. */
		private String type;
		private String typeLabel;
		private String name;
		private String metricName;
		/** ISO-8601 instants. */
		private String startsAt;
		private String endsAt;
	}

	@Data
	@EqualsAndHashCode(callSuper = true)
	public static class ActiveEvent extends EventSummary
	{
		private int participantCount;
		private List<Standing> standings = new ArrayList<>();
		private boolean standingsUnavailable;
	}

	@Data
	public static class Standing
	{
		private int position;
		private String playerName;
		private long gained;
	}
}
