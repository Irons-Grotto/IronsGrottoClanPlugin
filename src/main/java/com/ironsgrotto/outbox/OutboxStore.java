package com.ironsgrotto.outbox;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

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

	private final Path file;
	private final Gson gson;

	public OutboxStore(Path file, Gson gson)
	{
		this.file = file;
		this.gson = gson;
	}

	public List<OutboxEntry> load()
	{
		if (!Files.exists(file))
		{
			return new ArrayList<>();
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
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
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8))
			{
				gson.toJson(entries, ENTRY_LIST, writer);
			}

			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			log.warn("Could not save the Irons Grotto outbox", e);
		}
	}
}
