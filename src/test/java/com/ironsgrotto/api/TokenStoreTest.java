package com.ironsgrotto.api;

import com.google.gson.Gson;
import com.ironsgrotto.session.AccountIdentity;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.util.Filepath;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenStoreTest
{
	private static final AccountIdentity MAIN = new AccountIdentity("123", "Iron Dude");
	private static final AccountIdentity ALT = new AccountIdentity("456", "Iron Alt");

	/** What 1.1.0 left in the synced RS profile config, by account hash. */
	private final Map<String, String> synced = new HashMap<>();
	private Filepath file;

	@Before
	public void setUp() throws Exception
	{
		file = Filepath.Unchecked.getRooted(Files.createTempDirectory("tokens")).join("tokens.json");
	}

	private TokenStore store()
	{
		TokenStore store = new TokenStore(null)
		{
			@Override
			protected String readSynced(String accountHash)
			{
				return synced.get(accountHash);
			}
		};
		store.open(file, new Gson());
		return store;
	}

	@Test
	public void keepsTokensPerAccountAcrossRestarts()
	{
		TokenStore store = store();
		store.set(MAIN, " igp_main ");
		store.set(ALT, "igp_alt");

		TokenStore restarted = store();
		assertEquals("igp_main", restarted.get(MAIN));
		assertEquals("igp_alt", restarted.get(ALT));
	}

	@Test
	public void copiesTheSyncedTokenOnce()
	{
		synced.put(MAIN.getAccountHash(), "igp_synced");
		assertEquals("igp_synced", store().get(MAIN));

		// Another machine pasting a new token no longer changes this one.
		synced.put(MAIN.getAccountHash(), "igp_other_machine");
		assertEquals("igp_synced", store().get(MAIN));
	}

	@Test
	public void aTokenSetHereWinsOverTheSyncedOne()
	{
		synced.put(MAIN.getAccountHash(), "igp_synced");
		TokenStore store = store();
		store.set(MAIN, "igp_local");

		assertEquals("igp_local", store().get(MAIN));
	}

	@Test
	public void aRejectedTokenIsNotCopiedBackFromSync()
	{
		synced.put(MAIN.getAccountHash(), "igp_synced");
		TokenStore store = store();
		assertTrue(store.has(MAIN));

		store.clearRejected(MAIN, "revoked");

		assertFalse(store.has(MAIN));
		assertFalse(store().has(MAIN));
	}

	@Test
	public void anAccountWithNoTokenHasNone()
	{
		assertEquals("", store().get(MAIN));
		assertFalse(store().has(MAIN));
	}
}
