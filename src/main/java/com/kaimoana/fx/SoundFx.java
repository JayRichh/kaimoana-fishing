package com.kaimoana.fx;

import com.kaimoana.KaimoanaConfig;
import com.kaimoana.registry.CatchResult;
import javax.inject.Inject;
import net.runelite.api.Client;

public class SoundFx
{
	private final Client client;
	private final KaimoanaConfig config;

	@Inject
	public SoundFx(Client client, KaimoanaConfig config)
	{
		this.client = client;
		this.config = config;
	}

	public void play(CatchResult r)
	{
		if (!config.sound() || config.soundVolume() == 0)
		{
			return;
		}
		int id = r.isTrophyPlus() || r.isShiny() ? config.trophySoundId() : config.soundId();
		int volume = (int) Math.round(config.soundVolume() / 100.0 * 127);
		client.playSoundEffect(id, volume);
	}
}
