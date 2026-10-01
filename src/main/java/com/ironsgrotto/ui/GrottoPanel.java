package com.ironsgrotto.ui;

import com.ironsgrotto.api.model.ClanEventStatus;
import com.ironsgrotto.api.model.MeResponse;
import com.ironsgrotto.api.model.MemberStatus;
import com.ironsgrotto.api.model.TopLoot;
import com.ironsgrotto.ledger.LedgerEventType;
import com.ironsgrotto.outbox.OutboxEntry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;
import okhttp3.HttpUrl;

/**
 * The sidebar: who you are in the clan, how close the next rank is, and the
 * SOTW/BOTW running now.
 *
 * Every public method may be called from any thread; each hops onto the Swing
 * thread itself.
 */
public class GrottoPanel extends PluginPanel
{
	private static final Color ACCENT = new Color(76, 175, 120);
	private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance();

	private final JLabel statusLabel = new JLabel();
	private final JLabel lootTrackerLabel = wrapped(
		"Turn on RuneLite's Loot Tracker plugin. Raid, clue and chest loot is only recorded with it on.");
	private final CollapsibleSection accountSection;
	private final CollapsibleSection eventSection;
	private final CollapsibleSection topLootSection;
	private final CollapsibleSection activitySection;
	/** Dashboard and Discord, on their own row at the bottom. */
	private final JPanel linksRow = new JPanel(new GridLayout(1, 0, 4, 0));
	private final JLabel pendingLabel = new JLabel();

	{
		pendingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		pendingLabel.setFont(FontManager.getRunescapeSmallFont());
	}
	private final Deque<OutboxEntry> recent = new ArrayDeque<>();
	private final ItemManager itemManager;
	/** Read on every use, so a changed Server URL setting applies at once. */
	private final Supplier<String> siteUrl;
	/** The token prompt's explanation and link, swapped once we know if the account is registered. */
	private final JPanel tokenIntro = inline();
	private final JPanel tokenLink = inline();
	@Nullable
	private String tokenPromptRsn;
	private volatile Consumer<String> onTokenEntered = token -> { };

	private static final int RECENT_LIMIT = 10;
	private static final int PANEL_STANDINGS = 5;
	/** Drops shown in the panel; the server sends more. */
	private static final int TOP_LOOT_ROWS = 5;
	private static final int ITEM_COLUMNS = 5;
	/** The drop whose items are showing; one at a time. */
	@Nullable
	private String openLootId;
	private List<TopLoot> topLoots = java.util.Collections.emptyList();
	/** Quiet time after the last keystroke before a token is checked. */
	private static final int TOKEN_SETTLE_MS = 400;
	private static final Pattern TOKEN_SHAPE = Pattern.compile("^igp_[A-Za-z0-9_-]{43}$");

	/**
	 * @param siteUrl  the Irons Grotto site as currently configured, e.g. https://ironsgrotto.xyz
	 * @param sections remembers which blocks the member folded away
	 */
	public GrottoPanel(Supplier<String> siteUrl, ItemManager itemManager, CollapsibleSection.Store sections)
	{
		this.itemManager = itemManager;
		this.siteUrl = siteUrl;
		this.accountSection = new CollapsibleSection("account", sections);
		this.eventSection = new CollapsibleSection("event", sections);
		this.topLootSection = new CollapsibleSection("topLoot", sections);
		this.activitySection = new CollapsibleSection("activity", sections);

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("Irons Grotto");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(title);
		content.add(Box.createVerticalStrut(6));

		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(statusLabel);
		content.add(Box.createVerticalStrut(8));

		lootTrackerLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		lootTrackerLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
		lootTrackerLabel.setVisible(false);
		content.add(lootTrackerLabel);

		content.add(accountSection);
		content.add(Box.createVerticalStrut(8));
		content.add(eventSection);
		content.add(Box.createVerticalStrut(8));
		content.add(topLootSection);
		content.add(Box.createVerticalStrut(8));
		content.add(activitySection);
		content.add(Box.createVerticalStrut(8));

		// No buttons and no "all good" status: the plugin syncs by itself, and
		// the panel only speaks up when the member has something to do.
		pendingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(pendingLabel);
		content.add(Box.createVerticalStrut(8));

		linksRow.setOpaque(false);
		linksRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		linksRow.setVisible(false);
		content.add(linksRow);

		add(content, BorderLayout.NORTH);

		renderRecent();
		showLoggedOut();
		setPendingCount(0);
	}

