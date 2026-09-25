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
 * Every request carries the member's plugin token and the account it speaks
 * for; the server refuses an account that is not the token owner's. Responses
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
	private final String userAgent;

	@Inject
	GrottoApiClient(OkHttpClient http, Gson gson, IronsGrottoConfig config)
	{
		this(http, gson, config, "IronsGrottoPlugin/" + PLUGIN_VERSION);
	}

	protected GrottoApiClient(OkHttpClient http, Gson gson, IronsGrottoConfig config, String userAgent)
	{
		this.http = http;
		this.gson = gson;
		this.config = config;
		this.userAgent = userAgent;
	}

	public boolean hasToken()
	{
		return !config.pluginToken().trim().isEmpty();
	}

	public CompletableFuture<MeResponse> getMe(AccountIdentity identity)
	{
		return getAsync(API_PREFIX + "/me", identity, MeResponse.class);
	}

	public CompletableFuture<ClanEventStatus> getClanEvents(AccountIdentity identity)
	{
		return getAsync(API_PREFIX + "/clan-events", identity, ClanEventStatus.class);
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
			return parse(response, responseType);
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
			return parse(response, JsonObject.class);
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
			parse(response, Object.class);
		}
		catch (IOException e)
		{
			throw unreachable(request.url(), e);
		}
	}

	private <T> CompletableFuture<T> getAsync(String path, AccountIdentity identity, Type responseType)
	{
		CompletableFuture<T> future = new CompletableFuture<>();
		Request request;

		try
		{
			request = requestBuilder(path, identity).get().build();
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
					future.complete(parse(response, responseType));
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
		String token = config.pluginToken().trim();
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

	private <T> T parse(Response response, Type type) throws ApiException
	{
		JsonObject envelope = readEnvelope(response);

		if (!response.isSuccessful() || envelope == null || !isSuccess(envelope))
		{
			String error = envelope != null && envelope.has("error") && !envelope.get("error").isJsonNull()
				? envelope.get("error").getAsString()
				: response.code() == 404
					? config.apiBaseUrl() + " doesn't support the plugin (404). Check the Server URL setting."
					: "Server returned " + response.code();
			throw new ApiException(response.isSuccessful() ? 500 : response.code(), error);
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
