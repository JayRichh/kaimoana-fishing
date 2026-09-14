package com.kaimoana.unlocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.kaimoana.detect.CatchEvent;
import com.kaimoana.log.TideLog;
import com.kaimoana.registry.CatchResult;
import com.kaimoana.registry.FishEntry;
import com.kaimoana.registry.Grade;
import java.util.List;
import org.junit.Test;

public class UnlockEngineTest
{
	private CatchResult res(String fish, Grade g, boolean shiny, double kg)
	{
		return CatchResult.builder()
			.event(CatchEvent.builder().itemName(fish).itemId(1).tick(1).source(CatchEvent.Source.CHAT).build())
			.entry(FishEntry.builder().name(fish).weightMin(0).weightMax(10).rarity(1).build())
			.weightKg(kg).grade(g).shiny(shiny).build();
	}

	@Test
	public void recordsStatsAndFirstTrophy()
	{
		TideLog log = new TideLog();
		UnlockEngine u = new UnlockEngine();
		List<String> got = u.apply(log, res("Raw trout", Grade.TROPHY, false, 3.0), "Barbarian Village", 100);
		assertTrue(got.contains("First Trophy"));
		assertEquals(1, log.species.get("Raw trout").count);
		assertEquals(3.0, log.species.get("Raw trout").heaviest, 0.001);
		assertEquals(1, log.totalCatches);
		assertTrue(u.apply(log, res("Raw trout", Grade.TROPHY, false, 1.0), "Barbarian Village", 100).isEmpty());
	}

	@Test
	public void hatAt50AndGoldOnShiny()
	{
		TideLog log = new TideLog();
		UnlockEngine u = new UnlockEngine();
		for (int i = 0; i < 49; i++)
		{
			u.apply(log, res("Raw shark", Grade.COMMON, false, 1), "Catherby", 100);
		}
		assertFalse(UnlockEngine.hatUnlocked(log, "Raw shark"));
		assertTrue(u.apply(log, res("Raw shark", Grade.COMMON, false, 1), "Catherby", 100).contains("Raw shark hat"));
		assertTrue(UnlockEngine.hatUnlocked(log, "Raw shark"));
		assertTrue(u.apply(log, res("Raw shark", Grade.COMMON, true, 1), "Catherby", 100).contains("Shiny Hunter"));
		assertTrue(UnlockEngine.goldHatUnlocked(log, "Raw shark"));
	}

	@Test
	public void milestones()
	{
		assertTrue(UnlockEngine.isMilestone(100));
		assertTrue(UnlockEngine.isMilestone(10000));
		assertFalse(UnlockEngine.isMilestone(101));
	}
}
