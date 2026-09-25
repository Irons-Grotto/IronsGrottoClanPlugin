package com.ironsgrotto.api;

import com.ironsgrotto.IronsGrottoConfig;
import com.ironsgrotto.session.AccountIdentity;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.config.RuneScapeProfileType;

/**
 * One plugin token per in-game account, kept in RuneLite's per-account
 * ("RS profile") config.
 *
 * Per account rather than one global setting, because the server binds a
 * token to the first account that uses it: a global token would be sent for
 * every account logged in on this client, including a friend's. Per-account
 * config is also what RuneLite syncs between machines for members signed in
 * to a RuneLite account, so the token follows the account.
 *
 * Tokens are looked up by account hash, not "the current account", because
 * queued events and screenshots are sent after logout or an account switch,
 * as the account that produced them.
 */
@Singleton
public class TokenStore
{
	static final String KEY = "token";

	private final ConfigManager configManager;
	private volatile Consumer<AccountIdentity> onCleared = account -> { };

	@Inject
	TokenStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Called after the server made us drop an account's token. */
	public void setOnCleared(Consumer<AccountIdentity> onCleared)
	{
		this.onCleared = onCleared;
	}

	/** The account's token, or "" when it has none. */
	public String get(AccountIdentity account)
	{
		String token = read(account.getAccountHash());
		return token == null ? "" : token.trim();
	}

	public boolean has(AccountIdentity account)
	{
		return !get(account).isEmpty();
	}

	public void set(AccountIdentity account, String token)
	{
		write(account.getAccountHash(), token.trim());
	}

	/** Drops the account's token because the server will never accept it for this account. */
	public void clearRejected(AccountIdentity account)
	{
		remove(account.getAccountHash());
		onCleared.accept(account);
	}

	@Nullable
	protected String read(String accountHash)
	{
		String profile = profileKey(accountHash);
		return profile == null ? null : configManager.getConfiguration(IronsGrottoConfig.GROUP, profile, KEY);
	}

	protected void write(String accountHash, String token)
	{
		String profile = profileKey(accountHash);
		if (profile != null)
		{
			configManager.setConfiguration(IronsGrottoConfig.GROUP, profile, KEY, token);
		}
	}

	protected void remove(String accountHash)
	{
		String profile = profileKey(accountHash);
		if (profile != null)
		{
			configManager.unsetConfiguration(IronsGrottoConfig.GROUP, profile, KEY);
		}
	}

	/**
	 * RuneLite's profile key for the account's normal-world profile. Created
	 * by RuneLite on first login, so every account the plugin has seen has one.
	 */
	@Nullable
	private String profileKey(String accountHash)
	{
		long hash;
		try
		{
			hash = Long.parseLong(accountHash);
		}
		catch (NumberFormatException e)
		{
			return null;
		}

		for (RuneScapeProfile profile : configManager.getRSProfiles())
		{
			if (profile.getAccountHash() == hash && profile.getType() == RuneScapeProfileType.STANDARD)
			{
				return profile.getKey();
			}
		}
		return null;
	}
}
