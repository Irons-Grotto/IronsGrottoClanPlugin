package com.ironsgrotto.outbox;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ironsgrotto.api.ApiException;
import com.ironsgrotto.session.AccountIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OutboxTest
{
	private static final AccountIdentity MAIN = new AccountIdentity("123", "Iron Dude");
	private static final AccountIdentity ALT = new AccountIdentity("456", "Iron Alt");

	private final Gson gson = new Gson();
	private final List<String> messages = new ArrayList<>();
	private final List<List<OutboxEntry>> sent = new ArrayList<>();
	private MutableClock clock;
	private Path file;
	private ApiException nextFailure;

	@Before
	public void setUp() throws Exception
	{
		clock = new MutableClock();
		file = Files.createTempDirectory("outbox").resolve("outbox.json");
		nextFailure = null;
	}

	private Outbox outbox()
	{
		return new Outbox(this::send, new OutboxStore(file, gson), clock, messages::add);
	}

	private Outbox.SendResult send(AccountIdentity account, List<OutboxEntry> batch) throws ApiException
	{
		if (nextFailure != null)
		{
			throw nextFailure;
		}
		sent.add(batch);
		Set<String> ids = batch.stream().map(OutboxEntry::getId).collect(Collectors.toSet());
		return new Outbox.SendResult(ids, Collections.emptyMap(), Collections.singletonList("ok"));
	}

	private static OutboxEntry entry(AccountIdentity account)
	{
		return OutboxEntry.create("loot", new JsonObject(), account, Instant.EPOCH);
	}

	@Test
	public void sendsAndRemovesAcceptedEvents()
	{
		Outbox outbox = outbox();
		outbox.enqueue(entry(MAIN));
		outbox.enqueue(entry(MAIN));

		outbox.flush();

		assertEquals(1, sent.size());
		assertEquals(2, sent.get(0).size());
		assertEquals(0, outbox.size());
		assertEquals(Collections.singletonList("ok"), messages);
	}

	@Test
	public void survivesARestart()
	{
		outbox().enqueue(entry(MAIN));

		Outbox reloaded = outbox();

		assertEquals(1, reloaded.size());
		assertEquals(MAIN, reloaded.peekAll().get(0).getAccount());
	}

	@Test
	public void batchesOneAccountAtATime()
	{
		Outbox outbox = outbox();
		outbox.enqueue(entry(MAIN));
		outbox.enqueue(entry(ALT));
		outbox.enqueue(entry(MAIN));

		outbox.flush();

		assertEquals(2, sent.get(0).size());
		assertTrue(sent.get(0).stream().allMatch(e -> e.getAccount().equals(MAIN)));
		assertEquals(1, outbox.size());
	}

	@Test
	public void backsOffOnServerErrorAndKeepsEvents()
	{
		Outbox outbox = outbox();
		outbox.enqueue(entry(MAIN));
		nextFailure = new ApiException(503, "down");

		outbox.flush();
		nextFailure = null;
		outbox.flush();

		assertEquals(0, sent.size());
		assertEquals(1, outbox.size());

		clock.advance(Outbox.INITIAL_BACKOFF_MS);
		outbox.flush();

		assertEquals(1, sent.size());
		assertEquals(0, outbox.size());
	}

	@Test
	public void pausesOnBadTokenUntilResumed()
	{
		Outbox outbox = outbox();
		outbox.enqueue(entry(MAIN));
		nextFailure = new ApiException(401, "bad token");

		outbox.flush();
		nextFailure = null;
		clock.advance(Outbox.MAX_BACKOFF_MS);
		outbox.flush();

		assertTrue(outbox.isPaused());
		assertEquals(1, outbox.size());

		outbox.resume();
		outbox.flush();

		assertEquals(0, outbox.size());
	}

	@Test
	public void holdsEventsWhenThePluginIsOutdated()
	{
		Outbox outbox = outbox();
		outbox.enqueue(entry(MAIN));
		nextFailure = new ApiException(426, "update");

		outbox.flush();

		assertTrue(outbox.isPaused());
		assertEquals(1, outbox.size());
	}

	@Test
	public void discardsABatchTheServerCannotParse()
	{
		Outbox outbox = outbox();
		outbox.enqueue(entry(MAIN));
		nextFailure = new ApiException(400, "malformed");

		outbox.flush();

		assertEquals(0, outbox.size());
	}

	@Test
	public void dropsServerRejectedEventsButKeepsUnanswered()
	{
		OutboxEntry accepted = entry(MAIN);
		OutboxEntry rejected = entry(MAIN);
		OutboxEntry unanswered = entry(MAIN);
		Outbox outbox = new Outbox(
			(account, batch) -> new Outbox.SendResult(
				new HashSet<>(Collections.singletonList(accepted.getId())),
				Map.of(rejected.getId(), "too old"),
				Collections.emptyList()),
			new OutboxStore(file, gson), clock, messages::add);
		outbox.enqueue(accepted);
		outbox.enqueue(rejected);
		outbox.enqueue(unanswered);

		outbox.flush();

		assertEquals(Collections.singletonList(unanswered.getId()),
			outbox.peekAll().stream().map(OutboxEntry::getId).collect(Collectors.toList()));
	}

	@Test
	public void capsTheQueueByDroppingTheOldest()
	{
		Outbox outbox = outbox();
		OutboxEntry first = entry(MAIN);
		outbox.enqueue(first);
		for (int i = 0; i < Outbox.MAX_ENTRIES; i++)
		{
			outbox.enqueue(entry(MAIN));
		}

		assertEquals(Outbox.MAX_ENTRIES, outbox.size());
		assertTrue(outbox.peekAll().stream().noneMatch(e -> e.getId().equals(first.getId())));
	}

	private static class MutableClock extends Clock
	{
		private long millis = 1_000_000;

		void advance(long ms)
		{
			millis += ms;
		}

		@Override
		public long millis()
		{
			return millis;
		}

		@Override
		public Instant instant()
		{
			return Instant.ofEpochMilli(millis);
		}

		@Override
		public ZoneOffset getZone()
		{
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone)
		{
			return this;
		}
	}
}
