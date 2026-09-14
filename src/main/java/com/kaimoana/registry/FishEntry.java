package com.kaimoana.registry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FishEntry
{
	private String name;
	private double weightMin;
	private double weightMax;
	/** 1 common .. 5 very rare; drives the Tide Log "Rarest" sort. */
	private int rarity;
	/** Item name of the hat to merge onto the fish model, or null. */
	private String hat;
	private int hatOffsetY;
	private String flavour;

	/** Default entry for a fish not in the registry, scaled by High Alch value. */
	public static FishEntry fallback(String name, int haPrice)
	{
		double max;
		if (haPrice < 20)
		{
			max = 1.5;
		}
		else if (haPrice < 100)
		{
			max = 6;
		}
		else if (haPrice < 400)
		{
			max = 25;
		}
		else if (haPrice < 1500)
		{
			max = 120;
		}
		else
		{
			max = 400;
		}
		return FishEntry.builder()
			.name(name)
			.weightMin(max / 30)
			.weightMax(max)
			.rarity(1)
			.flavour("A fine addition to the haul.")
			.build();
	}
}
