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
		name = "Account",
		description = "Linking the plugin to your Irons Grotto account",
		position = 0
	)
	String accountSection = "account";

	@ConfigSection(
		name = "Notifications",
		description = "What the plugin tells you in game",
		position = 1
	)
	String notificationsSection = "notifications";

	@ConfigSection(
		name = "Advanced",
		description = "Only needed when developing the plugin",
		position = 2,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "pluginToken",
		name = "Plugin token",
		description = "Generate one at ironsgrotto.xyz/plugin after signing in with Discord",
		secret = true,
		section = accountSection,
		position = 0
	)
	default String pluginToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "chatFeedback",
		name = "Chat messages",
		description = "Show messages from the clan server in your chatbox (e.g. drops recorded for an event)",
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
		description = "Screenshot valuable drops, new collection log slots and pets as proof, and share them in the clan drops channel",
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
		description = "Echo every request result into the chatbox",
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
		description = "Only change this when developing against a local server",
		section = advancedSection
	)
	default String apiBaseUrl()
	{
		return DEFAULT_API_BASE_URL;
	}

	@ConfigItem(
		keyName = "developerTools",
		name = "Developer tools",
		description = "Show buttons in the panel that spawn test events (marked as tests; never counted for clan events)",
		section = advancedSection
	)
	default boolean developerTools()
	{
		return false;
	}
}
