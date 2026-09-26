package com.ironsgrotto.api.model;

import lombok.Data;

/** Server-controlled behaviour. Missing values fall back to these defaults. */
@Data
public class PluginPolicy
{
	private int panelRefreshSeconds = 300;
}
