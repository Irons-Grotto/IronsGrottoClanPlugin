package com.ironsgrotto.screenshot;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.ironsgrotto.session.AccountIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Screenshots waiting to be uploaded, kept on disk so a crash or an outage
 * does not lose the proof. Each is two files named by its ledger event id:
 * the JPEG, and a small JSON note of which account took it.
 */
@Slf4j
public class ScreenshotStore
{
	private final Path dir;
	private final Gson gson;

	public ScreenshotStore(Path dir, Gson gson)
	{
		this.dir = dir;
		this.gson = gson;
	}

	@Value
	public static class Pending
	{
		String eventId;
		AccountIdentity account;
		Path image;
		FileTime createdAt;
	}

	public void save(String eventId, AccountIdentity account, byte[] jpeg) throws IOException
	{
		Files.createDirectories(dir);
		// Image first: a note without an image is ignored, an image without a
		// note would be unattributable.
		Files.write(dir.resolve(eventId + ".jpg"), jpeg);
		Files.write(dir.resolve(eventId + ".json"), gson.toJson(account).getBytes(StandardCharsets.UTF_8));
	}

	public List<Pending> list()
	{
		List<Pending> pending = new ArrayList<>();
		if (!Files.isDirectory(dir))
		{
			return pending;
		}

		try (DirectoryStream<Path> notes = Files.newDirectoryStream(dir, "*.json"))
		{
			for (Path note : notes)
			{
				String eventId = note.getFileName().toString().replace(".json", "");
				Path image = dir.resolve(eventId + ".jpg");
				if (!Files.exists(image))
				{
					Files.deleteIfExists(note);
					continue;
				}

				try
				{
					AccountIdentity account = gson.fromJson(new String(Files.readAllBytes(note), StandardCharsets.UTF_8), AccountIdentity.class);
					pending.add(new Pending(eventId, account, image, Files.getLastModifiedTime(image)));
				}
				catch (JsonParseException e)
				{
					log.warn("Discarding unreadable screenshot note {}", note, e);
					remove(eventId);
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Could not list pending screenshots", e);
		}

		return pending;
	}

	public void remove(String eventId)
	{
		try
		{
			Files.deleteIfExists(dir.resolve(eventId + ".json"));
			Files.deleteIfExists(dir.resolve(eventId + ".jpg"));
		}
		catch (IOException e)
		{
			log.warn("Could not remove screenshot {}", eventId, e);
		}
	}
}
