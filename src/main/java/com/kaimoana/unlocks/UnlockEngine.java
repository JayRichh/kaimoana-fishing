package com.kaimoana.unlocks;

import com.kaimoana.log.TideLog;
import com.kaimoana.registry.CatchResult;
import com.kaimoana.registry.Grade;
import java.util.ArrayList;
import java.util.List;

public class UnlockEngine
{
	public static boolean isMilestone(long n)
	{
		return n == 100 || n == 1000 || n == 10000;
	}

	public static boolean hatUnlocked(TideLog log, String fish)
	{
		return log.unlocks.contains("HAT_" + fish);
	}

	public static boolean goldHatUnlocked(TideLog log, String fish)
	{
		return log.unlocks.contains("GOLD_HAT_" + fish);
	}

	public static boolean isTitle(String key)
	{
		return !key.startsWith("HAT_") && !key.startsWith("GOLD_HAT_");
	}

	public static String label(String key)
	{
		switch (key)
		{
			case "FIRST_TROPHY":
				return "First Trophy";
			case "FIRST_TANIWHA":
				return "First Taniwha";
			case "SHINY_HUNTER":
				return "Shiny Hunter";
			case "SPECIES_50":
				return "50 Species";
			case "ALL_SPECIES":
				return "All Species";
			case "CATCHES_10K":
				return "10,000 Catches";
			case "LOCAL_LEGEND":
				return "Local Legend";
			default:
				if (key.startsWith("GOLD_HAT_"))
				{
					return key.substring(9) + " golden hat";
				}
				if (key.startsWith("HAT_"))
				{
					return key.substring(4) + " hat";
				}
				return key;
		}
	}

	/** Records the catch into the log and returns labels of newly earned unlocks. */
	public List<String> apply(TideLog log, CatchResult r, String location, int registrySize)
	{
		String fish = r.getEvent().getItemName();
		long now = System.currentTimeMillis();
		TideLog.Species s = log.getOrCreate(fish);
		if (s.count == 0)
		{
			s.firstAt = now;
		}
		s.count++;
		log.totalCatches++;
		if (r.getWeightKg() > s.heaviest)
		{
			s.heaviest = r.getWeightKg();
			s.heaviestAt = now;
		}
		if (r.isShiny())
		{
			s.shinies++;
			log.totalShinies++;
		}
		s.grades.merge(r.getGrade().name(), 1L, Long::sum);
		log.recordLocationBest(location, fish, r.getWeightKg());

		List<String> earned = new ArrayList<>();
		if (r.getGrade().atLeast(Grade.TROPHY))
		{
			grant(log, "FIRST_TROPHY", earned);
		}
		if (r.getGrade() == Grade.TANIWHA)
		{
			grant(log, "FIRST_TANIWHA", earned);
		}
		if (r.isShiny())
		{
			grant(log, "SHINY_HUNTER", earned);
			grant(log, "GOLD_HAT_" + fish, earned);
		}
		if (s.count >= 50)
		{
			grant(log, "HAT_" + fish, earned);
		}
		if (log.species.size() >= 50)
		{
			grant(log, "SPECIES_50", earned);
		}
		if (registrySize > 0 && log.species.size() >= registrySize)
		{
			grant(log, "ALL_SPECIES", earned);
		}
		if (log.totalCatches >= 10000)
		{
			grant(log, "CATCHES_10K", earned);
		}
		if (log.locationBests.size() >= 10)
		{
			grant(log, "LOCAL_LEGEND", earned);
		}
		if (isMilestone(s.count))
		{
			log.milestones.add(fish + ":" + s.count);
		}
		return earned;
	}

	private static void grant(TideLog log, String key, List<String> earned)
	{
		if (log.unlocks.add(key))
		{
			earned.add(label(key));
		}
	}
}
