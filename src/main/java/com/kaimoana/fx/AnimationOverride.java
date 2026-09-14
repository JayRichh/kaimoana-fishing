package com.kaimoana.fx;

import com.kaimoana.KaimoanaConfig;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;

/** Optionally swaps the local player's fishing animation for a configured one. */
public class AnimationOverride
{
	private final Client client;
	private final KaimoanaConfig config;

	@Inject
	public AnimationOverride(Client client, KaimoanaConfig config)
	{
		this.client = client;
		this.config = config;
	}

	public void onAnimationChanged(AnimationChanged e, boolean fishing)
	{
		if (!config.fxEnabled() || !config.animOverride() || !fishing)
		{
			return;
		}
		Player p = client.getLocalPlayer();
		if (p == null || e.getActor() != p)
		{
			return;
		}
		int current = p.getAnimation();
		if (current == -1 || current == config.animOverrideId())
		{
			return;
		}
		p.setAnimation(config.animOverrideId());
		p.setAnimationFrame(0);
	}
}
