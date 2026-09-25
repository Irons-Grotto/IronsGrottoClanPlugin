package com.ironsgrotto.ui;

import com.ironsgrotto.api.model.ClanEventStatus;
import com.ironsgrotto.api.model.MeResponse;
import com.ironsgrotto.api.model.MemberStatus;
import com.ironsgrotto.ledger.LedgerEventType;
import com.ironsgrotto.outbox.OutboxEntry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
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
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

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
	private final JPanel accountSection = section();
	private final JPanel eventSection = section();
	private final JPanel activitySection = section();
	private final JLabel pendingLabel = new JLabel();

	{
		pendingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		pendingLabel.setFont(FontManager.getRunescapeSmallFont());
	}
	private final Deque<OutboxEntry> recent = new ArrayDeque<>();
	private final String tokenUrl;
	private final String joinUrl;
	/** The token prompt's explanation and link, swapped once we know if the account is registered. */
	private final JPanel tokenIntro = inline();
	private final JPanel tokenLink = inline();
	@Nullable
	private String tokenPromptRsn;
	private volatile Consumer<String> onTokenEntered = token -> { };

	private static final int RECENT_LIMIT = 10;
	/** Quiet time after the last keystroke before a token is checked. */
	private static final int TOKEN_SETTLE_MS = 400;
	private static final Pattern TOKEN_SHAPE = Pattern.compile("^igp_[A-Za-z0-9_-]{43}$");

	/** @param siteUrl the Irons Grotto site, e.g. https://ironsgrotto.xyz */
	public GrottoPanel(String siteUrl)
	{
		this.tokenUrl = siteUrl + "/plugin";
		this.joinUrl = siteUrl + "/join";

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

		content.add(accountSection);
		content.add(Box.createVerticalStrut(8));
		content.add(eventSection);
		content.add(Box.createVerticalStrut(8));
		content.add(activitySection);
		content.add(Box.createVerticalStrut(8));

		// No buttons and no "all good" status: the plugin syncs by itself, and
		// the panel only speaks up when the member has something to do.
		pendingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(pendingLabel);

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
			accountSection.removeAll();
			accountSection.add(heading("Connect " + rsn));
			tokenPromptRsn = rsn;
			fillTokenHelp(rsn, true);
			accountSection.add(tokenIntro);
			accountSection.add(Box.createVerticalStrut(6));

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

			accountSection.add(field);
			accountSection.add(Box.createVerticalStrut(6));
			accountSection.add(tokenLink);
			accountSection.setVisible(true);
			eventSection.setVisible(false);
			revalidateAll();
		});
	}

	/**
	 * Points the token prompt at the right place once the server says whether
	 * the account is on the site: a registered account gets a token straight
	 * from {@code /plugin}; a new one joins, and {@code /join} makes its token.
	 */
	public void showTokenSource(String rsn, boolean registered)
	{
		onEdt(() ->
		{
			if (rsn.equals(tokenPromptRsn))
			{
				fillTokenHelp(rsn, registered);
				revalidateAll();
			}
		});
	}

	private void fillTokenHelp(String rsn, boolean registered)
	{
		tokenIntro.removeAll();
		tokenLink.removeAll();
		if (registered)
		{
			tokenIntro.add(wrapped("Get a token for this account and paste it here."));
			tokenLink.add(linkButton("Get a token", tokenUrlFor(tokenUrl, rsn)));
		}
		else
		{
			tokenIntro.add(wrapped(rsn + " isn't in Irons Grotto yet. Join to get a token, then paste it here."));
			tokenLink.add(linkButton("Join Irons Grotto", joinUrl));
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
		try
		{
			return tokenUrl + "?name=" + URLEncoder.encode(rsn, "UTF-8").replace("+", "%20");
		}
		catch (UnsupportedEncodingException e)
		{
			return tokenUrl;
		}
	}

	public void showError(String message)
	{
		onEdt(() -> status("<html>" + escape(message) + "</html>"));
	}

	public void showMe(MeResponse me)
	{
		onEdt(() ->
		{
			accountSection.removeAll();
			tokenPromptRsn = null;
			MemberStatus member = me.getMember();

			accountSection.add(heading(me.getRsn()));

			status("");

			if (member == null)
			{
				accountSection.add(small("Not a clan member yet."));
				if (me.getJoinUrl() != null)
				{
					accountSection.add(Box.createVerticalStrut(6));
					// Linked but not joined yet: they're partway through /join, so
					// this takes them back to it rather than starting over.
					accountSection.add(linkButton("Continue", me.getJoinUrl()));
				}
			}
			else
			{
				accountSection.add(row("Rank", member.getRank()));
				accountSection.add(row("Points", NUMBERS.format(Math.floor(member.getPoints()))));
				accountSection.add(Box.createVerticalStrut(4));
				accountSection.add(rankProgress(member));
			}

			accountSection.setVisible(true);
			revalidateAll();
		});
	}

	public void showEvents(ClanEventStatus events)
	{
		onEdt(() ->
		{
			eventSection.removeAll();
			ClanEventStatus.ActiveEvent active = events.getActive();

			if (active != null)
			{
				eventSection.add(heading(shortType(active.getType()) + ": " + active.getMetricName()));
				eventSection.add(small(timeLeft("Ends", active.getEndsAt())));
				eventSection.add(Box.createVerticalStrut(4));

				List<ClanEventStatus.Standing> standings = active.getStandings();
				if (active.isStandingsUnavailable())
				{
					eventSection.add(small("Standings unavailable"));
				}
				else if (standings.isEmpty())
				{
					eventSection.add(small("No gains yet"));
				}
				else
				{
					for (ClanEventStatus.Standing standing : standings)
					{
						JPanel line = row(standing.getPosition() + ". " + standing.getPlayerName(),
							NUMBERS.format(standing.getGained()) + " " + gainUnit(active.getType()));
						if (standing.getPosition() == 1)
						{
							line.getComponent(0).setForeground(ACCENT);
						}
						eventSection.add(line);
					}
				}
			}
			ClanEventStatus.EventSummary next = events.getNext();
			if (next != null)
			{
				String upcoming = "Upcoming " + shortType(next.getType()) + ": " + next.getMetricName();
				if (active != null)
				{
					eventSection.add(Box.createVerticalStrut(4));
					eventSection.add(small(upcoming));
				}
				else
				{
					eventSection.add(heading(upcoming));
					eventSection.add(small(timeLeft("Starts", next.getStartsAt())));
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
		activitySection.removeAll();
		if (recent.isEmpty())
		{
			activitySection.setVisible(false);
			revalidateAll();
			return;
		}

		activitySection.add(heading("Recent activity"));
		for (OutboxEntry entry : recent)
		{
			activitySection.add(small(describe(entry)));
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

	private static JPanel section()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setVisible(false);
		return panel;
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

	private static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
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
