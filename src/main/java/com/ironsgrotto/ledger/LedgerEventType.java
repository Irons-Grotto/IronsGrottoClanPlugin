package com.ironsgrotto.ledger;

/** The ledger's event types; the strings are the server's (app/schemas/plugin-ledger.ts). */
public final class LedgerEventType
{
	public static final String LOOT = "loot";
	public static final String COLLECTION_LOG_ITEM = "collection_log_item";
	public static final String BOSS_KC = "boss_kc";
	public static final String PET = "pet";

	private LedgerEventType()
	{
	}
}
