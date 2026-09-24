package com.ironsgrotto.progress;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ironsgrotto.api.ApiException;
import com.ironsgrotto.api.GrottoApiClient;
import com.ironsgrotto.session.AccountIdentity;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends account progress to the server, a category at a time.
 *
 * Progress is state, not history, so there is no queue: the latest reading of
 * each category waits here until it is sent, replacing any older one. A
 * category identical to the last one sent is not sent again. Nothing is kept
 * on disk — a lost reading is simply taken again next login.
 */
@Slf4j
@Singleton
public class ProgressUploader
{
	static final long INITIAL_BACKOFF_MS = 10_000;
	static final long MAX_BACKOFF_MS = 10 * 60_000;

	private final GrottoApiClient api;
	private final Clock clock;

	private final Map<String, JsonObject> pending = new HashMap<>();
	private final Map<String, JsonElement> lastSent = new HashMap<>();
	private AccountIdentity pendingAccount;
	private long nextAttemptAt;
	private long backoffMs = INITIAL_BACKOFF_MS;
	private volatile Consumer<JsonObject> onSynced = result -> { };

	@Inject
	ProgressUploader(GrottoApiClient api)
	{
		this(api, Clock.systemUTC());
	}

	ProgressUploader(GrottoApiClient api, Clock clock)
	{
		this.api = api;
		this.clock = clock;
	}

	/** Called with the server's reply (applied categories, points, rank) after each upload. */
	public void setOnSynced(Consumer<JsonObject> onSynced)
	{
		this.onSynced = onSynced;
	}

	/** Queues one category's latest reading; a no-op if it matches what was last sent. */
	public synchronized void submit(AccountIdentity account, String category, JsonElement value)
	{
		if (!account.equals(pendingAccount))
		{
			// A different account: nothing queued or remembered applies to it.
			pending.clear();
			lastSent.clear();
			pendingAccount = account;
		}

		if (Objects.equals(lastSent.get(category), value))
		{
			return;
		}

		JsonObject queued = pending.get(category);
		if (queued != null && queued.get(category).isJsonObject() && value.isJsonObject())
		{
			// Merge rather than replace: the collection log's counters and its
			// item list arrive separately and both belong in the next upload.
			JsonObject merged = queued.get(category).getAsJsonObject().deepCopy();
			for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet())
			{
				merged.add(field.getKey(), field.getValue());
			}
			value = merged;
		}

		JsonObject part = new JsonObject();
		part.add(category, value);
		pending.put(category, part);
	}

	/** Sends everything waiting, in one request. Blocking; background threads only. */
	public void flush()
	{
		AccountIdentity account;
		JsonObject body = new JsonObject();

		synchronized (this)
		{
			if (pending.isEmpty() || pendingAccount == null || clock.millis() < nextAttemptAt)
			{
				return;
			}
			account = pendingAccount;
			for (JsonObject part : pending.values())
			{
				for (Map.Entry<String, JsonElement> entry : part.entrySet())
				{
					body.add(entry.getKey(), entry.getValue());
				}
			}
		}

		try
		{
			JsonObject result = api.putProgress(account, body);
			synchronized (this)
			{
				if (account.equals(pendingAccount))
				{
					for (Map.Entry<String, JsonElement> entry : body.entrySet())
					{
						lastSent.put(entry.getKey(), entry.getValue());
						// Only drop it if nothing newer arrived while sending.
						JsonObject queued = pending.get(entry.getKey());
						if (queued != null && Objects.equals(queued.get(entry.getKey()), entry.getValue()))
						{
							pending.remove(entry.getKey());
						}
					}
				}
				backoffMs = INITIAL_BACKOFF_MS;
				nextAttemptAt = 0;
			}
			onSynced.accept(result);
		}
		catch (ApiException e)
		{
			synchronized (this)
			{
				if (e.isRetryable() || e.getStatus() == 401 || e.getStatus() == 403)
				{
					nextAttemptAt = clock.millis() + backoffMs;
					backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
				}
				else
				{
					log.warn("Server refused progress ({}), dropping it: {}", body.keySet(), e.getMessage());
					pending.clear();
				}
			}
		}
	}

	public synchronized boolean hasPending()
	{
		return !pending.isEmpty();
	}
}
