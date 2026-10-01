package com.ironsgrotto;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.ui.overlay.components.ComponentConstants;

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
		name = "Overlay",
		description = "SOTW/BOTW standings on the game screen",
		position = 2
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Advanced",
		description = "For plugin development",
		position = 3,
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
		keyName = "showEventOverlay",
		name = "Show SOTW/BOTW overlay",
		description = "Standings on the game screen. Hold Alt to drag it, and drag an edge to resize; taller shows more rows",
		section = overlaySection,
		position = 0
	)
	default boolean showEventOverlay()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "overlayBackground",
		name = "Background",
		description = "The overlay's background colour; lower the opacity to see the game through it",
		section = overlaySection,
		position = 1
	)
	default Color overlayBackground()
	{
		return ComponentConstants.STANDARD_BACKGROUND_COLOR;
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
