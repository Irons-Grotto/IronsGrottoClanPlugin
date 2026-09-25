package com.ironsgrotto;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(IronsGrottoConfig.GROUP)
public interface IronsGrottoConfig extends Config
{
	String GROUP = "ironsgrotto";
	String DEFAULT_API_BASE_URL = "https://ironsgrotto.xyz";

	@ConfigSection(
		name = "Notifications",
		description = "What the plugin shows in game",
		position = 1
	)
	String notificationsSection = "notifications";

	@ConfigSection(
		name = "Advanced",
		description = "For plugin development",
		position = 2,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "chatFeedback",
		name = "Chat messages",
		description = "Confirm recorded drops, log slots and pets in chat",
		section = notificationsSection,
		position = 0
	)
	default boolean chatFeedback()
	{
		return true;
	}

	@ConfigItem(
		keyName = "screenshots",
		name = "Screenshots",
		description = "Screenshot valuable drops, new log slots and pets for the clan drops channel",
		section = notificationsSection,
		position = 2
	)
	default boolean screenshots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debug",
		name = "Debug messages",
		description = "Show every server response in chat",
		section = notificationsSection,
		position = 1
	)
	default boolean debug()
	{
		return false;
	}

	@ConfigItem(
		keyName = "apiBaseUrl",
		name = "Server URL",
		description = "For local development only",
		section = advancedSection
	)
	default String apiBaseUrl()
	{
		return DEFAULT_API_BASE_URL;
	}

}
