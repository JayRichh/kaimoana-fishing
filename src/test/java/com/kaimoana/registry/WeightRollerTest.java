package com.kaimoana.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Random;
import org.junit.Test;

public class WeightRollerTest
{
	private final FishEntry trout = FishEntry.builder().name("Raw trout").weightMin(0.3).weightMax(3.5).rarity(1).build();

	@Test
	public void rollsWithinBounds()
	{
		WeightRoller r = new WeightRoller(new Random(1));
		for (int i = 0; i < 10000; i++)
		{
			double w = r.roll(trout);
			assertTrue(w >= 0.3 && w <= 3.5);
		}
	}

	@Test
	public void distributionIsSkewedLow()
	{
		WeightRoller r = new WeightRoller(new Random(2));
		int low = 0;
		for (int i = 0; i < 10000; i++)
		{
			if (WeightRoller.percentile(trout, r.roll(trout)) < 0.5)
			{
				low++;
			}
		}
		assertTrue("expected majority below midpoint, got " + low, low > 6500);
	}

	@Test
	public void gradesByPercentile()
	{
		WeightRoller r = new WeightRoller(new Random(3));
		assertEquals(Grade.COMMON, r.grade(trout, 0.3, false));
		assertEquals(Grade.FINE, r.grade(trout, 0.3 + 3.2 * 0.70, false));
		assertEquals(Grade.PRIME, r.grade(trout, 0.3 + 3.2 * 0.90, false));
		assertEquals(Grade.TROPHY, r.grade(trout, 0.3 + 3.2 * 0.97, false));
		assertEquals(Grade.BIG_ONE, r.grade(trout, 0.3 + 3.2 * 0.995, false));
		assertEquals(Grade.TANIWHA, r.grade(trout, 3.5, false));
		assertEquals(Grade.TANIWHA, r.grade(trout, 0.3, true));
	}

	@Test
	public void shinyRateRoughlyOneIn512()
	{
		WeightRoller r = new WeightRoller(new Random(4));
		int n = 0;
		for (int i = 0; i < 512_000; i++)
		{
			if (r.rollShiny())
			{
				n++;
			}
		}
		assertTrue("got " + n, n > 800 && n < 1200);
	}
}
