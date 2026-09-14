package com.kaimoana.fx;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class CadenceGovernorTest
{
	@Test
	public void fullHoldWhenSlow()
	{
		CadenceGovernor g = new CadenceGovernor();
		g.recordCatch(0);
		g.recordCatch(12);
		g.recordCatch(25);
		assertEquals(4, g.effectiveHold(4));
		assertEquals(4, g.effectiveArc(4));
	}

	@Test
	public void clampsWhenFast()
	{
		CadenceGovernor g = new CadenceGovernor();
		for (int t = 0; t < 30; t += 3)
		{
			g.recordCatch(t);
		}
		// gap 3 -> budget 2 -> arc 1, hold 1
		assertEquals(1, g.effectiveArc(4));
		assertEquals(1, g.effectiveHold(4));
	}

	@Test
	public void mediumCadenceSplitsBudget()
	{
		CadenceGovernor g = new CadenceGovernor();
		for (int t = 0; t < 60; t += 6)
		{
			g.recordCatch(t);
		}
		// gap 6 -> budget 5 -> arc 3, hold 2
		assertEquals(3, g.effectiveArc(4));
		assertEquals(2, g.effectiveHold(4));
	}

	@Test
	public void neverBelowOne()
	{
		CadenceGovernor g = new CadenceGovernor();
		for (int t = 0; t < 10; t++)
		{
			g.recordCatch(t);
		}
		assertEquals(1, g.effectiveHold(20));
		assertEquals(1, g.effectiveArc(12));
	}
}
