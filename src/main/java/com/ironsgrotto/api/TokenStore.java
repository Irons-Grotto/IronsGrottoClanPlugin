package com.ironsgrotto.api;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.ironsgrotto.IronsGrottoConfig;
import com.ironsgrotto.session.AccountIdentity;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.util.Filepath;

/**
 * One plugin token per in-game account, kept on this machine only.
 *
 * Per account rather than one setting, because the server binds a token to
 * the first account that uses it: a single token would be sent for every
 * account logged in on this client, including a friend's.
 *
 * Per machine, like any login token, in the plugin's data directory. Up to 1.1.0,
 * the plugin kept tokens in RuneLite's per-account config, which RuneLite syncs between
 * machines: a token pasted on one machine replaced, or wiped, the token on
 * another. A token found there is copied here the first time an account is
 * read, so upgrading needs no new token. The synced copy is left alone, since
 * clients on an older version read it.
 *
 * Tokens are looked up by account hash, not "the current account", because
 * queued events are sent after logout or an account switch,
 * as the account that produced them.
 */
@Slf4j
@Singleton
public class TokenStore
{
	static final String LEGACY_KEY = "token";

	private static final Type TOKEN_MAP = new TypeToken<Map<String, String>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private volatile BiConsumer<AccountIdentity, String> onCleared = (account, reason) -> { };

	/**
	 * Account hash to token. "" is an account whose token was dropped here,
	 * so the old synced copy isn't copied in again.
	 */
	private final Map<String, String> tokens = new HashMap<>();
	@Nullable
	private Filepath file;
	private Gson gson = new Gson();
	private boolean loaded;

	@Inject
	TokenStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Where this machine's tokens live. Until it is set, they are kept in memory. */
	public synchronized void open(Filepath file, Gson gson)
	{
		this.file = file;
		this.gson = gson;
		tokens.clear();
		loaded = false;
	}

	/** Called after the server made us drop an account's token, with why. */
	public void setOnCleared(BiConsumer<AccountIdentity, String> onCleared)
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
	public void clearRejected(AccountIdentity account, String reason)
	{
		remove(account.getAccountHash());
		onCleared.accept(account, reason);
	}

	@Nullable
	protected synchronized String read(String accountHash)
	{
		load();
		if (!tokens.containsKey(accountHash))
		{
			String synced = readSynced(accountHash);
			if (synced == null || synced.trim().isEmpty())
			{
				return null;
			}
			tokens.put(accountHash, synced.trim());
			save();
		}
		return tokens.get(accountHash);
	}

	protected synchronized void write(String accountHash, String token)
	{
		load();
		tokens.put(accountHash, token);
		save();
	}

	protected synchronized void remove(String accountHash)
	{
		load();
		tokens.put(accountHash, "");
		save();
	}

	/**
	 * The token older versions stored in the account's synced RS profile config, if
	 * any. RuneLite creates an account's RS profile on its first login, so
	 * every account the plugin has seen has one.
	 */
	@Nullable
	protected String readSynced(String accountHash)
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
				return configManager.getConfiguration(IronsGrottoConfig.GROUP, profile.getKey(), LEGACY_KEY);
			}
		}
		return null;
	}

	private void load()
	{
		if (loaded)
		{
			return;
		}
		loaded = true;

		if (file == null || !file.exists())
		{
			return;
		}

		try (Reader reader = file.openBufferedReader())
		{
			Map<String, String> stored = gson.fromJson(reader, TOKEN_MAP);
			if (stored != null)
			{
				tokens.putAll(stored);
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Could not read the Irons Grotto tokens", e);
		}
	}

	/** Written to a temp file and moved into place, like the outbox. */
	private void save()
	{
		if (file == null)
		{
			return;
		}

		try
		{
			file.getParent().createDirectories();
			Filepath temp = file.getParent().join(file.getFileName() + ".tmp");

			try (Writer writer = temp.openBufferedWriter())
			{
				gson.toJson(tokens, TOKEN_MAP, writer);
			}

			temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			log.warn("Could not save the Irons Grotto tokens", e);
		}
	}
}
