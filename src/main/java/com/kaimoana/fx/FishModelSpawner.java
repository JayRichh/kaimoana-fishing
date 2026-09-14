package com.kaimoana.fx;

import com.kaimoana.KaimoanaConfig;
import com.kaimoana.registry.CatchResult;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.ItemManager;

/**
 * Spawns the caught fish's item model at the fishing spot, arcs it to the player, holds it, then
 * despawns. One active object at a time. All methods must run on the client thread.
 */
@Slf4j
public class FishModelSpawner
{
	private static final int HAND_HEIGHT = 110;
	private static final int PEAK_HEIGHT = 220;
	private static final int HAND_FORWARD = 36;
	private static final int MAX_DRIFT = 128 * 2;
	/** Gold in Jagex HSL: hue 8, saturation 6, luminance 60. */
	private static final short GOLD = (short) ((8 << 10) | (6 << 7) | 60);

	private final Client client;
	private final ItemManager items;
	private final KaimoanaConfig config;

	private RuneLiteObject obj;
	private LocalPoint from;
	private int tick;
	private int arc;
	private int hold;
	private boolean pop;
	private Model model;
	private Model popModel;

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
			short[] cols = fish.getFaceColors();
			for (short c : cols.clone())
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
		model = fish.light();
		popModel = fish.cloneVertices().scale(40, 40, 40).light();

		LocalPoint to = p.getLocalLocation();
		from = spot != null ? spot : to;
		arc = Math.max(1, arcTicks);
		hold = Math.max(1, holdTicks);
		tick = 0;
		pop = config.splashPop();

		obj = client.createRuneLiteObject();
		obj.setModel(pop ? popModel : model);
		obj.setLocation(from, client.getPlane());
		obj.setZ(0);
		obj.setOrientation(p.getOrientation());
		obj.setDrawFrontTilesFirst(true);
		obj.setActive(true);
	}

	/** Called every game tick; advances arc, then hold, then despawns. */
	public void onTick()
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
		tick++;
		LocalPoint to = p.getLocalLocation();
		if (from.distanceTo(to) > MAX_DRIFT * 4)
		{
			clear();
			return;
		}
		if (tick <= arc)
		{
			double t = tick / (double) arc;
			int x = (int) (from.getX() + (to.getX() - from.getX()) * t);
			int y = (int) (from.getY() + (to.getY() - from.getY()) * t);
			int h = (int) (HAND_HEIGHT * t + PEAK_HEIGHT * 4 * t * (1 - t));
			obj.setModel(model);
			obj.setLocation(new LocalPoint(x, y), client.getPlane());
			obj.setZ(-h);
			obj.setOrientation(p.getOrientation());
		}
		else if (tick <= arc + hold)
		{
			obj.setModel(model);
			obj.setLocation(forward(to, p.getOrientation()), client.getPlane());
			obj.setZ(-HAND_HEIGHT);
			obj.setOrientation(p.getOrientation());
		}
		else
		{
			clear();
		}
	}

	private static LocalPoint forward(LocalPoint at, int orientation)
	{
		double rad = orientation * Math.PI / 1024.0;
		return new LocalPoint(at.getX() + (int) (Math.sin(rad) * HAND_FORWARD), at.getY() + (int) (Math.cos(rad) * HAND_FORWARD));
	}

	public void clear()
	{
		if (obj != null)
		{
			obj.setActive(false);
			obj = null;
		}
		model = null;
		popModel = null;
	}
}
