package com.ironsgrotto.api.model;

import lombok.Data;

/** Server-controlled behaviour. Missing values fall back to these defaults. */
@Data
public class PluginPolicy
{
	private long minScreenshotLootValue = 1_000_000;
	private boolean screenshotCollectionLog = true;
	private boolean screenshotPets = true;
	private int panelRefreshSeconds = 300;
}
