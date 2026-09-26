package com.ironsgrotto.api.model;

import javax.annotation.Nullable;
import lombok.Data;

/** {@code GET /public/registration}'s data. */
@Data
public class Registration
{
	private boolean registered;
	/**
	 * Whether {@code /join} sets up the plugin and makes its token. Null from
	 * a server older than the field, which always did.
	 */
	@Nullable
	private Boolean pluginOnboarding;

	/** A new account joins first, when joining makes the token; otherwise it gets one from the token page. */
	public boolean sendsToJoin()
	{
		return !registered && !Boolean.FALSE.equals(pluginOnboarding);
	}
}
