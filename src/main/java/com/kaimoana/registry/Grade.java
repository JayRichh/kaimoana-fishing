package com.kaimoana.registry;

import java.awt.Color;

public enum Grade
{
	COMMON("Common", new Color(0xC8C8C8)),
	FINE("Fine", new Color(0x7FD37F)),
	PRIME("Prime", new Color(0x5FA8FF)),
	TROPHY("Trophy", new Color(0xE0B000)),
	BIG_ONE("Big One", new Color(0xFF7A1A)),
	TANIWHA("Taniwha", new Color(0xC04CFF));

	private final String label;
	private final Color color;

	Grade(String label, Color color)
	{
		this.label = label;
		this.color = color;
	}

	public String label()
	{
		return label;
	}

	public Color color()
	{
		return color;
	}

	public boolean atLeast(Grade other)
	{
		return ordinal() >= other.ordinal();
	}
}
