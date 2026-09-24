package com.ironsgrotto.api;

import lombok.Getter;

/** A request the server answered with an error, or could not be reached for. */
@Getter
public class ApiException extends Exception
{
	/** HTTP status, or 0 when the server could not be reached. */
	private final int status;

	public ApiException(int status, String message)
	{
		super(message);
		this.status = status;
	}

	public ApiException(String message, Throwable cause)
	{
		super(message, cause);
		this.status = 0;
	}

	/** Worth retrying later: network failures, rate limits and server errors. */
	public boolean isRetryable()
	{
		return status == 0 || status == 429 || status >= 500;
	}

	/** The token is missing, wrong or revoked. */
	public boolean isUnauthorized()
	{
		return status == 401;
	}
}
