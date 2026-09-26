package com.ironsgrotto;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The plugin's own background thread, for everything that talks to the
 * server.
 *
 * ⚠️ Not RuneLite's injected {@code ScheduledExecutorService}: that is one
 * thread shared by every plugin, and this plugin makes blocking HTTP calls
 * that can take seconds when the server is slow or unreachable. On the shared
 * thread that would stall everyone else's scheduled work. Nothing here ever
 * runs on the client thread either.
 */
@Slf4j
@Singleton
public class SyncExecutor
{
	private ScheduledExecutorService executor;

	public synchronized void start()
	{
		if (executor == null || executor.isShutdown())
		{
			executor = Executors.newSingleThreadScheduledExecutor(runnable ->
			{
				Thread thread = new Thread(runnable, "irons-grotto-sync");
				thread.setDaemon(true);
				return thread;
			});
		}
	}

	public synchronized void stop()
	{
		if (executor != null)
		{
			executor.shutdown();
			executor = null;
		}
	}

	public synchronized void execute(Runnable task)
	{
		submit(task);
	}

	/** @return the task's future, or a completed one if the plugin has stopped */
	public synchronized Future<?> submit(Runnable task)
	{
		if (executor == null)
		{
			return java.util.concurrent.CompletableFuture.completedFuture(null);
		}
		try
		{
			return executor.submit(logged(task));
		}
		catch (RejectedExecutionException e)
		{
			return java.util.concurrent.CompletableFuture.completedFuture(null);
		}
	}

	public synchronized ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit)
	{
		start();
		return executor.scheduleWithFixedDelay(logged(task), initialDelay, delay, unit);
	}

	/** A task that throws would otherwise silently cancel a repeating schedule. */
	private static Runnable logged(Runnable task)
	{
		return () ->
		{
			try
			{
				task.run();
			}
			catch (RuntimeException e)
			{
				log.warn("Irons Grotto background task failed", e);
			}
		};
	}
}
