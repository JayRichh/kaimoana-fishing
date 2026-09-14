package com.kaimoana.chat;

import com.kaimoana.KaimoanaConfig;
import com.kaimoana.log.TideLog;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

/** {@code !fish} and {@code !fish <name>}. Output is local only. */
public class FishChatCommand
{
	private static final String CMD = "!fish";

	private final ChatCommandManager commands;
	private final ChatMessageManager chat;
	private final Supplier<TideLog> log;
	private final KaimoanaConfig config;

	public FishChatCommand(ChatCommandManager commands, ChatMessageManager chat, Supplier<TideLog> log, KaimoanaConfig config)
	{
		this.commands = commands;
		this.chat = chat;
		this.log = log;
		this.config = config;
	}

	public void register()
	{
		commands.registerCommand(CMD, this::run);
	}

	public void unregister()
	{
		commands.unregisterCommand(CMD);
	}

	private void run(ChatMessage msg, String text)
	{
		if (!config.chatCommands())
		{
			return;
		}
		TideLog t = log.get();
		String arg = text.substring(CMD.length()).trim();
		String out;
		if (arg.isEmpty())
		{
			out = String.format(Locale.ROOT, "Tide Log: %d catches, %d species, %d shinies, %d unlocks.",
				t.totalCatches, t.species.size(), t.totalShinies, t.unlocks.size());
		}
		else
		{
			String q = arg.toLowerCase(Locale.ROOT);
			Optional<Map.Entry<String, TideLog.Species>> e = t.species.entrySet().stream()
				.filter(x -> x.getKey().toLowerCase(Locale.ROOT).contains(q)).findFirst();
			out = e.map(x -> String.format(Locale.ROOT, "%s: %d caught, best %.2f kg, %d shiny.",
					x.getKey(), x.getValue().count, x.getValue().heaviest, x.getValue().shinies))
				.orElse("No " + arg + " in your Tide Log yet.");
		}
		chat.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(out).build());
	}
}
