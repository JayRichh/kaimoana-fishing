package com.kaimoana.registry;

import com.kaimoana.detect.CatchEvent;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CatchResult
{
	CatchEvent event;
	FishEntry entry;
	double weightKg;
	Grade grade;
	boolean shiny;
	int speciesCount;
	boolean milestone;

	public boolean isTrophyPlus()
	{
		return grade.atLeast(Grade.TROPHY);
	}
}
