package com.ironsgrotto.chat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ironsgrotto.IronsGrottoConfig;
import com.ironsgrotto.api.GrottoApiClient;
import com.ironsgrotto.api.TokenStore;
import com.ironsgrotto.api.model.MemberCard;
import com.ironsgrotto.session.AccountIdentity;
import com.ironsgrotto.session.AccountSession;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * {@code !rank}, {@code !wins} and {@code !showcase}. Like RuneLite's
 * {@code !kc}, nothing is sent with the message: every client that sees one
 * looks the sender up and rewrites its own copy. So only members with the
 * plugin see the answer, and a message from anyone else stays as typed.
 */
@Slf4j
@Singleton
public class MemberCardCommands
{
	private static final String RANK = "!rank";
	private static final String WINS = "!wins";
	private static final String SHOWCASE = "!showcase";
	private static final String SEPARATOR = " | ";
	/** Chat icons sit on one line of text; item sprites are 36x32. */
	private static final int ICON_WIDTH = 18;
	private static final int ICON_HEIGHT = 16;

	private final Client client;
	private final ClientThread clientThread;
	private final ChatCommandManager commands;
	private final ChatIconManager icons;
	private final ItemManager itemManager;
	private final GrottoApiClient api;
	private final TokenStore tokens;
	private final AccountSession session;
	private final IronsGrottoConfig config;

	/** One lookup per name for a minute, however many times it is typed. */
	private final Cache<String, CompletableFuture<MemberCard>> cards = CacheBuilder.newBuilder()
		.expireAfterWrite(1, TimeUnit.MINUTES)
		.maximumSize(200)
		.build();
	/** Registered icons, kept: the game has no way to give one back. Client thread only. */
	private final Map<String, Integer> rankIcons = new HashMap<>();
	private final Map<Integer, Integer> itemIcons = new ConcurrentHashMap<>();

	@Inject
	MemberCardCommands(Client client, ClientThread clientThread, ChatCommandManager commands, ChatIconManager icons,
		ItemManager itemManager, GrottoApiClient api, TokenStore tokens, AccountSession session,
		IronsGrottoConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.commands = commands;
		this.icons = icons;
		this.itemManager = itemManager;
		this.api = api;
		this.tokens = tokens;
		this.session = session;
		this.config = config;
	}

	public void startUp()
	{
		commands.registerCommand(RANK, (message, text) -> answer(message, this::rankMessage));
		commands.registerCommand(WINS, (message, text) -> answer(message, MemberCardCommands::winsMessage));
		commands.registerCommand(SHOWCASE, (message, text) -> answer(message, this::showcaseMessage));
	}

	public void shutDown()
	{
		commands.unregisterCommand(RANK);
		commands.unregisterCommand(WINS);
		commands.unregisterCommand(SHOWCASE);
		cards.invalidateAll();
	}

	private void answer(ChatMessage message, Function<MemberCard, String> format)
	{
		AccountIdentity viewer = session.getIdentity();
		if (!config.chatCommands() || viewer == null || !tokens.has(viewer))
		{
			return;
		}

		String sender = sender(message);
		if (sender == null)
		{
			return;
		}

		MessageNode node = message.getMessageNode();
		lookUp(viewer, sender).thenAccept(card ->
		{
			if (card != null)
			{
				clientThread.invokeLater(() ->
				{
					node.setValue(format.apply(card));
					client.refreshChat();
				});
			}
		}).exceptionally(error ->
		{
			// An older server has no route (404): the message stays as typed.
			log.debug("Could not look up {}", sender, error);
			return null;
		});
	}

	/** Who typed it. A private message we sent names the recipient instead. */
	@Nullable
	private String sender(ChatMessage message)
	{
		if (message.getType() == ChatMessageType.PRIVATECHATOUT)
		{
			return client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
		}
		return Text.sanitize(message.getName());
	}

