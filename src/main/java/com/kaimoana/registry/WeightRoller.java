package com.kaimoana.registry;

import java.util.Random;

public class WeightRoller
{
	private final Random rng;

	public WeightRoller(Random rng)
	{
		this.rng = rng;
	}

	/** Skewed-low roll clamped to the species range, rounded to 2dp. */
	public double roll(FishEntry e)
	{
		double u = Math.abs(rng.nextGaussian()) / 3.0;
		double frac = Math.min(1.0, u * u);
		double w = e.getWeightMin() + (e.getWeightMax() - e.getWeightMin()) * frac;
		return Math.round(w * 100.0) / 100.0;
	}

	public static double percentile(FishEntry e, double kg)
	{
		double span = e.getWeightMax() - e.getWeightMin();
		if (span <= 0)
		{
			return 1.0;
		}
		return Math.max(0, Math.min(1, (kg - e.getWeightMin()) / span));
	}

	public Grade grade(FishEntry e, double kg, boolean taniwhaRoll)
	{
		if (taniwhaRoll)
		{
			return Grade.TANIWHA;
		}
		double p = percentile(e, kg);
		if (p >= 0.998)
		{
			return Grade.TANIWHA;
		}
		if (p >= 0.99)
		{
			return Grade.BIG_ONE;
		}
		if (p >= 0.95)
		{
			return Grade.TROPHY;
		}
		if (p >= 0.85)
		{
			return Grade.PRIME;
		}
		if (p >= 0.60)
		{
			return Grade.FINE;
		}
		return Grade.COMMON;
	}

	public boolean rollShiny()
	{
		return rng.nextInt(512) == 0;
	}

	public boolean rollTaniwha()
	{
		return rng.nextInt(1000) == 0;
	}
}
