package com.kaimoana.integrations;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Fetches the intro paragraph for a fish from the OSRS Wiki, once per species. Opt-in. */
@Slf4j
public class WikiLookup
{
	private final OkHttpClient http;
	private final Map<String, String> cache = new ConcurrentHashMap<>();

	@Inject
	public WikiLookup(OkHttpClient http)
	{
		this.http = http;
	}

	public Optional<String> cached(String fish)
	{
		return Optional.ofNullable(cache.get(fish));
	}

	public void fetch(String fish, Consumer<String> callback)
	{
		String hit = cache.get(fish);
		if (hit != null)
		{
			callback.accept(hit);
			return;
		}
		HttpUrl url = HttpUrl.parse("https://oldschool.runescape.wiki/api.php").newBuilder()
			.addQueryParameter("action", "query")
			.addQueryParameter("prop", "extracts")
			.addQueryParameter("exintro", "1")
			.addQueryParameter("explaintext", "1")
			.addQueryParameter("format", "json")
			.addQueryParameter("titles", fish)
			.build();
		Request req = new Request.Builder().url(url).header("User-Agent", "RuneLite Kaimoana Fishing").build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Wiki lookup failed for {}", fish, e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (body == null)
					{
						return;
					}
					JsonObject root = new JsonParser().parse(body.string()).getAsJsonObject();
					JsonObject pages = root.getAsJsonObject("query").getAsJsonObject("pages");
					for (Map.Entry<String, JsonElement> e : pages.entrySet())
					{
						JsonElement extract = e.getValue().getAsJsonObject().get("extract");
						if (extract == null)
						{
							continue;
						}
						String s = extract.getAsString();
						if (s.length() > 300)
						{
							s = s.substring(0, 297) + "...";
						}
						cache.put(fish, s);
						callback.accept(s);
					}
				}
				catch (RuntimeException e)
				{
					log.debug("Wiki parse failed for {}", fish, e);
				}
			}
		});
	}
}
