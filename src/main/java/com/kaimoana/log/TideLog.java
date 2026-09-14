package com.kaimoana.log;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Persisted per RuneScape profile as JSON. Public fields for Gson. */
public class TideLog
{
	public static class Species
	{
		public long count;
		public double heaviest;
		public long heaviestAt;
		public long firstAt;
		public long shinies;
		public Map<String, Long> grades = new HashMap<>();
	}

	public int version = 1;
	public Map<String, Species> species = new TreeMap<>();
	public Map<String, Map<String, Double>> locationBests = new TreeMap<>();
	public Set<String> unlocks = new TreeSet<>();
	public Set<String> milestones = new TreeSet<>();
	public long totalCatches;
	public long totalShinies;

	public Species getOrCreate(String name)
	{
		return species.computeIfAbsent(name, k -> new Species());
	}

	/** @return true if this is a new best at the location */
	public boolean recordLocationBest(String location, String fish, double kg)
	{
		Map<String, Double> m = locationBests.computeIfAbsent(location, k -> new TreeMap<>());
		Double prev = m.get(fish);
		if (prev == null || kg > prev)
		{
			m.put(fish, kg);
			return true;
		}
		return false;
	}
}
