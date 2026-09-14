package com.kaimoana.detect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;

/**
 * Turns chat messages and inventory deltas into {@link CatchEvent}s. Pure logic, no client access.
 */
public class CatchDetector
{
	public interface ItemNameResolver
	{
		int idFor(String itemName);

		String nameFor(int id);

		boolean isFish(String itemName);
	}

	private static final Pattern CATCH = Pattern.compile(
		"^You (?:catch|manage to catch|get) (?:(\\d+) |an? |some )?([a-z' -]+?)[.!]?$",
		Pattern.CASE_INSENSITIVE);

	private final ItemNameResolver resolver;
	private final Map<Integer, Integer> lastChatTickByItem = new HashMap<>();

	public CatchDetector(ItemNameResolver resolver)
	{
		this.resolver = resolver;
	}

	public List<CatchEvent> onChat(String message, int tick, WorldPoint where)
	{
		Matcher m = CATCH.matcher(stripTags(message).trim());
		if (!m.matches())
		{
			return List.of();
		}
		int count = m.group(1) == null ? 1 : Integer.parseInt(m.group(1));
		String resolved = resolveName(m.group(2));
		if (resolved == null)
		{
			return List.of();
		}
		int id = resolver.idFor(resolved);
		lastChatTickByItem.put(id, tick);
		List<CatchEvent> out = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			out.add(CatchEvent.builder().itemName(resolved).itemId(id).tick(tick).where(where).source(CatchEvent.Source.CHAT).build());
		}
		return out;
	}

	public List<CatchEvent> onInventoryDelta(Map<Integer, Integer> delta, int tick, WorldPoint where, boolean fishing)
	{
		if (!fishing)
		{
			return List.of();
		}
		List<CatchEvent> out = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : delta.entrySet())
		{
			if (e.getValue() <= 0)
			{
				continue;
			}
			Integer chatTick = lastChatTickByItem.get(e.getKey());
			if (chatTick != null && tick - chatTick <= 1)
			{
				continue;
			}
			String name = resolver.nameFor(e.getKey());
			if (name == null || !resolver.isFish(name))
			{
				continue;
			}
			for (int i = 0; i < e.getValue(); i++)
			{
				out.add(CatchEvent.builder().itemName(cap(name)).itemId(e.getKey()).tick(tick).where(where).source(CatchEvent.Source.INVENTORY).build());
			}
		}
		return out;
	}

	/** Tries "raw X", "X", and singularised forms. Returns a capitalised name, or null. */
	private String resolveName(String raw)
	{
		String base = raw.toLowerCase(Locale.ROOT).trim();
		List<String> candidates = new ArrayList<>();
		for (String s : List.of(base, singular(base)))
		{
			candidates.add("raw " + s);
			candidates.add(s);
		}
		for (String c : candidates)
		{
			if (resolver.isFish(c))
			{
				return cap(c);
			}
		}
		return null;
	}

	private static String singular(String s)
	{
		if (s.endsWith("ies"))
		{
			return s.substring(0, s.length() - 3) + "y";
		}
		if (s.endsWith("shes") || s.endsWith("ches") || s.endsWith("xes"))
		{
			return s.substring(0, s.length() - 2);
		}
		if (s.endsWith("s") && !s.endsWith("ss"))
		{
			return s.substring(0, s.length() - 1);
		}
		return s;
	}

	private static String cap(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static String stripTags(String s)
	{
		return s.replaceAll("<[^>]*>", "");
	}
}
