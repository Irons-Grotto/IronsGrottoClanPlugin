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
	/** Where the panel's buttons go. Null from a server older than the field: no buttons. */
	@Nullable
	private Links links;

	@Data
	public static class Links
	{
		/** The clan's public about page, for accounts that are not members. */
		@Nullable
		private String about;
		@Nullable
		private String dashboard;
		@Nullable
		private String discord;
	}

	/** The clan message of the day; null when there is none, or from an older server. */
	@Nullable
	private Motd motd;

	@Data
	public static class Motd
	{
		/** Changes whenever the message does. */
		private String id;
		private String message;
		/** {@code clan} (set by staff) or {@code event} (the SOTW/BOTW line). */
		private String source;
	}
}
