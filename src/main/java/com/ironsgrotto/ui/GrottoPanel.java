package com.ironsgrotto.ui;

import com.ironsgrotto.api.model.ClanEventStatus;
import com.ironsgrotto.api.model.MeResponse;
import com.ironsgrotto.api.model.MemberStatus;
import com.ironsgrotto.dev.DevTools;
import com.ironsgrotto.ledger.LedgerEventType;
import com.ironsgrotto.outbox.OutboxEntry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
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
	private final JPanel devSection = section();
	private final JLabel pendingLabel = new JLabel();
	private final Deque<OutboxEntry> recent = new ArrayDeque<>();
	private final String tokenUrl;

	private static final int RECENT_LIMIT = 10;

	public GrottoPanel(Runnable onRefresh, String tokenUrl, DevTools devTools)
	{
		this.tokenUrl = tokenUrl;

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
		content.add(devSection);
		content.add(Box.createVerticalStrut(8));

		JPanel footer = new JPanel(new BorderLayout());
		footer.setAlignmentX(Component.LEFT_ALIGNMENT);
		pendingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		pendingLabel.setFont(FontManager.getRunescapeSmallFont());
		JButton refresh = new JButton("Refresh");
		refresh.addActionListener(e -> onRefresh.run());
		footer.add(pendingLabel, BorderLayout.WEST);
		footer.add(refresh, BorderLayout.EAST);
		content.add(footer);

		add(content, BorderLayout.NORTH);

		buildDevSection(devTools);
		renderRecent();
		showLoggedOut();
		setPendingCount(0);
	}

	public void showLoggedOut()
	{
		onEdt(() ->
		{
			status("Log in to see your clan progress.");
			accountSection.setVisible(false);
			eventSection.setVisible(false);
		});
	}

	public void showNoToken()
	{
		onEdt(() ->
		{
			status("Link the plugin to get started.");
			accountSection.removeAll();
			accountSection.add(wrapped("1. Sign in with Discord on the Irons Grotto website."));
			accountSection.add(wrapped("2. Generate a plugin token."));
			accountSection.add(wrapped("3. Paste it into this plugin's settings."));
			accountSection.add(Box.createVerticalStrut(6));
			accountSection.add(linkButton("Get a token", tokenUrl));
			accountSection.setVisible(true);
			eventSection.setVisible(false);
			revalidateAll();
		});
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
			MemberStatus member = me.getMember();

			accountSection.add(heading(me.getRsn()));

			if (member == null)
			{
				status("Linked — not a clan member yet.");
				accountSection.add(wrapped("Your activity is being recorded. Join the clan to start ranking up."));
				if (me.getJoinUrl() != null)
				{
					accountSection.add(Box.createVerticalStrut(6));
					accountSection.add(linkButton("Join Irons Grotto", me.getJoinUrl()));
				}
			}
			else
			{
				status("Linked.");
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
				eventSection.add(heading(active.getTypeLabel() + ": " + active.getMetricName()));
				eventSection.add(small(timeLeft("Ends", active.getEndsAt())));
				eventSection.add(Box.createVerticalStrut(4));

				List<ClanEventStatus.Standing> standings = active.getStandings();
				if (active.isStandingsUnavailable())
				{
					eventSection.add(small("Standings unknown right now."));
				}
				else if (standings.isEmpty())
				{
					eventSection.add(small("Nobody has gained anything yet."));
				}
				else
				{
					for (ClanEventStatus.Standing standing : standings)
					{
						JPanel line = row(standing.getPosition() + ". " + standing.getPlayerName(), NUMBERS.format(standing.getGained()));
						if (standing.getPosition() == 1)
						{
							line.getComponent(0).setForeground(ACCENT);
						}
						eventSection.add(line);
					}
				}
			}
			else if (events.getNext() != null)
			{
				ClanEventStatus.EventSummary next = events.getNext();
				eventSection.add(heading("Next: " + next.getTypeLabel() + ": " + next.getMetricName()));
				eventSection.add(small(timeLeft("Starts", next.getStartsAt())));
			}
			else
			{
				eventSection.add(heading("Clan events"));
				eventSection.add(small("No event running."));
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

	public void setDevToolsVisible(boolean visible)
	{
		onEdt(() ->
		{
			devSection.setVisible(visible);
			revalidateAll();
		});
	}

	private void renderRecent()
	{
		activitySection.removeAll();
		activitySection.add(heading("Recent activity"));

		if (recent.isEmpty())
		{
			activitySection.add(small("Nothing recorded this session yet."));
		}
		for (OutboxEntry entry : recent)
		{
			activitySection.add(small((entry.isTest() ? "[Test] " : "") + describe(entry)));
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
				return "Loot: " + payload.get("source").getAsString()
					+ (payload.has("kc") ? " kc " + NUMBERS.format(payload.get("kc").getAsInt()) : "")
					+ " (" + NUMBERS.format(payload.get("totalValue").getAsLong()) + " gp)";
			case LedgerEventType.COLLECTION_LOG_ITEM:
				return "Collection log: " + payload.get("itemName").getAsString();
			case LedgerEventType.PET:
				return "Pet!";
			default:
				return entry.getType();
		}
	}

	private void buildDevSection(DevTools devTools)
	{
		devSection.add(heading("Developer tools"));
		devSection.add(wrapped("Spawn test events through the real hooks. They are marked as tests and never count for clan events."));
		devSection.add(Box.createVerticalStrut(4));

		JPanel buttons = new JPanel(new GridLayout(3, 2, 4, 4));
		buttons.setOpaque(false);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttons.add(devButton("Kill count", devTools::spawnKillCount));
		buttons.add(devButton("Drop", devTools::spawnDrop));
		buttons.add(devButton("Clog slot", devTools::spawnCollectionLog));
		buttons.add(devButton("Pet", devTools::spawnPet));
		buttons.add(devButton("Kill + drop", devTools::spawnKillWithDrop));
		devSection.add(buttons);
	}

	private static JButton devButton(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.addActionListener(e -> action.run());
		return button;
	}

	public void setPendingCount(int pending)
	{
		onEdt(() -> pendingLabel.setText(pending == 0 ? "All activity synced" : pending + " events waiting to send"));
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

	private void status(String text)
	{
		statusLabel.setText(text);
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
