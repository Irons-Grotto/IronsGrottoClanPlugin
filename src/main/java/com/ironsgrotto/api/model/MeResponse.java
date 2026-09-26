package com.ironsgrotto.api.model;

import javax.annotation.Nullable;
import lombok.Data;

/** {@code GET /api/plugin/me}. */
@Data
public class MeResponse
{
	private String rsn;
	/** Null for an account that has linked the plugin but is not a clan member yet. */
	@Nullable
	private MemberStatus member;
	/** Where a prospective member signs up; null for members. */
	@Nullable
	private String joinUrl;
	private PluginPolicy policy = new PluginPolicy();
	/**
	 * Whether {@code /join} has the plugin steps. Null from a server older
	 * than the field, which always had them.
	 */
	@Nullable
	private Boolean pluginOnboarding;
}
