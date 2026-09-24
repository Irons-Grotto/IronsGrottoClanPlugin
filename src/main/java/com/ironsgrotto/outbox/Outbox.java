package com.ironsgrotto.outbox;

import com.ironsgrotto.api.ApiException;
import com.ironsgrotto.session.AccountIdentity;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * The single, durable queue every ledger event goes through.
 *
 * Events are appended as they happen and sent in batches by {@link #flush()},
 * which the plugin calls on a background schedule. Nothing is dropped for a
 * transient failure: network errors, rate limits and server errors back off
 * and retry. A bad token or an outdated plugin pauses sending until
 * {@link #resume()} — on the next start after an update, or a token change.
 */
@Slf4j
public class Outbox
{
	public static final int MAX_BATCH = 50;
	/** Beyond this the oldest events are discarded; a member offline for weeks. */
	public static final int MAX_ENTRIES = 5000;

	static final long INITIAL_BACKOFF_MS = 5_000;
	static final long MAX_BACKOFF_MS = 5 * 60_000;

	/** Delivers one batch, all for the same account. */
	public interface Sender
	{
		SendResult send(AccountIdentity account, List<OutboxEntry> batch) throws ApiException;
	}

	@Value
	public static class SendResult
	{
		/** Ids the server stored, or already had. */
		Set<String> accepted;
		/** Ids the server refused, with why; retrying would not help. */
		Map<String, String> rejected;
		/** Anything the server wants shown to the player. */
		List<String> messages;
	}

	private final List<OutboxEntry> entries;
	private final Sender sender;
	private final OutboxStore store;
	private final Clock clock;
	private final Consumer<String> messageSink;

	private long nextAttemptAt;
	private long backoffMs = INITIAL_BACKOFF_MS;
	private boolean paused;

	public Outbox(Sender sender, OutboxStore store, Clock clock, Consumer<String> messageSink)
	{
		this.sender = sender;
		this.store = store;
		this.clock = clock;
		this.messageSink = messageSink;
		this.entries = store.load();
	}

	public synchronized void enqueue(OutboxEntry entry)
	{
		entries.add(entry);

		if (entries.size() > MAX_ENTRIES)
		{
			int overflow = entries.size() - MAX_ENTRIES;
			log.warn("Irons Grotto outbox full, discarding {} oldest events", overflow);
			entries.subList(0, overflow).clear();
		}

		store.save(entries);
	}

	public synchronized int size()
	{
		return entries.size();
	}

	public synchronized boolean isPaused()
	{
		return paused;
	}

	/** Clears a pause or backoff, e.g. after the token is changed. */
	public synchronized void resume()
	{
		paused = false;
		nextAttemptAt = 0;
		backoffMs = INITIAL_BACKOFF_MS;
	}

	/**
	 * Sends the oldest batch, if it is time to. Blocks on the network, so call
	 * it from a background thread, never the client thread.
	 */
	public void flush()
	{
		AccountIdentity account;
		List<OutboxEntry> batch;

		synchronized (this)
		{
			if (paused || entries.isEmpty() || clock.millis() < nextAttemptAt)
			{
				return;
			}

			// One account per request: the headers name the account, and the
			// server checks every event against them.
			account = entries.get(0).getAccount();
			batch = new ArrayList<>();
			for (OutboxEntry entry : entries)
			{
				if (entry.getAccount().equals(account))
				{
					batch.add(entry);
					if (batch.size() == MAX_BATCH)
					{
						break;
					}
				}
			}
		}

		SendResult result;
		try
		{
			result = sender.send(account, batch);
		}
		catch (ApiException e)
		{
			handleFailure(e, batch);
			return;
		}

		Set<String> done = new HashSet<>(result.getAccepted());
		done.addAll(result.getRejected().keySet());
		result.getRejected().forEach((id, reason) -> log.warn("Server rejected event {}: {}", id, reason));

		synchronized (this)
		{
			entries.removeIf(entry -> done.contains(entry.getId()));
			backoffMs = INITIAL_BACKOFF_MS;
			nextAttemptAt = 0;
			store.save(entries);
		}

		result.getMessages().forEach(messageSink);
	}

	private synchronized void handleFailure(ApiException e, List<OutboxEntry> batch)
	{
		if (e.isRetryable())
		{
			log.debug("Outbox send failed ({}), retrying in {}ms", e.getMessage(), backoffMs);
			nextAttemptAt = clock.millis() + backoffMs;
			backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
			return;
		}

		if (e.isClientBlocked())
		{
			// Not the events' fault: the token, the account link or the plugin
			// version is wrong. Keep them and wait for the member to fix it.
			log.warn("Outbox paused: {}", e.getMessage());
			paused = true;
			return;
		}

		// Any other 4xx means the batch itself is malformed; resending it
		// unchanged would fail forever and block everything behind it.
		log.warn("Discarding {} events the server could not accept: {}", batch.size(), e.getMessage());
		Set<String> ids = new HashSet<>();
		batch.forEach(entry -> ids.add(entry.getId()));
		entries.removeIf(entry -> ids.contains(entry.getId()));
		store.save(entries);
	}

	/** A snapshot for display; not live. */
	public synchronized List<OutboxEntry> peekAll()
	{
		return Collections.unmodifiableList(new ArrayList<>(entries));
	}
}
