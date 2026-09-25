package com.ironsgrotto.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.ironsgrotto.IronsGrottoConfig;
import com.ironsgrotto.api.model.ClanEventStatus;
import com.ironsgrotto.api.model.MeResponse;
import com.ironsgrotto.session.AccountIdentity;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The only thing in the plugin that talks to the Irons Grotto server.
 *
 * Every request carries the account it speaks for and that account's plugin
 * token (see {@link TokenStore}). When the server says the token can never
 * speak for the account, the token is dropped here, in one place. Responses
 * use the backend's envelope, {@code {success, data}} or {@code {success, error}}.
 */
@Slf4j
@Singleton
public class GrottoApiClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType JPEG = MediaType.parse("image/jpeg");
	/**
	 * The API contract this release speaks. v1 routes only ever change in
	 * backward-compatible ways; a breaking change is a new version beside it.
	 */
	public static final String API_PREFIX = "/api/plugin/v1";

	/**
	 * Sent with every request. The server refuses releases older than its
	 * minimum with 426, which the plugin shows as "please update".
	 */
	public static final String PLUGIN_VERSION = "1.0.0";

	private final OkHttpClient http;
	private final Gson gson;
	private final IronsGrottoConfig config;
	private final TokenStore tokens;
	private final String userAgent;

	@Inject
	GrottoApiClient(OkHttpClient http, Gson gson, IronsGrottoConfig config, TokenStore tokens)
	{
		this(http, gson, config, tokens, "IronsGrottoPlugin/" + PLUGIN_VERSION);
	}

	protected GrottoApiClient(OkHttpClient http, Gson gson, IronsGrottoConfig config, TokenStore tokens, String userAgent)
	{
		this.http = http;
		this.gson = gson;
		this.config = config;
		this.tokens = tokens;
		this.userAgent = userAgent;
	}

	public CompletableFuture<MeResponse> getMe(AccountIdentity identity)
	{
		return getAsync(API_PREFIX + "/me", identity, MeResponse.class, null);
	}

	/**
	 * Tries a token the member has just pasted, before it is saved. Accepting
	 * it also binds it to this account on the server. A refusal leaves the
	 * saved token (if any) alone: it is this candidate that was refused.
	 */
	public CompletableFuture<MeResponse> checkToken(AccountIdentity identity, String candidate)
	{
		return getAsync(API_PREFIX + "/me", identity, MeResponse.class, candidate.trim());
	}

	/**
	 * Whether a name is on the site at all. Public: no token and no account
	 * headers, so it works before the member has a token.
	 */
	public CompletableFuture<Boolean> checkRegistration(String rsn)
	{
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		HttpUrl base = HttpUrl.parse(config.apiBaseUrl());
		if (base == null)
		{
			future.completeExceptionally(new ApiException(0, "Invalid server URL: " + config.apiBaseUrl()));
			return future;
		}

		Request request = new Request.Builder()
			.url(base.newBuilder()
				.encodedPath(API_PREFIX + "/public/registration")
				.addQueryParameter("rsn", rsn)
				.build())
			.header("X-Plugin-Version", PLUGIN_VERSION)
			.header("User-Agent", userAgent)
			.get()
			.build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				future.completeExceptionally(unreachable(call.request().url(), e));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					Registration registration = parse(response, null, Registration.class, false);
					future.complete(registration != null && registration.registered);
				}
				catch (ApiException e)
				{
					future.completeExceptionally(e);
				}
			}
		});

		return future;
	}

	/** {@code GET /public/registration}'s data. */
	private static class Registration
	{
		boolean registered;
	}

	public CompletableFuture<ClanEventStatus> getClanEvents(AccountIdentity identity)
	{
		return getAsync(API_PREFIX + "/clan-events", identity, ClanEventStatus.class, null);
	}

	/**
	 * Sends a JSON body and blocks for the reply. For background senders such
	 * as the outbox, which run off the client thread and need ordering.
	 */
	public <T> T postBlocking(String path, AccountIdentity identity, Object body, Type responseType)
		throws ApiException
	{
		Request request = requestBuilder(path, identity)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		try (Response response = http.newCall(request).execute())
		{
			return parse(response, identity, responseType);
		}
		catch (IOException e)
		{
			throw unreachable(request.url(), e);
		}
	}

	/** Sends account progress; returns the server's reply. Blocking; background threads only. */
	public JsonObject putProgress(AccountIdentity identity, JsonObject progress) throws ApiException
	{
		Request request = requestBuilder(API_PREFIX + "/progress", identity)
			.put(RequestBody.create(JSON, gson.toJson(progress)))
			.build();

		try (Response response = http.newCall(request).execute())
		{
			return parse(response, identity, JsonObject.class);
		}
		catch (IOException e)
		{
			throw unreachable(request.url(), e);
		}
	}

	/** Attaches a JPEG to a delivered ledger event. Blocking; background threads only. */
	public void uploadScreenshot(AccountIdentity identity, String eventId, byte[] jpeg) throws ApiException
	{
		RequestBody body = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("image", eventId + ".jpg", RequestBody.create(JPEG, jpeg))
			.build();

		Request request = requestBuilder(API_PREFIX + "/events/" + eventId + "/screenshot", identity)
			.post(body)
			.build();

		try (Response response = http.newCall(request).execute())
		{
			parse(response, identity, Object.class);
		}
		catch (IOException e)
		{
			throw unreachable(request.url(), e);
		}
	}

	private <T> CompletableFuture<T> getAsync(String path, AccountIdentity identity, Type responseType,
		@Nullable String candidateToken)
	{
		CompletableFuture<T> future = new CompletableFuture<>();
		Request request;

		try
		{
			request = requestBuilder(path, identity, candidateToken).get().build();
		}
		catch (ApiException e)
		{
			future.completeExceptionally(e);
			return future;
		}

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				future.completeExceptionally(unreachable(call.request().url(), e));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					future.complete(parse(response, identity, responseType, candidateToken == null));
				}
				catch (ApiException e)
				{
					future.completeExceptionally(e);
				}
			}
		});

		return future;
	}

	/** Names the URL and the underlying reason, so a wrong Server URL is obvious. */
	private static ApiException unreachable(HttpUrl url, IOException e)
	{
		log.warn("Irons Grotto request to {} failed", url, e);
		String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
		return new ApiException("Can't reach " + url.scheme() + "://" + url.host() + ":" + url.port() + " (" + reason + ")", e);
	}

	private Request.Builder requestBuilder(String path, AccountIdentity identity) throws ApiException
	{
		return requestBuilder(path, identity, null);
	}

	/** @param candidateToken a token to try instead of the account's saved one */
	private Request.Builder requestBuilder(String path, AccountIdentity identity, @Nullable String candidateToken)
		throws ApiException
	{
		String token = candidateToken != null ? candidateToken : tokens.get(identity);
		if (token.isEmpty())
		{
			throw new ApiException(401, "No plugin token set");
		}

		HttpUrl base = HttpUrl.parse(config.apiBaseUrl());
		if (base == null)
		{
			throw new ApiException(0, "Invalid server URL: " + config.apiBaseUrl());
		}

		HttpUrl url = base.newBuilder().encodedPath(path).build();

		return new Request.Builder()
			.url(url)
			.header("Authorization", "Bearer " + token)
			.header("X-Account-Hash", identity.getAccountHash())
			.header("X-Player-Name", identity.getRsn())
			.header("X-Plugin-Version", PLUGIN_VERSION)
			.header("User-Agent", userAgent);
	}

	private <T> T parse(Response response, AccountIdentity identity, Type type) throws ApiException
	{
		return parse(response, identity, type, true);
	}

	/** @param clearOnReject drop the account's saved token if the server refuses it for this account */
	private <T> T parse(Response response, AccountIdentity identity, Type type, boolean clearOnReject)
		throws ApiException
	{
		JsonObject envelope = readEnvelope(response);

		if (!response.isSuccessful() || envelope == null || !isSuccess(envelope))
		{
			String error = envelope != null && envelope.has("error") && !envelope.get("error").isJsonNull()
				? envelope.get("error").getAsString()
				: response.code() == 404
					? config.apiBaseUrl() + " doesn't support the plugin (404). Check the Server URL setting."
					: "Server returned " + response.code();
			String code = envelope != null && envelope.has("code") && envelope.get("code").isJsonPrimitive()
				? envelope.get("code").getAsString()
				: null;
			ApiException failure = new ApiException(response.isSuccessful() ? 500 : response.code(), error, code);
			if (clearOnReject && failure.isTokenRejectedForAccount())
			{
				tokens.clearRejected(identity);
			}
			throw failure;
		}

		JsonElement data = envelope.get("data");
		try
		{
			return gson.fromJson(data, type);
		}
		catch (JsonParseException e)
		{
			throw new ApiException("Unexpected response from the Irons Grotto server.", e);
		}
	}

	private JsonObject readEnvelope(Response response)
	{
		ResponseBody body = response.body();
		if (body == null)
		{
			return null;
		}

		try
		{
			JsonElement element = gson.fromJson(body.string(), JsonElement.class);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
		}
		catch (IOException | JsonParseException e)
		{
			log.debug("Unparseable response body ({})", response.code(), e);
			return null;
		}
	}

	private static boolean isSuccess(JsonObject envelope)
	{
		return envelope.has("success") && envelope.get("success").getAsBoolean();
	}
}
