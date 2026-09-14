package com.kaimoana.integrations;

import com.kaimoana.KaimoanaConfig;
import com.kaimoana.registry.CatchResult;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageCapture;

public class ScreenshotOnTrophy
{
	private final DrawManager draw;
	private final ImageCapture capture;
	private final KaimoanaConfig config;

	@Inject
	public ScreenshotOnTrophy(DrawManager draw, ImageCapture capture, KaimoanaConfig config)
	{
		this.draw = draw;
		this.capture = capture;
		this.config = config;
	}

	public void maybeCapture(CatchResult r)
	{
		if (!config.screenshot() || !(r.isTrophyPlus() || r.isShiny()))
		{
			return;
		}
		String name = String.format(Locale.ROOT, "%s %s %.2fkg", r.getGrade().label(), r.getEvent().getItemName(), r.getWeightKg())
			.replaceAll("[^A-Za-z0-9 .]", "");
		draw.requestNextFrameListener(img -> capture.saveScreenshot(capture.addClientFrame(img), name, "Kaimoana", false, false));
	}
}
