package com.kaimoana.registry;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class FishRegistry
{
	private final Map<String, FishEntry> byName = new LinkedHashMap<>();

	public static FishRegistry load()
	{
		try (InputStream in = FishRegistry.class.getResourceAsStream("/com/kaimoana/fish.json"))
		{
			List<FishEntry> list = new Gson().fromJson(
				new InputStreamReader(Objects.requireNonNull(in, "fish.json missing"), StandardCharsets.UTF_8),
				new TypeToken<List<FishEntry>>()
				{
				}.getType());
			FishRegistry r = new FishRegistry();
			for (FishEntry e : list)
			{
				r.byName.put(e.getName().toLowerCase(Locale.ROOT), e);
			}
			return r;
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	public Optional<FishEntry> find(String name)
	{
		if (name == null)
		{
			return Optional.empty();
		}
		return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT).trim()));
	}

	public FishEntry resolve(String name, int haPrice)
	{
		return find(name).orElseGet(() -> FishEntry.fallback(name, haPrice));
	}

	public List<FishEntry> all()
	{
		return new ArrayList<>(byName.values());
	}

	public int size()
	{
		return byName.size();
	}
}
