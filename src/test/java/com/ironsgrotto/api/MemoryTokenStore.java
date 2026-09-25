package com.ironsgrotto.api;

import java.util.HashMap;
import java.util.Map;

/** A {@link TokenStore} in memory, keyed by account hash, for tests. */
public class MemoryTokenStore extends TokenStore
{
	final Map<String, String> tokens = new HashMap<>();

	public MemoryTokenStore()
	{
		super(null);
	}

	@Override
	protected String read(String accountHash)
	{
		return tokens.get(accountHash);
	}

	@Override
	protected void write(String accountHash, String token)
	{
		tokens.put(accountHash, token);
	}

	@Override
	protected void remove(String accountHash)
	{
		tokens.remove(accountHash);
	}
}
