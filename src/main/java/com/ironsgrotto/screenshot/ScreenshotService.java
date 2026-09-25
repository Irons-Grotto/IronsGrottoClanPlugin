package com.ironsgrotto.screenshot;

import com.ironsgrotto.session.AccountIdentity;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import com.ironsgrotto.SyncExecutor;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.DrawManager;

/**
 * Captures the game view for a ledger event and queues it for upload.
 *
 * The frame is taken on the next render, so a drop on the floor or a chat
 * line added this tick is in it. Encoding and the disk write happen off the
 * render thread.
 */
@Slf4j
@Singleton
public class ScreenshotService
{
	/** Keeps uploads well under the server's 2 MB limit on large clients. */
	static final int MAX_WIDTH = 1600;
	private static final float JPEG_QUALITY = 0.85f;

	private final DrawManager drawManager;
	private final SyncExecutor executor;

	private volatile ScreenshotStore store;

	@Inject
	ScreenshotService(DrawManager drawManager, SyncExecutor executor)
	{
		this.drawManager = drawManager;
		this.executor = executor;
	}

	public void attach(ScreenshotStore store)
	{
		this.store = store;
	}

	public void capture(String eventId, AccountIdentity account)
	{
		ScreenshotStore target = store;
		if (target == null)
		{
			return;
		}

		drawManager.requestNextFrameListener(frame -> executor.execute(() ->
		{
			try
			{
				target.save(eventId, account, encode(frame));
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Could not save screenshot for {}", eventId, e);
			}
		}));
	}

	static byte[] encode(Image frame) throws IOException
	{
		int width = frame.getWidth(null);
		int height = frame.getHeight(null);
		if (width > MAX_WIDTH)
		{
			height = height * MAX_WIDTH / width;
			width = MAX_WIDTH;
		}

		// JPEG has no alpha; draw onto an RGB canvas first.
		BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = rgb.createGraphics();
		try
		{
			g.drawImage(frame, 0, 0, width, height, null);
		}
		finally
		{
			g.dispose();
		}

		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		ImageWriter writer = writers.next();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out))
		{
			writer.setOutput(stream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.write(null, new IIOImage(rgb, null, null), param);
		}
		finally
		{
			writer.dispose();
		}
		return out.toByteArray();
	}
}
