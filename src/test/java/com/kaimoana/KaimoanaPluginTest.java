package com.kaimoana;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class KaimoanaPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(KaimoanaPlugin.class);
		RuneLite.main(args);
	}
}
