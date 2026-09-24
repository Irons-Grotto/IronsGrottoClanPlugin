package com.ironsgrotto.session;

import lombok.Value;

/**
 * The in-game account a request speaks for. Captured when the data is
 * produced, so queued work is always sent as the account that generated it,
 * even if the player has since switched accounts.
 */
@Value
public class AccountIdentity
{
	/** RuneLite's account hash, as decimal text so it survives JSON exactly. */
	String accountHash;
	String rsn;
}
