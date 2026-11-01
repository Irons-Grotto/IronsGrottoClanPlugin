package com.ironsgrotto.chat;

import static org.junit.Assert.assertEquals;

import com.ironsgrotto.api.model.CalendarEntry;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.Test;

public class EventsCommandTest
{
	private static CalendarEntry entry(String title, String status, String startsAt, String endsAt)
	{
		CalendarEntry entry = new CalendarEntry();
		entry.setTitle(title);
		entry.setStatus(status);
		entry.setStartsAt(startsAt);
		entry.setEndsAt(endsAt);
		return entry;
	}

	@Test
	public void listsWhatIsOnThenWhatIsNext()
	{
		assertEquals("Now: Firemaking SOTW, 4d left | Fri 6 Nov 9:00am: BOTW",
			EventsCommand.format(List.of(
				entry("Firemaking SOTW", "live", "2026-10-30T14:00:00Z", "2026-11-06T10:00:00Z"),
				entry("BOTW", "expected", "2026-11-06T14:00:00Z", "2026-11-13T10:00:00Z")),
				Instant.parse("2026-11-01T12:00:00Z"), ZoneId.of("America/New_York")));
	}
}
