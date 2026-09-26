package com.ironsgrotto.progress;

import com.ironsgrotto.session.AccountSession;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.SpriteOverride;

/**
 * A small square button with the clan crest in the collection log's header,
 * right of the search button: pressing it syncs the whole log
 * ({@link CollectionLogSync}).
 *
 * Left of the title, not in the row on the right: WikiSync and TempleOSRS put
 * their buttons there, and with both on a third one covered the log's title.
 * The title bar is moved to start after ours.
 *
 * Both of those plugins clear the log's custom widgets when it's built, and
 * Temple again when it's turned off, so this one:
 * - is added after theirs (a lower event priority runs later);
 * - puts itself back if one of them deletes it while the log is open;
 * - only deletes widgets itself when every custom widget there is ours.
 */
@Singleton
public class CollectionLogButton
{
	static final String NAME = "Irons Grotto";
	private static final int GAP = 4;
	private static final int CORNER = 9;
	private static final int ICON_SIZE = 16;
	/** Fully see-through: the click layer over the button draws nothing. */
	private static final int TRANSPARENT = 255;

	/** The crest, registered as a game sprite so a widget can draw it. */
	@Getter
	@RequiredArgsConstructor
	enum Sprites implements SpriteOverride
	{
		CREST(-24_801, "/com/ironsgrotto/panel_icon.png");

		private final int spriteId;
		private final String fileName;
	}

	private final Client client;
	private final ClientThread clientThread;
	private final AccountSession session;
	private final CollectionLogSync sync;
	private final SpriteManager spriteManager;

	@Inject
	CollectionLogButton(Client client, ClientThread clientThread, AccountSession session, CollectionLogSync sync,
		SpriteManager spriteManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.session = session;
		this.sync = sync;
		this.spriteManager = spriteManager;
	}

	/** Adds the button if the log is already open, e.g. when the plugin is turned on. */
	public void startUp()
	{
		spriteManager.addSpriteOverrides(Sprites.values());
		clientThread.invokeLater(() -> add(true));
	}

	public void shutDown()
	{
		clientThread.invokeLater(this::remove);
		spriteManager.removeSpriteOverrides(Sprites.values());
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

	/** @param makeRoom move the title bar past the button; only once per build of the log */
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

		// A square the height of the search button, just right of it.
		int size = search.getOriginalHeight();
		int x = search.getOriginalX() + search.getOriginalWidth() + GAP;
		int y = search.getOriginalY();
		int xMode = search.getXPositionMode();
		int yMode = search.getYPositionMode();
		int edge = size - 2 * CORNER;
		int[] sprites = GameIds.BUTTON_SPRITES;

		// Left-anchored like the search button, so the top-left corner is at x.
		Widget[] frame = {
			graphic(parent, sprites[0], x, y, size, size, xMode, yMode),
			graphic(parent, sprites[1], x, y, CORNER, CORNER, xMode, yMode),
			graphic(parent, sprites[2], x + size - CORNER, y, CORNER, CORNER, xMode, yMode),
			graphic(parent, sprites[3], x, y + size - CORNER, CORNER, CORNER, xMode, yMode),
			graphic(parent, sprites[4], x + size - CORNER, y + size - CORNER, CORNER, CORNER, xMode, yMode),
			graphic(parent, sprites[5], x, y + CORNER, CORNER, edge, xMode, yMode),
			graphic(parent, sprites[6], x + CORNER, y, edge, CORNER, xMode, yMode),
			graphic(parent, sprites[7], x + size - CORNER, y + CORNER, CORNER, edge, xMode, yMode),
			graphic(parent, sprites[8], x + CORNER, y + size - CORNER, edge, CORNER, xMode, yMode),
		};
		int inset = (size - ICON_SIZE) / 2;
		graphic(parent, Sprites.CREST.getSpriteId(), x + inset, y + inset, ICON_SIZE, ICON_SIZE, xMode, yMode);

		// On top of everything, so the whole button takes the mouse.
		Widget clickLayer = parent.createChild(-1, WidgetType.RECTANGLE)
			.setFilled(true)
			.setOpacity(TRANSPARENT)
			.setXPositionMode(xMode)
			.setYPositionMode(yMode)
			.setPos(x, y)
			.setSize(size, size)
			.setName(NAME);
		clickLayer.setHasListener(true);
		clickLayer.setOnMouseOverListener((JavaScriptCallback) ev -> hover(frame, true));
		clickLayer.setOnMouseLeaveListener((JavaScriptCallback) ev -> hover(frame, false));
		clickLayer.setAction(0, "Sync collection log");
		clickLayer.setOnOpListener((JavaScriptCallback) ev -> sync.requestSync());
		clickLayer.revalidate();

		// The draggable title bar would otherwise sit over the button and take
		// its clicks. It's left-anchored after the search button: start it
		// after ours instead.
		if (makeRoom && topBar.getXPositionMode() == WidgetPositionMode.ABSOLUTE_LEFT
			&& topBar.getOriginalX() < x + size + GAP)
		{
			int shift = x + size + GAP - topBar.getOriginalX();
			topBar.setOriginalX(topBar.getOriginalX() + shift);
			topBar.setOriginalWidth(topBar.getOriginalWidth() - shift);
			topBar.revalidate();
		}
		parent.revalidate();
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

	private static Widget graphic(Widget parent, int sprite, int x, int y, int w, int h, int xMode, int yMode)
	{
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC)
			.setSpriteId(sprite)
			.setXPositionMode(xMode)
			.setYPositionMode(yMode)
			.setPos(x, y)
			.setSize(w, h)
			.setName(NAME);
		widget.revalidate();
		return widget;
	}

	private static void hover(Widget[] frame, boolean hovered)
	{
		int[] sprites = hovered ? GameIds.BUTTON_SPRITES_HOVERED : GameIds.BUTTON_SPRITES;
		for (int i = 0; i < frame.length; i++)
		{
			frame[i].setSpriteId(sprites[i]);
		}
	}
}
