package com.kaimoana.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FishRegistryTest
{
	@Test
	public void loadsBundledAndFindsCaseInsensitive()
	{
		FishRegistry r = FishRegistry.load();
		assertTrue(r.size() > 40);
		FishEntry trout = r.find("Raw Trout").orElseThrow(AssertionError::new);
		assertEquals("Raw trout", trout.getName());
		assertTrue(trout.getWeightMin() < trout.getWeightMax());
	}

	@Test
	public void unknownFallsBackScaledByPrice()
	{
		FishRegistry r = FishRegistry.load();
		FishEntry cheap = r.resolve("Raw mystery minnow", 5);
		FishEntry dear = r.resolve("Raw mystery leviathan", 5000);
		assertEquals("Raw mystery minnow", cheap.getName());
		assertTrue(dear.getWeightMax() > cheap.getWeightMax());
		assertEquals(1, cheap.getRarity());
	}

	@Test
	public void gradeOrder()
	{
		assertTrue(Grade.TANIWHA.atLeast(Grade.TROPHY));
		assertFalse(Grade.FINE.atLeast(Grade.PRIME));
		assertEquals("Big One", Grade.BIG_ONE.label());
	}
}
