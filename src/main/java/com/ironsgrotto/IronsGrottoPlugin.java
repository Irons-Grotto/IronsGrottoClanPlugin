package com.ironsgrotto;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.ironsgrotto.api.ApiException;
import com.ironsgrotto.api.GrottoApiClient;
import com.ironsgrotto.api.model.PluginPolicy;
import com.ironsgrotto.dev.DevTools;
import com.ironsgrotto.ledger.LedgerRecorder;
import com.ironsgrotto.outbox.Outbox;
import com.ironsgrotto.outbox.OutboxEntry;
import com.ironsgrotto.outbox.OutboxStore;
import com.ironsgrotto.progress.CollectionLogSync;
import com.ironsgrotto.progress.ProgressSync;
import com.ironsgrotto.progress.ProgressUploader;
import com.ironsgrotto.screenshot.ScreenshotPolicy;
import com.ironsgrotto.screenshot.ScreenshotService;
import com.ironsgrotto.screenshot.ScreenshotStore;
import com.ironsgrotto.screenshot.ScreenshotUploader;
import com.ironsgrotto.session.AccountIdentity;
import com.ironsgrotto.session.AccountSession;
import com.ironsgrotto.tracker.ChatEventTracker;
import com.ironsgrotto.tracker.LootEventTracker;
import com.ironsgrotto.ui.GrottoPanel;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Irons Grotto",
	description = "Clan companion for Irons Grotto: rank progress, SOTW/BOTW standings and automatic event tracking",
	tags = {"clan", "irons grotto", "rank", "bingo", "events"}
)
public class IronsGrottoPlugin extends Plugin
{
	private static final String EVENTS_PATH = GrottoApiClient.API_PREFIX + "/events";
	private static final long FLUSH_INTERVAL_SECONDS = 5;
	private static final long REFRESH_CHECK_SECONDS = 30;

	@Inject
	private IronsGrottoConfig config;

	@Inject
	private AccountSession session;

	@Inject
	private GrottoApiClient api;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private Gson gson;

	@Inject
	private EventBus eventBus;

	@Inject
	private LedgerRecorder recorder;

	@Inject
	private ChatEventTracker chatTracker;

	@Inject
	private LootEventTracker lootTracker;

	@Inject
	private DevTools devTools;

	@Inject
	private ScreenshotService screenshots;

	@Inject
	private ProgressSync progressSync;

	@Inject
	private ProgressUploader progressUploader;

	@Inject
	private CollectionLogSync collectionLogSync;


	private GrottoPanel panel;
	private NavigationButton navButton;
	private Outbox outbox;
	private ScreenshotUploader screenshotUploader;
	private ScheduledFuture<?> flushTask;
	private ScheduledFuture<?> refreshTask;

	private volatile PluginPolicy policy = new PluginPolicy();
	private volatile long lastRefreshAt;

