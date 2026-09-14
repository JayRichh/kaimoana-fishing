package com.kaimoana;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(KaimoanaConfig.GROUP)
public interface KaimoanaConfig extends Config
{
	String GROUP = "kaimoana";

	@ConfigSection(name = "Effects", description = "Fish model, sound, animation", position = 0)
	String fx = "fx";

	@ConfigSection(name = "Weights", description = "Weight and grade rolls", position = 1)
	String weights = "weights";

	@ConfigSection(name = "Chat", description = "Chat output", position = 2)
	String chat = "chat";

	@ConfigSection(name = "Tide Log", description = "Catch log panel", position = 3)
	String log = "log";

	@ConfigSection(name = "Integrations", description = "Opt-in external features", position = 4, closedByDefault = true)
	String integrations = "integrations";

	// ---- Effects ----

	@ConfigItem(keyName = "fxEnabled", name = "Enable effects", description = "Master toggle for all visual and audio effects", section = fx, position = 0)
	default boolean fxEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "showFishModel", name = "Show caught fish", description = "Spawn the fish model jumping from the water to your hand", section = fx, position = 1)
	default boolean showFishModel()
	{
		return true;
	}

	@Range(min = 2, max = 12)
	@ConfigItem(keyName = "arcTicks", name = "Jump ticks", description = "Ticks the fish takes to jump from water to hand", section = fx, position = 2)
	default int arcTicks()
	{
		return 4;
	}

	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "holdTicks", name = "Hold ticks", description = "Ticks the fish is held. Auto-shortened when catching fast", section = fx, position = 3)
	default int holdTicks()
	{
		return 4;
	}

	@Range(min = 0, max = 400)
	@ConfigItem(keyName = "holdHeight", name = "Hold height", description = "How high above the ground the held fish sits. ~95 waist, ~190 raised hand", section = fx, position = 3)
	default int holdHeight()
	{
		return 190;
	}

	@Range(min = -60, max = 60)
	@ConfigItem(keyName = "holdForward", name = "Hold forward offset", description = "Forward/back offset of the held fish from your tile centre", section = fx, position = 3)
	default int holdForward()
	{
		return 10;
	}

	@Range(min = -60, max = 60)
	@ConfigItem(keyName = "holdRight", name = "Hold sideways offset", description = "Right/left offset of the held fish from your tile centre", section = fx, position = 3)
	default int holdRight()
	{
		return 22;
	}

	@ConfigItem(keyName = "hats", name = "Fish hats", description = "Unlocked hats are shown on caught fish", section = fx, position = 4)
	default boolean hats()
	{
		return true;
	}

	@ConfigItem(keyName = "unlockAllHats", name = "Unlock all hats (preview)", description = "Show every hat regardless of unlock progress", section = fx, position = 5)
	default boolean unlockAllHats()
	{
		return false;
	}

	@ConfigItem(keyName = "splashPop", name = "Splash pop", description = "Scale-pop the fish out of the water", section = fx, position = 6)
	default boolean splashPop()
	{
		return true;
	}

	@ConfigItem(keyName = "sound", name = "Catch sound", description = "Play a sound on each catch", section = fx, position = 7)
	default boolean sound()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "soundVolume", name = "Sound volume", description = "0-100", section = fx, position = 8)
	default int soundVolume()
	{
		return 60;
	}

	@ConfigItem(keyName = "soundId", name = "Sound effect ID", description = "In-game sound effect to play on a catch", section = fx, position = 9)
	default int soundId()
	{
		return 2581;
	}

	@ConfigItem(keyName = "trophySoundId", name = "Trophy sound ID", description = "Sound for Trophy grade and above", section = fx, position = 10)
	default int trophySoundId()
	{
		return 3924;
	}

	@ConfigItem(keyName = "catchAnim", name = "Catch animation", description = "Play an animation on your character when you land a fish", section = fx, position = 10)
	default boolean catchAnim()
	{
		return true;
	}

	@ConfigItem(keyName = "catchAnimId", name = "Catch animation ID", description = "4275 Idea (one arm up), 862 Cheer, 2112 Salute, 865 Clap, 2109 Jump for joy", section = fx, position = 10)
	default int catchAnimId()
	{
		return 4275;
	}

	@ConfigItem(keyName = "animOverride", name = "Override fishing animation", description = "Replace your fishing animation while fishing", section = fx, position = 11)
	default boolean animOverride()
	{
		return false;
	}

	@ConfigItem(keyName = "animOverrideId", name = "Override animation ID", description = "Animation ID to use while fishing", section = fx, position = 12)
	default int animOverrideId()
	{
		return 618;
	}

	@Range(min = 0, max = 60)
	@ConfigItem(keyName = "fpsFloor", name = "Disable effects below FPS", description = "0 = never", section = fx, position = 13)
	default int fpsFloor()
	{
		return 15;
	}

	// ---- Weights ----

	@ConfigItem(keyName = "grades", name = "Grades", description = "Assign a grade to each catch", section = weights, position = 0)
	default boolean grades()
	{
		return true;
	}

	@ConfigItem(keyName = "shiny", name = "Shiny fish", description = "1 in 512 catches is shiny", section = weights, position = 1)
	default boolean shiny()
	{
		return true;
	}

	// ---- Chat ----

	@ConfigItem(keyName = "chatIcon", name = "Fish icon in chat", description = "Prefix catch messages with the fish sprite", section = chat, position = 0)
	default boolean chatIcon()
	{
		return true;
	}

	@ConfigItem(keyName = "chatWeight", name = "Weight in chat", description = "Append weight to catch messages", section = chat, position = 1)
	default boolean chatWeight()
	{
		return true;
	}

	@ConfigItem(keyName = "chatGrade", name = "Grade in chat", description = "Append grade and colour to catch messages", section = chat, position = 2)
	default boolean chatGrade()
	{
		return true;
	}

	@ConfigItem(keyName = "chatFlavour", name = "Flavour text", description = "Show a flavour line on Trophy and above", section = chat, position = 3)
	default boolean chatFlavour()
	{
		return true;
	}

	@ConfigItem(keyName = "chatCommands", name = "!fish command", description = "Enable !fish and !fish <name>", section = chat, position = 4)
	default boolean chatCommands()
	{
		return true;
	}

	// ---- Tide Log ----

	@ConfigItem(keyName = "panel", name = "Show Tide Log panel", description = "Sidebar panel with your catch log", section = log, position = 0)
	default boolean panel()
	{
		return true;
	}

	@ConfigItem(keyName = "silhouettes", name = "Silhouettes for uncaught", description = "Show uncaught species greyed out", section = log, position = 1)
	default boolean silhouettes()
	{
		return true;
	}

	// ---- Integrations ----

	@ConfigItem(keyName = "discordUrl", name = "Discord webhook URL", description = "Post Trophy+ catches here. Blank = off", section = integrations, position = 0, secret = true)
	default String discordUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "party", name = "Party broadcast", description = "Tell party members about Trophy+ catches", section = integrations, position = 1)
	default boolean party()
	{
		return false;
	}

	@ConfigItem(keyName = "wiki", name = "Wiki facts", description = "Fetch fish facts from the OSRS Wiki for the Tide Log", section = integrations, position = 2)
	default boolean wiki()
	{
		return false;
	}

	@ConfigItem(keyName = "screenshot", name = "Screenshot Trophy+", description = "Take a screenshot on Trophy grade and above", section = integrations, position = 3)
	default boolean screenshot()
	{
		return false;
	}
}
