package com.ironsgrotto.chat;

import com.ironsgrotto.IronsGrottoConfig;
import com.ironsgrotto.api.GrottoApiClient;
import com.ironsgrotto.api.TokenStore;
import com.ironsgrotto.api.model.Broadcast;
import com.ironsgrotto.session.AccountIdentity;
import com.ironsgrotto.session.AccountSession;
import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.Text;

/**
 * Asks the server for a broadcast every so often, shows it, and sends its
 * timestamp back with the next ask. That timestamp is the only record that a
 * member saw it. The server sends one at most, the newest, so however long a
 * member has been away they get one line.
 */
@Slf4j
@Singleton
public class BroadcastPoller
{
	private static final Color COLOUR = new Color(0x2F8FE0);
	private static final int MINIMUM_SECONDS = 30;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("EEE h:mma", Locale.ENGLISH);

	private final GrottoApiClient api;
	private final TokenStore tokens;
	private final AccountSession session;
	private final ChatMessageManager chat;
	private final IronsGrottoConfig config;

	private volatile boolean asking;
	private long lastAskedAt;
	private volatile String account;
	/** Shown and not yet reported. */
	@Nullable
	private volatile String unreported;
	/** Shown, so a repeat from a report still on its way is not shown twice. */
	@Nullable
	private volatile String lastShown;

	@Inject
	BroadcastPoller(GrottoApiClient api, TokenStore tokens, AccountSession session, ChatMessageManager chat,
		IronsGrottoConfig config)
	{
		this.api = api;
		this.tokens = tokens;
		this.session = session;
		this.chat = chat;
		this.config = config;
	}

	/** Called every game tick; asks only when {@code pollSeconds} have passed. */
	public void tick(int pollSeconds)
	{
		long now = System.currentTimeMillis();
		long interval = TimeUnit.SECONDS.toMillis(Math.max(MINIMUM_SECONDS, pollSeconds));
		AccountIdentity identity = session.getIdentity();
		if (asking || now - lastAskedAt < interval || !config.showBroadcasts()
			|| identity == null || !tokens.has(identity))
		{
			return;
		}

		if (!identity.getAccountHash().equals(account))
		{
			account = identity.getAccountHash();
			unreported = null;
			lastShown = null;
		}

		lastAskedAt = now;
		asking = true;
		String reporting = unreported;
		api.getBroadcast(identity, reporting)
			.thenAccept(broadcast ->
			{
				if (reporting != null && reporting.equals(unreported))
				{
					unreported = null;
				}
				if (broadcast != null && !broadcast.getSendsAt().equals(lastShown))
				{
					show(broadcast);
					lastShown = broadcast.getSendsAt();
					unreported = broadcast.getSendsAt();
				}
			})
			.exceptionally(error ->
			{
				// An older server has no route (404): nothing to show.
				log.debug("Could not check for a broadcast", error);
				return null;
			})
			.whenComplete((result, error) -> asking = false);
	}

	private void show(Broadcast broadcast)
	{
		String text = withEndTime(Text.removeTags(broadcast.getMessage()), broadcast.getEventEndsAt(),
			ZoneId.systemDefault(), Instant.now());
		chat.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append("[Irons Grotto] ")
				.append(COLOUR, text)
				.build())
			.build());
	}

	/** Fills in {@code {end}}: the time alone today, with the day otherwise. */
	static String withEndTime(String message, @Nullable String endsAt, ZoneId zone, Instant now)
	{
		if (endsAt == null || !message.contains("{end}"))
		{
			return message;
		}

		try
		{
			ZonedDateTime end = Instant.parse(endsAt).atZone(zone);
			boolean today = end.toLocalDate().equals(now.atZone(zone).toLocalDate());
			String time = (today ? TIME : DAY_AND_TIME).format(end)
				.replace("AM", "am")
				.replace("PM", "pm");
			return message.replace("{end}", time);
		}
		catch (DateTimeParseException e)
		{
			return message;
		}
	}
}
