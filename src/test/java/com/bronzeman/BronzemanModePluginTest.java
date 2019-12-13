package com.bronzeman;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BronzemanModePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BronzemanModePlugin.class);
		RuneLite.main(args);
	}
}