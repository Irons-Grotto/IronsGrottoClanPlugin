package com.ironsgrotto.outbox;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Keeps the outbox on disk, so events survive a crash, a closed client or a
 * server outage. Written to a temp file and moved into place, so a crash
 * mid-write leaves the previous copy rather than half a file.
 */
@Slf4j
public class OutboxStore
{
	private static final Type ENTRY_LIST = new TypeToken<List<OutboxEntry>>()
	{
	}.getType();

	private final Filepath file;
	private final Gson gson;

	public OutboxStore(Filepath file, Gson gson)
	{
		this.file = file;
		this.gson = gson;
	}

	public List<OutboxEntry> load()
	{
		if (!file.exists())
		{
			return new ArrayList<>();
		}

		try (Reader reader = file.openBufferedReader())
		{
			List<OutboxEntry> entries = gson.fromJson(reader, ENTRY_LIST);
			return entries != null ? new ArrayList<>(entries) : new ArrayList<>();
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Could not read the Irons Grotto outbox, starting empty", e);
			return new ArrayList<>();
		}
	}

	public void save(List<OutboxEntry> entries)
	{
		try
		{
			file.getParent().createDirectories();
			Filepath temp = file.getParent().join(file.getFileName() + ".tmp");

			try (Writer writer = temp.openBufferedWriter())
			{
				gson.toJson(entries, ENTRY_LIST, writer);
			}

			temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			log.warn("Could not save the Irons Grotto outbox", e);
		}
	}
}
