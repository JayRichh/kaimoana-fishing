package com.kaimoana.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import org.junit.Test;

public class TideLogStoreTest
{
	@Test
	public void roundTrips()
	{
		TideLog log = new TideLog();
		TideLog.Species s = log.getOrCreate("Raw trout");
		s.count = 3;
		s.heaviest = 2.5;
		s.grades.put("FINE", 2L);
		log.unlocks.add("FIRST_TROPHY");
		log.recordLocationBest("Barbarian Village", "Raw trout", 2.5);

		String json = new Gson().toJson(log);
		TideLog back = TideLogStore.fromJson(new Gson(), json);

		assertEquals(3, back.species.get("Raw trout").count);
		assertEquals(2.5, back.locationBests.get("Barbarian Village").get("Raw trout"), 0.001);
		assertTrue(back.unlocks.contains("FIRST_TROPHY"));
	}

	@Test
	public void emptyOrGarbageGivesFreshLog()
	{
		assertNotNull(TideLogStore.fromJson(new Gson(), null).species);
		assertNotNull(TideLogStore.fromJson(new Gson(), "{not json").species);
	}

	@Test
	public void regionFallback()
	{
		assertEquals("Barbarian Village", LocationResolver.nameForRegion(12341));
		assertTrue(LocationResolver.nameForRegion(1).startsWith("Region "));
	}
}