	public void showLoggedOut()
	{
		onEdt(() ->
		{
			status("Log in to see your progress.");
			accountSection.setVisible(false);
			eventSection.setVisible(false);
			topLootSection.setVisible(false);
			linksRow.setVisible(false);
		});
	}

	/** Warns while RuneLite's Loot Tracker is off: chest and activity loot only arrives through it. */
	public void showLootTrackerOff(boolean off)
	{
		onEdt(() ->
		{
			lootTrackerLabel.setVisible(off);
			revalidateAll();
		});
	}

	/** Called with a token the member pasted into the panel. */
	public void setOnTokenEntered(Consumer<String> onTokenEntered)
	{
		this.onTokenEntered = onTokenEntered;
	}

	/**
	 * Asks for the logged-in account's token. Tokens are per account, so this
	 * names the account it is for.
	 *
	 * @param problem why the last token was dropped, or null
	 */
	public void showNoToken(String rsn, @Nullable String problem)
	{
		onEdt(() ->
		{
			status(problem == null ? "" : "<html>" + escape(problem) + "</html>");
			accountSection.clear("Connect " + rsn);
			// Asking the member to act: never folded away.
			accountSection.setPinnedOpen(true);
			tokenPromptRsn = rsn;
			fillTokenHelp(rsn, false);
			accountSection.body().add(tokenIntro);
			accountSection.body().add(Box.createVerticalStrut(6));

			// No save button: a complete token is checked with the server as
			// soon as it is pasted, and saved only if the server takes it.
			JPasswordField field = new JPasswordField();
			field.setAlignmentX(Component.LEFT_ALIGNMENT);
			field.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, field.getPreferredSize().height));
			Timer settle = new Timer(TOKEN_SETTLE_MS, e ->
			{
				String token = new String(field.getPassword()).trim();
				if (looksLikeToken(token))
				{
					field.setEnabled(false);
					status("Checking token…");
					onTokenEntered.accept(token);
				}
			});
			settle.setRepeats(false);
			field.getDocument().addDocumentListener(new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent e)
				{
					settle.restart();
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					settle.restart();
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
				}
			});

			accountSection.body().add(field);
			accountSection.body().add(Box.createVerticalStrut(6));
			accountSection.body().add(tokenLink);
			accountSection.setVisible(true);
			eventSection.setVisible(false);
			topLootSection.setVisible(false);
			linksRow.setVisible(false);
			revalidateAll();
		});
	}

	/**
	 * Points the token prompt at the right place once the server has said
	 * where this account gets its token: a new account joins first when
	 * {@code /join} makes the token; otherwise {@code /plugin} makes one.
	 */
	public void showTokenSource(String rsn, boolean sendToJoin)
	{
		onEdt(() ->
		{
			if (rsn.equals(tokenPromptRsn))
			{
				fillTokenHelp(rsn, sendToJoin);
				revalidateAll();
			}
		});
	}

	private void fillTokenHelp(String rsn, boolean sendToJoin)
	{
		tokenIntro.removeAll();
		tokenLink.removeAll();
		if (sendToJoin)
		{
			tokenIntro.add(wrapped(rsn + " isn't in Irons Grotto yet. Join to get a token, then paste it here."));
			tokenLink.add(linkButton("Join Irons Grotto", siteUrl.get() + "/join"));
		}
		else
		{
			tokenIntro.add(wrapped("Get a token for this account and paste it here."));
			tokenLink.add(linkButton("Get a token", tokenUrlFor(siteUrl.get() + "/plugin", rsn)));
		}
	}

	/**
	 * A whole token rather than part of one being typed: the prefix and the
	 * full length of the random part. Only then is it worth asking the server.
	 */
	static boolean looksLikeToken(String text)
	{
		return TOKEN_SHAPE.matcher(text).matches();
	}

	/** The token page, naming the account so the new token is labelled with it. */
	static String tokenUrlFor(String tokenUrl, String rsn)
	{
		HttpUrl url = HttpUrl.parse(tokenUrl);
		if (url == null)
		{
			return tokenUrl;
		}
		return url.newBuilder().addQueryParameter("name", rsn).build().toString();
	}

	public void showError(String message)
	{
		onEdt(() -> status("<html>" + escape(message) + "</html>"));
	}

	public void showMe(MeResponse me)
	{
		onEdt(() ->
		{
			accountSection.clear(me.getRsn());
			tokenPromptRsn = null;
			MemberStatus member = me.getMember();

			status("");

			if (member == null)
			{
				accountSection.body().add(small("Not a clan member yet."));
				if (me.getJoinUrl() != null)
				{
					accountSection.body().add(Box.createVerticalStrut(6));
					// Linked but not joined yet. With the plugin steps on /join
					// they're partway through it; without, it's a plain sign-up.
					boolean partway = !Boolean.FALSE.equals(me.getPluginOnboarding());
					accountSection.body().add(linkButton(partway ? "Continue" : "Join Irons Grotto", me.getJoinUrl()));
				}
			}
			else
			{
				accountSection.body().add(row("Rank", member.getRank()));
				accountSection.body().add(row("Points", NUMBERS.format(Math.floor(member.getPoints()))));
				accountSection.body().add(Box.createVerticalStrut(4));
				accountSection.body().add(rankProgress(member));
			}

			fillLinks(me.getLinks(), member != null);

			accountSection.setVisible(true);
			revalidateAll();
		});
	}

	public void showEvents(ClanEventStatus events)
	{
		onEdt(() ->
		{
			eventSection.clear("");
			ClanEventStatus.ActiveEvent active = events.getActive();

			if (active != null)
			{
				eventSection.setTitle(shortType(active.getType()) + ": " + active.getMetricName());
				eventSection.body().add(small(timeLeft("Ends", active.getEndsAt())));
				eventSection.body().add(Box.createVerticalStrut(4));

				List<ClanEventStatus.Standing> standings = active.getStandings();
				if (active.isStandingsUnavailable())
				{
					eventSection.body().add(small("Standings unavailable"));
				}
				else if (standings.isEmpty())
				{
					eventSection.body().add(small("No gains yet"));
				}
				else
				{
					// The overlay asks for more rows; the panel keeps its top five.
					for (ClanEventStatus.Standing standing : standings.subList(0, Math.min(PANEL_STANDINGS, standings.size())))
					{
						JPanel line = row(standing.getPosition() + ". " + standing.getPlayerName(),
							NUMBERS.format(standing.getGained()) + " " + gainUnit(active.getType()));
						if (standing.getPosition() == 1)
						{
							line.getComponent(0).setForeground(ACCENT);
						}
						eventSection.body().add(line);
					}
				}
			}
			ClanEventStatus.EventSummary next = events.getNext();
			if (next != null)
			{
				String upcoming = "Upcoming " + shortType(next.getType()) + ": " + next.getMetricName();
				if (active != null)
				{
					eventSection.body().add(Box.createVerticalStrut(4));
					eventSection.body().add(small(upcoming));
				}
				else
				{
					eventSection.setTitle(upcoming);
					eventSection.body().add(small(timeLeft("Starts", next.getStartsAt())));
				}
			}

			// Nothing running or booked: no section, rather than one saying so.
			if (active == null && next == null)
			{
				eventSection.setVisible(false);
				revalidateAll();
				return;
			}

			eventSection.setVisible(true);
			revalidateAll();
		});
	}

	/**
	 * The clan's biggest drops in the last 24 hours. Clicking a drop shows its
	 * items; an empty list or an older server (null) hides the section.
	 */
	public void showTopLoots(@Nullable List<TopLoot> loots)
	{
		onEdt(() ->
		{
			topLoots = loots == null ? java.util.Collections.emptyList() : loots;
			renderTopLoots();
		});
	}

	private void renderTopLoots()
	{
		topLootSection.clear("Top loots today");
		if (topLoots.isEmpty())
		{
			topLootSection.setVisible(false);
			revalidateAll();
			return;
		}

		topLootSection.setAside("last 24h");

		int position = 1;
		for (TopLoot loot : topLoots.subList(0, Math.min(TOP_LOOT_ROWS, topLoots.size())))
		{
			boolean open = Objects.equals(loot.getId(), openLootId);
			topLootSection.body().add(lootRow(position++, loot, open));
			if (open)
			{
				topLootSection.body().add(lootItems(loot));
			}
		}

		topLootSection.setVisible(true);
		revalidateAll();
	}

	private JPanel lootRow(int position, TopLoot loot, boolean open)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBackground(open ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 2, 2, 2));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText(open ? "Hide items" : "Show items");

		JLabel number = small(position + ".");
		number.setVerticalAlignment(JLabel.TOP);
		row.add(number, BorderLayout.WEST);

		JPanel who = inline();
		JLabel name = new JLabel(loot.getPlayerName());
		name.setForeground(Color.WHITE);
		who.add(name);
		who.add(small(loot.getSource()));
		row.add(who, BorderLayout.CENTER);

		JLabel value = new JLabel(shortGp(loot.getTotalValue()));
		value.setForeground(ACCENT);
		value.setVerticalAlignment(JLabel.TOP);
		row.add(value, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, row.getPreferredSize().height));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				openLootId = open ? null : loot.getId();
				renderTopLoots();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(open ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			}
		});
		return row;
	}

	/** The drop's items as icons, biggest stack value first, five to a row. */
	private JPanel lootItems(TopLoot loot)
	{
		JPanel grid = new JPanel(new GridLayout(0, ITEM_COLUMNS, 2, 2));
		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
		grid.setBorder(new EmptyBorder(2, 16, 4, 2));
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (TopLoot.Item item : loot.getItems())
		{
			JLabel icon = new JLabel();
			icon.setHorizontalAlignment(JLabel.CENTER);
			icon.setToolTipText("<html>" + escape(item.getName()) + " x " + NUMBERS.format(item.getQuantity())
				+ "<br>" + NUMBERS.format(item.getPrice() * item.getQuantity()) + " gp</html>");
			itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1).addTo(icon);
			grid.add(icon);
		}
		grid.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, grid.getPreferredSize().height));
		return grid;
	}

	/**
	 * Three significant figures, the way OSRS players write values: 950, 952K,
	 * 1.25M, 42.8M. Rounded down, so a drop is never shown as more than it was
	 * (and 999,999 is 999K, not 1000K).
	 */
	static String shortGp(long gp)
	{
		if (gp < 1_000)
		{
			return Long.toString(gp);
		}

		String[] units = {"K", "M", "B"};
		double value = gp;
		int unit = -1;
		while (value >= 1_000 && unit < units.length - 1)
		{
			value /= 1_000;
			unit++;
		}

		// Round to three significant figures, then trim a trailing ".0".
		int decimals = value >= 100 ? 0 : value >= 10 ? 1 : 2;
		String text = String.format(Locale.ROOT, "%." + decimals + "f", Math.floor(value * Math.pow(10, decimals)) / Math.pow(10, decimals));
		if (text.contains("."))
		{
			text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return text + units[unit];
	}

	/** Adds an event this session recorded to the "Recent activity" list. */
	public void addRecentEvent(OutboxEntry entry)
	{
		onEdt(() ->
		{
			recent.addFirst(entry);
			while (recent.size() > RECENT_LIMIT)
			{
				recent.removeLast();
			}
			renderRecent();
		});
	}

	private void renderRecent()
	{
		activitySection.clear("Recent activity");
		if (recent.isEmpty())
		{
			activitySection.setVisible(false);
			revalidateAll();
			return;
		}

		for (OutboxEntry entry : recent)
		{
			activitySection.body().add(small(describe(entry)));
		}

		activitySection.setVisible(true);
		revalidateAll();
	}

	static String describe(OutboxEntry entry)
	{
		com.google.gson.JsonObject payload = entry.getPayload();
		switch (entry.getType())
		{
			case LedgerEventType.BOSS_KC:
				return payload.get("boss").getAsString() + " kc " + NUMBERS.format(payload.get("kc").getAsInt());
			case LedgerEventType.LOOT:
				return payload.get("source").getAsString() + ": "
					+ NUMBERS.format(payload.get("totalValue").getAsLong()) + " gp";
			case LedgerEventType.COLLECTION_LOG_ITEM:
				return "New log slot: " + payload.get("itemName").getAsString();
			case LedgerEventType.PET:
				return "Pet";
			default:
				return entry.getType();
		}
	}

	public void setPendingCount(int pending)
	{
		onEdt(() ->
		{
			pendingLabel.setText(pending == 1 ? "1 event waiting to send" : pending + " events waiting to send");
			pendingLabel.setVisible(pending > 0);
		});
	}

	private JProgressBar rankProgress(MemberStatus member)
	{
		JProgressBar bar = new JProgressBar();
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		bar.setForeground(ACCENT);
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setStringPainted(true);
		bar.setPreferredSize(new Dimension(0, 16));

		Double nextThreshold = member.getNextRankThreshold();
		if (member.getNextRank() == null || nextThreshold == null)
		{
			bar.setMaximum(1);
			bar.setValue(1);
			bar.setString("Top rank");
			return bar;
		}

		double floor = member.getCurrentRankThreshold();
		int span = (int) Math.max(1, nextThreshold - floor);
		int into = (int) Math.max(0, Math.min(span, member.getPoints() - floor));
		bar.setMaximum(span);
		bar.setValue(into);
		bar.setString(NUMBERS.format(Math.ceil(nextThreshold - member.getPoints())) + " pts to " + member.getNextRank());
		return bar;
	}

	/** A line under the title, shown only when the member has something to act on. */
	private void status(String text)
	{
		statusLabel.setText(text);
		statusLabel.setVisible(!text.isEmpty());
	}

	private void revalidateAll()
	{
		revalidate();
		repaint();
	}

	/** A see-through holder that stacks its children, for parts of a section that change. */
	private static JPanel inline()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static JLabel small(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel wrapped(String text)
	{
		JLabel label = small("<html>" + escape(text) + "</html>");
		label.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, Integer.MAX_VALUE));
		return label;
	}

	private static JPanel row(String left, String right)
	{
		JPanel row = new JPanel(new GridLayout(1, 2));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel l = new JLabel(left);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JLabel r = new JLabel(right, JLabel.RIGHT);
		r.setForeground(Color.WHITE);
		row.add(l);
		row.add(r);
		return row;
	}

	/**
	 * Dashboard (members) and Discord, side by side at the bottom. Hidden when
	 * the server sent no links (an older server) or none apply.
	 */
	private void fillLinks(@Nullable MeResponse.Links links, boolean member)
	{
		linksRow.removeAll();
		if (links != null && member && links.getDashboard() != null)
		{
			linksRow.add(linkButton("Dashboard", links.getDashboard()));
		}
		if (links != null && links.getDiscord() != null)
		{
			linksRow.add(linkButton("Discord", links.getDiscord()));
		}
		linksRow.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, linksRow.getPreferredSize().height));
		linksRow.setVisible(linksRow.getComponentCount() > 0);
	}

	private static JButton linkButton(String text, String url)
	{
		JButton button = new JButton(text);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.addActionListener(e -> LinkBrowser.browse(url));
		return button;
	}

	/** SOTW or BOTW, from the event type the server sends. */
	static String shortType(@Nullable String eventType)
	{
		return eventType == null ? "Event" : eventType.toUpperCase(java.util.Locale.ROOT);
	}

	/** What a competition's "gained" counts: experience for a skill week, kills for a boss week. */
	static String gainUnit(@Nullable String eventType)
	{
		return "botw".equals(eventType) ? "kc" : "xp";
	}

	static String timeLeft(String verb, @Nullable String iso)
	{
		if (iso == null)
		{
			return "";
		}

		try
		{
			Duration left = Duration.between(Instant.now(), Instant.parse(iso));
			if (left.isNegative())
			{
				return verb.equals("Ends") ? "Ended" : "Starting soon";
			}
			long days = left.toDays();
			long hours = left.minusDays(days).toHours();
			long minutes = left.minusDays(days).minusHours(hours).toMinutes();
			String span = days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
			return verb + " in " + span;
		}
		catch (DateTimeParseException e)
		{
			return "";
		}
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static void onEdt(Runnable runnable)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			runnable.run();
		}
		else
		{
			SwingUtilities.invokeLater(runnable);
		}
	}
}
