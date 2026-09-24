package com.ironsgrotto.screenshot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ironsgrotto.api.model.PluginPolicy;
import com.ironsgrotto.ledger.LedgerEventType;
import com.ironsgrotto.outbox.OutboxEntry;
import com.ironsgrotto.session.AccountIdentity;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenshotTest
{
	private static final AccountIdentity ACCOUNT = new AccountIdentity("1", "Iron Dude");

	private static OutboxEntry loot(long value)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("totalValue", value);
		return OutboxEntry.create(LedgerEventType.LOOT, payload, ACCOUNT, Instant.EPOCH);
	}

	@Test
	public void screenshotsLootAtOrAboveTheServersThreshold()
	{
		PluginPolicy policy = new PluginPolicy();
		policy.setMinScreenshotLootValue(1_000_000);

		assertTrue(ScreenshotPolicy.shouldCapture(loot(1_000_000), policy));
		assertFalse(ScreenshotPolicy.shouldCapture(loot(999_999), policy));
	}

	@Test
	public void screenshotsClogAndPetsOnlyWhenPolicyAllows()
	{
		PluginPolicy policy = new PluginPolicy();
		OutboxEntry clog = OutboxEntry.create(LedgerEventType.COLLECTION_LOG_ITEM, new JsonObject(), ACCOUNT, Instant.EPOCH);
		OutboxEntry kc = OutboxEntry.create(LedgerEventType.BOSS_KC, new JsonObject(), ACCOUNT, Instant.EPOCH);

		assertTrue(ScreenshotPolicy.shouldCapture(clog, policy));
		policy.setScreenshotCollectionLog(false);
		assertFalse(ScreenshotPolicy.shouldCapture(clog, policy));
		assertFalse(ScreenshotPolicy.shouldCapture(kc, policy));
	}

	@Test
	public void encodesAScaledJpegWithoutAlpha() throws Exception
	{
		BufferedImage frame = new BufferedImage(3200, 1800, BufferedImage.TYPE_INT_ARGB);

		byte[] jpeg = ScreenshotService.encode(frame);
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));

		assertEquals(ScreenshotService.MAX_WIDTH, decoded.getWidth());
		assertEquals(900, decoded.getHeight());
	}

	@Test
	public void storeRoundTripsPendingScreenshots() throws Exception
	{
		Path dir = Files.createTempDirectory("shots");
		ScreenshotStore store = new ScreenshotStore(dir, new Gson());

		store.save("event-1", ACCOUNT, new byte[] {1, 2, 3});
		List<ScreenshotStore.Pending> pending = store.list();

		assertEquals(1, pending.size());
		assertEquals("event-1", pending.get(0).getEventId());
		assertEquals(ACCOUNT, pending.get(0).getAccount());

		store.remove("event-1");
		assertTrue(store.list().isEmpty());
	}
}
