package com.ironsgrotto.screenshot;

import com.ironsgrotto.api.ApiException;
import com.ironsgrotto.api.GrottoApiClient;
import com.ironsgrotto.outbox.Outbox;
import com.ironsgrotto.outbox.OutboxEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Uploads queued screenshots once their events have been delivered.
 *
 * An event still in the outbox is not on the server yet, so its screenshot
 * waits. The server answering 404 means the same; after a day of that the
 * event was evidently rejected and the screenshot is discarded.
 */
@Slf4j
public class ScreenshotUploader
{
	static final long GIVE_UP_AFTER_MS = 24 * 60 * 60_000L;

	private final ScreenshotStore store;
	private final Outbox outbox;
	private final GrottoApiClient api;
	private final Clock clock;

	public ScreenshotUploader(ScreenshotStore store, Outbox outbox, GrottoApiClient api, Clock clock)
	{
		this.store = store;
		this.outbox = outbox;
		this.api = api;
		this.clock = clock;
	}

	/** One pass over the queue. Blocks on the network; call off the client thread. */
	public void uploadPending()
	{
		Set<String> undelivered = new HashSet<>();
		for (OutboxEntry entry : outbox.peekAll())
		{
			undelivered.add(entry.getId());
		}

		for (ScreenshotStore.Pending pending : store.list())
		{
			if (undelivered.contains(pending.getEventId()))
			{
				continue;
			}

			try
			{
				api.uploadScreenshot(pending.getAccount(), pending.getEventId(), Files.readAllBytes(pending.getImage()));
				store.remove(pending.getEventId());
			}
			catch (ApiException e)
			{
				if (e.getStatus() == 404)
				{
					if (clock.millis() - pending.getCreatedAt().toMillis() > GIVE_UP_AFTER_MS)
					{
						store.remove(pending.getEventId());
					}
				}
				else if (e.isRetryable() || e.isClientBlocked())
				{
					// Try again next pass; stop this one so a dead server is not
					// hit once per screenshot.
					return;
				}
				else
				{
					log.warn("Server refused screenshot {}: {}", pending.getEventId(), e.getMessage());
					store.remove(pending.getEventId());
				}
			}
			catch (IOException e)
			{
				log.warn("Could not read screenshot {}", pending.getEventId(), e);
				store.remove(pending.getEventId());
			}
		}
	}
}
