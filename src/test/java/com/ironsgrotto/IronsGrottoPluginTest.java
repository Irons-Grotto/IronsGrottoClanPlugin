package com.ironsgrotto;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches RuneLite with the plugin loaded, for manual testing. */
public class IronsGrottoPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(IronsGrottoPlugin.class);
		RuneLite.main(args);
	}
}
