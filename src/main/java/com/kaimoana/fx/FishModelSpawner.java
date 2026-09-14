package com.kaimoana.fx;

import com.kaimoana.KaimoanaConfig;
import com.kaimoana.registry.CatchResult;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.ItemManager;

/**
 * Spawns the caught fish's item model at the fishing spot, arcs it smoothly to the player's hand,
 * holds it there, then despawns. One active object at a time. Client thread only.
 * Timing is in game cycles (20 ms) so movement is smooth rather than stepping once per tick.
 */
@Slf4j
public class FishModelSpawner
{
	private static final int CYCLES_PER_TICK = 30;
	private static final int HAND_HEIGHT = 95;
	private static final int PEAK_EXTRA = 160;
	private static final int HAND_FORWARD = 28;
	private static final int HAND_RIGHT = 26;
	private static final int FISH_SCALE = 60;
	private static final int MAX_DRIFT = 128 * 6;
	/** Gold in Jagex HSL: hue 8, saturation 6, luminance 60. */
	private static final short GOLD = (short) ((8 << 10) | (6 << 7) | 60);

	private final Client client;
	private final ItemManager items;
	private final KaimoanaConfig config;

	private RuneLiteObject obj;
	private LocalPoint from;
	private int startCycle;
	private int arcCycles;
	private int holdCycles;
	private Model model;

	@Inject
	public FishModelSpawner(Client client, ItemManager items, KaimoanaConfig config)
	{
		this.client = client;
		this.items = items;
		this.config = config;
	}

	public void spawn(CatchResult r, LocalPoint spot, int arcTicks, int holdTicks, Integer hatItemId, int hatOffsetY)
	{
		clear();
		Player p = client.getLocalPlayer();
		int itemId = r.getEvent().getItemId();
		if (p == null || itemId < 0)
		{
			return;
		}
		ModelData fish = client.loadModelData(items.getItemComposition(itemId).getInventoryModel());
		if (fish == null)
		{
			return;
		}
		fish = fish.cloneVertices().cloneColors();
		if (r.isShiny())
		{
			for (short c : fish.getFaceColors().clone())
			{
				fish.recolor(c, GOLD);
			}
		}
		if (hatItemId != null)
		{
			ModelData hat = client.loadModelData(items.getItemComposition(hatItemId).getInventoryModel());
			if (hat != null)
			{
				hat = hat.cloneVertices().scale(70, 70, 70).translate(0, hatOffsetY - 40, 0);
				fish = client.mergeModels(fish, hat);
			}
		}
		fish.scale(FISH_SCALE, FISH_SCALE, FISH_SCALE);
		model = fish.light();

		LocalPoint to = p.getLocalLocation();
		from = spot != null ? spot : to;
		arcCycles = Math.max(1, arcTicks) * CYCLES_PER_TICK;
		holdCycles = Math.max(1, holdTicks) * CYCLES_PER_TICK;
		startCycle = client.getGameCycle();

		obj = client.createRuneLiteObject();
		obj.setModel(model);
		obj.setDrawFrontTilesFirst(true);
		place(from, 0, p.getOrientation());
		obj.setActive(true);
	}

	/** Called every client tick (20 ms). */
	public void onClientTick()
	{
		if (obj == null)
		{
			return;
		}
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			clear();
			return;
		}
		LocalPoint hand = handPoint(p);
		if (from.distanceTo(hand) > MAX_DRIFT)
		{
			clear();
			return;
		}
		int elapsed = client.getGameCycle() - startCycle;
		if (elapsed <= arcCycles)
		{
			double t = elapsed / (double) arcCycles;
			int x = (int) (from.getX() + (hand.getX() - from.getX()) * t);
			int y = (int) (from.getY() + (hand.getY() - from.getY()) * t);
			int h = (int) (HAND_HEIGHT * t + PEAK_EXTRA * 4 * t * (1 - t));
			place(new LocalPoint(x, y), h, p.getOrientation() + 512);
		}
		else if (elapsed <= arcCycles + holdCycles)
		{
			place(hand, HAND_HEIGHT, p.getOrientation() + 512);
		}
		else
		{
			clear();
		}
	}

	private void place(LocalPoint lp, int heightAboveGround, int orientation)
	{
		int plane = client.getPlane();
		int ground = Perspective.getTileHeight(client, lp, plane);
		obj.setLocation(lp, plane);
		obj.setZ(ground - heightAboveGround);
		obj.setOrientation(orientation & 2047);
	}

	/** Point just forward and to the right of the player, where the rod hand sits. */
	private static LocalPoint handPoint(Player p)
	{
		LocalPoint at = p.getLocalLocation();
		double a = p.getOrientation() * Math.PI / 1024.0;
		double fx = -Math.sin(a), fy = -Math.cos(a);
		double rx = -Math.cos(a), ry = Math.sin(a);
		int x = at.getX() + (int) Math.round(fx * HAND_FORWARD + rx * HAND_RIGHT);
		int y = at.getY() + (int) Math.round(fy * HAND_FORWARD + ry * HAND_RIGHT);
		return new LocalPoint(x, y);
	}

	public void clear()
	{
		if (obj != null)
		{
			obj.setActive(false);
			obj = null;
		}
		model = null;
	}
}
