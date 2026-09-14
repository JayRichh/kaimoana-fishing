package com.kaimoana.fx;

import com.kaimoana.KaimoanaConfig;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;

/**
 * Two jobs: play the catch pose for the hold window (re-applying it whenever the server resets
 * the animation), and optionally swap the fishing animation while fishing.
 */
public class AnimationOverride
{
	private static final int CYCLES_PER_TICK = 30;

	private final Client client;
	private final KaimoanaConfig config;
	private int catchUntilCycle = -1;

	@Inject
	public AnimationOverride(Client client, KaimoanaConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/** Start the catch pose, kept alive for the given number of ticks. Client thread. */
	public void beginCatch(int ticks)
	{
		if (!config.fxEnabled() || !config.catchAnim())
		{
			return;
		}
		catchUntilCycle = client.getGameCycle() + ticks * CYCLES_PER_TICK;
		apply(config.catchAnimId());
	}

	/** Called every client tick. Re-asserts the catch pose if the game overwrote it. */
	public void onClientTick()
	{
		if (catchUntilCycle < 0)
		{
			return;
		}
		if (client.getGameCycle() > catchUntilCycle)
		{
			catchUntilCycle = -1;
			return;
		}
		Player p = client.getLocalPlayer();
		if (p != null && p.getAnimation() != config.catchAnimId())
		{
			apply(config.catchAnimId());
		}
	}

	public void onAnimationChanged(AnimationChanged e, boolean fishing)
	{
		Player p = client.getLocalPlayer();
		if (p == null || e.getActor() != p || !config.fxEnabled())
		{
			return;
		}
		if (catchUntilCycle >= 0 && client.getGameCycle() <= catchUntilCycle)
		{
			if (p.getAnimation() != config.catchAnimId())
			{
				apply(config.catchAnimId());
			}
			return;
		}
		if (!config.animOverride() || !fishing)
		{
			return;
		}
		int current = p.getAnimation();
		if (current == -1 || current == config.animOverrideId())
		{
			return;
		}
		apply(config.animOverrideId());
	}

	public void cancel()
	{
		catchUntilCycle = -1;
	}

	private void apply(int animId)
	{
		Player p = client.getLocalPlayer();
		if (p != null)
		{
			p.setAnimation(animId);
			p.setAnimationFrame(0);
		}
	}
}
