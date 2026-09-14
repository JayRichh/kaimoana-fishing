package com.kaimoana.detect;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
@Builder
public class CatchEvent
{
	public enum Source
	{
		CHAT,
		INVENTORY
	}

	String itemName;
	int itemId;
	int tick;
	WorldPoint where;
	Source source;
}
