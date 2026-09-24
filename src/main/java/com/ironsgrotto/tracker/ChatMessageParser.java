package com.ironsgrotto.tracker;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * Reads the game messages the ledger cares about. Pure, so every pattern is
 * unit tested against the game's real wording.
 *
 * Messages must already have their colour tags removed.
 */
public final class ChatMessageParser
{
	/*
	 * "Your Vorkath kill count is: 123."
	 * "Your completed Chambers of Xeric count is: 5."
	 * "Your Barrows chest count is: 1,234."
	 * "Your subdued Wintertodt count is: 10."
	 * "Your Gauntlet completion count is: 3."
	 */
	private static final Pattern KILL_COUNT = Pattern.compile(
		"^Your (?:completed |subdued )?(.+?)(?: total)?(?: kill| chest| completion| harvest| success)? count is: ([\\d,]+)\\.?$");

	private static final Pattern COLLECTION_LOG = Pattern.compile(
		"^New item added to your collection log: (.+?)\\.?$");

	/* "You have completed 12 hard Treasure Trails." */
	private static final Pattern CLUE_COUNT = Pattern.compile(
		"^You have completed ([\\d,]+) (beginner|easy|medium|hard|elite|master) Treasure Trails?\\.?$", Pattern.CASE_INSENSITIVE);

	private static final String PET_FOLLOWER = "You have a funny feeling like you're being followed";
	private static final String PET_BACKPACK = "You feel something weird sneaking into your backpack";
	private static final String PET_DUPLICATE = "You have a funny feeling like you would have been followed";

	private ChatMessageParser()
	{
	}

	@Value
	public static class KillCount
	{
		String boss;
		int kc;
	}

	public static Optional<KillCount> killCount(String message)
	{
		// Agility courses use the same wording for laps; they are not kills.
		if (message.contains(" lap count is"))
		{
			return Optional.empty();
		}

		Matcher matcher = KILL_COUNT.matcher(message.trim());
		if (!matcher.matches())
		{
			return Optional.empty();
		}

		try
		{
			int kc = Integer.parseInt(matcher.group(2).replace(",", ""));
			return kc > 0 ? Optional.of(new KillCount(matcher.group(1).trim(), kc)) : Optional.empty();
		}
		catch (NumberFormatException e)
		{
			return Optional.empty();
		}
	}

	public static Optional<String> collectionLogItem(String message)
	{
		Matcher matcher = COLLECTION_LOG.matcher(message.trim());
		return matcher.matches() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
	}

	@Value
	public static class ClueCount
	{
		/** Beginner, Easy, Medium, Hard, Elite or Master — the server's tier names. */
		String tier;
		int count;
	}

	public static Optional<ClueCount> clueCount(String message)
	{
		Matcher matcher = CLUE_COUNT.matcher(message.trim());
		if (!matcher.matches())
		{
			return Optional.empty();
		}
		String tier = matcher.group(2).toLowerCase();
		try
		{
			return Optional.of(new ClueCount(
				Character.toUpperCase(tier.charAt(0)) + tier.substring(1),
				Integer.parseInt(matcher.group(1).replace(",", ""))));
		}
		catch (NumberFormatException e)
		{
			return Optional.empty();
		}
	}

	/** @return follower, backpack or duplicate — the server's pet variants */
	public static Optional<String> petVariant(String message)
	{
		if (message.startsWith(PET_DUPLICATE))
		{
			return Optional.of("duplicate");
		}
		if (message.startsWith(PET_FOLLOWER))
		{
			return Optional.of("follower");
		}
		if (message.startsWith(PET_BACKPACK))
		{
			return Optional.of("backpack");
		}
		return Optional.empty();
	}
}
