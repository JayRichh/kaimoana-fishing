package com.kaimoana.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CatchDetectorTest
{
	private final CatchDetector.ItemNameResolver res = new CatchDetector.ItemNameResolver()
	{
		final Map<String, Integer> ids = Map.of(
			"raw trout", 335, "raw shark", 383, "raw karambwan", 3142,
			"leaping trout", 11328, "minnow", 21356, "raw harpoonfish", 25564, "raw shrimps", 317);

		public int idFor(String n)
		{
			return ids.getOrDefault(n.toLowerCase(), -1);
		}

		public String nameFor(int id)
		{
			return ids.entrySet().stream().filter(e -> e.getValue() == id).map(Map.Entry::getKey).findFirst().orElse(null);
		}

		public boolean isFish(String n)
		{
			return ids.containsKey(n.toLowerCase());
		}
	};
	private final CatchDetector d = new CatchDetector(res);

	@Test
	public void parsesStandardCatch()
	{
		List<CatchEvent> ev = d.onChat("You catch a trout.", 10, null);
		assertEquals(1, ev.size());
		assertEquals("Raw trout", ev.get(0).getItemName());
		assertEquals(335, ev.get(0).getItemId());
	}

	@Test
	public void parsesSomeAndPlural()
	{
		assertEquals("Raw shark", d.onChat("You catch a shark!", 1, null).get(0).getItemName());
		assertEquals("Raw karambwan", d.onChat("You catch a karambwan.", 1, null).get(0).getItemName());
		assertEquals("Minnow", d.onChat("You catch some minnows.", 1, null).get(0).getItemName());
		assertEquals("Raw shrimps", d.onChat("You catch some shrimps.", 1, null).get(0).getItemName());
		assertEquals("Leaping trout", d.onChat("You catch a leaping trout.", 1, null).get(0).getItemName());
	}

	@Test
	public void parsesCountVariant()
	{
		assertEquals(3, d.onChat("You catch 3 harpoonfish.", 1, null).size());
	}

	@Test
	public void ignoresNoise()
	{
		assertTrue(d.onChat("You fail to catch anything.", 1, null).isEmpty());
		assertTrue(d.onChat("You catch a cold.", 1, null).isEmpty());
	}

	@Test
	public void inventoryDeltaWhileFishing()
	{
		List<CatchEvent> ev = d.onInventoryDelta(Map.of(383, 1), 5, null, true);
		assertEquals(1, ev.size());
		assertEquals(CatchEvent.Source.INVENTORY, ev.get(0).getSource());
		assertTrue(d.onInventoryDelta(Map.of(383, 1), 5, null, false).isEmpty());
	}

	@Test
	public void dedupsInventoryAfterChat()
	{
		d.onChat("You catch a shark.", 20, null);
		assertTrue(d.onInventoryDelta(Map.of(383, 1), 20, null, true).isEmpty());
		assertTrue(d.onInventoryDelta(Map.of(383, 1), 21, null, true).isEmpty());
		assertEquals(1, d.onInventoryDelta(Map.of(383, 1), 23, null, true).size());
	}
}
