package com.ironsgrotto.progress;

import com.ironsgrotto.session.AccountSession;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * The "Grotto" button in the collection log's header, beside the search
 * button: pressing it syncs the whole log ({@link CollectionLogSync}).
 *
 * The same stone button WikiSync and TempleOSRS add in the same row. Each of
 * them sits to the left of the close button and clears the log's custom
 * widgets when the log is built, so this one:
 * - is added after them (a lower event priority runs later), so theirs never
 *   delete it;
 * - goes to the left of whatever buttons are already in the row, however many
 *   of those plugins are on;
 * - puts itself back if one of them deletes it while the log is open. Turning
 *   TempleOSRS off runs its cleanup, which deletes every custom widget on the
 *   log, ours included.
 */
@Singleton
public class CollectionLogButton
{
	static final String NAME = "Irons Grotto";
	private static final int CLOSE_BUTTON_OFFSET = 28;
	private static final int GAP = 5;
	private static final int WIDTH = 60;
	private static final int CORNER = 9;
	private static final int TEXT_COLOUR = 0xd6d6d6;
	private static final int TEXT_COLOUR_HOVERED = 0xffffff;

	private final Client client;
	private final ClientThread clientThread;
	private final AccountSession session;
	private final CollectionLogSync sync;

	@Inject
	CollectionLogButton(Client client, ClientThread clientThread, AccountSession session, CollectionLogSync sync)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.session = session;
		this.sync = sync;
	}

	/** Adds the button if the log is already open, e.g. when the plugin is turned on. */
	public void startUp()
	{
		clientThread.invokeLater(() -> add(true));
	}

	public void shutDown()
	{
		clientThread.invokeLater(this::remove);
	}

	@Subscribe(priority = -1)
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == GameIds.SCRIPT_COLLECTION_SETUP)
		{
			remove();
			add(true);
		}
	}

	/** Re-adds the button if another plugin deleted it while the log is open. */
	@Subscribe
	public void onGameTick(GameTick tick)
	{
		Widget parent = client.getWidget(InterfaceID.Collection.UNIVERSE);
		if (parent != null && !parent.isHidden() && !hasVisibleButton(parent))
		{
			// The title bar already made room for it when the log was built.
			add(false);
		}
	}

	private static boolean hasVisibleButton(Widget parent)
	{
		Widget[] children = parent.getChildren();
		if (children == null)
		{
			return false;
		}
		for (Widget child : children)
		{
			if (child != null && NAME.equals(child.getName()) && !child.isSelfHidden())
			{
				return true;
			}
		}
		return false;
	}

	/** @param makeRoom narrow the title bar; only once per build of the log */
	private void add(boolean makeRoom)
	{
		// Only for an account the plugin tracks (not on excluded world types).
		if (session.getIdentity() == null)
		{
			return;
		}

		Widget parent = client.getWidget(InterfaceID.Collection.UNIVERSE);
		Widget search = client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE);
		Widget container = client.getWidget(InterfaceID.Collection.INFINITY);
		Widget[] containerChildren = container == null ? null : container.getChildren();
		if (parent == null || search == null || containerChildren == null || containerChildren.length == 0
			|| containerChildren[0] == null)
		{
			return;
		}
		Widget topBar = containerChildren[0];

		int y = search.getOriginalY();
		int x = leftOfExistingButtons(parent, y);
		int h = search.getOriginalHeight();
		int[] sprites = GameIds.BUTTON_SPRITES;

		// Positions count from the right edge (ABSOLUTE_RIGHT), so the left
		// corners are the ones at x + WIDTH - CORNER.
		Widget[] frame = {
			graphic(parent, sprites[0], x, y, WIDTH, h, search.getYPositionMode()),
			graphic(parent, sprites[1], x + WIDTH - CORNER, y, CORNER, CORNER, WidgetPositionMode.ABSOLUTE_TOP),
			graphic(parent, sprites[2], x, y, CORNER, CORNER, WidgetPositionMode.ABSOLUTE_TOP),
			graphic(parent, sprites[3], x + WIDTH - CORNER, y + h - CORNER, CORNER, CORNER, WidgetPositionMode.ABSOLUTE_TOP),
			graphic(parent, sprites[4], x, y + h - CORNER, CORNER, CORNER, WidgetPositionMode.ABSOLUTE_TOP),
			graphic(parent, sprites[5], x + WIDTH - CORNER, y + CORNER, CORNER, h - 2 * CORNER, WidgetPositionMode.ABSOLUTE_TOP),
			graphic(parent, sprites[6], x + CORNER, y, WIDTH - 2 * CORNER, CORNER, WidgetPositionMode.ABSOLUTE_TOP),
			graphic(parent, sprites[7], x, y + CORNER, CORNER, h - 2 * CORNER, WidgetPositionMode.ABSOLUTE_TOP),
			graphic(parent, sprites[8], x + CORNER, y + h - CORNER, WIDTH - 2 * CORNER, CORNER, WidgetPositionMode.ABSOLUTE_TOP),
		};

		Widget text = parent.createChild(-1, WidgetType.TEXT)
			.setText("Grotto")
			.setTextColor(TEXT_COLOUR)
			.setFontId(FontID.PLAIN_11)
			.setTextShadowed(true)
			.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
			.setYPositionMode(search.getYPositionMode())
			.setXTextAlignment(WidgetTextAlignment.CENTER)
			.setYTextAlignment(WidgetTextAlignment.CENTER)
			.setPos(x, y)
			.setSize(WIDTH, h)
			.setName(NAME);
		text.setHasListener(true);
		text.setOnMouseOverListener((JavaScriptCallback) ev -> hover(frame, text, true));
		text.setOnMouseLeaveListener((JavaScriptCallback) ev -> hover(frame, text, false));
		text.setAction(0, "Sync your collection log with Irons Grotto");
		text.setOnOpListener((JavaScriptCallback) ev -> sync.requestSync());
		text.revalidate();

		// The draggable title bar would otherwise sit over the button and take its clicks.
		if (makeRoom)
		{
			topBar.setOriginalWidth(topBar.getOriginalWidth() - (WIDTH + GAP));
			topBar.revalidate();
		}
		parent.revalidate();
	}

	/**
	 * Where this button goes: left of the close button, and left of any
	 * button another plugin already put in the row.
	 */
	private static int leftOfExistingButtons(Widget parent, int rowY)
	{
		int x = CLOSE_BUTTON_OFFSET + GAP;
		Widget[] children = parent.getChildren();
		if (children == null)
		{
			return x;
		}
		for (Widget child : children)
		{
			if (child != null && !child.isSelfHidden() && !NAME.equals(child.getName())
				&& child.getXPositionMode() == WidgetPositionMode.ABSOLUTE_RIGHT && child.getOriginalY() == rowY)
			{
				x = Math.max(x, child.getOriginalX() + child.getOriginalWidth() + GAP);
			}
		}
		return x;
	}

	/**
	 * Takes this button off the log. When it's the only custom widget there
	 * they're all cleared, as the other plugins do; otherwise only ours are
	 * hidden, never someone else's deleted.
	 */
	private void remove()
	{
		Widget parent = client.getWidget(InterfaceID.Collection.UNIVERSE);
		Widget[] children = parent == null ? null : parent.getChildren();
		if (children == null || children.length == 0)
		{
			return;
		}

		boolean onlyOurs = true;
		for (Widget child : children)
		{
			if (child != null && !NAME.equals(child.getName()))
			{
				onlyOurs = false;
				break;
			}
		}

		if (onlyOurs)
		{
			parent.deleteAllChildren();
		}
		else
		{
			for (Widget child : children)
			{
				if (child != null && NAME.equals(child.getName()))
				{
					child.setHidden(true);
				}
			}
		}
		parent.revalidate();
	}

	private static Widget graphic(Widget parent, int sprite, int x, int y, int w, int h, int yMode)
	{
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC)
			.setSpriteId(sprite)
			.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
			.setYPositionMode(yMode)
			.setPos(x, y)
			.setSize(w, h)
			.setName(NAME);
		widget.revalidate();
		return widget;
	}

	private static void hover(Widget[] frame, Widget text, boolean hovered)
	{
		int[] sprites = hovered ? GameIds.BUTTON_SPRITES_HOVERED : GameIds.BUTTON_SPRITES;
		for (int i = 0; i < frame.length; i++)
		{
			frame[i].setSpriteId(sprites[i]);
		}
		text.setTextColor(hovered ? TEXT_COLOUR_HOVERED : TEXT_COLOUR);
	}
}
