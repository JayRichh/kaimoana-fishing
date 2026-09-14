package com.kaimoana.log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kaimoana.KaimoanaConfig;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class TideLogStore
{
	static final String KEY = "tidelog";

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	public TideLogStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public TideLog load()
	{
		return fromJson(gson, configManager.getRSProfileConfiguration(KaimoanaConfig.GROUP, KEY));
	}

	public void save(TideLog log)
	{
		configManager.setRSProfileConfiguration(KaimoanaConfig.GROUP, KEY, gson.toJson(log));
	}

	static TideLog fromJson(Gson gson, String json)
	{
		if (json == null || json.isEmpty())
		{
			return new TideLog();
		}
		try
		{
			TideLog t = gson.fromJson(json, TideLog.class);
			return t == null ? new TideLog() : t;
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Tide Log data was corrupt, starting fresh", e);
			return new TideLog();
		}
	}
}
