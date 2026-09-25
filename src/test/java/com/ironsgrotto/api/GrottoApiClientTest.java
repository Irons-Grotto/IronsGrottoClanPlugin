package com.ironsgrotto.api;

import com.google.gson.Gson;
import com.ironsgrotto.IronsGrottoConfig;
import com.ironsgrotto.api.model.MeResponse;
import com.ironsgrotto.session.AccountIdentity;
import java.util.concurrent.ExecutionException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GrottoApiClientTest
{
	private static final AccountIdentity ACCOUNT = new AccountIdentity("-4611686018427387904", "Iron Dude");
	private static final AccountIdentity ALT = new AccountIdentity("42", "Alt Dude");

	private MockWebServer server;
	private GrottoApiClient client;
	private MemoryTokenStore tokens;

	@Before
	public void setUp() throws Exception
	{
		server = new MockWebServer();
		server.start();
		String baseUrl = server.url("/").toString();

		tokens = new MemoryTokenStore();
		tokens.set(ACCOUNT, "igp_test");

		IronsGrottoConfig config = new IronsGrottoConfig()
		{
			@Override
			public String apiBaseUrl()
			{
				return baseUrl;
			}
		};

		client = new GrottoApiClient(new OkHttpClient(), new Gson(), config, tokens, "test-agent");
	}

	@After
	public void tearDown() throws Exception
	{
		server.shutdown();
	}

	@Test
	public void sendsTokenAndAccountHeaders() throws Exception
	{
		server.enqueue(new MockResponse().setBody("{\"success\":true,\"data\":{\"rsn\":\"Iron Dude\",\"member\":null,\"joinUrl\":\"https://x/join\",\"policy\":{\"minScreenshotLootValue\":5}}}"));

		MeResponse me = client.getMe(ACCOUNT).get();

		RecordedRequest request = server.takeRequest();
		assertEquals("/api/plugin/v1/me", request.getPath());
		assertEquals(GrottoApiClient.PLUGIN_VERSION, request.getHeader("X-Plugin-Version"));
		assertEquals("Bearer igp_test", request.getHeader("Authorization"));
		assertEquals(ACCOUNT.getAccountHash(), request.getHeader("X-Account-Hash"));
		assertEquals("Iron Dude", request.getHeader("X-Player-Name"));
		assertNull(me.getMember());
		assertEquals("https://x/join", me.getJoinUrl());
		assertEquals(5, me.getPolicy().getMinScreenshotLootValue());
		// Fields the server left out keep the plugin's defaults.
		assertTrue(me.getPolicy().isScreenshotPets());
	}

	@Test
	public void surfacesTheServersErrorMessage() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"success\":false,\"error\":\"Not yours\"}"));

		try
		{
			client.getMe(ACCOUNT).get();
			fail("expected an error");
		}
		catch (ExecutionException e)
		{
			ApiException cause = (ApiException) e.getCause();
			assertEquals(403, cause.getStatus());
			assertEquals("Not yours", cause.getMessage());
			assertTrue(!cause.isRetryable());
		}
	}

	@Test
	public void refusesToSendWithoutAToken() throws Exception
	{
		tokens.set(ACCOUNT, " ");

		try
		{
			client.getMe(ACCOUNT).get();
			fail("expected an error");
		}
		catch (ExecutionException e)
		{
			assertTrue(((ApiException) e.getCause()).isUnauthorized());
			assertEquals(0, server.getRequestCount());
		}
	}

	@Test
	public void blockingPostTreatsServerErrorsAsRetryable()
	{
		server.enqueue(new MockResponse().setResponseCode(502).setBody("Bad gateway"));

		try
		{
			client.postBlocking(GrottoApiClient.API_PREFIX + "/events", ACCOUNT, new Object(), Object.class);
			fail("expected an error");
		}
		catch (ApiException e)
		{
			assertEquals(502, e.getStatus());
			assertTrue(e.isRetryable());
		}
	}

	@Test
	public void sendsEachAccountItsOwnToken() throws Exception
	{
		tokens.set(ALT, "igp_alt");
		server.enqueue(new MockResponse().setBody("{\"success\":true,\"data\":{}}"));

		client.getMe(ALT).get();

		assertEquals("Bearer igp_alt", server.takeRequest().getHeader("Authorization"));
	}

	@Test
	public void neverSendsAnotherAccountsToken()
	{
		try
		{
			client.getMe(ALT).get();
			fail("expected an error");
		}
		catch (InterruptedException | ExecutionException e)
		{
			assertTrue(((ApiException) e.getCause()).isUnauthorized());
			assertEquals(0, server.getRequestCount());
		}
	}

	@Test
	public void dropsTheTokenWhenTheServerSaysItIsForAnotherAccount()
	{
		AccountIdentity[] cleared = new AccountIdentity[1];
		tokens.setOnCleared(account -> cleared[0] = account);
		server.enqueue(new MockResponse().setResponseCode(403)
			.setBody("{\"success\":false,\"error\":\"This token is for a different account.\",\"code\":\"token_account_mismatch\"}"));

		try
		{
			client.postBlocking(GrottoApiClient.API_PREFIX + "/events", ACCOUNT, new Object(), Object.class);
			fail("expected an error");
		}
		catch (ApiException e)
		{
			assertTrue(e.isTokenRejectedForAccount());
			assertTrue(e.isClientBlocked());
		}

		assertEquals("", tokens.get(ACCOUNT));
		assertEquals(ACCOUNT, cleared[0]);
	}

	@Test
	public void keepsTheTokenOnOtherRefusals()
	{
		server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"success\":false,\"error\":\"Nope\"}"));

		try
		{
			client.postBlocking(GrottoApiClient.API_PREFIX + "/events", ACCOUNT, new Object(), Object.class);
			fail("expected an error");
		}
		catch (ApiException e)
		{
			assertEquals(403, e.getStatus());
		}

		assertEquals("igp_test", tokens.get(ACCOUNT));
	}

	@Test
	public void checksAPastedTokenWithoutUsingTheSavedOne() throws Exception
	{
		server.enqueue(new MockResponse().setBody("{\"success\":true,\"data\":{}}"));

		client.checkToken(ACCOUNT, " igp_pasted ").get();

		assertEquals("Bearer igp_pasted", server.takeRequest().getHeader("Authorization"));
		assertEquals("igp_test", tokens.get(ACCOUNT));
	}

	@Test
	public void aRefusedCandidateNeverClearsTheSavedToken()
	{
		server.enqueue(new MockResponse().setResponseCode(403)
			.setBody("{\"success\":false,\"error\":\"This token is for a different account.\",\"code\":\"token_account_mismatch\"}"));

		try
		{
			client.checkToken(ACCOUNT, "igp_other").get();
			fail("expected an error");
		}
		catch (InterruptedException | ExecutionException e)
		{
			assertTrue(((ApiException) e.getCause()).isTokenRejectedForAccount());
		}

		assertEquals("igp_test", tokens.get(ACCOUNT));
	}

	@Test
	public void asksWhetherANameIsRegisteredWithoutAnyCredentials() throws Exception
	{
		tokens.set(ACCOUNT, "igp_secret");
		server.enqueue(new MockResponse().setBody("{\"success\":true,\"data\":{\"registered\":true}}"));

		assertTrue(client.checkRegistration("Iron Dude").get());

		RecordedRequest request = server.takeRequest();
		assertEquals("/api/plugin/v1/public/registration?rsn=Iron%20Dude", request.getPath());
		assertNull(request.getHeader("Authorization"));
		assertNull(request.getHeader("X-Account-Hash"));
		assertEquals(GrottoApiClient.PLUGIN_VERSION, request.getHeader("X-Plugin-Version"));
	}

	@Test
	public void readsAnUnregisteredName() throws Exception
	{
		server.enqueue(new MockResponse().setBody("{\"success\":true,\"data\":{\"registered\":false}}"));

		assertFalse(client.checkRegistration("Nobody Here").get());
	}
}
