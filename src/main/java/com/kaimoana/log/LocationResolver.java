package com.kaimoana.log;

import java.util.Map;
import net.runelite.api.coords.WorldPoint;

public final class LocationResolver
{
	private static final Map<Integer, String> REGIONS = Map.ofEntries(
		Map.entry(12341, "Barbarian Village"),
		Map.entry(12342, "Barbarian Village"),
		Map.entry(12850, "Lumbridge Swamp"),
		Map.entry(12593, "Draynor Village"),
		Map.entry(11058, "Karamja"),
		Map.entry(11057, "Musa Point"),
		Map.entry(10804, "Catherby"),
		Map.entry(11316, "Shilo Village"),
		Map.entry(9276, "Piscatoris"),
		Map.entry(9275, "Piscatoris"),
		Map.entry(11310, "Fishing Guild"),
		Map.entry(10037, "Otto's Grotto"),
		Map.entry(12591, "Al Kharid"),
		Map.entry(12079, "Zul-Andra"),
		Map.entry(8261, "Tempoross Cove"),
		Map.entry(12106, "Lake Molch"),
		Map.entry(11875, "Mor Ul Rek"),
		Map.entry(12070, "Zeah Deep Sea"),
		Map.entry(6710, "Land's End"),
		Map.entry(11566, "Camdozaal"),
		Map.entry(14638, "Prifddinas"),
		Map.entry(13110, "Rellekka"),
		Map.entry(12858, "Tai Bwo Wannai"),
		Map.entry(9782, "Kingdom of Miscellania"),
		Map.entry(14906, "Aldarin"),
		Map.entry(6715, "Farming Guild"),
		Map.entry(13621, "Port Piscarilius"),
		Map.entry(7226, "Isle of Souls"));

	private LocationResolver()
	{
	}

	public static String nameFor(WorldPoint p)
	{
		return p == null ? "Unknown" : nameForRegion(p.getRegionID());
	}

	public static String nameForRegion(int region)
	{
		return REGIONS.getOrDefault(region, "Region " + region);
	}
}
