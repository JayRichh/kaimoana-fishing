package com.kaimoana;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Kaimoana Fishing",
	description = "Weights, grades, a fish in your hand and a Tide Log for every catch",
	tags = {"fishing", "catch", "weight", "collection", "cosmetic"}
)
public class KaimoanaPlugin extends Plugin
{
	@Inject
	private KaimoanaConfig config;

	@Provides
	KaimoanaConfig provideConfig(ConfigManager cm)
	{
		return cm.getConfig(KaimoanaConfig.class);
	}

	@Override
	protected void startUp()
	{
		log.info("Kaimoana Fishing started");
	}

	@Override
	protected void shutDown()
	{
		log.info("Kaimoana Fishing stopped");
	}
}
