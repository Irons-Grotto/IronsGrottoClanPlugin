package com.ironsgrotto.session;

import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;

/**
 * Tracks which account is logged in, and whether its activity should count.
 *
 * The local player's name is not available on the tick the game reports
 * LOGGED_IN, so {@link #poll()} is called every game tick until it is. The
 * plugin calls {@link #clear()} on logout and world hops, so a hop onto an
 * excluded world stops tracking.
 */
@Slf4j
@Singleton
public class AccountSession
{
	/**
	 * Worlds whose progress is not the member's real account: temporary game
	 * modes, practice modes and worlds that reset.
	 */
	private static final Set<WorldType> EXCLUDED_WORLD_TYPES = EnumSet.of(
		WorldType.SEASONAL,
		WorldType.DEADMAN,
		WorldType.BETA_WORLD,
		WorldType.FRESH_START_WORLD,
		WorldType.PVP_ARENA,
		WorldType.QUEST_SPEEDRUNNING,
		WorldType.TOURNAMENT_WORLD,
		WorldType.NOSAVE_MODE
	);

	private final Client client;

	@Nullable
	private volatile AccountIdentity identity;

	@Inject
	AccountSession(Client client)
	{
		this.client = client;
	}

	/**
	 * Captures the account once the game can tell us who it is.
	 *
	 * @return true when the identity changed on this call
	 */
	public boolean poll()
	{
		if (identity != null || client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}

		Player player = client.getLocalPlayer();
		long hash = client.getAccountHash();

		if (player == null || player.getName() == null || hash == -1)
		{
			return false;
		}

		if (!isTrackedWorld())
		{
			return false;
		}

		identity = new AccountIdentity(Long.toString(hash), sanitiseName(player.getName()));
		log.debug("Irons Grotto session started for {}", identity.getRsn());
		return true;
	}

	/** @return true when there was an identity to clear */
	public boolean clear()
	{
		boolean had = identity != null;
		identity = null;
		return had;
	}

	/** The logged-in account, or null when logged out or on an excluded world. */
	@Nullable
	public AccountIdentity getIdentity()
	{
		return identity;
	}

	public boolean isTrackedWorld()
	{
		for (WorldType type : client.getWorldType())
		{
			if (EXCLUDED_WORLD_TYPES.contains(type))
			{
				return false;
			}
		}
		return true;
	}

	/** The game uses non-breaking spaces in names; the clan roster does not. */
	static String sanitiseName(String name)
	{
		return name.replace(' ', ' ').trim();
	}
}
