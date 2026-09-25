package com.ironsgrotto.api;

import lombok.Getter;

/** A request the server answered with an error, or could not be reached for. */
@Getter
public class ApiException extends Exception
{
	/** The server's machine-readable reason for refusing this token for this account. */
	public static final String ACCOUNT_NOT_YOURS = "account_not_yours";
	public static final String TOKEN_ACCOUNT_MISMATCH = "token_account_mismatch";

	/** HTTP status, or 0 when the server could not be reached. */
	private final int status;
	/** The server's error code, when it sent one. */
	private final String code;

	public ApiException(int status, String message)
	{
		this(status, message, null);
	}

	public ApiException(int status, String message, String code)
	{
		super(message);
		this.status = status;
		this.code = code;
	}

	public ApiException(String message, Throwable cause)
	{
		super(message, cause);
		this.status = 0;
		this.code = null;
	}

	/** Worth retrying later: network failures, rate limits and server errors. */
	public boolean isRetryable()
	{
		return status == 0 || status == 429 || status >= 500;
	}

	/** This plugin release is older than the server supports; the member must update. */
	public boolean isUpgradeRequired()
	{
		return status == 426;
	}

	/**
	 * Nothing is wrong with the data, only with this client: a bad token, an
	 * account it may not speak for, or an outdated release. Keep the data and
	 * wait for the member to fix it.
	 */
	public boolean isClientBlocked()
	{
		return status == 401 || status == 403 || status == 426;
	}

	/**
	 * This token can never speak for this account: the account is someone
	 * else's, or the token is bound to another account. The token is dropped.
	 */
	public boolean isTokenRejectedForAccount()
	{
		return ACCOUNT_NOT_YOURS.equals(code) || TOKEN_ACCOUNT_MISMATCH.equals(code);
	}

	/** The token is missing, wrong or revoked. */
	public boolean isUnauthorized()
	{
		return status == 401;
	}
}