	@Provides
	IronsGrottoConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(IronsGrottoConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new GrottoPanel(tokenPageUrl(), devTools);
		panel.setDevToolsVisible(config.developerTools());
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Irons Grotto")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		java.nio.file.Path dataDir = RuneLite.RUNELITE_DIR.toPath().resolve("irons-grotto");
		OutboxStore store = new OutboxStore(dataDir.resolve("outbox.json"), gson);
		outbox = new Outbox(this::sendEvents, store, Clock.systemUTC(), this::chat);

		ScreenshotStore screenshotStore = new ScreenshotStore(dataDir.resolve("screenshots"), gson);
		screenshots.attach(screenshotStore);
		screenshotUploader = new ScreenshotUploader(screenshotStore, outbox, api, Clock.systemUTC());

		recorder.attach(outbox, entry ->
		{
			if (config.screenshots() && ScreenshotPolicy.shouldCapture(entry, policy))
			{
				screenshots.capture(entry.getId(), entry.getAccount());
			}
			panel.addRecentEvent(entry);
			panel.setPendingCount(outbox.size());
		});
		eventBus.register(chatTracker);
		eventBus.register(lootTracker);
		eventBus.register(progressSync);
		eventBus.register(collectionLogSync);

		progressUploader.setOnSynced(result ->
		{
			panel.setProgressSynced(java.time.LocalTime.now());
			// Points may have moved; show the new standing.
			refresh();
		});

		flushTask = executor.scheduleWithFixedDelay(this::flushOutbox, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
		refreshTask = executor.scheduleWithFixedDelay(this::refreshIfStale, REFRESH_CHECK_SECONDS, REFRESH_CHECK_SECONDS, TimeUnit.SECONDS);

		// Enabling the plugin while already logged in: the next game tick picks
		// the account up and refreshes.
		refreshPanelState();
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(chatTracker);
		eventBus.unregister(lootTracker);
		eventBus.unregister(progressSync);
		eventBus.unregister(collectionLogSync);
		recorder.detach();
		clientToolbar.removeNavigation(navButton);
		if (flushTask != null)
		{
			flushTask.cancel(false);
		}
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
		}
		session.clear();
		panel = null;
		outbox = null;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (session.poll())
		{
			refresh();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			if (session.clear() && state == GameState.LOGIN_SCREEN)
			{
				panel.showLoggedOut();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!IronsGrottoConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("developerTools".equals(event.getKey()))
		{
			panel.setDevToolsVisible(config.developerTools());
		}

		if ("pluginToken".equals(event.getKey()) || "apiBaseUrl".equals(event.getKey()))
		{
			outbox.resume();
			refresh();
		}
	}

	public PluginPolicy getPolicy()
	{
		return policy;
	}

	private void refresh()
	{
		lastRefreshAt = System.currentTimeMillis();

		AccountIdentity identity = refreshPanelState();
		if (identity == null)
		{
			return;
		}

		api.getMe(identity)
			.thenAccept(me ->
			{
				policy = me.getPolicy() != null ? me.getPolicy() : new PluginPolicy();
				panel.showMe(me);
			})
			.exceptionally(this::handleRefreshError);

		api.getClanEvents(identity)
			.thenAccept(events -> panel.showEvents(events))
			.exceptionally(error ->
			{
				log.debug("Could not load clan events", error);
				return null;
			});
	}

	/** Shows the logged-out or unlinked state, and returns who to load data for. */
	private AccountIdentity refreshPanelState()
	{
		AccountIdentity identity = session.getIdentity();
		if (identity == null)
		{
			panel.showLoggedOut();
			return null;
		}
		if (!api.hasToken())
		{
			panel.showNoToken();
			return null;
		}
		return identity;
	}

	private Void handleRefreshError(Throwable error)
	{
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;

		if (cause instanceof ApiException && ((ApiException) cause).isUnauthorized())
		{
			panel.showError("Your plugin token was not accepted. Generate a new one on the website.");
		}
		else if (cause instanceof ApiException && ((ApiException) cause).isUpgradeRequired())
		{
			// Nothing recorded is lost: the outbox and screenshots wait for the update.
			panel.showError(cause.getMessage() + " Your recorded activity is kept and sent after you update.");
		}
		else
		{
			panel.showError(cause.getMessage() != null ? cause.getMessage() : "Could not reach the Irons Grotto server");
		}

		if (config.debug())
		{
			chat("Refresh failed: " + cause.getMessage());
		}
		return null;
	}

	private void refreshIfStale()
	{
		long intervalMs = TimeUnit.SECONDS.toMillis(Math.max(60, policy.getPanelRefreshSeconds()));
		if (session.getIdentity() != null && System.currentTimeMillis() - lastRefreshAt >= intervalMs)
		{
			refresh();
		}
	}

	private void flushOutbox()
	{
		Outbox current = outbox;
		if (current == null || !api.hasToken())
		{
			return;
		}

		try
		{
			current.flush();
			screenshotUploader.uploadPending();
			progressUploader.flush();
		}
		catch (RuntimeException e)
		{
			// A scheduled task that throws is never run again.
			log.warn("Irons Grotto outbox flush failed", e);
		}

		GrottoPanel currentPanel = panel;
		if (currentPanel != null)
		{
			currentPanel.setPendingCount(current.size());
		}
	}

	private Outbox.SendResult sendEvents(AccountIdentity account, List<OutboxEntry> batch) throws ApiException
	{
		EventsResponse response = api.postBlocking(EVENTS_PATH, account, new EventsRequest(batch), EventsResponse.class);

		if (config.debug())
		{
			chat("Sent " + batch.size() + " events, " + response.getAccepted().size() + " accepted");
		}

		return new Outbox.SendResult(response.getAccepted(), response.getRejected(), response.getMessages());
	}

	private void chat(String message)
	{
		if (!config.chatFeedback() && !config.debug())
		{
			return;
		}

		String formatted = new ChatMessageBuilder()
			.append("[Irons Grotto] ")
			.append(message)
			.build();

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(formatted)
			.build());
	}

	private String tokenPageUrl()
	{
		return config.apiBaseUrl().replaceAll("/+$", "") + "/plugin";
	}

	/** Body of {@code POST /api/plugin/events}; the account travels in headers. */
	@Data
	static class EventsRequest
	{
		private final List<EventDto> events;

		EventsRequest(List<OutboxEntry> batch)
		{
			this.events = new ArrayList<>();
			for (OutboxEntry entry : batch)
			{
				events.add(new EventDto(entry.getId(), entry.getType(), entry.getOccurredAt(), entry.getPayload(), entry.isTest()));
			}
		}
	}

	@Data
	static class EventDto
	{
		private final String id;
		private final String type;
		private final String occurredAt;
		private final JsonObject payload;
		private final boolean test;
	}

	@Data
	static class EventsResponse
	{
		private Set<String> accepted = new HashSet<>();
		private Map<String, String> rejected = new HashMap<>();
		private List<String> messages = new ArrayList<>();
	}
}
