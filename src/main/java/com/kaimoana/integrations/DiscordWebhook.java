package com.kaimoana.integrations;

import com.google.gson.Gson;
import com.kaimoana.KaimoanaConfig;
import com.kaimoana.registry.CatchResult;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class DiscordWebhook
{
	private final OkHttpClient http;
	private final Gson gson;
	private final KaimoanaConfig config;

	@Inject
	public DiscordWebhook(OkHttpClient http, Gson gson, KaimoanaConfig config)
	{
		this.http = http;
		this.gson = gson;
		this.config = config;
	}

	public void maybePost(CatchResult r, String player)
	{
		String url = config.discordUrl();
		if (url == null || url.trim().isEmpty() || !(r.isTrophyPlus() || r.isShiny()))
		{
			return;
		}
		String content = String.format(Locale.ROOT, "**%s** caught %s%s: %.2f kg (%s)",
			player, r.isShiny() ? "a shiny " : "", r.getEvent().getItemName(), r.getWeightKg(), r.getGrade().label());
		Request req = new Request.Builder()
			.url(url.trim())
			.post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(Map.of("content", content))))
			.build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Discord webhook failed", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
			}
		});
	}
}
