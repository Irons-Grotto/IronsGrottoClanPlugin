package com.ironsgrotto.ui;

import com.ironsgrotto.IronsGrottoConfig;
import com.ironsgrotto.IronsGrottoPlugin;
import com.ironsgrotto.api.model.ClanEventStatus;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The running SOTW/BOTW on the game screen, for members who would rather not
 * keep the side panel open.
 *
 * RuneLite does the moving and sizing: hold Alt to drag it anywhere, drag an
 * edge to resize, right-click with Alt to reset; position and size are saved.
 * Its height decides how many standings fit, so dragging it taller shows more.
 */
public class ClanEventOverlay extends OverlayPanel
{
	/** Rows shown before the member has resized it. */
	static final int DEFAULT_ROWS = 3;
	private static final Color TITLE = new Color(255, 152, 31);
	private static final Color LEADER = new Color(76, 175, 120);
	private static final Color GAINED = Color.YELLOW;
	private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance();

	private final IronsGrottoConfig config;
	@Nullable
	private volatile ClanEventStatus status;

	@Inject
	ClanEventOverlay(IronsGrottoPlugin plugin, IronsGrottoConfig config)
	{
		super(plugin);
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setResizable(true);
		setResettable(true);
		setMinimumSize(110);
	}

	/** The latest standings; called from any thread. */
	public void setStatus(@Nullable ClanEventStatus status)
	{
		this.status = status;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ClanEventStatus current = status;
		if (!config.showEventOverlay() || current == null)
		{
			return null;
		}

		panelComponent.setBackgroundColor(config.overlayBackground());
		ClanEventStatus.ActiveEvent active = current.getActive();
		ClanEventStatus.EventSummary next = current.getNext();

		if (active == null)
		{
			if (next == null)
			{
				return null;
			}
			// Nothing running: say what's coming and when.
			title("Next " + GrottoPanel.shortType(next.getType()) + ": " + next.getMetricName());
			subtitle(GrottoPanel.timeLeft("Starts", next.getStartsAt()));
			return super.render(graphics);
		}

		title(GrottoPanel.shortType(active.getType()) + ": " + active.getMetricName());
		subtitle(GrottoPanel.timeLeft("Ends", active.getEndsAt()));

		List<ClanEventStatus.Standing> standings = active.getStandings();
		if (active.isStandingsUnavailable())
		{
			subtitle("Standings unavailable");
		}
		else if (standings.isEmpty())
		{
			subtitle("No gains yet");
		}
		else
		{
			Dimension size = getPreferredSize();
			int lineHeight = graphics.getFontMetrics().getHeight() + 1;
			int rows = rowsFor(size == null ? 0 : size.height, lineHeight, standings.size());
			for (ClanEventStatus.Standing standing : standings.subList(0, rows))
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(standing.getPosition() + ". " + standing.getPlayerName())
					.leftColor(standing.getPosition() == 1 ? LEADER : Color.WHITE)
					.right(NUMBERS.format(standing.getGained()))
					.rightColor(GAINED)
					.build());
			}
		}

		return super.render(graphics);
	}

	private void title(String text)
	{
		panelComponent.getChildren().add(TitleComponent.builder().text(text).color(TITLE).build());
	}

	private void subtitle(String text)
	{
		if (!text.isEmpty())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text(text).color(Color.LIGHT_GRAY).build());
		}
	}

	/**
	 * How many standings fit in a box of this height: what is left after the
	 * border and the two title lines, one line per row, at least one and no
	 * more than there are. An overlay never resized (height 0) shows the
	 * default.
	 */
	static int rowsFor(int height, int lineHeight, int available)
	{
		if (available <= 0)
		{
			return 0;
		}
		if (height <= 0 || lineHeight <= 0)
		{
			return Math.min(DEFAULT_ROWS, available);
		}
		int border = ComponentConstants.STANDARD_BORDER * 2;
		int rows = (height - border - 2 * lineHeight) / lineHeight;
		return Math.max(1, Math.min(available, rows));
	}
}
