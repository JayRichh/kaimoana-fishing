package com.kaimoana.detect;

import java.util.Locale;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * Heuristic: the local player is fishing if they are interacting with an NPC whose name contains
 * "fishing spot", or did so within the last few ticks. Name-based so new spots work automatically.
 */
public class FishingStateTracker
{
	private static final int GRACE_TICKS = 5;

	private int lastFishingTick = -100;
	private NPC spot;
	private WorldPoint spotLoc;

	public void onTick(Client client)
	{
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return;
		}
		Actor target = p.getInteracting();
		if (target instanceof NPC && target.getName() != null
			&& target.getName().toLowerCase(Locale.ROOT).contains("fishing spot"))
		{
			spot = (NPC) target;
			spotLoc = spot.getWorldLocation();
			lastFishingTick = client.getTickCount();
		}
	}

	public boolean isFishing(Client client)
	{
		return client.getTickCount() - lastFishingTick <= GRACE_TICKS;
	}

	public NPC currentSpot()
	{
		return spot;
	}

	public WorldPoint lastSpotLocation()
	{
		return spotLoc;
	}
}
