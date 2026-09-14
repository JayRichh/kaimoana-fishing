package com.kaimoana.chat;

import com.kaimoana.KaimoanaConfig;
import com.kaimoana.registry.CatchResult;
import com.kaimoana.registry.Grade;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

/** Writes client-local catch lines. {@link #announce} must run on the client thread. */
public class CatchChatWriter
{
	private static final Color SHINY = new Color(0xFFD700);

	private final Client client;
	private final ChatMessageManager chat;
	private final ItemManager items;
	private final KaimoanaConfig config;
	private final Map<Integer, Integer> iconIndexByItem = new HashMap<>();

	@Inject
	public CatchChatWriter(Client client, ChatMessageManager chat, ItemManager items, KaimoanaConfig config)
	{
		this.client = client;
		this.chat = chat;
		this.items = items;
		this.config = config;
	}

	public void announce(CatchResult r, List<String> newUnlocks)
	{
		String fishName = r.getEvent().getItemName().replaceFirst("(?i)^raw ", "").toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder();
		if (config.chatIcon())
		{
			int idx = iconFor(r.getEvent().getItemId());
			if (idx >= 0)
			{
				sb.append("<img=").append(idx).append("> ");
			}
		}
		if (r.isShiny())
		{
			sb.append(ColorUtil.wrapWithColorTag("Shiny! ", SHINY));
		}
		sb.append("You catch ").append(article(fishName)).append(' ').append(fishName).append('.');
		if (config.chatWeight())
		{
			sb.append(' ').append(String.format(Locale.ROOT, "%.2f kg", r.getWeightKg()));
		}
		if (config.chatGrade() && config.grades())
		{
			sb.append(' ').append(ColorUtil.wrapWithColorTag("(" + r.getGrade().label() + ")", r.getGrade().color()));
		}
		if (r.isMilestone())
		{
			sb.append(ColorUtil.wrapWithColorTag(" Milestone: " + r.getSpeciesCount() + " caught!", Grade.TROPHY.color()));
		}
		send(sb.toString());

		if (config.chatFlavour() && r.isTrophyPlus() && r.getEntry().getFlavour() != null)
		{
			send(ColorUtil.wrapWithColorTag(r.getEntry().getFlavour(), r.getGrade().color()));
		}
		for (String u : newUnlocks)
		{
			send(ColorUtil.wrapWithColorTag("Tide Log unlock: " + u, Grade.TANIWHA.color()));
		}
	}

	public void send(String msg)
	{
		chat.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(msg).build());
	}

	private static String article(String n)
	{
		if (n.endsWith("s") && !n.endsWith("ss"))
		{
			return "some";
		}
		return "aeiou".indexOf(n.charAt(0)) >= 0 ? "an" : "a";
	}

	/** Registers the item sprite as a mod icon once. Returns -1 if the client is not ready. */
	private int iconFor(int itemId)
	{
		if (itemId < 0)
		{
			return -1;
		}
		Integer idx = iconIndexByItem.get(itemId);
		if (idx != null)
		{
			return idx;
		}
		IndexedSprite[] mod = client.getModIcons();
		if (mod == null)
		{
			return -1;
		}
		BufferedImage img = ImageUtil.resizeImage(items.getImage(itemId), 16, 16);
		IndexedSprite sprite = ImageUtil.getImageIndexedSprite(img, client);
		IndexedSprite[] grown = Arrays.copyOf(mod, mod.length + 1);
		grown[mod.length] = sprite;
		client.setModIcons(grown);
		iconIndexByItem.put(itemId, mod.length);
		return mod.length;
	}
}
