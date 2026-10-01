package com.ironsgrotto.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * One block of the side panel: a header that folds the block away when
 * clicked, and a body the panel fills. Whether it is folded is remembered per
 * block (see {@link Store}), so a member's layout survives a restart.
 *
 * Swing thread only, like the rest of the panel.
 */
public class CollapsibleSection extends JPanel
{
	/** Remembers which blocks are folded. */
	public interface Store
	{
		boolean isCollapsed(String id);

		void setCollapsed(String id, boolean collapsed);
	}

	/** Caret colour; brighter while the header is hovered. */
	private static final Color CARET = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color CARET_HOVER = Color.WHITE;

	private final String id;
	private final Store store;
	private final JLabel title = new JLabel();
	private final JLabel aside = new JLabel();
	private final JLabel chevron = new JLabel();
	private final JPanel body = new JPanel();
	/** Held open whatever the member chose, while it asks them to act (the token prompt). */
	private boolean pinnedOpen;
	private final Caret caret = new Caret();

	CollapsibleSection(String id, Store store)
	{
		this.id = id;
		this.store = store;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setVisible(false);

		// Sized from its contents on every layout: fixing it once, before it has
		// a title, gave it no height, and a folded block then had nothing to
		// click to open it again.
		JPanel header = new JPanel(new BorderLayout(4, 0))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(PluginPanel.PANEL_WIDTH, getPreferredSize().height);
			}
		};
		header.setOpaque(false);
		header.setToolTipText("Click to show or hide");
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.CENTER);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
		right.setOpaque(false);
		aside.setFont(FontManager.getRunescapeSmallFont());
		aside.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		chevron.setIcon(caret);
		right.add(aside);
		right.add(Box.createHorizontalStrut(6));
		right.add(chevron);
		header.add(right, BorderLayout.EAST);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!pinnedOpen)
				{
					store.setCollapsed(id, !store.isCollapsed(id));
					refreshFolding();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setHovered(true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setHovered(false);
			}
		});

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		body.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.setBorder(new EmptyBorder(4, 0, 0, 0));

		add(header);
		add(body);
		refreshFolding();
	}

	/** Empties the block for a fresh render: no note, no pin. */
	void clear(String titleText)
	{
		body.removeAll();
		title.setText(titleText);
		aside.setText("");
		pinnedOpen = false;
		refreshFolding();
	}

	void setTitle(String text)
	{
		title.setText(text);
	}

	/** A short note right of the title ("last 24h"); null for none. */
	void setAside(@Nullable String text)
	{
		aside.setText(text == null ? "" : text);
	}

	/** Keeps the block open (and unfoldable) while it needs the member. */
	void setPinnedOpen(boolean pinned)
	{
		pinnedOpen = pinned;
		refreshFolding();
	}

	JPanel body()
	{
		return body;
	}

	private void refreshFolding()
	{
		boolean collapsed = !pinnedOpen && store.isCollapsed(id);
		body.setVisible(!collapsed);
		caret.open = !collapsed;
		chevron.setVisible(!pinnedOpen);
		revalidate();
		repaint();
	}

	private void setHovered(boolean hovered)
	{
		if (pinnedOpen)
		{
			return;
		}
		caret.color = hovered ? CARET_HOVER : CARET;
		title.setForeground(hovered ? ColorScheme.BRAND_ORANGE : Color.WHITE);
		chevron.repaint();
	}

	/**
	 * A small filled triangle, pointing down when open and right when folded.
	 * Drawn rather than typed: the RuneScape font has no arrow glyphs.
	 */
	private static final class Caret implements Icon
	{
		private static final int SIZE = 9;
		private boolean open = true;
		private Color color = CARET;

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			Polygon triangle = open
				? new Polygon(new int[]{x, x + SIZE, x + SIZE / 2}, new int[]{y + 2, y + 2, y + SIZE - 1}, 3)
				: new Polygon(new int[]{x + 2, x + SIZE - 1, x + 2}, new int[]{y, y + SIZE / 2, y + SIZE}, 3);
			g2.fill(triangle);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}
}
