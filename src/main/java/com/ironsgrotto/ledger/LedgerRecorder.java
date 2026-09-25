package com.ironsgrotto.ledger;

import com.google.gson.JsonObject;
import com.ironsgrotto.outbox.Outbox;
import com.ironsgrotto.outbox.OutboxEntry;
import com.ironsgrotto.session.AccountIdentity;
import com.ironsgrotto.session.AccountSession;
import java.time.Instant;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Where every tracker hands its events. Stamps them with the account and the
 * moment they happened, then queues them in the outbox.
 *
 * Events are only recorded while the session has an account on a tracked
 * world — so nothing from a seasonal or beta world, and nothing before the
 * game has said who is logged in. They are recorded whether or not a token is
 * set yet; the outbox holds them until one is.
 */
@Slf4j
@Singleton
public class LedgerRecorder
{
	private final AccountSession session;
	private final KillLootLinker linker;

	private volatile Outbox outbox;
	private volatile Consumer<OutboxEntry> onRecorded = entry -> { };

	@Inject
	LedgerRecorder(AccountSession session, KillLootLinker linker)
	{
		this.session = session;
		this.linker = linker;
	}

	public void attach(Outbox outbox, Consumer<OutboxEntry> onRecorded)
	{
		this.outbox = outbox;
		this.onRecorded = onRecorded;
	}

	public void detach()
	{
		this.outbox = null;
		this.onRecorded = entry -> { };
	}

	/** @return true when the event was queued */
	public boolean record(String type, JsonObject payload, boolean test)
	{
		Outbox current = outbox;
		AccountIdentity account = session.getIdentity();

		if (current == null || account == null)
		{
			log.debug("Not recording {}: no session", type);
			return false;
		}

		OutboxEntry entry = OutboxEntry.create(type, payload, account, Instant.now(), test);
		linker.link(entry);
		current.enqueue(entry);
		onRecorded.accept(entry);
		return true;
	}
}
