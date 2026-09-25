package com.ironsgrotto.outbox;

import com.google.gson.JsonObject;
import com.ironsgrotto.session.AccountIdentity;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One ledger event waiting to be delivered.
 *
 * The id is generated here, when the event happens, and never changes — the
 * server treats a repeated id as the same event, which is what makes retrying
 * after a timeout safe.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEntry
{
	private String id;
	private String type;
	/** ISO-8601 instant the event happened in game. */
	private String occurredAt;
	private JsonObject payload;
	private AccountIdentity account;

	public static OutboxEntry create(String type, JsonObject payload, AccountIdentity account, Instant occurredAt)
	{
		return new OutboxEntry(UUID.randomUUID().toString(), type, occurredAt.toString(), payload, account);
	}
}
