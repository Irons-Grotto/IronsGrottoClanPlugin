package com.ironsgrotto.progress;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ironsgrotto.api.ApiException;
import com.ironsgrotto.api.GrottoApiClient;
import com.ironsgrotto.session.AccountIdentity;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProgressTest
{
	private static final AccountIdentity ACCOUNT = new AccountIdentity("1", "Iron Dude");

	@Test
	public void diaryTierIsTheHighestCompleted()
	{
		assertEquals("None", ProgressRules.diaryTier(new int[] {0, 0, 0, 0}));
		assertEquals("Hard", ProgressRules.diaryTier(new int[] {1, 1, 1, 0}));
		// Karamja's older varbits may read 2 when done; any non-zero counts.
		assertEquals("Medium", ProgressRules.diaryTier(new int[] {2, 2, 0, 0}));
	}

	@Test
	public void caTierFollowsPointThresholds()
	{
		int[] thresholds = {41, 161, 416, 1064, 1904, 2630};
		assertEquals("None", ProgressRules.combatAchievementTier(40, thresholds));
		assertEquals("Elite", ProgressRules.combatAchievementTier(1100, thresholds));
		assertEquals("Grandmaster", ProgressRules.combatAchievementTier(2630, thresholds));
		assertEquals("None", ProgressRules.combatAchievementTier(3000, new int[6]));
	}

	/** An API client that records what it was asked to send. */
	private static class RecordingApi extends GrottoApiClient
	{
		final List<JsonObject> sent = new ArrayList<>();
		ApiException failWith;

		RecordingApi()
		{
			super(null, new Gson(), null, null, "test");
		}

		@Override
		public JsonObject putProgress(AccountIdentity identity, JsonObject progress) throws ApiException
		{
			if (failWith != null)
			{
				throw failWith;
			}
			sent.add(progress);
			return new JsonObject();
		}
	}

	private static JsonObject skills(int total)
	{
		JsonObject skills = new JsonObject();
		skills.addProperty("totalLevel", total);
		return skills;
	}

	@Test
	public void collectionLogChatIsBitOneOfTheSetting()
	{
		assertFalse(ProgressRules.collectionLogChatEnabled(0));
		assertTrue(ProgressRules.collectionLogChatEnabled(1));
		assertFalse(ProgressRules.collectionLogChatEnabled(2));
		assertTrue(ProgressRules.collectionLogChatEnabled(3));
	}

	@Test
	public void sendsEachCategoryOnceUntilItChanges()
	{
		RecordingApi api = new RecordingApi();
		ProgressUploader uploader = new ProgressUploader(api, Clock.systemUTC());

		uploader.submit(ACCOUNT, "skills", skills(1500));
		uploader.flush();
		uploader.submit(ACCOUNT, "skills", skills(1500));
		uploader.flush();
		uploader.submit(ACCOUNT, "skills", skills(1501));
		uploader.flush();

		assertEquals(2, api.sent.size());
		assertEquals(1501, api.sent.get(1).getAsJsonObject("skills").get("totalLevel").getAsInt());
	}

	@Test
	public void mergesCollectionLogCountsWithItsItems()
	{
		RecordingApi api = new RecordingApi();
		ProgressUploader uploader = new ProgressUploader(api, Clock.systemUTC());
		JsonObject items = new JsonObject();
		items.addProperty("complete", true);
		JsonObject counts = new JsonObject();
		counts.addProperty("obtained", 500);

		uploader.submit(ACCOUNT, "collectionLog", items);
		uploader.submit(ACCOUNT, "collectionLog", counts);
		uploader.flush();

		JsonObject log = api.sent.get(0).getAsJsonObject("collectionLog");
		assertTrue(log.get("complete").getAsBoolean());
		assertEquals(500, log.get("obtained").getAsInt());
	}

	@Test
	public void keepsProgressWhenTheServerIsDown()
	{
		RecordingApi api = new RecordingApi();
		ProgressUploader uploader = new ProgressUploader(api, Clock.systemUTC());
		api.failWith = new ApiException(503, "down");

		uploader.submit(ACCOUNT, "skills", skills(1500));
		uploader.flush();

		assertTrue(uploader.hasPending());
	}

	@Test
	public void dropsProgressTheServerRejects()
	{
		RecordingApi api = new RecordingApi();
		ProgressUploader uploader = new ProgressUploader(api, Clock.systemUTC());
		api.failWith = new ApiException(400, "bad");

		uploader.submit(ACCOUNT, "skills", skills(1500));
		uploader.flush();

		assertFalse(uploader.hasPending());
	}

	@Test
	public void tellsWhichUploadsCarriedTheFullLog()
	{
		RecordingApi api = new RecordingApi();
		ProgressUploader uploader = new ProgressUploader(api, Clock.systemUTC());
		List<JsonObject> sent = new ArrayList<>();
		uploader.setOnSent(sent::add);
		JsonObject full = new JsonObject();
		full.addProperty("complete", true);

		assertTrue(uploader.submit(ACCOUNT, "collectionLog", full));
		uploader.flush();
		// The same log again: nothing to send, so the caller knows it's already there.
		assertFalse(uploader.submit(ACCOUNT, "collectionLog", full));

		assertEquals(1, sent.size());
		assertTrue(ProgressSync.isFullLog(sent.get(0)));
		JsonObject countsOnly = new JsonObject();
		countsOnly.add("collectionLog", new JsonObject());
		assertFalse(ProgressSync.isFullLog(countsOnly));
		assertFalse(ProgressSync.isFullLog(new JsonObject()));
	}

	@Test
	public void reportsARefusedUploadButNotARetriedOne()
	{
		RecordingApi api = new RecordingApi();
		ProgressUploader uploader = new ProgressUploader(api, Clock.systemUTC());
		List<JsonObject> dropped = new ArrayList<>();
		uploader.setOnDropped(dropped::add);

		api.failWith = new ApiException(503, "down");
		uploader.submit(ACCOUNT, "skills", skills(1500));
		uploader.flush();
		assertTrue(dropped.isEmpty());

		RecordingApi refusing = new RecordingApi();
		ProgressUploader other = new ProgressUploader(refusing, Clock.systemUTC());
		other.setOnDropped(dropped::add);
		refusing.failWith = new ApiException(400, "bad");
		other.submit(ACCOUNT, "skills", skills(1500));
		other.flush();
		assertEquals(1, dropped.size());
	}

	@Test
	public void syncStageFollowsTheLogAndThePhase()
	{
		java.time.Instant before = java.time.Instant.parse("2026-09-01T00:00:00Z");
		assertEquals(CollectionLogSyncState.Stage.OPEN_LOG,
			new CollectionLogSyncState(false, CollectionLogSyncState.Phase.IDLE, null).stage());
		assertEquals(CollectionLogSyncState.Stage.READY,
			new CollectionLogSyncState(true, CollectionLogSyncState.Phase.IDLE, before).stage());
		// A running or finished sync shows as such whether the log is open or not.
		assertEquals(CollectionLogSyncState.Stage.SYNCING,
			new CollectionLogSyncState(false, CollectionLogSyncState.Phase.SYNCING, null).stage());
		assertEquals(CollectionLogSyncState.Stage.SYNCED,
			new CollectionLogSyncState(false, CollectionLogSyncState.Phase.SYNCED, before).stage());
		assertTrue(new CollectionLogSyncState(true, CollectionLogSyncState.Phase.IDLE, null).isFirstSync());
		assertFalse(new CollectionLogSyncState(true, CollectionLogSyncState.Phase.IDLE, before).isFirstSync());
	}
}
