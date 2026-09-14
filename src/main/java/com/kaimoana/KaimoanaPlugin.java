package com.kaimoana;

import com.google.inject.Provides;
import com.kaimoana.chat.CatchChatWriter;
import com.kaimoana.chat.FishChatCommand;
import com.kaimoana.detect.CatchDetector;
import com.kaimoana.detect.CatchEvent;
import com.kaimoana.detect.FishingStateTracker;
import com.kaimoana.fx.AnimationOverride;
import com.kaimoana.fx.CadenceGovernor;
import com.kaimoana.fx.FishModelSpawner;
import com.kaimoana.fx.SoundFx;
import com.kaimoana.integrations.DiscordWebhook;
import com.kaimoana.integrations.PartyBroadcast;
import com.kaimoana.integrations.ScreenshotOnTrophy;
import com.kaimoana.integrations.WikiLookup;
import com.kaimoana.log.LocationResolver;
import com.kaimoana.log.TideLog;
import com.kaimoana.log.TideLogPanel;
import com.kaimoana.log.TideLogStore;
import com.kaimoana.registry.CatchResult;
import com.kaimoana.registry.FishEntry;
import com.kaimoana.registry.FishRegistry;
import com.kaimoana.registry.Grade;
import com.kaimoana.registry.WeightRoller;
import com.kaimoana.unlocks.UnlockEngine;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
@PluginDescriptor(
	name = "Kaimoana Fishing",
	description = "Weights, grades, a fish in your hand and a Tide Log for every catch",
	tags = {"fishing", "catch", "weight", "collection", "cosmetic"}
)
public class KaimoanaPlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private KaimoanaConfig config;
	@Inject
	private ItemManager itemManager;
	@Inject
	private ChatMessageManager chatMessageManager;
	@Inject
	private ChatCommandManager chatCommandManager;
	@Inject
	private ClientToolbar toolbar;
	@Inject
	private TideLogStore store;
	@Inject
	private CatchChatWriter chatWriter;
	@Inject
	private FishModelSpawner spawner;
	@Inject
	private SoundFx sound;
	@Inject
	private AnimationOverride animOverride;
	@Inject
	private DiscordWebhook discord;
	@Inject
	private PartyBroadcast partyBroadcast;
	@Inject
	private PartyService partyService;
	@Inject
	private WSClient wsClient;
	@Inject
	private ScreenshotOnTrophy screenshot;
	@Inject
	private WikiLookup wiki;

	private final FishRegistry registry = FishRegistry.load();
	private final WeightRoller roller = new WeightRoller(new Random());
	private final UnlockEngine unlocks = new UnlockEngine();
	private final CadenceGovernor cadence = new CadenceGovernor();
	private final FishingStateTracker fishing = new FishingStateTracker();
	private final Map<String, Integer> itemIdCache = new HashMap<>();

	private CatchDetector detector;
	private FishChatCommand fishCommand;
	private TideLog tideLog = new TideLog();
	private TideLogPanel panel;
	private NavigationButton navButton;
	private Map<Integer, Integer> lastInventory = Collections.emptyMap();
	private boolean dirty;

	@Provides
	KaimoanaConfig provideConfig(ConfigManager cm)
	{
		return cm.getConfig(KaimoanaConfig.class);
	}

	@Override
	protected void startUp()
	{
		detector = new CatchDetector(new CatchDetector.ItemNameResolver()
		{
			@Override
			public int idFor(String n)
			{
				return itemIdFor(n);
			}

			@Override
			public String nameFor(int id)
			{
				return itemManager.getItemComposition(id).getName();
			}

			@Override
			public boolean isFish(String n)
			{
				return registry.find(n).isPresent()
					|| (n.toLowerCase(Locale.ROOT).startsWith("raw ") && itemIdFor(n) >= 0);
			}
		});
		tideLog = store.load();
		fishCommand = new FishChatCommand(chatCommandManager, chatMessageManager, () -> tideLog, config);
		fishCommand.register();
		wsClient.registerMessage(PartyBroadcast.KaimoanaCatch.class);

		panel = new TideLogPanel(itemManager, registry, config);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder().tooltip("Tide Log").icon(icon).priority(7).panel(panel).build();
		if (config.panel())
		{
			toolbar.addNavigation(navButton);
		}
		refreshPanel();
	}

	@Override
	protected void shutDown()
	{
		fishCommand.unregister();
		wsClient.unregisterMessage(PartyBroadcast.KaimoanaCatch.class);
		toolbar.removeNavigation(navButton);
		clientThread.invoke(spawner::clear);
		flush();
		lastInventory = Collections.emptyMap();
	}

	@Subscribe
	public void onGameTick(GameTick t)
	{
		fishing.onTick(client);
		if (dirty && client.getTickCount() % 50 == 0)
		{
			flush();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick t)
	{
		spawner.onClientTick();
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (e.getType() != ChatMessageType.GAMEMESSAGE && e.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		for (CatchEvent ev : detector.onChat(e.getMessage(), client.getTickCount(), playerLocation()))
		{
			handle(ev);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}
		Map<Integer, Integer> now = new HashMap<>();
		for (Item it : e.getItemContainer().getItems())
		{
			if (it.getId() >= 0)
			{
				now.merge(it.getId(), it.getQuantity(), Integer::sum);
			}
		}
		Map<Integer, Integer> delta = new HashMap<>();
		now.forEach((id, q) ->
		{
			int d = q - lastInventory.getOrDefault(id, 0);
			if (d > 0)
			{
				delta.put(id, d);
			}
		});
		boolean first = lastInventory.isEmpty();
		lastInventory = now;
		if (first)
		{
			return;
		}
		for (CatchEvent ev : detector.onInventoryDelta(delta, client.getTickCount(), playerLocation(), fishing.isFishing(client)))
		{
			handle(ev);
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		animOverride.onAnimationChanged(e, fishing.isFishing(client));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState s = e.getGameState();
		if (s == GameState.LOGIN_SCREEN || s == GameState.HOPPING)
		{
			spawner.clear();
			lastInventory = Collections.emptyMap();
			flush();
		}
		else if (s == GameState.LOGGED_IN)
		{
			tideLog = store.load();
			refreshPanel();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!KaimoanaConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		if ("panel".equals(e.getKey()))
		{
			toolbar.removeNavigation(navButton);
			if (config.panel())
			{
				toolbar.addNavigation(navButton);
			}
		}
		refreshPanel();
	}

	@Subscribe
	public void onKaimoanaCatch(PartyBroadcast.KaimoanaCatch c)
	{
		if (!config.party())
		{
			return;
		}
		PartyMember m = partyService.getMemberById(c.getMemberId());
		if (m == null)
		{
			return;
		}
		clientThread.invoke(() -> chatWriter.send(PartyBroadcast.describe(c, m.getDisplayName())));
	}

	private void handle(CatchEvent ev)
	{
		cadence.recordCatch(client.getTickCount());
		int haPrice = ev.getItemId() >= 0 ? itemManager.getItemComposition(ev.getItemId()).getHaPrice() : 0;
		FishEntry entry = registry.resolve(ev.getItemName(), haPrice);
		double kg = roller.roll(entry);
		Grade grade = config.grades() ? roller.grade(entry, kg, roller.rollTaniwha()) : Grade.COMMON;
		boolean shiny = config.shiny() && roller.rollShiny();
		long count = tideLog.getOrCreate(ev.getItemName()).count + 1;
		CatchResult r = CatchResult.builder()
			.event(ev).entry(entry).weightKg(kg).grade(grade).shiny(shiny)
			.speciesCount((int) count).milestone(UnlockEngine.isMilestone(count))
			.build();
		List<String> earned = unlocks.apply(tideLog, r, LocationResolver.nameFor(ev.getWhere()), registry.size());
		dirty = true;
		if (registry.find(ev.getItemName()).isEmpty())
		{
			log.debug("Unregistered fish caught, using fallback: {}", ev.getItemName());
		}
		if (config.wiki())
		{
			wiki.fetch(ev.getItemName(), s -> refreshPanel());
		}

		clientThread.invoke(() ->
		{
			chatWriter.announce(r, earned);
			if (!config.fxEnabled() || (config.fpsFloor() > 0 && client.getFPS() < config.fpsFloor()))
			{
				return;
			}
			if (config.showFishModel())
			{
				Integer hatId = null;
				if (config.hats() && entry.getHat() != null
					&& (config.unlockAllHats() || UnlockEngine.hatUnlocked(tideLog, ev.getItemName())))
				{
					int id = itemIdFor(entry.getHat());
					if (id >= 0)
					{
						hatId = id;
					}
				}
				LocalPoint from = fishing.currentSpot() != null ? fishing.currentSpot().getLocalLocation() : null;
				int arc = cadence.effectiveArc(config.arcTicks());
				int hold = cadence.effectiveHold(config.holdTicks());
				if (r.isMilestone())
				{
					arc += 2;
					hold += 4;
				}
				spawner.spawn(r, from, arc, hold, hatId, entry.getHatOffsetY());
			}
			Player me = client.getLocalPlayer();
			if (config.catchAnim() && me != null)
			{
				me.setAnimation(config.catchAnimId());
				me.setAnimationFrame(0);
			}
			sound.play(r);
		});

		Player p = client.getLocalPlayer();
		discord.maybePost(r, p == null || p.getName() == null ? "Someone" : p.getName());
		partyBroadcast.maybeSend(r);
		screenshot.maybeCapture(r);
		refreshPanel();
	}

	private WorldPoint playerLocation()
	{
		Player p = client.getLocalPlayer();
		return p == null ? null : p.getWorldLocation();
	}

	/** Resolves an item name to an id via the tradeable item search, then the current inventory. Cached. */
	private int itemIdFor(String name)
	{
		String key = name.toLowerCase(Locale.ROOT);
		Integer cached = itemIdCache.get(key);
		if (cached != null)
		{
			return cached;
		}
		int id = itemManager.search(key).stream()
			.filter(i -> i.getName().equalsIgnoreCase(key))
			.map(ItemPrice::getId)
			.findFirst()
			.orElse(-1);
		if (id < 0)
		{
			for (int invId : lastInventory.keySet())
			{
				if (itemManager.getItemComposition(invId).getName().equalsIgnoreCase(key))
				{
					id = invId;
					break;
				}
			}
		}
		if (id >= 0)
		{
			itemIdCache.put(key, id);
		}
		return id;
	}

	private void flush()
	{
		if (dirty)
		{
			store.save(tideLog);
			dirty = false;
		}
	}

	/** Item lookups need the client thread, so resolve every id there and hand Swing a plain map. */
	private void refreshPanel()
	{
		if (panel == null)
		{
			return;
		}
		clientThread.invoke(() ->
		{
			Map<String, Integer> ids = new HashMap<>();
			for (FishEntry e : registry.all())
			{
				ids.put(e.getName(), itemIdFor(e.getName()));
			}
			for (String name : tideLog.species.keySet())
			{
				ids.putIfAbsent(name, itemIdFor(name));
			}
			panel.refresh(tideLog, ids);
		});
	}
}
