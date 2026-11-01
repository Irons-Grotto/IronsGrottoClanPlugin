package com.ironsgrotto.chat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ironsgrotto.IronsGrottoConfig;
import com.ironsgrotto.api.GrottoApiClient;
import com.ironsgrotto.api.TokenStore;
import com.ironsgrotto.api.model.CalendarEntry;
import com.ironsgrotto.session.AccountIdentity;
import com.ironsgrotto.session.AccountSession;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.util.Text;

/**
 * {@code !events}: what is on now and next. Like {@code !rank}, every client
 * that sees it rewrites its own copy, so times read in each viewer's own zone.
 */
@Slf4j
@Singleton
public class EventsCommand
{
	private static final String EVENTS = "!events";
	private static final String SEPARATOR = " | ";
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM h:mma", Locale.ENGLISH);

	private final Client client;
	private final ClientThread clientThread;
	private final ChatCommandManager commands;
	private final GrottoApiClient api;
	private final TokenStore tokens;
	private final AccountSession session;
	private final IronsGrottoConfig config;

	private final Cache<String, CompletableFuture<List<CalendarEntry>>> calendar = CacheBuilder.newBuilder()
		.expireAfterWrite(1, TimeUnit.MINUTES)
		.build();

	@Inject
	EventsCommand(Client client, ClientThread clientThread, ChatCommandManager commands, GrottoApiClient api,
		TokenStore tokens, AccountSession session, IronsGrottoConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.commands = commands;
		this.api = api;
		this.tokens = tokens;
		this.session = session;
		this.config = config;
	}

	public void startUp()
	{
		commands.registerCommand(EVENTS, (message, text) -> answer(message));
	}

	public void shutDown()
	{
		commands.unregisterCommand(EVENTS);
		calendar.invalidateAll();
	}

	private void answer(ChatMessage message)
	{
		AccountIdentity viewer = session.getIdentity();
		if (!config.chatCommands() || viewer == null || !tokens.has(viewer) || Text.sanitize(message.getName()) == null)
		{
			return;
		}

		MessageNode node = message.getMessageNode();
		lookUp(viewer).thenAccept(entries ->
		{
			if (entries != null && !entries.isEmpty())
			{
				String answer = format(entries, Instant.now(), ZoneId.systemDefault());
				clientThread.invokeLater(() ->
				{
					node.setValue(answer);
					client.refreshChat();
				});
			}
		}).exceptionally(error ->
		{
			// An older server has no route (404): the message stays as typed.
			log.debug("Could not look up the calendar", error);
			return null;
		});
	}

	private CompletableFuture<List<CalendarEntry>> lookUp(AccountIdentity viewer)
	{
		try
		{
			CompletableFuture<List<CalendarEntry>> lookup = calendar.get("all", () -> api.getCalendar(viewer));
			lookup.whenComplete((entries, error) ->
			{
				if (error != null)
				{
					calendar.invalidateAll();
				}
			});
			return lookup;
		}
		catch (ExecutionException e)
		{
			return CompletableFuture.failedFuture(e);
		}
	}

	/** "Now: Firemaking SOTW, 4d left | Fri 6 Nov 9:00am: BOTW" */
	static String format(List<CalendarEntry> entries, Instant now, ZoneId zone)
	{
		return entries.stream()
			.map(entry -> "live".equals(entry.getStatus())
				? "Now: " + entry.getTitle() + ", " + left(Duration.between(now, Instant.parse(entry.getEndsAt())))
				: WHEN.format(Instant.parse(entry.getStartsAt()).atZone(zone))
					.replace("AM", "am").replace("PM", "pm") + ": " + entry.getTitle())
			.collect(Collectors.joining(SEPARATOR));
	}

	private static String left(Duration duration)
	{
		long hours = Math.max(0, duration.toHours());
		return hours >= 48 ? hours / 24 + "d left" : hours >= 1 ? hours + "h left" : "ending soon";
	}
}
