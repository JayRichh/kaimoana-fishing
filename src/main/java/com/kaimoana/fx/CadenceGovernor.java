package com.kaimoana.fx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shrinks jump/hold durations so effects never overlap when catches come fast.
 */
public class CadenceGovernor
{
	private final Deque<Integer> gaps = new ArrayDeque<>();
	private int lastTick = Integer.MIN_VALUE;

	public void recordCatch(int tick)
	{
		if (lastTick != Integer.MIN_VALUE)
		{
			gaps.addLast(Math.max(1, tick - lastTick));
			if (gaps.size() > 8)
			{
				gaps.removeFirst();
			}
		}
		lastTick = tick;
	}

	/** Median inter-catch gap in ticks, or Integer.MAX_VALUE when unknown. */
	int medianGap()
	{
		if (gaps.size() < 2)
		{
			return Integer.MAX_VALUE;
		}
		List<Integer> s = gaps.stream().sorted().collect(Collectors.toList());
		return s.get(s.size() / 2);
	}

	private int budget()
	{
		return Math.max(2, medianGap() - 1);
	}

	/** Arc gets roughly two thirds of the budget, at least 1. */
	public int effectiveArc(int configuredArc)
	{
		if (medianGap() == Integer.MAX_VALUE)
		{
			return configuredArc;
		}
		return Math.max(1, Math.min(configuredArc, (budget() * 2) / 3));
	}

	public int effectiveHold(int configuredHold)
	{
		if (medianGap() == Integer.MAX_VALUE)
		{
			return configuredHold;
		}
		return Math.max(1, Math.min(configuredHold, budget() - effectiveArc(Integer.MAX_VALUE)));
	}
}
