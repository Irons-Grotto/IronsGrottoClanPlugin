package com.ironsgrotto.progress;

import java.time.Instant;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Where the side panel's collection log sync stands, for the account logged
 * in now. The panel draws only from this.
 */
@Value
public class CollectionLogSyncState
{
	public enum Phase
	{
		/** Nothing running: waiting for the member. */
		IDLE,
		/** Reading the log, or sending what was read. */
		SYNCING,
		/** The server has the full log from the sync just run. */
		SYNCED,
		/** The read drew no items (the log closed, or never finished opening). */
		FAILED,
	}

	/** What the member should see. */
	public enum Stage
	{
		/** The log is closed; the member has to open it first. */
		OPEN_LOG,
		/** The log is open; the Sync button is live. */
		READY,
		SYNCING,
		SYNCED,
		FAILED,
	}

	boolean logOpen;
	Phase phase;
	/** When this account's full log last reached the server, or null if never. */
	@Nullable
	Instant lastSyncedAt;

	public Stage stage()
	{
		switch (phase)
		{
			case SYNCING:
				return Stage.SYNCING;
			case SYNCED:
				return Stage.SYNCED;
			case FAILED:
				return Stage.FAILED;
			default:
				return logOpen ? Stage.READY : Stage.OPEN_LOG;
		}
	}

	/** No full log has reached the server from this account yet. */
	public boolean isFirstSync()
	{
		return lastSyncedAt == null;
	}
}