	private CompletableFuture<MemberCard> lookUp(AccountIdentity viewer, String name)
	{
		String key = name.toLowerCase(Locale.ROOT);
		try
		{
			CompletableFuture<MemberCard> lookup = cards.get(key, () -> api.getMemberCard(viewer, name));
			// A failure is not an answer: the next message tries again.
			lookup.whenComplete((card, error) ->
			{
				if (error != null)
				{
					cards.invalidate(key);
				}
			});
			return lookup;
		}
		catch (ExecutionException e)
		{
			return CompletableFuture.failedFuture(e);
		}
	}

	/** The rank's icon, name and points, and for the top 25 their place: "General | 16,877/19,000 | #12 in the Grotto". */
	private String rankMessage(MemberCard card)
	{
		ChatMessageBuilder builder = new ChatMessageBuilder();
		int icon = rankIcon(card.getRank());
		if (icon >= 0)
		{
			builder.img(icon).append(" ");
		}
		builder.append(ChatColorType.HIGHLIGHT)
			.append(card.getRankLabel())
			.append(ChatColorType.NORMAL)
			.append(SEPARATOR + pointsText(card));
		String position = positionText(card);
		if (position != null)
		{
			builder.append(SEPARATOR)
				.append(ChatColorType.HIGHLIGHT)
				.append(position);
		}
		return builder.build();
	}

	static String pointsText(MemberCard card)
	{
		NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.ROOT);
		String points = numbers.format(card.getPoints());
		return card.getNextRankThreshold() == null
			? points
			: points + "/" + numbers.format(card.getNextRankThreshold());
	}

	@Nullable
	static String positionText(MemberCard card)
	{
		return card.getLeaderboardPosition() == null
			? null
			: "#" + card.getLeaderboardPosition() + " in the Grotto";
	}

	static String winsMessage(MemberCard card)
	{
		return new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Event wins: ")
			.append(ChatColorType.HIGHLIGHT)
			.append(Integer.toString(card.getWins()))
			.build();
	}

	/** The member's rarest items as a row of icons. */
	private String showcaseMessage(MemberCard card)
	{
		ChatMessageBuilder builder = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Showcase:");
		if (card.getShowcase().isEmpty())
		{
			return builder.append(" nothing logged yet").build();
		}
		for (MemberCard.Item item : card.getShowcase())
		{
			int icon = icons.chatIconIndex(itemIcon(item.getItemId()));
			builder.append(" ").img(icon);
		}
		return builder.build();
	}

	/** Index for {@code <img>}, or -1 if there is none. */
	private int rankIcon(String rank)
	{
		Integer handle = rankIcons.computeIfAbsent(rank, id ->
		{
			try
			{
				BufferedImage image = ImageUtil.loadImageResource(getClass(),
					"/com/ironsgrotto/ranks/" + id.toLowerCase(Locale.ROOT) + ".png");
				// Reserved, not registerChatIcon: that has no index until a later
				// client tick, and the message is written now.
				int reserved = icons.reserveChatIcon();
				icons.updateChatIcon(reserved, image);
				return reserved;
			}
			catch (RuntimeException e)
			{
				// A rank the plugin has no picture of still shows its name.
				log.debug("No icon for rank {}", id, e);
				return -1;
			}
		});
		return handle < 0 ? -1 : icons.chatIconIndex(handle);
	}

	/** Reserves the slot now; the picture lands in it once the sprite has loaded. */
	private int itemIcon(int itemId)
	{
		return itemIcons.computeIfAbsent(itemId, id ->
		{
			int handle = icons.reserveChatIcon();
			AsyncBufferedImage image = itemManager.getImage(id);
			image.onLoaded(() ->
			{
				icons.updateChatIcon(handle, ImageUtil.resizeImage(image, ICON_WIDTH, ICON_HEIGHT, true));
				clientThread.invokeLater(client::refreshChat);
			});
			return handle;
		});
	}
}
