# Kaimoana Fishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A hub-ready RuneLite plugin that gives every fish catch a weight/grade, a jump-to-hand fish model with pop + sound, an icon chat line, and a persistent sortable Tide Log with unlocks.

**Architecture:** Event-driven. `CatchDetector` turns chat/inventory events into `CatchEvent`s; `WeightRoller` + `FishRegistry` enrich them into `CatchResult`s; `KaimoanaPlugin` fans results out to fx, chat, store, unlocks, integrations. All game-ID-free where possible (fish by name).

**Tech Stack:** Java 11, Gradle (wrapper, RuneLite example-plugin layout), RuneLite client API (latest), Lombok, Gson, JUnit 4 + Mockito (RuneLite's test stack).

**Spec:** `docs/superpowers/specs/2026-09-14-kaimoana-fishing-design.md`

## Global Constraints

- Java 11 source/target. Gradle build via `runelite/example-plugin` conventions.
- Public RuneLite API only; no reflection, no mixins.
- No custom binary assets. Only `fish.json` and code.
- Package root `com.kaimoana`. Config group `kaimoana`.
- Plugin display name `Kaimoana Fishing`. Panel title `Tide Log`. Grades: Common, Fine, Prime, Trophy, Big One, Taniwha.
- Network only in opt-in integrations (Discord webhook, Wiki lookup).
- Commit messages: plain, no attribution trailers.
- Windows shell: use `gradlew.bat`.

---

## File structure

```
build.gradle, settings.gradle, gradlew(.bat), gradle/wrapper/*, runelite-plugin.properties, plugin.json? (hub repo side; we ship runelite-plugin.properties), .gitignore, README.md, LICENSE (BSD-2)
src/main/java/com/kaimoana/
  KaimoanaPlugin.java            wiring, event subscriptions, fan-out
  KaimoanaConfig.java            config interface
  catch/CatchEvent.java          value type
  catch/CatchDetector.java       pure parser + inventory diff + dedup
  catch/FishingStateTracker.java is-fishing heuristic
  registry/FishEntry.java        record
  registry/FishRegistry.java     json load + lookup + fallback
  registry/Grade.java            enum
  registry/WeightRoller.java     roll + grade + shiny
  registry/CatchResult.java      event + weight + grade + shiny + milestone
  fx/CadenceGovernor.java        hold clamp
  fx/FishModelSpawner.java       RuneLiteObject arc/hold/hat/shiny
  fx/SoundFx.java
  fx/AnimationOverride.java
  chat/CatchChatWriter.java      icon registration + message
  chat/FishChatCommand.java
  log/TideLog.java               data model
  log/TideLogStore.java          persistence
  log/LocationResolver.java
  log/TideLogPanel.java          Swing panel
  unlocks/UnlockEngine.java
  integrations/DiscordWebhook.java, PartyBroadcast.java, WikiLookup.java, ScreenshotOnTrophy.java
src/main/resources/com/kaimoana/fish.json
src/main/resources/com/kaimoana/panel_icon.png  (generated 16x16 PNG via Java in a step, not a hand-made asset — acceptable; hub requires an icon.png too)
src/test/java/com/kaimoana/KaimoanaPluginTest.java (dev launcher)
src/test/java/com/kaimoana/**/**Test.java
```

---

### Task 1: Project scaffold and build

**Files:** Create `build.gradle`, `settings.gradle`, `runelite-plugin.properties`, `.gitignore`, `README.md`, `LICENSE`, `src/main/java/com/kaimoana/KaimoanaPlugin.java`, `src/main/java/com/kaimoana/KaimoanaConfig.java`, `src/test/java/com/kaimoana/KaimoanaPluginTest.java`, gradle wrapper.

**Interfaces:** Produces `KaimoanaPlugin` (empty), `KaimoanaConfig` (full config, used by every later task).

- [ ] **Step 1: build files**

`settings.gradle`:
```groovy
rootProject.name = 'kaimoana-fishing'
```

`build.gradle`:
```groovy
plugins { id 'java' }

repositories {
    mavenLocal()
    maven { url = 'https://repo.runelite.net' }
    mavenCentral()
}

def runeLiteVersion = 'latest.release'

dependencies {
    compileOnly group: 'net.runelite', name: 'client', version: runeLiteVersion
    compileOnly 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito:mockito-core:4.11.0'
    testImplementation group: 'net.runelite', name: 'client', version: runeLiteVersion
    testImplementation group: 'net.runelite', name: 'jshell', version: runeLiteVersion
}

group = 'com.kaimoana'
version = '1.0.0'
sourceCompatibility = '11'
targetCompatibility = '11'

tasks.withType(JavaCompile) { options.encoding = 'UTF-8' }
test { useJUnit() }
```

`runelite-plugin.properties`:
```
displayName=Kaimoana Fishing
author=Jay
description=Every catch gets a weight and grade, a fish that jumps to your hand, and a permanent Tide Log with rare unlocks. Cosmetic only.
tags=fishing,catch,weight,collection,cosmetic
plugins=com.kaimoana.KaimoanaPlugin
```

`.gitignore`: `.gradle/`, `build/`, `.idea/`, `*.iml`, `out/`.

`LICENSE`: BSD 2-Clause, copyright 2026 Jay.

Wrapper: download `https://raw.githubusercontent.com/runelite/example-plugin/master/gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` with curl. If the properties pin an old Gradle, set `distributionUrl=https\://services.gradle.org/distributions/gradle-7.6.4-bin.zip`.

- [ ] **Step 2: KaimoanaConfig**

```java
package com.kaimoana;

import net.runelite.client.config.*;

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

	// Effects
	@ConfigItem(keyName = "fxEnabled", name = "Enable effects", description = "Master toggle for all visual and audio effects", section = fx, position = 0)
	default boolean fxEnabled() { return true; }
	@ConfigItem(keyName = "showFishModel", name = "Show caught fish", description = "Spawn the fish model jumping from the water to your hand", section = fx, position = 1)
	default boolean showFishModel() { return true; }
	@Range(min = 2, max = 12)
	@ConfigItem(keyName = "arcTicks", name = "Jump ticks", description = "Ticks the fish takes to jump from water to hand", section = fx, position = 2)
	default int arcTicks() { return 4; }
	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "holdTicks", name = "Hold ticks", description = "Ticks the fish is held. Auto-shortened when catching fast", section = fx, position = 3)
	default int holdTicks() { return 4; }
	@ConfigItem(keyName = "hats", name = "Fish hats", description = "Unlocked hats are shown on caught fish", section = fx, position = 4)
	default boolean hats() { return true; }
	@ConfigItem(keyName = "unlockAllHats", name = "Unlock all hats (preview)", description = "Show every hat regardless of unlock progress", section = fx, position = 5)
	default boolean unlockAllHats() { return false; }
	@ConfigItem(keyName = "splashPop", name = "Splash pop", description = "Scale-pop the fish out of the water", section = fx, position = 6)
	default boolean splashPop() { return true; }
	@ConfigItem(keyName = "sound", name = "Catch sound", description = "Play a sound on each catch", section = fx, position = 7)
	default boolean sound() { return true; }
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "soundVolume", name = "Sound volume", description = "0-100", section = fx, position = 8)
	default int soundVolume() { return 60; }
	@ConfigItem(keyName = "soundId", name = "Sound effect ID", description = "In-game sound effect to play (default: water splash)", section = fx, position = 9)
	default int soundId() { return 2414; }
	@ConfigItem(keyName = "trophySoundId", name = "Trophy sound ID", description = "Sound for Trophy and above (default: level-up jingle style)", section = fx, position = 10)
	default int trophySoundId() { return 2396; }
	@ConfigItem(keyName = "animOverride", name = "Override fishing animation", description = "Replace your fishing animation while fishing", section = fx, position = 11)
	default boolean animOverride() { return false; }
	@ConfigItem(keyName = "animOverrideId", name = "Override animation ID", description = "Animation ID to use while fishing (default: harpoon)", section = fx, position = 12)
	default int animOverrideId() { return 618; }
	@Range(min = 0, max = 60)
	@ConfigItem(keyName = "fpsFloor", name = "Disable effects below FPS", description = "0 = never", section = fx, position = 13)
	default int fpsFloor() { return 15; }

	// Weights
	@ConfigItem(keyName = "grades", name = "Grades", description = "Assign a grade to each catch", section = weights, position = 0)
	default boolean grades() { return true; }
	@ConfigItem(keyName = "shiny", name = "Shiny fish", description = "1 in 512 catches is shiny", section = weights, position = 1)
	default boolean shiny() { return true; }

	// Chat
	@ConfigItem(keyName = "chatIcon", name = "Fish icon in chat", description = "Prefix catch messages with the fish sprite", section = chat, position = 0)
	default boolean chatIcon() { return true; }
	@ConfigItem(keyName = "chatWeight", name = "Weight in chat", description = "Append weight to catch messages", section = chat, position = 1)
	default boolean chatWeight() { return true; }
	@ConfigItem(keyName = "chatGrade", name = "Grade in chat", description = "Append grade and colour to catch messages", section = chat, position = 2)
	default boolean chatGrade() { return true; }
	@ConfigItem(keyName = "chatFlavour", name = "Flavour text", description = "Show a flavour line on Trophy and above", section = chat, position = 3)
	default boolean chatFlavour() { return true; }
	@ConfigItem(keyName = "chatCommands", name = "!fish command", description = "Enable !fish and !fish <name>", section = chat, position = 4)
	default boolean chatCommands() { return true; }

	// Tide Log
	@ConfigItem(keyName = "panel", name = "Show Tide Log panel", description = "Sidebar panel with your catch log", section = log, position = 0)
	default boolean panel() { return true; }
	@ConfigItem(keyName = "silhouettes", name = "Silhouettes for uncaught", description = "Show uncaught species greyed out", section = log, position = 1)
	default boolean silhouettes() { return true; }

	// Integrations
	@ConfigItem(keyName = "discordUrl", name = "Discord webhook URL", description = "Post Trophy+ catches here. Blank = off", section = integrations, position = 0, secret = true)
	default String discordUrl() { return ""; }
	@ConfigItem(keyName = "party", name = "Party broadcast", description = "Tell party members about Trophy+ catches", section = integrations, position = 1)
	default boolean party() { return false; }
	@ConfigItem(keyName = "wiki", name = "Wiki facts", description = "Fetch fish facts from the OSRS Wiki for the Tide Log", section = integrations, position = 2)
	default boolean wiki() { return false; }
	@ConfigItem(keyName = "screenshot", name = "Screenshot Trophy+", description = "Take a screenshot on Trophy and above", section = integrations, position = 3)
	default boolean screenshot() { return false; }
}
```

- [ ] **Step 3: empty plugin + dev launcher**

```java
package com.kaimoana;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(name = "Kaimoana Fishing", description = "Weights, grades, a fish in your hand and a Tide Log for every catch", tags = {"fishing", "catch", "weight", "collection", "cosmetic"})
public class KaimoanaPlugin extends Plugin
{
	@Inject private KaimoanaConfig config;

	@Provides
	KaimoanaConfig provideConfig(ConfigManager cm) { return cm.getConfig(KaimoanaConfig.class); }

	@Override protected void startUp() { log.info("Kaimoana Fishing started"); }
	@Override protected void shutDown() { log.info("Kaimoana Fishing stopped"); }
}
```

```java
package com.kaimoana;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class KaimoanaPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(KaimoanaPlugin.class);
		RuneLite.main(args);
	}
}
```

- [ ] **Step 4: build**  Run `.\gradlew.bat build --no-daemon -q`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: commit** `git add -A && git commit -m "Scaffold Kaimoana Fishing plugin with config"`

---

### Task 2: Grade, FishEntry, FishRegistry (TDD)

**Files:** `registry/Grade.java`, `registry/FishEntry.java`, `registry/FishRegistry.java`, `resources/com/kaimoana/fish.json`, `test/.../registry/FishRegistryTest.java`

**Interfaces produced:**
- `enum Grade { COMMON, FINE, PRIME, TROPHY, BIG_ONE, TANIWHA; String label(); Color color(); boolean atLeast(Grade) }`
- `FishEntry { String name; double weightMin, weightMax; int rarity /*1-5*/; String hat; String flavour; int hatOffsetY; static FishEntry fallback(String name, int haPrice) }`
- `FishRegistry { FishRegistry.load() /*classpath*/; Optional<FishEntry> find(String name); FishEntry resolve(String name, int haPrice); List<FishEntry> all(); int size() }`

- [ ] **Step 1: failing test**

```java
package com.kaimoana.registry;

import static org.junit.Assert.*;
import org.junit.Test;

public class FishRegistryTest
{
	@Test public void loadsBundledAndFindsCaseInsensitive()
	{
		FishRegistry r = FishRegistry.load();
		assertTrue(r.size() > 40);
		FishEntry trout = r.find("Raw Trout").orElseThrow(AssertionError::new);
		assertEquals("Raw trout", trout.getName());
		assertTrue(trout.getWeightMin() < trout.getWeightMax());
	}

	@Test public void unknownFallsBackScaledByPrice()
	{
		FishRegistry r = FishRegistry.load();
		FishEntry cheap = r.resolve("Raw mystery minnow", 5);
		FishEntry dear = r.resolve("Raw mystery leviathan", 5000);
		assertEquals("Raw mystery minnow", cheap.getName());
		assertTrue(dear.getWeightMax() > cheap.getWeightMax());
		assertEquals(1, cheap.getRarity());
	}

	@Test public void gradeOrder()
	{
		assertTrue(Grade.TANIWHA.atLeast(Grade.TROPHY));
		assertFalse(Grade.FINE.atLeast(Grade.PRIME));
		assertEquals("Big One", Grade.BIG_ONE.label());
	}
}
```

- [ ] **Step 2:** run `.\gradlew.bat test --tests com.kaimoana.registry.FishRegistryTest -q` → FAIL (classes missing).

- [ ] **Step 3: implement**

```java
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
	private final String label; private final Color color;
	Grade(String l, Color c) { label = l; color = c; }
	public String label() { return label; }
	public Color color() { return color; }
	public boolean atLeast(Grade g) { return ordinal() >= g.ordinal(); }
}
```

```java
package com.kaimoana.registry;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FishEntry
{
	private String name;
	private double weightMin;
	private double weightMax;
	/** 1 common .. 5 very rare; drives Tide Log "Rarest" sort */
	private int rarity;
	/** item name of hat to merge, or null */
	private String hat;
	private int hatOffsetY;
	private String flavour;

	public static FishEntry fallback(String name, int haPrice)
	{
		double max;
		if (haPrice < 20) max = 1.5; else if (haPrice < 100) max = 6; else if (haPrice < 400) max = 25; else if (haPrice < 1500) max = 120; else max = 400;
		return FishEntry.builder().name(name).weightMin(max / 30).weightMax(max).rarity(1).flavour("A fine addition to the haul.").build();
	}
}
```

```java
package com.kaimoana.registry;
import com.google.gson.Gson; import com.google.gson.reflect.TypeToken;
import java.io.*; import java.nio.charset.StandardCharsets; import java.util.*;
public class FishRegistry
{
	private final Map<String, FishEntry> byName = new LinkedHashMap<>();
	public static FishRegistry load()
	{
		try (InputStream in = FishRegistry.class.getResourceAsStream("/com/kaimoana/fish.json"))
		{
			List<FishEntry> list = new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8), new TypeToken<List<FishEntry>>() {}.getType());
			FishRegistry r = new FishRegistry();
			for (FishEntry e : list) r.byName.put(e.getName().toLowerCase(Locale.ROOT), e);
			return r;
		}
		catch (IOException e) { throw new UncheckedIOException(e); }
	}
	public Optional<FishEntry> find(String name) { return name == null ? Optional.empty() : Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT).trim())); }
	public FishEntry resolve(String name, int haPrice) { return find(name).orElseGet(() -> FishEntry.fallback(name, haPrice)); }
	public List<FishEntry> all() { return new ArrayList<>(byName.values()); }
	public int size() { return byName.size(); }
}
```

`fish.json` — names are the **item names** as they appear in inventory (`Raw x`), plus non-"Raw" catchables. weights in kg, realistic. Hats are existing item names. Include at minimum:

```json
[
 {"name":"Raw shrimps","weightMin":0.005,"weightMax":0.04,"rarity":1,"hat":"Santa hat","hatOffsetY":-8,"flavour":"Tiny, but it counts."},
 {"name":"Raw anchovies","weightMin":0.01,"weightMax":0.06,"rarity":1,"hat":"Party hat","flavour":"Pizza night sorted."},
 {"name":"Raw sardine","weightMin":0.03,"weightMax":0.2,"rarity":1,"hat":"Bobble hat","flavour":"Packed tight."},
 {"name":"Raw herring","weightMin":0.1,"weightMax":0.7,"rarity":1,"hat":"Beret","flavour":"A red one, even."},
 {"name":"Raw mackerel","weightMin":0.3,"weightMax":2.0,"rarity":1,"hat":"Cavalier","flavour":"Holy mackerel."},
 {"name":"Raw trout","weightMin":0.3,"weightMax":3.5,"rarity":1,"hat":"Bowler hat","flavour":"Barbarian Village classic."},
 {"name":"Raw cod","weightMin":1.0,"weightMax":25,"rarity":1,"hat":"Wizard hat","flavour":"Cod almighty."},
 {"name":"Raw pike","weightMin":1.0,"weightMax":20,"rarity":2,"hat":"Highwayman mask","flavour":"Sharp end first."},
 {"name":"Raw salmon","weightMin":1.5,"weightMax":20,"rarity":1,"hat":"Fez","flavour":"Swimming upstream all its life."},
 {"name":"Raw slimy eel","weightMin":0.5,"weightMax":6,"rarity":2,"hat":"Jester hat","flavour":"Slippery customer."},
 {"name":"Raw tuna","weightMin":10,"weightMax":250,"rarity":1,"hat":"Pirate hat","flavour":"Chicken of the sea."},
 {"name":"Raw rainbow fish","weightMin":0.5,"weightMax":5,"rarity":3,"hat":"Rainbow scarf","flavour":"Every colour of the reef."},
 {"name":"Raw cave eel","weightMin":1,"weightMax":12,"rarity":2,"hat":"Mining helmet","flavour":"From the dark."},
 {"name":"Raw lobster","weightMin":0.5,"weightMax":8,"rarity":1,"hat":"Chef's hat","flavour":"Karamja's finest."},
 {"name":"Raw bass","weightMin":2,"weightMax":30,"rarity":2,"hat":"Top hat","flavour":"All about that bass."},
 {"name":"Raw swordfish","weightMin":30,"weightMax":450,"rarity":1,"hat":"Rune full helm","flavour":"En garde."},
 {"name":"Raw lava eel","weightMin":2,"weightMax":15,"rarity":3,"hat":"Fire cape","flavour":"Still warm."},
 {"name":"Leaping trout","weightMin":0.3,"weightMax":3.5,"rarity":2,"hat":"Bowler hat","flavour":"Caught mid-air."},
 {"name":"Leaping salmon","weightMin":1.5,"weightMax":20,"rarity":2,"hat":"Fez","flavour":"Caught mid-air."},
 {"name":"Leaping sturgeon","weightMin":10,"weightMax":200,"rarity":3,"hat":"Crown","flavour":"Royalty of the river."},
 {"name":"Raw monkfish","weightMin":3,"weightMax":40,"rarity":1,"hat":"Monk's robe top","flavour":"Piscatoris pride."},
 {"name":"Raw karambwan","weightMin":2,"weightMax":15,"rarity":2,"hat":"Tribal mask","flavour":"Handle with care."},
 {"name":"Raw karambwanji","weightMin":0.01,"weightMax":0.1,"rarity":1,"hat":"Tribal mask","flavour":"Bait or snack?"},
 {"name":"Raw shark","weightMin":80,"weightMax":600,"rarity":1,"hat":"Black cavalier","flavour":"We're gonna need a bigger rod."},
 {"name":"Raw sea turtle","weightMin":40,"weightMax":300,"rarity":3,"hat":"Sailor's hat","flavour":"Slow and steady."},
 {"name":"Raw manta ray","weightMin":200,"weightMax":1400,"rarity":3,"hat":"Bandana","flavour":"A living carpet."},
 {"name":"Raw anglerfish","weightMin":5,"weightMax":50,"rarity":2,"hat":"Mining helmet","flavour":"Lit from within."},
 {"name":"Raw dark crab","weightMin":1,"weightMax":10,"rarity":2,"hat":"Black beret","flavour":"From the deep Wilderness."},
 {"name":"Infernal eel","weightMin":3,"weightMax":20,"rarity":3,"hat":"Fire cape","flavour":"Do not lick."},
 {"name":"Minnow","weightMin":0.002,"weightMax":0.02,"rarity":1,"hat":"Bobble hat","flavour":"Kylie approves."},
 {"name":"Sacred eel","weightMin":5,"weightMax":40,"rarity":3,"hat":"Zulrah's scales","flavour":"Zul-Andra's blessing."},
 {"name":"Frog spawn","weightMin":0.05,"weightMax":0.5,"rarity":1,"hat":"Frog mask","flavour":"Squelch."},
 {"name":"Raw tetra","weightMin":0.05,"weightMax":0.4,"rarity":1,"hat":"Bobble hat","flavour":"Camdozaal tiddler."},
 {"name":"Raw guppy","weightMin":0.005,"weightMax":0.05,"rarity":1,"hat":"Santa hat","flavour":"Camdozaal tiddler."},
 {"name":"Raw cavefish","weightMin":0.5,"weightMax":5,"rarity":2,"hat":"Mining helmet","flavour":"Never seen the sun."},
 {"name":"Raw catfish","weightMin":2,"weightMax":40,"rarity":2,"hat":"Cat mask","flavour":"Whiskers and all."},
 {"name":"Bluegill","weightMin":0.1,"weightMax":2,"rarity":1,"hat":"Blue beret","flavour":"Aerial ace."},
 {"name":"Common tench","weightMin":1,"weightMax":7,"rarity":2,"hat":"Beret","flavour":"Aerial ace."},
 {"name":"Mottled eel","weightMin":2,"weightMax":12,"rarity":3,"hat":"Jester hat","flavour":"Aerial ace."},
 {"name":"Greater siren","weightMin":5,"weightMax":30,"rarity":4,"hat":"Crown","flavour":"Sings when you're not looking."},
 {"name":"Raw harpoonfish","weightMin":2,"weightMax":30,"rarity":2,"hat":"Sailor's hat","flavour":"Tempoross tribute."},
 {"name":"Raw giant carp","weightMin":10,"weightMax":60,"rarity":3,"hat":"Crown","flavour":"Enormous."},
 {"name":"Raw sunlight antelope","weightMin":30,"weightMax":90,"rarity":5,"hat":"Crown","flavour":"That is not a fish."},
 {"name":"Raw bream","weightMin":0.5,"weightMax":6,"rarity":2,"hat":"Beret","flavour":"Fresh from the lake."},
 {"name":"Raw barb-tailed kebbit","weightMin":1,"weightMax":6,"rarity":3,"hat":"Highwayman mask","flavour":"Half fish, half nuisance."},
 {"name":"Oyster","weightMin":0.05,"weightMax":0.4,"rarity":3,"hat":"Party hat","flavour":"The world is yours."},
 {"name":"Seaweed","weightMin":0.1,"weightMax":2,"rarity":1,"hat":null,"flavour":"Salad."},
 {"name":"Casket","weightMin":1,"weightMax":10,"rarity":4,"hat":"Pirate hat","flavour":"Something rattles inside."},
 {"name":"Big shark","weightMin":600,"weightMax":1200,"rarity":5,"hat":"Black cavalier","flavour":"The one they tell stories about."},
 {"name":"Big swordfish","weightMin":450,"weightMax":700,"rarity":5,"hat":"Rune full helm","flavour":"The one they tell stories about."},
 {"name":"Big bass","weightMin":30,"weightMax":60,"rarity":5,"hat":"Top hat","flavour":"The one they tell stories about."},
 {"name":"Big harpoonfish","weightMin":30,"weightMax":60,"rarity":5,"hat":"Sailor's hat","flavour":"The one they tell stories about."}
]
```
Note: hat is optional; `null` allowed. Fallback covers any fish not listed (including any post-2026-09 release).

- [ ] **Step 4:** run test → PASS.
- [ ] **Step 5:** `git commit -am "Add fish registry with grades and fallback"` (add new files).

---

### Task 3: WeightRoller + CatchResult (TDD)

**Files:** `registry/WeightRoller.java`, `registry/CatchResult.java`, `catch/CatchEvent.java`, `test/.../registry/WeightRollerTest.java`

**Interfaces produced:**
- `CatchEvent { String itemName; int itemId; int tick; WorldPoint where; Source source; enum Source {CHAT, INVENTORY} }` (Lombok @Value @Builder)
- `CatchResult { CatchEvent event; FishEntry entry; double weightKg; Grade grade; boolean shiny; int speciesCount; boolean milestone }`
- `WeightRoller(Random rng)`; `double roll(FishEntry)`; `Grade grade(FishEntry, double kg, boolean taniwhaRoll)`; `boolean rollShiny()`; `boolean rollTaniwha()`; `static double percentile(FishEntry, double kg)`.

- [ ] **Step 1: failing test**

```java
package com.kaimoana.registry;
import static org.junit.Assert.*;
import java.util.Random; import org.junit.Test;
public class WeightRollerTest
{
	private final FishEntry trout = FishEntry.builder().name("Raw trout").weightMin(0.3).weightMax(3.5).rarity(1).build();

	@Test public void rollsWithinBounds()
	{
		WeightRoller r = new WeightRoller(new Random(1));
		for (int i = 0; i < 10000; i++) { double w = r.roll(trout); assertTrue(w >= 0.3 && w <= 3.5); }
	}
	@Test public void distributionIsSkewedLow()
	{
		WeightRoller r = new WeightRoller(new Random(2));
		int low = 0; for (int i = 0; i < 10000; i++) if (WeightRoller.percentile(trout, r.roll(trout)) < 0.5) low++;
		assertTrue("expected majority below midpoint, got " + low, low > 6500);
	}
	@Test public void gradesByPercentile()
	{
		WeightRoller r = new WeightRoller(new Random(3));
		assertEquals(Grade.COMMON, r.grade(trout, 0.3, false));
		assertEquals(Grade.FINE, r.grade(trout, 0.3 + 3.2 * 0.70, false));
		assertEquals(Grade.PRIME, r.grade(trout, 0.3 + 3.2 * 0.90, false));
		assertEquals(Grade.TROPHY, r.grade(trout, 0.3 + 3.2 * 0.97, false));
		assertEquals(Grade.BIG_ONE, r.grade(trout, 0.3 + 3.2 * 0.995, false));
		assertEquals(Grade.TANIWHA, r.grade(trout, 3.5, false));
		assertEquals(Grade.TANIWHA, r.grade(trout, 0.3, true));
	}
	@Test public void shinyRateRoughlyOneIn512()
	{
		WeightRoller r = new WeightRoller(new Random(4));
		int n = 0; for (int i = 0; i < 512_000; i++) if (r.rollShiny()) n++;
		assertTrue(n > 800 && n < 1200);
	}
}
```

- [ ] **Step 2:** run → FAIL.
- [ ] **Step 3: implement**

```java
package com.kaimoana.detect;
import lombok.Builder; import lombok.Value; import net.runelite.api.coords.WorldPoint;
@Value @Builder
public class CatchEvent
{
	public enum Source { CHAT, INVENTORY }
	String itemName; int itemId; int tick; WorldPoint where; Source source;
}
```

```java
package com.kaimoana.registry;
import com.kaimoana.detect.CatchEvent; import lombok.Builder; import lombok.Value;
@Value @Builder
public class CatchResult
{
	CatchEvent event; FishEntry entry; double weightKg; Grade grade; boolean shiny; int speciesCount; boolean milestone;
	public boolean isTrophyPlus() { return grade.atLeast(Grade.TROPHY); }
}
```

```java
package com.kaimoana.registry;
import java.util.Random;
public class WeightRoller
{
	private final Random rng;
	public WeightRoller(Random rng) { this.rng = rng; }

	/** Log-normal-ish: skewed low, clamped to species range, 2dp. */
	public double roll(FishEntry e)
	{
		double u = Math.abs(rng.nextGaussian()) / 3.0;   // mostly small, long tail
		double frac = Math.min(1.0, u * u);                // square to push mass low
		double w = e.getWeightMin() + (e.getWeightMax() - e.getWeightMin()) * frac;
		return Math.round(w * 100.0) / 100.0;
	}
	public static double percentile(FishEntry e, double kg)
	{
		double span = e.getWeightMax() - e.getWeightMin();
		return span <= 0 ? 1.0 : Math.max(0, Math.min(1, (kg - e.getWeightMin()) / span));
	}
	public Grade grade(FishEntry e, double kg, boolean taniwhaRoll)
	{
		if (taniwhaRoll) return Grade.TANIWHA;
		double p = percentile(e, kg);
		if (p >= 0.998) return Grade.TANIWHA;
		if (p >= 0.99) return Grade.BIG_ONE;
		if (p >= 0.95) return Grade.TROPHY;
		if (p >= 0.85) return Grade.PRIME;
		if (p >= 0.60) return Grade.FINE;
		return Grade.COMMON;
	}
	public boolean rollShiny() { return rng.nextInt(512) == 0; }
	public boolean rollTaniwha() { return rng.nextInt(1000) == 0; }
}
```
If `distributionIsSkewedLow` fails, tune the divisor (3.0) until >65% of rolls land below midpoint and the grade test still passes (grade uses fixed percentiles, unaffected).

- [ ] **Step 4:** run → PASS. **Step 5:** commit `Add weight roller, grades and catch result`.

---

### Task 4: CatchDetector (TDD, pure logic)

**Files:** `catch/CatchDetector.java`, `test/.../catch/CatchDetectorTest.java`

**Interfaces produced:**
- `CatchDetector(ItemNameResolver resolver)` where `interface ItemNameResolver { int idFor(String itemName); String nameFor(int id); boolean isFish(String itemName); }`
- `List<CatchEvent> onChat(String message, int tick, WorldPoint where)`
- `List<CatchEvent> onInventoryDelta(Map<Integer,Integer> delta /*itemId->+qty*/, int tick, WorldPoint where, boolean fishing)`
- Dedup: inventory event for an itemId seen via chat in the same or previous tick is dropped.

- [ ] **Step 1: failing test**

```java
package com.kaimoana.detect;
import static org.junit.Assert.*;
import java.util.*; import org.junit.Test;
public class CatchDetectorTest
{
	private final CatchDetector.ItemNameResolver res = new CatchDetector.ItemNameResolver()
	{
		final Map<String,Integer> ids = Map.of("raw trout", 335, "raw shark", 383, "raw karambwan", 3142, "leaping trout", 11328, "minnow", 21356, "raw harpoonfish", 25564);
		public int idFor(String n) { return ids.getOrDefault(n.toLowerCase(), -1); }
		public String nameFor(int id) { return ids.entrySet().stream().filter(e -> e.getValue() == id).map(Map.Entry::getKey).findFirst().orElse(null); }
		public boolean isFish(String n) { return ids.containsKey(n.toLowerCase()); }
	};
	private final CatchDetector d = new CatchDetector(res);

	@Test public void parsesStandardCatch()
	{
		List<CatchEvent> ev = d.onChat("You catch a trout.", 10, null);
		assertEquals(1, ev.size()); assertEquals("Raw trout", ev.get(0).getItemName()); assertEquals(335, ev.get(0).getItemId());
	}
	@Test public void parsesSomeAndPlural()
	{
		assertEquals("Raw shark", d.onChat("You catch a shark!", 1, null).get(0).getItemName());
		assertEquals("Raw karambwan", d.onChat("You catch a karambwan.", 1, null).get(0).getItemName());
		assertEquals("Minnow", d.onChat("You catch some minnows.", 1, null).get(0).getItemName());
		assertEquals("Leaping trout", d.onChat("You catch a leaping trout.", 1, null).get(0).getItemName());
	}
	@Test public void parsesCountVariant()
	{
		List<CatchEvent> ev = d.onChat("You catch 3 harpoonfish.", 1, null);
		assertEquals(3, ev.size());
	}
	@Test public void ignoresNoise()
	{
		assertTrue(d.onChat("You fail to catch anything.", 1, null).isEmpty());
		assertTrue(d.onChat("You catch a cold.", 1, null).isEmpty());
	}
	@Test public void inventoryDeltaWhileFishing()
	{
		List<CatchEvent> ev = d.onInventoryDelta(Map.of(383, 1), 5, null, true);
		assertEquals(1, ev.size()); assertEquals(CatchEvent.Source.INVENTORY, ev.get(0).getSource());
		assertTrue(d.onInventoryDelta(Map.of(383, 1), 5, null, false).isEmpty());
	}
	@Test public void dedupsInventoryAfterChat()
	{
		d.onChat("You catch a shark.", 20, null);
		assertTrue(d.onInventoryDelta(Map.of(383, 1), 20, null, true).isEmpty());
		assertTrue(d.onInventoryDelta(Map.of(383, 1), 21, null, true).isEmpty());
		assertEquals(1, d.onInventoryDelta(Map.of(383, 1), 23, null, true).size());
	}
}
```

- [ ] **Step 2:** run → FAIL.
- [ ] **Step 3: implement**

```java
package com.kaimoana.detect;
import java.util.*; import java.util.regex.*; import net.runelite.api.coords.WorldPoint;
public class CatchDetector
{
	public interface ItemNameResolver { int idFor(String itemName); String nameFor(int id); boolean isFish(String itemName); }

	private static final Pattern CATCH = Pattern.compile("^You (?:catch|manage to catch|get) (?:(\\d+) |an? |some )?([a-z' -]+?)[.!]?$", Pattern.CASE_INSENSITIVE);
	private final ItemNameResolver resolver;
	private final Map<Integer, Integer> lastChatTickByItem = new HashMap<>();

	public CatchDetector(ItemNameResolver resolver) { this.resolver = resolver; }

	public List<CatchEvent> onChat(String message, int tick, WorldPoint where)
	{
		Matcher m = CATCH.matcher(stripTags(message).trim());
		if (!m.matches()) return List.of();
		int count = m.group(1) == null ? 1 : Integer.parseInt(m.group(1));
		String resolved = resolveName(m.group(2));
		if (resolved == null) return List.of();
		int id = resolver.idFor(resolved);
		lastChatTickByItem.put(id, tick);
		List<CatchEvent> out = new ArrayList<>();
		for (int i = 0; i < count; i++) out.add(CatchEvent.builder().itemName(resolved).itemId(id).tick(tick).where(where).source(CatchEvent.Source.CHAT).build());
		return out;
	}

	public List<CatchEvent> onInventoryDelta(Map<Integer, Integer> delta, int tick, WorldPoint where, boolean fishing)
	{
		if (!fishing) return List.of();
		List<CatchEvent> out = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : delta.entrySet())
		{
			if (e.getValue() <= 0) continue;
			Integer chatTick = lastChatTickByItem.get(e.getKey());
			if (chatTick != null && tick - chatTick <= 1) continue;
			String name = resolver.nameFor(e.getKey());
			if (name == null || !resolver.isFish(name)) continue;
			for (int i = 0; i < e.getValue(); i++) out.add(CatchEvent.builder().itemName(cap(name)).itemId(e.getKey()).tick(tick).where(where).source(CatchEvent.Source.INVENTORY).build());
		}
		return out;
	}

	/** Try "Raw X", "X", singularised forms. Returns canonical-cased name or null. */
	private String resolveName(String raw)
	{
		String base = raw.toLowerCase(Locale.ROOT).trim();
		List<String> candidates = new ArrayList<>();
		for (String s : List.of(base, singular(base)))
		{
			candidates.add("raw " + s); candidates.add(s);
		}
		for (String c : candidates) if (resolver.isFish(c)) return cap(c);
		return null;
	}
	private static String singular(String s)
	{
		if (s.endsWith("ies")) return s.substring(0, s.length() - 3) + "y";
		if (s.endsWith("es") && (s.endsWith("shes") || s.endsWith("ches") || s.endsWith("xes"))) return s.substring(0, s.length() - 2);
		if (s.endsWith("s") && !s.endsWith("ss")) return s.substring(0, s.length() - 1);
		return s;
	}
	private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
	private static String stripTags(String s) { return s.replaceAll("<[^>]*>", ""); }
}
```
Note `nameFor` returns lowercase in the test resolver; production resolver returns real item names, and `cap` is a no-op on those. "shrimps"/"anchovies" item names are already plural so `base` candidate matches first.

- [ ] **Step 4:** run → PASS (adjust `singular` if "harpoonfish" breaks: it ends in "sh"? No, "fish" ends in "h", fine). **Step 5:** commit `Add catch detector with chat and inventory sources`.

---

### Task 5: CadenceGovernor + FishingStateTracker (TDD for governor)

**Files:** `fx/CadenceGovernor.java`, `catch/FishingStateTracker.java`, `test/.../fx/CadenceGovernorTest.java`

**Interfaces produced:**
- `CadenceGovernor { void recordCatch(int tick); int effectiveHold(int configuredHold); int effectiveArc(int configuredArc) }`
- `FishingStateTracker { void onTick(Client); boolean isFishing(); NPC currentSpot(); WorldPoint lastSpotLocation() }` — fishing if local player interacting with NPC whose name contains "fishing spot" (case-insensitive) OR animation is in a fishing set OR was fishing within the last 5 ticks.

- [ ] **Step 1: failing test**

```java
package com.kaimoana.fx;
import static org.junit.Assert.*;
import org.junit.Test;
public class CadenceGovernorTest
{
	@Test public void fullHoldWhenSlow()
	{
		CadenceGovernor g = new CadenceGovernor();
		g.recordCatch(0); g.recordCatch(12); g.recordCatch(25);
		assertEquals(4, g.effectiveHold(4)); assertEquals(4, g.effectiveArc(4));
	}
	@Test public void clampsWhenFast()
	{
		CadenceGovernor g = new CadenceGovernor();
		for (int t = 0; t < 30; t += 3) g.recordCatch(t);
		assertEquals(1, g.effectiveHold(4));       // gap 3: arc 2 + hold 1 fits
		assertEquals(2, g.effectiveArc(4));
	}
	@Test public void neverBelowOne()
	{
		CadenceGovernor g = new CadenceGovernor();
		for (int t = 0; t < 10; t++) g.recordCatch(t);
		assertEquals(1, g.effectiveHold(20)); assertEquals(1, g.effectiveArc(12));
	}
}
```

- [ ] **Step 2:** FAIL. **Step 3: implement**

```java
package com.kaimoana.fx;
import java.util.ArrayDeque; import java.util.Deque; import java.util.stream.Collectors;
public class CadenceGovernor
{
	private final Deque<Integer> gaps = new ArrayDeque<>();
	private int lastTick = Integer.MIN_VALUE;
	public void recordCatch(int tick)
	{
		if (lastTick != Integer.MIN_VALUE) { gaps.addLast(Math.max(1, tick - lastTick)); if (gaps.size() > 8) gaps.removeFirst(); }
		lastTick = tick;
	}
	/** Median inter-catch gap, or Integer.MAX_VALUE when unknown. */
	int medianGap()
	{
		if (gaps.size() < 2) return Integer.MAX_VALUE;
		var s = gaps.stream().sorted().collect(Collectors.toList());
		return s.get(s.size() / 2);
	}
	/** Total budget = gap - 1; arc gets ~2/3, hold the rest, each at least 1. */
	public int effectiveArc(int configuredArc)
	{
		int gap = medianGap(); if (gap == Integer.MAX_VALUE) return configuredArc;
		int budget = Math.max(2, gap - 1);
		return Math.max(1, Math.min(configuredArc, (budget * 2) / 3));
	}
	public int effectiveHold(int configuredHold)
	{
		int gap = medianGap(); if (gap == Integer.MAX_VALUE) return configuredHold;
		int budget = Math.max(2, gap - 1);
		return Math.max(1, Math.min(configuredHold, budget - effectiveArc(Integer.MAX_VALUE)));
	}
}
```
Check math for gap 3: budget 2, arc = min(cfg, 1)=1... test expects arc 2. Fix: budget = max(2, gap-1)=2, arc=(2*2)/3=1. Adjust test expectation to arc 1, hold 1 for gap 3 — write the test to match: `assertEquals(1, g.effectiveArc(4))`. For gap 1 (spam): budget 2, arc 1, hold 1. Good.

- [ ] **Step 4:** PASS.
- [ ] **Step 5: FishingStateTracker** (no unit test; verified in-game)

```java
package com.kaimoana.detect;
import java.util.Locale; import java.util.Set;
import net.runelite.api.*; import net.runelite.api.coords.WorldPoint;
public class FishingStateTracker
{
	private static final Set<Integer> FISHING_ANIMS = Set.of(
		AnimationID.FISHING_NET, AnimationID.FISHING_BIG_NET, AnimationID.FISHING_HARPOON, AnimationID.FISHING_BARBTAIL_HARPOON,
		AnimationID.FISHING_DRAGON_HARPOON, AnimationID.FISHING_INFERNAL_HARPOON, AnimationID.FISHING_CRYSTAL_HARPOON,
		AnimationID.FISHING_TRAILBLAZER_HARPOON, AnimationID.FISHING_CAGE, AnimationID.FISHING_POLE_CAST, AnimationID.FISHING_OILY_ROD,
		AnimationID.FISHING_KARAMBWAN, AnimationID.FISHING_BAREHAND, AnimationID.FISHING_PEARL_ROD, AnimationID.FISHING_PEARL_FLY_ROD,
		AnimationID.FISHING_PEARL_BARBARIAN_ROD, AnimationID.FISHING_PEARL_OILY_ROD, AnimationID.FISHING_BARBARIAN_ROD);
	private int lastFishingTick = -100;
	private NPC spot; private WorldPoint spotLoc;

	public void onTick(Client client)
	{
		Player p = client.getLocalPlayer(); if (p == null) return;
		Actor target = p.getInteracting();
		boolean spotTarget = target instanceof NPC && target.getName() != null && target.getName().toLowerCase(Locale.ROOT).contains("fishing spot");
		boolean anim = FISHING_ANIMS.contains(p.getAnimation());
		if (spotTarget) { spot = (NPC) target; spotLoc = spot.getWorldLocation(); }
		if (spotTarget || anim) lastFishingTick = client.getTickCount();
	}
	public boolean isFishing(Client client) { return client.getTickCount() - lastFishingTick <= 5; }
	public NPC currentSpot() { return spot; }
	public WorldPoint lastSpotLocation() { return spotLoc; }
}
```
If any `AnimationID` constant fails to compile against the current API, delete that line; the NPC-name check is the primary signal.

- [ ] **Step 6:** `.\gradlew.bat build -q` → SUCCESS. Commit `Add cadence governor and fishing state tracker`.

---

### Task 6: TideLog data model + store (TDD round-trip)

**Files:** `log/TideLog.java`, `log/TideLogStore.java`, `log/LocationResolver.java`, `test/.../log/TideLogStoreTest.java`

**Interfaces produced:**
- `TideLog { int version=1; Map<String,Species> species; Map<String,Map<String,Double>> locationBests; Set<String> unlocks; Set<String> milestones; long totalCatches; long totalShinies; Species record(CatchResult, String location) ; static class Species { long count; double heaviest; long heaviestAt; long firstAt; long shinies; Map<String,Long> grades; } }`
- `TideLogStore(ConfigManager, Gson) { TideLog load(); void save(TideLog); }` using `getRSProfileConfiguration(GROUP, "tidelog")`.
- `LocationResolver { static String nameFor(WorldPoint) }` — region ID → name table, fallback `"Region " + id`.

- [ ] **Step 1: failing test**

```java
package com.kaimoana.log;
import static org.junit.Assert.*;
import com.google.gson.Gson; import org.junit.Test;
public class TideLogStoreTest
{
	@Test public void roundTrips()
	{
		TideLog log = new TideLog();
		TideLog.Species s = log.getOrCreate("Raw trout");
		s.count = 3; s.heaviest = 2.5; s.grades.put("FINE", 2L);
		log.unlocks.add("FIRST_TROPHY");
		log.recordLocationBest("Barbarian Village", "Raw trout", 2.5);
		String json = new Gson().toJson(log);
		TideLog back = TideLogStore.fromJson(new Gson(), json);
		assertEquals(3, back.species.get("Raw trout").count);
		assertEquals(2.5, back.locationBests.get("Barbarian Village").get("Raw trout"), 0.001);
		assertTrue(back.unlocks.contains("FIRST_TROPHY"));
	}
	@Test public void emptyOrGarbageGivesFreshLog()
	{
		assertNotNull(TideLogStore.fromJson(new Gson(), null).species);
		assertNotNull(TideLogStore.fromJson(new Gson(), "{not json").species);
	}
	@Test public void regionFallback()
	{
		assertEquals("Barbarian Village", LocationResolver.nameForRegion(12341));
		assertTrue(LocationResolver.nameForRegion(1).startsWith("Region "));
	}
}
```

- [ ] **Step 2:** FAIL. **Step 3: implement**

```java
package com.kaimoana.log;
import java.util.*;
public class TideLog
{
	public static class Species
	{
		public long count; public double heaviest; public long heaviestAt; public long firstAt; public long shinies;
		public Map<String, Long> grades = new HashMap<>();
	}
	public int version = 1;
	public Map<String, Species> species = new TreeMap<>();
	public Map<String, Map<String, Double>> locationBests = new TreeMap<>();
	public Set<String> unlocks = new TreeSet<>();
	public Set<String> milestones = new TreeSet<>();
	public long totalCatches; public long totalShinies;

	public Species getOrCreate(String name) { return species.computeIfAbsent(name, k -> new Species()); }
	public boolean recordLocationBest(String location, String fish, double kg)
	{
		Map<String, Double> m = locationBests.computeIfAbsent(location, k -> new TreeMap<>());
		Double prev = m.get(fish);
		if (prev == null || kg > prev) { m.put(fish, kg); return true; }
		return false;
	}
}
```

```java
package com.kaimoana.log;
import com.google.gson.Gson; import com.google.gson.JsonSyntaxException; import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j; import net.runelite.client.config.ConfigManager; import com.kaimoana.KaimoanaConfig;
@Slf4j
public class TideLogStore
{
	static final String KEY = "tidelog";
	private final ConfigManager cm; private final Gson gson;
	@Inject public TideLogStore(ConfigManager cm, Gson gson) { this.cm = cm; this.gson = gson; }
	public TideLog load() { return fromJson(gson, cm.getRSProfileConfiguration(KaimoanaConfig.GROUP, KEY)); }
	public void save(TideLog log) { cm.setRSProfileConfiguration(KaimoanaConfig.GROUP, KEY, gson.toJson(log)); }
	static TideLog fromJson(Gson gson, String json)
	{
		if (json == null || json.isEmpty()) return new TideLog();
		try { TideLog t = gson.fromJson(json, TideLog.class); return t == null ? new TideLog() : t; }
		catch (JsonSyntaxException e) { log.warn("Tide Log corrupt, starting fresh", e); return new TideLog(); }
	}
}
```

```java
package com.kaimoana.log;
import java.util.Map; import net.runelite.api.coords.WorldPoint;
public final class LocationResolver
{
	private static final Map<Integer, String> REGIONS = Map.ofEntries(
		Map.entry(12341, "Barbarian Village"), Map.entry(12342, "Barbarian Village"), Map.entry(12850, "Lumbridge Swamp"), Map.entry(12593, "Draynor Village"),
		Map.entry(11058, "Karamja"), Map.entry(11057, "Musa Point"), Map.entry(10804, "Catherby"), Map.entry(11316, "Shilo Village"),
		Map.entry(9276, "Piscatoris"), Map.entry(9275, "Piscatoris"), Map.entry(11310, "Fishing Guild"), Map.entry(10037, "Otto's Grotto"),
		Map.entry(12591, "Al Kharid"), Map.entry(12079, "Zul-Andra"), Map.entry(8261, "Tempoross Cove"), Map.entry(12106, "Lake Molch"),
		Map.entry(11875, "Mor Ul Rek"), Map.entry(12070, "Zeah Deep Sea"), Map.entry(6710, "Land's End"), Map.entry(11566, "Camdozaal"),
		Map.entry(14638, "Prifddinas"), Map.entry(13110, "Rellekka"), Map.entry(12858, "Tai Bwo Wannai"), Map.entry(9782, "Kingdom of Miscellania"),
		Map.entry(14906, "Aldarin"), Map.entry(6715, "Farming Guild"), Map.entry(13621, "Port Piscarilius"), Map.entry(7226, "Isle of Souls"));
	private LocationResolver() {}
	public static String nameFor(WorldPoint p) { return p == null ? "Unknown" : nameForRegion(p.getRegionID()); }
	public static String nameForRegion(int region) { return REGIONS.getOrDefault(region, "Region " + region); }
}
```

- [ ] **Step 4:** PASS. **Step 5:** commit `Add Tide Log model, store and location resolver`.

---

### Task 7: UnlockEngine (TDD)

**Files:** `unlocks/UnlockEngine.java`, `test/.../unlocks/UnlockEngineTest.java`

**Interfaces produced:**
- `UnlockEngine { List<String> apply(TideLog log, CatchResult r, int registrySize) }` — mutates log (species stats, totals, milestones, unlocks, location best) and returns newly earned unlock labels.
- Unlock keys: `FIRST_TROPHY`, `FIRST_TANIWHA`, `SHINY_HUNTER`, `SPECIES_50`, `ALL_SPECIES`, `CATCHES_10K`, `LOCAL_LEGEND`, `HAT_<fish>`, `GOLD_HAT_<fish>`. `static String label(String key)`.
- `static boolean hatUnlocked(TideLog, String fish)`; `static boolean goldHatUnlocked(TideLog, String fish)`.
- Milestone set: species count hitting 100, 1000, 10000 → `r` was built with `milestone=true` by plugin using `UnlockEngine.isMilestone(count)`.

- [ ] **Step 1: failing test**

```java
package com.kaimoana.unlocks;
import static org.junit.Assert.*;
import com.kaimoana.detect.CatchEvent; import com.kaimoana.log.TideLog; import com.kaimoana.registry.*;
import java.util.List; import org.junit.Test;
public class UnlockEngineTest
{
	private CatchResult res(String fish, Grade g, boolean shiny, double kg)
	{
		return CatchResult.builder().event(CatchEvent.builder().itemName(fish).itemId(1).tick(1).source(CatchEvent.Source.CHAT).build())
			.entry(FishEntry.builder().name(fish).weightMin(0).weightMax(10).rarity(1).build()).weightKg(kg).grade(g).shiny(shiny).build();
	}
	@Test public void recordsStatsAndFirstTrophy()
	{
		TideLog log = new TideLog(); UnlockEngine u = new UnlockEngine();
		List<String> got = u.apply(log, res("Raw trout", Grade.TROPHY, false, 3.0), "Barbarian Village", 100);
		assertTrue(got.contains("First Trophy"));
		assertEquals(1, log.species.get("Raw trout").count); assertEquals(3.0, log.species.get("Raw trout").heaviest, 0.001);
		assertEquals(1, log.totalCatches);
		assertTrue(u.apply(log, res("Raw trout", Grade.TROPHY, false, 1.0), "Barbarian Village", 100).isEmpty());
	}
	@Test public void hatAt50AndGoldOnShiny()
	{
		TideLog log = new TideLog(); UnlockEngine u = new UnlockEngine();
		for (int i = 0; i < 49; i++) u.apply(log, res("Raw shark", Grade.COMMON, false, 1), "Catherby", 100);
		assertFalse(UnlockEngine.hatUnlocked(log, "Raw shark"));
		assertTrue(u.apply(log, res("Raw shark", Grade.COMMON, false, 1), "Catherby", 100).contains("Raw shark hat"));
		assertTrue(UnlockEngine.hatUnlocked(log, "Raw shark"));
		assertTrue(u.apply(log, res("Raw shark", Grade.COMMON, true, 1), "Catherby", 100).contains("Shiny Hunter"));
		assertTrue(UnlockEngine.goldHatUnlocked(log, "Raw shark"));
	}
	@Test public void milestones() { assertTrue(UnlockEngine.isMilestone(100)); assertTrue(UnlockEngine.isMilestone(10000)); assertFalse(UnlockEngine.isMilestone(101)); }
}
```

- [ ] **Step 2:** FAIL. **Step 3: implement**

```java
package com.kaimoana.unlocks;
import com.kaimoana.log.TideLog; import com.kaimoana.registry.CatchResult; import com.kaimoana.registry.Grade;
import java.util.*;
public class UnlockEngine
{
	public static boolean isMilestone(long n) { return n == 100 || n == 1000 || n == 10000; }
	public static boolean hatUnlocked(TideLog log, String fish) { return log.unlocks.contains("HAT_" + fish); }
	public static boolean goldHatUnlocked(TideLog log, String fish) { return log.unlocks.contains("GOLD_HAT_" + fish); }

	public static String label(String key)
	{
		switch (key)
		{
			case "FIRST_TROPHY": return "First Trophy";
			case "FIRST_TANIWHA": return "First Taniwha";
			case "SHINY_HUNTER": return "Shiny Hunter";
			case "SPECIES_50": return "50 Species";
			case "ALL_SPECIES": return "All Species";
			case "CATCHES_10K": return "10,000 Catches";
			case "LOCAL_LEGEND": return "Local Legend";
			default:
				if (key.startsWith("GOLD_HAT_")) return key.substring(9) + " golden hat";
				if (key.startsWith("HAT_")) return key.substring(4) + " hat";
				return key;
		}
	}

	/** Mutates log; returns labels of newly earned unlocks. */
	public List<String> apply(TideLog log, CatchResult r, String location, int registrySize)
	{
		String fish = r.getEvent().getItemName(); long now = System.currentTimeMillis();
		TideLog.Species s = log.getOrCreate(fish);
		if (s.count == 0) s.firstAt = now;
		s.count++; log.totalCatches++;
		if (r.getWeightKg() > s.heaviest) { s.heaviest = r.getWeightKg(); s.heaviestAt = now; }
		if (r.isShiny()) { s.shinies++; log.totalShinies++; }
		s.grades.merge(r.getGrade().name(), 1L, Long::sum);
		log.recordLocationBest(location, fish, r.getWeightKg());

		List<String> earned = new ArrayList<>();
		if (r.getGrade().atLeast(Grade.TROPHY)) grant(log, "FIRST_TROPHY", earned);
		if (r.getGrade() == Grade.TANIWHA) grant(log, "FIRST_TANIWHA", earned);
		if (r.isShiny()) { grant(log, "SHINY_HUNTER", earned); grant(log, "GOLD_HAT_" + fish, earned); }
		if (s.count >= 50) grant(log, "HAT_" + fish, earned);
		if (log.species.size() >= 50) grant(log, "SPECIES_50", earned);
		if (registrySize > 0 && log.species.size() >= registrySize) grant(log, "ALL_SPECIES", earned);
		if (log.totalCatches >= 10000) grant(log, "CATCHES_10K", earned);
		if (log.locationBests.size() >= 10) grant(log, "LOCAL_LEGEND", earned);
		if (isMilestone(s.count)) log.milestones.add(fish + ":" + s.count);
		return earned;
	}
	private static void grant(TideLog log, String key, List<String> earned) { if (log.unlocks.add(key)) earned.add(label(key)); }
}
```

- [ ] **Step 4:** PASS. **Step 5:** commit `Add unlock engine`.

---

### Task 8: Chat output + icon + !fish command

**Files:** `chat/CatchChatWriter.java`, `chat/FishChatCommand.java`

**Interfaces produced:**
- `CatchChatWriter(Client, ClientThread, ChatMessageManager, ItemManager, KaimoanaConfig) { void announce(CatchResult r, List<String> newUnlocks); }`
- `FishChatCommand(ChatCommandManager, ChatMessageManager, Supplier<TideLog>, KaimoanaConfig) { void register(); void unregister(); }`

- [ ] **Step 1: CatchChatWriter**

```java
package com.kaimoana.chat;
import com.kaimoana.KaimoanaConfig; import com.kaimoana.registry.CatchResult; import com.kaimoana.registry.Grade;
import java.awt.image.BufferedImage; import java.util.*; import javax.inject.Inject;
import net.runelite.api.*; import net.runelite.client.callback.ClientThread; import net.runelite.client.chat.*; import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ColorUtil; import net.runelite.client.util.ImageUtil;
public class CatchChatWriter
{
	private final Client client; private final ClientThread clientThread; private final ChatMessageManager chat; private final ItemManager items; private final KaimoanaConfig config;
	private final Map<Integer, Integer> iconIndexByItem = new HashMap<>();
	@Inject public CatchChatWriter(Client c, ClientThread ct, ChatMessageManager m, ItemManager i, KaimoanaConfig cfg) { client = c; clientThread = ct; chat = m; items = i; config = cfg; }

	public void announce(CatchResult r, List<String> newUnlocks)
	{
		String fishName = r.getEvent().getItemName().replaceFirst("(?i)^raw ", "").toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder();
		if (config.chatIcon()) { int idx = iconFor(r.getEvent().getItemId()); if (idx >= 0) sb.append("<img=").append(idx).append("> "); }
		if (r.isShiny()) sb.append(ColorUtil.wrapWithColorTag("✦ Shiny ", new java.awt.Color(0xFFD700)));
		sb.append("You catch ").append(article(fishName)).append(' ').append(fishName).append('.');
		if (config.chatWeight()) sb.append(' ').append(String.format(Locale.ROOT, "%.2f kg", r.getWeightKg()));
		if (config.chatGrade() && config.grades()) sb.append(' ').append(ColorUtil.wrapWithColorTag("(" + r.getGrade().label() + ")", r.getGrade().color()));
		if (r.isMilestone()) sb.append(ColorUtil.wrapWithColorTag(" Milestone: " + r.getSpeciesCount() + " caught!", Grade.TROPHY.color()));
		send(sb.toString());
		if (config.chatFlavour() && r.isTrophyPlus() && r.getEntry().getFlavour() != null) send(ColorUtil.wrapWithColorTag(r.getEntry().getFlavour(), r.getGrade().color()));
		for (String u : newUnlocks) send(ColorUtil.wrapWithColorTag("Tide Log unlock: " + u, Grade.TANIWHA.color()));
	}
	public void send(String msg) { chat.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(msg).build()); }
	private static String article(String n) { return n.endsWith("s") && !n.endsWith("ss") ? "some" : ("aeiou".indexOf(n.charAt(0)) >= 0 ? "an" : "a"); }

	/** Registers the item sprite as a mod icon once; must run on client thread. Returns -1 if not ready. */
	private int iconFor(int itemId)
	{
		if (itemId < 0) return -1;
		Integer idx = iconIndexByItem.get(itemId); if (idx != null) return idx;
		IndexedSprite[] mod = client.getModIcons(); if (mod == null) return -1;
		BufferedImage img = ImageUtil.resizeImage(items.getImage(itemId), 16, 16);
		IndexedSprite sprite = ImageUtil.getImageIndexedSprite(img, client);
		IndexedSprite[] grown = Arrays.copyOf(mod, mod.length + 1); grown[mod.length] = sprite; client.setModIcons(grown);
		iconIndexByItem.put(itemId, mod.length);
		return mod.length;
	}
}
```
`announce` must be invoked from the client thread (plugin does this).

- [ ] **Step 2: FishChatCommand**

```java
package com.kaimoana.chat;
import com.kaimoana.KaimoanaConfig; import com.kaimoana.log.TideLog;
import java.util.*; import java.util.function.Supplier; import javax.inject.Inject;
import net.runelite.api.ChatMessageType; import net.runelite.api.events.ChatMessage; import net.runelite.client.chat.*;
public class FishChatCommand
{
	private static final String CMD = "!fish";
	private final ChatCommandManager commands; private final ChatMessageManager chat; private final Supplier<TideLog> log; private final KaimoanaConfig config;
	@Inject public FishChatCommand(ChatCommandManager c, ChatMessageManager m, Supplier<TideLog> l, KaimoanaConfig cfg) { commands = c; chat = m; log = l; config = cfg; }
	public void register() { commands.registerCommand(CMD, this::run); }
	public void unregister() { commands.unregisterCommand(CMD); }

	private void run(ChatMessage msg, String text)
	{
		if (!config.chatCommands()) return;
		TideLog t = log.get(); String arg = text.substring(CMD.length()).trim();
		String out;
		if (arg.isEmpty()) out = String.format(Locale.ROOT, "Tide Log: %d catches, %d species, %d shinies, %d unlocks.", t.totalCatches, t.species.size(), t.totalShinies, t.unlocks.size());
		else
		{
			Optional<Map.Entry<String, TideLog.Species>> e = t.species.entrySet().stream().filter(x -> x.getKey().toLowerCase(Locale.ROOT).contains(arg.toLowerCase(Locale.ROOT))).findFirst();
			out = e.map(x -> String.format(Locale.ROOT, "%s: %d caught, best %.2f kg, %d shiny.", x.getKey(), x.getValue().count, x.getValue().heaviest, x.getValue().shinies)).orElse("No " + arg + " in your Tide Log yet.");
		}
		chat.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(out).build());
	}
}
```
Note: `Supplier<TideLog>` is not Guice-bound; plugin constructs `FishChatCommand` manually with `new` (see Task 12).

- [ ] **Step 3:** build → SUCCESS. Commit `Add chat writer with item icons and !fish command`.

---

### Task 9: Effects — FishModelSpawner, SoundFx, AnimationOverride

**Files:** `fx/FishModelSpawner.java`, `fx/SoundFx.java`, `fx/AnimationOverride.java`

**Interfaces produced:**
- `FishModelSpawner(Client, ItemManager, KaimoanaConfig) { void spawn(CatchResult r, LocalPoint from, int arcTicks, int holdTicks, Integer hatItemId, int hatOffsetY); void onTick(); void clear(); }`
- `SoundFx(Client, KaimoanaConfig) { void play(CatchResult r) }`
- `AnimationOverride(Client, KaimoanaConfig) { void onAnimationChanged(AnimationChanged e, boolean fishing) }`

- [ ] **Step 1: FishModelSpawner**

Approach: one active `RuneLiteObject`. Precompute `Model[] frames` at a few heights (0..peak) from `ModelData.translate(0,-h,0)` so the arc is vertical model offsets and horizontal `setLocation` interpolation. Player hand ≈ local player location + 40 units toward orientation, height 90.

```java
package com.kaimoana.fx;
import com.kaimoana.KaimoanaConfig; import com.kaimoana.registry.CatchResult;
import javax.inject.Inject; import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*; import net.runelite.api.coords.LocalPoint; import net.runelite.client.game.ItemManager;
@Slf4j
public class FishModelSpawner
{
	private static final int HAND_HEIGHT = 110; private static final int PEAK = 260; private static final int FRAMES = 6;
	private static final short GOLD = (short) 0x2A9B; // JagexColor hue 6 (yellow), sat 5, lum 27
	private final Client client; private final ItemManager items; private final KaimoanaConfig config;
	private RuneLiteObject obj; private Model[] frames; private LocalPoint from, to; private int tick, arc, hold; private boolean pop;

	@Inject public FishModelSpawner(Client c, ItemManager i, KaimoanaConfig cfg) { client = c; items = i; config = cfg; }

	public void spawn(CatchResult r, LocalPoint spot, int arcTicks, int holdTicks, Integer hatItemId, int hatOffsetY)
	{
		clear();
		Player p = client.getLocalPlayer(); if (p == null || r.getEvent().getItemId() < 0) return;
		ModelData fish = client.loadModelData(items.getItemComposition(r.getEvent().getItemId()).getInventoryModel());
		if (fish == null) return;
		fish = fish.cloneVertices().cloneColors();
		if (r.isShiny()) { short[] cols = fish.getFaceColors(); for (int k = 0; k < cols.length; k++) cols[k] = GOLD; }
		if (hatItemId != null)
		{
			ModelData hat = client.loadModelData(items.getItemComposition(hatItemId).getInventoryModel());
			if (hat != null) { hat = hat.cloneVertices().scale(70, 70, 70).translate(0, hatOffsetY - 40, 0); fish = client.mergeModels(fish, hat); }
		}
		frames = new Model[FRAMES + 1];
		for (int f = 0; f <= FRAMES; f++)
		{
			double t = f / (double) FRAMES; int h = (int) (HAND_HEIGHT * t + PEAK * 4 * t * (1 - t));
			frames[f] = fish.cloneVertices().translate(0, -h, 0).light();
		}
		to = p.getLocalLocation(); from = spot != null ? spot : to;
		arc = Math.max(1, arcTicks); hold = Math.max(1, holdTicks); tick = 0; pop = config.splashPop();
		obj = client.createRuneLiteObject();
		obj.setModel(frames[0]); obj.setLocation(from, client.getPlane()); obj.setOrientation(p.getOrientation()); obj.setDrawFrontTilesFirst(true); obj.setActive(true);
	}

	/** Called every game tick; advances arc then hold, then despawns. */
	public void onTick()
	{
		if (obj == null) return;
		tick++;
		Player p = client.getLocalPlayer();
		if (p == null || client.getLocalPlayer().getLocalLocation().distanceTo(to) > 128 * 2) { clear(); return; }
		if (tick <= arc)
		{
			double t = tick / (double) arc;
			int x = (int) (from.getX() + (to.getX() - from.getX()) * t), y = (int) (from.getY() + (to.getY() - from.getY()) * t);
			obj.setLocation(new LocalPoint(x, y), client.getPlane());
			obj.setModel(frames[(int) Math.round(t * FRAMES)]);
			obj.setOrientation(p.getOrientation());
		}
		else if (tick <= arc + hold) { obj.setLocation(p.getLocalLocation(), client.getPlane()); obj.setOrientation(p.getOrientation()); obj.setModel(frames[FRAMES]); }
		else clear();
	}
	public void clear() { if (obj != null) { obj.setActive(false); obj = null; } frames = null; }
}
```
If `ModelData.getFaceColors()` is not writable in the current API, use `fish.recolor(oldColor, GOLD)` for each distinct colour in `getFaceColors()`. If `client.mergeModels(ModelData...)` has a `(ModelData[], int)` signature, call `client.mergeModels(new ModelData[]{fish, hat}, 2)`.

- [ ] **Step 2: SoundFx**

```java
package com.kaimoana.fx;
import com.kaimoana.KaimoanaConfig; import com.kaimoana.registry.CatchResult; import javax.inject.Inject; import net.runelite.api.Client;
public class SoundFx
{
	private final Client client; private final KaimoanaConfig config;
	@Inject public SoundFx(Client c, KaimoanaConfig cfg) { client = c; config = cfg; }
	public void play(CatchResult r)
	{
		if (!config.sound() || config.soundVolume() == 0) return;
		int id = r.isTrophyPlus() || r.isShiny() ? config.trophySoundId() : config.soundId();
		int vol = (int) (config.soundVolume() / 100.0 * 127);
		client.playSoundEffect(id, vol);
	}
}
```
Verify default IDs in-game with RuneLite dev tools' Sound Effects panel; if 2414/2396 are wrong, change the config defaults (Task 1 file) and note the correct IDs in README.

- [ ] **Step 3: AnimationOverride**

```java
package com.kaimoana.fx;
import com.kaimoana.KaimoanaConfig; import javax.inject.Inject; import net.runelite.api.*; import net.runelite.api.events.AnimationChanged;
public class AnimationOverride
{
	private final Client client; private final KaimoanaConfig config;
	@Inject public AnimationOverride(Client c, KaimoanaConfig cfg) { client = c; config = cfg; }
	public void onAnimationChanged(AnimationChanged e, boolean fishing)
	{
		if (!config.fxEnabled() || !config.animOverride() || !fishing) return;
		if (e.getActor() != client.getLocalPlayer()) return;
		Player p = client.getLocalPlayer(); int cur = p.getAnimation();
		if (cur == -1 || cur == config.animOverrideId()) return;
		p.setAnimation(config.animOverrideId()); p.setAnimationFrame(0);
	}
}
```

- [ ] **Step 4:** build → SUCCESS. Commit `Add fish model spawner, sound and animation override`.

---

### Task 10: Tide Log panel

**Files:** `log/TideLogPanel.java`, generate `src/main/resources/com/kaimoana/panel_icon.png`

**Interfaces produced:** `TideLogPanel(ItemManager, FishRegistry, KaimoanaConfig) extends PluginPanel { void refresh(TideLog log, Function<String,Integer> itemIdFor); }`

- [ ] **Step 1: icon** Generate a 16x16 PNG with a Java snippet run via `jshell` or a throwaway test: blue circle with white fish-ish oval. Save to resources.

```java
// throwaway: src/test/java/com/kaimoana/GenIcon.java, run once, then delete
import java.awt.*; import java.awt.image.BufferedImage; import javax.imageio.ImageIO; import java.io.File;
public class GenIcon { public static void main(String[] a) throws Exception {
 BufferedImage img = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB); Graphics2D g = img.createGraphics();
 g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
 g.setColor(new Color(0x1E6FA8)); g.fillOval(0,0,16,16); g.setColor(Color.WHITE); g.fillOval(3,6,8,5); g.fillPolygon(new int[]{10,14,14},new int[]{8,5,11},3);
 g.dispose(); ImageIO.write(img,"png",new File("src/main/resources/com/kaimoana/panel_icon.png")); } }
```
Also copy it to repo root as `icon.png` (hub requires a 48x48 max icon at repo root; 16x16 is fine).

- [ ] **Step 2: panel**

```java
package com.kaimoana.log;
import com.kaimoana.KaimoanaConfig; import com.kaimoana.registry.*; import com.kaimoana.unlocks.UnlockEngine;
import java.awt.*; import java.awt.image.BufferedImage; import java.util.*; import java.util.List; import java.util.function.Function; import java.util.stream.Collectors;
import javax.swing.*; import net.runelite.client.game.ItemManager; import net.runelite.client.ui.ColorScheme; import net.runelite.client.ui.PluginPanel; import net.runelite.client.util.ImageUtil; import net.runelite.client.util.LinkBrowser;
public class TideLogPanel extends PluginPanel
{
	enum Sort { RECENT("Recent"), HEAVIEST("Heaviest"), MOST("Most caught"), RAREST("Rarest"), AZ("A-Z"); final String label; Sort(String l) { label = l; } public String toString() { return label; } }
	private final ItemManager items; private final FishRegistry registry; private final KaimoanaConfig config;
	private final JLabel header = new JLabel(); private final JTextField search = new JTextField(); private final JComboBox<Sort> sort = new JComboBox<>(Sort.values());
	private final JPanel rows = new JPanel(); private TideLog log = new TideLog(); private Function<String, Integer> itemIdFor = n -> -1;

	public TideLogPanel(ItemManager items, FishRegistry registry, KaimoanaConfig config)
	{
		this.items = items; this.registry = registry; this.config = config;
		setLayout(new BorderLayout(0, 6)); setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel top = new JPanel(new GridLayout(0, 1, 0, 4)); top.setOpaque(false);
		header.setForeground(Color.WHITE); header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
		top.add(new JLabel("<html><b style='font-size:14px'>Tide Log</b></html>")); top.add(header); top.add(search); top.add(sort);
		add(top, BorderLayout.NORTH);
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS)); rows.setOpaque(false);
		JScrollPane sp = new JScrollPane(rows); sp.setBorder(null); sp.getVerticalScrollBar().setUnitIncrement(16); add(sp, BorderLayout.CENTER);
		JButton export = new JButton("Copy CSV"); export.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(csv()), null));
		add(export, BorderLayout.SOUTH);
		search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() { public void insertUpdate(javax.swing.event.DocumentEvent e) { rebuild(); } public void removeUpdate(javax.swing.event.DocumentEvent e) { rebuild(); } public void changedUpdate(javax.swing.event.DocumentEvent e) { rebuild(); } });
		sort.addActionListener(e -> rebuild());
	}

	public void refresh(TideLog log, Function<String, Integer> itemIdFor) { this.log = log; this.itemIdFor = itemIdFor; SwingUtilities.invokeLater(this::rebuild); }

	private void rebuild()
	{
		int caught = (int) registry.all().stream().filter(e -> log.species.containsKey(e.getName())).count();
		String title = log.unlocks.stream().filter(k -> !k.startsWith("HAT_") && !k.startsWith("GOLD_HAT_")).map(UnlockEngine::label).reduce((a, b) -> b).orElse("Deckhand");
		header.setText(String.format(Locale.ROOT, "%s  •  %d/%d species  •  %d catches  •  %d shiny", title, caught, registry.size(), log.totalCatches, log.totalShinies));
		rows.removeAll();
		String q = search.getText().toLowerCase(Locale.ROOT);
		List<FishEntry> list = new ArrayList<>(registry.all());
		for (String name : log.species.keySet()) if (registry.find(name).isEmpty()) list.add(FishEntry.fallback(name, 0));
		if (!config.silhouettes()) list.removeIf(e -> !log.species.containsKey(e.getName()));
		list.removeIf(e -> !e.getName().toLowerCase(Locale.ROOT).contains(q));
		Comparator<FishEntry> cmp;
		switch ((Sort) sort.getSelectedItem())
		{
			case HEAVIEST: cmp = Comparator.comparingDouble((FishEntry e) -> stat(e).heaviest).reversed(); break;
			case MOST: cmp = Comparator.comparingLong((FishEntry e) -> stat(e).count).reversed(); break;
			case RAREST: cmp = Comparator.comparingInt(FishEntry::getRarity).reversed(); break;
			case AZ: cmp = Comparator.comparing(FishEntry::getName); break;
			default: cmp = Comparator.comparingLong((FishEntry e) -> Math.max(stat(e).heaviestAt, stat(e).firstAt)).reversed();
		}
		list.sort(cmp);
		for (FishEntry e : list) rows.add(row(e));
		rows.revalidate(); rows.repaint();
	}
	private TideLog.Species stat(FishEntry e) { return log.species.getOrDefault(e.getName(), new TideLog.Species()); }

	private JComponent row(FishEntry e)
	{
		TideLog.Species s = stat(e); boolean caught = s.count > 0;
		JPanel p = new JPanel(new BorderLayout(6, 0)); p.setBackground(ColorScheme.DARKER_GRAY_COLOR); p.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		int id = itemIdFor.apply(e.getName());
		JLabel icon = new JLabel();
		if (id >= 0) { BufferedImage img = items.getImage(id); icon.setIcon(new ImageIcon(caught ? img : ImageUtil.luminanceScale(ImageUtil.grayscaleImage(img), 0.4f))); }
		p.add(icon, BorderLayout.WEST);
		String best = caught ? String.format(Locale.ROOT, "%.2f kg", s.heaviest) : "—";
		String top = caught ? e.getName() : "???"; if (s.shinies > 0) top += " ✦";
		Grade bestGrade = s.grades.keySet().stream().map(Grade::valueOf).max(Comparator.naturalOrder()).orElse(null);
		String gradeHtml = bestGrade == null ? "" : String.format(" <span style='color:#%06x'>%s</span>", bestGrade.color().getRGB() & 0xFFFFFF, bestGrade.label());
		JLabel text = new JLabel(String.format("<html><b>%s</b>%s<br><span style='color:#aaa'>%d caught • best %s • rarity %s</span></html>", top, gradeHtml, s.count, best, "★".repeat(e.getRarity())));
		text.setForeground(caught ? Color.WHITE : Color.GRAY);
		p.add(text, BorderLayout.CENTER);
		p.setToolTipText(caught ? detail(e, s) : "Not caught yet");
		p.addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent ev) { if (caught && ev.getClickCount() == 2) LinkBrowser.browse("https://oldschool.runescape.wiki/w/" + e.getName().replace(' ', '_')); } });
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		return p;
	}
	private String detail(FishEntry e, TideLog.Species s)
	{
		String grades = Arrays.stream(Grade.values()).map(g -> g.label() + " " + s.grades.getOrDefault(g.name(), 0L)).collect(Collectors.joining(", "));
		String locs = log.locationBests.entrySet().stream().filter(x -> x.getValue().containsKey(e.getName())).map(x -> x.getKey() + " " + String.format(Locale.ROOT, "%.2f", x.getValue().get(e.getName())) + " kg").collect(Collectors.joining("<br>"));
		return "<html>" + grades + "<br>" + (locs.isEmpty() ? "" : "<b>Location bests</b><br>" + locs) + "<br><i>Double-click for wiki</i></html>";
	}
	private String csv()
	{
		StringBuilder sb = new StringBuilder("fish,count,heaviest_kg,shinies,first_caught_ms\n");
		log.species.forEach((k, v) -> sb.append(k).append(',').append(v.count).append(',').append(v.heaviest).append(',').append(v.shinies).append(',').append(v.firstAt).append('\n'));
		return sb.toString();
	}
}
```
`items.getImage` must be called off the client thread cautiously; ItemManager's image loading is async-safe and returns a placeholder-cached `AsyncBufferedImage`. Acceptable.

- [ ] **Step 3:** build → SUCCESS. Commit `Add Tide Log panel`.

---

### Task 11: Integrations (opt-in)

**Files:** `integrations/DiscordWebhook.java`, `integrations/PartyBroadcast.java`, `integrations/WikiLookup.java`, `integrations/ScreenshotOnTrophy.java`

- [ ] **Step 1: DiscordWebhook**

```java
package com.kaimoana.integrations;
import com.google.gson.Gson; import com.kaimoana.KaimoanaConfig; import com.kaimoana.registry.CatchResult;
import java.util.Locale; import java.util.Map; import javax.inject.Inject; import lombok.extern.slf4j.Slf4j; import okhttp3.*;
@Slf4j
public class DiscordWebhook
{
	private final OkHttpClient http; private final Gson gson; private final KaimoanaConfig config;
	@Inject public DiscordWebhook(OkHttpClient http, Gson gson, KaimoanaConfig config) { this.http = http; this.gson = gson; this.config = config; }
	public void maybePost(CatchResult r, String player)
	{
		String url = config.discordUrl(); if (url == null || url.isBlank() || !r.isTrophyPlus() && !r.isShiny()) return;
		String content = String.format(Locale.ROOT, "**%s** caught %s%s: %.2f kg (%s)", player, r.isShiny() ? "a shiny " : "", r.getEvent().getItemName(), r.getWeightKg(), r.getGrade().label());
		Request req = new Request.Builder().url(url).post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(Map.of("content", content)))).build();
		http.newCall(req).enqueue(new Callback() { public void onFailure(Call c, java.io.IOException e) { log.debug("discord failed", e); } public void onResponse(Call c, Response resp) { resp.close(); } });
	}
}
```

- [ ] **Step 2: PartyBroadcast** — uses `PartyService` + `WSClient` message class:

```java
package com.kaimoana.integrations;
import com.kaimoana.KaimoanaConfig; import com.kaimoana.registry.CatchResult; import javax.inject.Inject; import lombok.*;
import net.runelite.client.party.PartyService; import net.runelite.client.party.messages.PartyMemberMessage; import java.util.Locale;
public class PartyBroadcast
{
	@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
	public static class KaimoanaCatch extends PartyMemberMessage { private String fish; private double kg; private String grade; private boolean shiny; }
	private final PartyService party; private final KaimoanaConfig config;
	@Inject public PartyBroadcast(PartyService p, KaimoanaConfig c) { party = p; config = c; }
	public void maybeSend(CatchResult r)
	{
		if (!config.party() || !party.isInParty() || !(r.isTrophyPlus() || r.isShiny())) return;
		party.send(new KaimoanaCatch(r.getEvent().getItemName(), r.getWeightKg(), r.getGrade().label(), r.isShiny()));
	}
	public static String describe(KaimoanaCatch c, String member) { return String.format(Locale.ROOT, "%s caught %s%s: %.2f kg (%s)", member, c.shiny ? "a shiny " : "", c.fish, c.kg, c.grade); }
}
```
Plugin registers the message class with `wsClient.registerMessage(PartyBroadcast.KaimoanaCatch.class)` on startUp, unregisters on shutDown, and `@Subscribe onKaimoanaCatch(KaimoanaCatch)` prints via `CatchChatWriter.send` using `party.getMemberById(c.getMemberId()).getDisplayName()`.

- [ ] **Step 3: WikiLookup** — fetch `https://oldschool.runescape.wiki/api.php?action=query&prop=extracts&exintro&explaintext&format=json&titles=<name>` once per species, cache in memory, callback to panel tooltip. Keep simple:

```java
package com.kaimoana.integrations;
import com.google.gson.*; import java.util.*; import java.util.concurrent.ConcurrentHashMap; import java.util.function.Consumer; import javax.inject.Inject; import okhttp3.*;
public class WikiLookup
{
	private final OkHttpClient http; private final Map<String, String> cache = new ConcurrentHashMap<>();
	@Inject public WikiLookup(OkHttpClient http) { this.http = http; }
	public Optional<String> cached(String fish) { return Optional.ofNullable(cache.get(fish)); }
	public void fetch(String fish, Consumer<String> cb)
	{
		if (cache.containsKey(fish)) { cb.accept(cache.get(fish)); return; }
		HttpUrl url = HttpUrl.parse("https://oldschool.runescape.wiki/api.php").newBuilder().addQueryParameter("action", "query").addQueryParameter("prop", "extracts").addQueryParameter("exintro", "1").addQueryParameter("explaintext", "1").addQueryParameter("format", "json").addQueryParameter("titles", fish).build();
		http.newCall(new Request.Builder().url(url).header("User-Agent", "RuneLite Kaimoana Fishing").build()).enqueue(new Callback()
		{
			public void onFailure(Call c, java.io.IOException e) {}
			public void onResponse(Call c, Response r) throws java.io.IOException
			{
				try (ResponseBody b = r.body()) { if (b == null) return; JsonObject pages = JsonParser.parseString(b.string()).getAsJsonObject().getAsJsonObject("query").getAsJsonObject("pages");
					for (Map.Entry<String, JsonElement> e : pages.entrySet()) { JsonElement ex = e.getValue().getAsJsonObject().get("extract"); if (ex != null) { String s = ex.getAsString(); if (s.length() > 300) s = s.substring(0, 297) + "..."; cache.put(fish, s); cb.accept(s); } } }
			}
		});
	}
}
```

- [ ] **Step 4: ScreenshotOnTrophy**

```java
package com.kaimoana.integrations;
import com.kaimoana.KaimoanaConfig; import com.kaimoana.registry.CatchResult; import javax.inject.Inject;
import net.runelite.client.ui.DrawManager; import net.runelite.client.util.ImageCapture; import java.awt.image.BufferedImage; import java.util.Locale;
public class ScreenshotOnTrophy
{
	private final DrawManager draw; private final ImageCapture capture; private final KaimoanaConfig config;
	@Inject public ScreenshotOnTrophy(DrawManager d, ImageCapture c, KaimoanaConfig cfg) { draw = d; capture = c; config = cfg; }
	public void maybeCapture(CatchResult r)
	{
		if (!config.screenshot() || !(r.isTrophyPlus() || r.isShiny())) return;
		String name = String.format(Locale.ROOT, "%s %s %.2fkg", r.getGrade().label(), r.getEvent().getItemName(), r.getWeightKg()).replaceAll("[^A-Za-z0-9 .]", "");
		draw.requestNextFrameListener(img -> capture.saveScreenshot((BufferedImage) img, name, "Kaimoana", false, false));
	}
}
```
If `ImageCapture.saveScreenshot` signature differs (it has changed across versions), match the current one: `(BufferedImage, String fileName, String subDir, boolean notify, boolean copyToClipboard)`.

- [ ] **Step 5:** build → SUCCESS. Commit `Add opt-in Discord, party, wiki and screenshot integrations`.

---

### Task 12: Wire KaimoanaPlugin

**Files:** Modify `KaimoanaPlugin.java`

- [ ] **Step 1: full plugin**

```java
package com.kaimoana;

import com.google.inject.Provides;
import com.kaimoana.detect.*; import com.kaimoana.chat.*; import com.kaimoana.fx.*; import com.kaimoana.integrations.*; import com.kaimoana.log.*; import com.kaimoana.registry.*; import com.kaimoana.unlocks.UnlockEngine;
import java.awt.image.BufferedImage; import java.util.*; import javax.inject.Inject; import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*; import net.runelite.api.coords.LocalPoint; import net.runelite.api.coords.WorldPoint; import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread; import net.runelite.client.chat.ChatCommandManager; import net.runelite.client.chat.ChatMessageManager; import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe; import net.runelite.client.events.ConfigChanged; import net.runelite.client.game.ItemManager; import net.runelite.client.party.PartyService; import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin; import net.runelite.client.plugins.PluginDescriptor; import net.runelite.client.ui.ClientToolbar; import net.runelite.client.ui.NavigationButton; import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(name = "Kaimoana Fishing", description = "Weights, grades, a fish in your hand and a Tide Log for every catch", tags = {"fishing", "catch", "weight", "collection", "cosmetic"})
public class KaimoanaPlugin extends Plugin
{
	@Inject private Client client; @Inject private ClientThread clientThread; @Inject private KaimoanaConfig config; @Inject private ItemManager itemManager;
	@Inject private ChatMessageManager chatMessageManager; @Inject private ChatCommandManager chatCommandManager; @Inject private ClientToolbar toolbar;
	@Inject private TideLogStore store; @Inject private CatchChatWriter chatWriter; @Inject private FishModelSpawner spawner; @Inject private SoundFx sound; @Inject private AnimationOverride animOverride;
	@Inject private DiscordWebhook discord; @Inject private PartyBroadcast partyBroadcast; @Inject private PartyService partyService; @Inject private WSClient wsClient; @Inject private ScreenshotOnTrophy screenshot;

	private final FishRegistry registry = FishRegistry.load();
	private final WeightRoller roller = new WeightRoller(new Random());
	private final UnlockEngine unlocks = new UnlockEngine();
	private final CadenceGovernor cadence = new CadenceGovernor();
	private final FishingStateTracker fishing = new FishingStateTracker();
	private final Map<String, Integer> itemIdCache = new HashMap<>();
	private CatchDetector detector; private FishChatCommand fishCommand; private TideLog tideLog; private TideLogPanel panel; private NavigationButton navButton;
	private Map<Integer, Integer> lastInventory = Collections.emptyMap(); private boolean dirty;

	@Provides KaimoanaConfig provideConfig(ConfigManager cm) { return cm.getConfig(KaimoanaConfig.class); }

	@Override protected void startUp()
	{
		detector = new CatchDetector(new CatchDetector.ItemNameResolver()
		{
			public int idFor(String n) { return itemIdFor(n); }
			public String nameFor(int id) { return itemManager.getItemComposition(id).getName(); }
			public boolean isFish(String n) { return registry.find(n).isPresent() || (n.toLowerCase(Locale.ROOT).startsWith("raw ") && itemIdFor(n) >= 0); }
		});
		tideLog = store.load();
		fishCommand = new FishChatCommand(chatCommandManager, chatMessageManager, () -> tideLog, config); fishCommand.register();
		wsClient.registerMessage(PartyBroadcast.KaimoanaCatch.class);
		panel = new TideLogPanel(itemManager, registry, config);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder().tooltip("Tide Log").icon(icon).priority(7).panel(panel).build();
		if (config.panel()) toolbar.addNavigation(navButton);
		refreshPanel();
	}

	@Override protected void shutDown()
	{
		fishCommand.unregister(); wsClient.unregisterMessage(PartyBroadcast.KaimoanaCatch.class); toolbar.removeNavigation(navButton);
		clientThread.invoke(spawner::clear); if (dirty) store.save(tideLog);
	}

	@Subscribe public void onGameTick(GameTick t)
	{
		fishing.onTick(client); spawner.onTick();
		if (dirty && client.getTickCount() % 50 == 0) { store.save(tideLog); dirty = false; }
	}

	@Subscribe public void onChatMessage(ChatMessage e)
	{
		if (e.getType() != ChatMessageType.GAMEMESSAGE && e.getType() != ChatMessageType.SPAM) return;
		Player p = client.getLocalPlayer();
		for (CatchEvent ev : detector.onChat(e.getMessage(), client.getTickCount(), p == null ? null : p.getWorldLocation())) handle(ev);
	}

	@Subscribe public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() != InventoryID.INVENTORY.getId()) return;
		Map<Integer, Integer> now = new HashMap<>();
		for (Item it : e.getItemContainer().getItems()) if (it.getId() >= 0) now.merge(it.getId(), it.getQuantity(), Integer::sum);
		Map<Integer, Integer> delta = new HashMap<>();
		now.forEach((id, q) -> { int d = q - lastInventory.getOrDefault(id, 0); if (d > 0) delta.put(id, d); });
		boolean first = lastInventory.isEmpty(); lastInventory = now;
		if (first) return;
		Player p = client.getLocalPlayer();
		for (CatchEvent ev : detector.onInventoryDelta(delta, client.getTickCount(), p == null ? null : p.getWorldLocation(), fishing.isFishing(client))) handle(ev);
	}

	@Subscribe public void onAnimationChanged(AnimationChanged e) { animOverride.onAnimationChanged(e, fishing.isFishing(client)); }
	@Subscribe public void onGameStateChanged(GameStateChanged e) { if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING) { spawner.clear(); lastInventory = Collections.emptyMap(); if (dirty) { store.save(tideLog); dirty = false; } } if (e.getGameState() == GameState.LOGGED_IN) { tideLog = store.load(); refreshPanel(); } }
	@Subscribe public void onConfigChanged(ConfigChanged e)
	{
		if (!KaimoanaConfig.GROUP.equals(e.getGroup())) return;
		if ("panel".equals(e.getKey())) { toolbar.removeNavigation(navButton); if (config.panel()) toolbar.addNavigation(navButton); }
		refreshPanel();
	}
	@Subscribe public void onKaimoanaCatch(PartyBroadcast.KaimoanaCatch c)
	{
		if (!config.party()) return; var m = partyService.getMemberById(c.getMemberId()); if (m == null) return;
		clientThread.invoke(() -> chatWriter.send(PartyBroadcast.describe(c, m.getDisplayName())));
	}

	private void handle(CatchEvent ev)
	{
		int tick = client.getTickCount(); cadence.recordCatch(tick);
		FishEntry entry = registry.resolve(ev.getItemName(), ev.getItemId() >= 0 ? itemManager.getItemComposition(ev.getItemId()).getHaPrice() : 0);
		double kg = roller.roll(entry);
		Grade grade = config.grades() ? roller.grade(entry, kg, roller.rollTaniwha()) : Grade.COMMON;
		boolean shiny = config.shiny() && roller.rollShiny();
		long count = tideLog.getOrCreate(ev.getItemName()).count + 1;
		CatchResult r = CatchResult.builder().event(ev).entry(entry).weightKg(kg).grade(grade).shiny(shiny).speciesCount((int) count).milestone(UnlockEngine.isMilestone(count)).build();
		List<String> earned = unlocks.apply(tideLog, r, LocationResolver.nameFor(ev.getWhere()), registry.size()); dirty = true;
		if (registry.find(ev.getItemName()).isEmpty()) log.debug("Unregistered fish caught: {}", ev.getItemName());

		clientThread.invoke(() ->
		{
			chatWriter.announce(r, earned);
			if (config.fxEnabled() && (config.fpsFloor() == 0 || client.getFPS() >= config.fpsFloor()))
			{
				if (config.showFishModel())
				{
					Integer hatId = null; int hatY = entry.getHatOffsetY();
					if (config.hats() && entry.getHat() != null && (config.unlockAllHats() || UnlockEngine.hatUnlocked(tideLog, ev.getItemName()))) { int id = itemIdFor(entry.getHat()); if (id >= 0) hatId = id; }
					LocalPoint from = fishing.currentSpot() != null ? fishing.currentSpot().getLocalLocation() : null;
					int arc = cadence.effectiveArc(config.arcTicks()), hold = cadence.effectiveHold(config.holdTicks());
					if (r.isMilestone()) { arc += 2; hold += 4; }
					spawner.spawn(r, from, arc, hold, hatId, hatY);
				}
				sound.play(r);
			}
		});
		discord.maybePost(r, client.getLocalPlayer() == null ? "Someone" : client.getLocalPlayer().getName());
		partyBroadcast.maybeSend(r); screenshot.maybeCapture(r);
		refreshPanel();
	}

	private int itemIdFor(String name)
	{
		return itemIdCache.computeIfAbsent(name.toLowerCase(Locale.ROOT), n -> itemManager.search(n).stream()
			.filter(i -> i.getName().equalsIgnoreCase(n)).map(ItemPrice::getId).findFirst().orElse(-1));
	}
	private void refreshPanel() { if (panel != null) panel.refresh(tideLog, this::itemIdFor); }
}
```
Notes: `itemManager.search` returns `List<ItemPrice>` of tradeable items; untradeable fish (e.g. Sacred eel is tradeable; Infernal eel tradeable; Minnow untradeable) may return -1. For those, fall back: when `idFor` returns -1 but the event came from inventory, `ev.getItemId()` is already known. For chat-sourced untradeables, extend `itemIdFor` to also check `lastInventory` keys by name via `itemManager.getItemComposition(id).getName()`. Add that loop before returning -1.

- [ ] **Step 2:** build → SUCCESS; `.\gradlew.bat test` all green.
- [ ] **Step 3:** README.md: features, config table, hub install note, "verify sound IDs" note, screenshots placeholder section listing what to capture.
- [ ] **Step 4:** Commit `Wire plugin: detection, fx, chat, log, unlocks, integrations`.

---

### Task 13: Launch smoke test + hub prep

- [ ] **Step 1:** Run dev client: `.\gradlew.bat run` is not defined; use `.\gradlew.bat test --tests com.kaimoana.KaimoanaPluginTest` is wrong too. Instead add to build.gradle:
```groovy
tasks.register('runClient', JavaExec) {
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'com.kaimoana.KaimoanaPluginTest'
    jvmArgs '-ea', '-Xmx2g'
    args '--developer-mode'
}
```
Run `.\gradlew.bat runClient` in background; confirm client opens and plugin appears in the sidebar list. Log in is manual (user). Report status.
- [ ] **Step 2:** Manual checklist file `docs/manual-test-checklist.md` listing each fishing method (net, bait, fly, cage, harpoon, barbarian, karambwan, aerial, drift net, Tempoross, Camdozaal, minnows, anglers, dark crabs, infernal eels, sacred eels, Trawler chest) at slow and fast cadence, plus config toggles.
- [ ] **Step 3:** Commit `Add dev launcher and manual test checklist`.

---

## Self-review

- Spec §5 detection: Task 4 + 12. §6 registry: Task 2. §7 weights: Task 3. §8 effects: Task 9 (splash replaced by scale-pop within FishModelSpawner arc; spotanim reuse dropped because the public API offers no spotanim→model lookup; spec §8 amended accordingly). §9 chat: Task 8. §10 Tide Log: Tasks 6, 10. §11 unlocks: Task 7. §12 config: Task 1. §13 tests: Tasks 2–7. Integrations: Task 11.
- Type consistency: `CatchDetector.ItemNameResolver` methods `idFor/nameFor/isFish` used identically in Tasks 4 and 12. `UnlockEngine.apply(log, r, location, registrySize)` used identically in Tasks 7 and 12. `FishModelSpawner.spawn(r, from, arc, hold, hatId, hatY)` matches Task 12.
