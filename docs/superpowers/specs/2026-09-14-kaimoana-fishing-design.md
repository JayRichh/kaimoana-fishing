# Kaimoana Fishing — RuneLite Plugin Design

Date: 2026-09-14. Target: RuneLite Plugin Hub from day one.

## 1. Goal

A cosmetic fishing companion for OSRS via RuneLite. Every catch gets a weight and grade, a visible
fish model that jumps from the water to the player's hand, splash + sound, an inline chat line with
the fish icon, and a permanent per-profile catch log ("Tide Log") with rare unlocks. Zero gameplay
advantage. Everything toggleable. Works for every fish in the game, including ones released after
this plugin ships, by resolving fish by *name* from the live item cache rather than hardcoded IDs.

## 2. Constraints (hub compliance)

- Public RuneLite API only. No reflection, no mixins, no client-state mutation beyond
  `RuneLiteObject`, chat message injection, sound playback and the player's own animation override.
- Java 11, gradle, based on `runelite/example-plugin` template. `runelite-plugin.properties` +
  `plugin.json` per hub spec.
- No custom models/textures/audio assets. Only reuse of existing cache models, spotanims and sounds.
- No network calls except: OSRS Wiki lookup (opt-in, cached) and Discord webhook (opt-in).
- Never write to game chat that others see. Injected messages are client-local `GAMEMESSAGE`.

## 3. Naming

- Plugin display name: **Kaimoana Fishing**. Tags: fishing, catch, weight, collection, cosmetic.
- Side panel: **Tide Log**.
- Grade tiers (ascending): Common, Fine, Prime, Trophy, Big One, **Taniwha**.
- Package: `com.kaimoana`.

## 4. Architecture

```
KaimoanaPlugin              @PluginDescriptor, wires everything, subscribes to events
KaimoanaConfig              @ConfigGroup("kaimoana"), sectioned
catch/
  CatchDetector             ChatMessage regex + ItemContainerChanged diff -> CatchEvent
  CatchEvent                (fishName, itemId, tick, worldPoint, source: CHAT|INVENTORY)
  FishingStateTracker       are we fishing? (animation IDs + equipped tool + interacting Fishing spot NPC)
registry/
  FishRegistry              loads fish.json; lookup by lowercase name; default entry fallback
  FishEntry                 name, weightMin, weightMax, rarityTier, hatItemName, trophyKg, flavour
  WeightRoller              log-normal roll in [min,max]; grade assignment; shiny + taniwha rolls
  Grade                     enum with colour, label
fx/
  FishModelSpawner          RuneLiteObject: spawn fish item model at spot tile, arc to player hand,
                            hold N ticks, despawn; merges hat ModelData if unlocked+enabled;
                            recolour for shiny
  SplashFx                  spotanim RuneLiteObject at spot tile
  SoundFx                   client.playSoundEffect(splashId, volume)
  AnimationOverride         optional player fishing animation swap
  CadenceGovernor           tracks inter-catch tick gaps; clamps hold duration so effects never
                            overlap (level 99 spam) and never feel truncated (level 3)
chat/
  CatchChatWriter           "<img=N> You catch a trout. 1.34 kg (Fine)" + tier colour + flavour on Trophy+
  FishChatCommand           !fish, !fish <name> (self-only display)
log/
  TideLogStore              per-RS-profile JSON via ConfigManager (RSProfile scope); catches,
                            species stats, unlocks, location bests
  TideLogPanel              PluginPanel: header (titles, completion %), sortable grid, detail view
  LocationResolver          WorldPoint -> named fishing location (region table)
unlocks/
  UnlockEngine              titles, hat unlocks, milestones; evaluated on each CatchEvent
integrations/
  DiscordWebhook            Trophy+ POST (opt-in)
  PartyBroadcast            RuneLite Party plugin message for Trophy+ (opt-in)
  WikiLookup                fetch+cache per species facts (opt-in)
  ScreenshotOnTrophy        ImageCapture API (opt-in)
resources/com/kaimoana/fish.json
```

## 5. Catch detection

Primary: `ChatMessage` type GAMEMESSAGE/SPAM matching
`^You catch (?:a|an|some) (?<name>[a-z ]+?)[.!]` (case-insensitive) and variants:
`You catch (\d+) (.+)` (Trawler/Karambwan multi), `You manage to catch`, `You get some`.
Secondary: `ItemContainerChanged` on INVENTORY while `FishingStateTracker.isFishing()`; delta items
whose name resolves in `FishRegistry` or whose item name contains a known fish token become
`CatchEvent(source=INVENTORY)`. Dedup: an INVENTORY event within 1 tick of a CHAT event for the same
item is dropped.

Minigame coverage: Trawler (reward chest — inventory-only, no fx), Tempoross (inventory, fx
enabled), Drift Net (chat), Aerial fishing (chat), Barbarian (chat), Karambwan (chat), Camdozaal
(chat), Kingdom/Managing Miscellania (excluded), any new area works via the same paths.

Fish name -> item: `ItemManager.search(name)` then prefer exact name match, non-noted, non-placeholder.
Cached per name for the session.

## 6. Fish registry

`fish.json` array of `FishEntry`. Ships with every catchable fish as of 2026-09 (incl. recent
additions). Lookup is by lowercase name. Missing entry -> `FishEntry.defaultFor(itemName, haPrice)`:
weight range scaled from High Alch value bands, tier Common, no hat, generic flavour.
Unknown fish are logged at debug so the registry can be extended by a one-line JSON PR.

## 7. Weight and grades

- Weight: log-normal with median at 25th percentile of [min,max], clamped. 2 decimals.
- Grade thresholds by percentile within species range: Common <60, Fine <85, Prime <95,
  Trophy <99, Big One <99.8, Taniwha >=99.8 (independent 1/1000 roll also promotes to Taniwha).
- Shiny: independent 1/512 roll. Shiny recolours the model gold and adds a badge in Tide Log.
  Shiny and Taniwha can stack.
- Milestone: 100th, 1000th, 10000th of a species -> extended arc + hold, once each.

## 8. Effects

- Fish model: `client.loadModel(itemComposition.getInventoryModel())` -> `RuneLiteObject`.
  Spawn at fishing-spot tile (interacting NPC location), 6-tick arc (configurable 3–12) to the
  player's local point with hand offset, then hold H ticks. Rotate to face camera-ish (player
  orientation + 90). Despawn on hold end, player move away >1 tile, logout, or region change.
- Hold H: config default 4 ticks (1–20). `CadenceGovernor` computes rolling median inter-catch
  gap; effective hold = min(H, gap-1), min 1.
- Hats: `ModelData.merge(fishModel, hatModel.translate(...))` with per-species offset from
  registry. Only when unlocked (50 catches of species) or "unlock all hats" debug toggle.
- Splash: existing water splash spotanim id (verified at build time) at spot tile, 1 tick.
- Sound: existing splash sound effect id via `client.playSoundEffect(id, volume)`. Volume 0–100
  config, scaled by client effects volume.
- Animation override: optional; config picks from a curated list of existing animation IDs.
  Only applied while `FishingStateTracker.isFishing()`, reverted otherwise.
- Each: toggle. Master "Effects" toggle. Auto-disable effects if FPS < configurable floor.

## 9. Chat

`<img=ICON> You catch a trout. 1.34 kg (Fine)`; icon registered via `client.getModIcons()` +
`ItemManager.getImage`. Tier colour from `Grade`. Trophy+ appends flavour line from registry.
Shiny prefix `✦`. Toggle per: weight, grade, flavour, icon. `!fish` / `!fish <name>` via
`ChatCommandManager`, output local only.

## 10. Tide Log

Persistence: `ConfigManager.setRSProfileConfiguration("kaimoana", "tidelog", json)`.
Schema v1: `{version, species:{name:{count, heaviest, heaviestAt, first, shinies, grades:{}}},
locations:{loc:{name:{heaviest}}}, unlocks:[...], totals:{catches, shinies}}`.
Panel: header with equipped title + completion (`caught species / registry species`), search box,
sort dropdown (Recent, Heaviest, Most caught, Rarest, A–Z), grid rows with fish icon (silhouette if
uncaught), count, best weight, tier badge, shiny badge. Row click -> detail (grade histogram,
location bests, wiki facts if enabled, wiki link). Export CSV button.

## 11. Unlocks (permanent, per profile)

Titles: First Trophy, First Taniwha, Shiny Hunter (1 shiny), 50 Species, All Species,
10k Catches, Local Legend (best at 10 locations). Hats: per species at 50 catches; golden hat at 1
shiny of species. Displayed only in panel/local chat.

## 12. Config sections

Effects (master, model, arc ticks, hold ticks, hats, splash, sound, volume, anim override, fps floor)
Weights (grades on, shiny on) · Chat (icon, weight, grade, flavour, commands) ·
Tide Log (panel on, silhouettes, sort default) · Integrations (discord url, party, wiki, screenshot).
Defaults: everything on except integrations and anim override.

## 13. Testing

JUnit 5: `CatchDetectorTest` (all message variants, dedup), `WeightRollerTest` (bounds, grade
percentiles, distribution sanity), `FishRegistryTest` (lookup, fallback), `CadenceGovernorTest`,
`TideLogStoreTest` (round-trip, migration). Manual checklist per method listed in §5, at level 3
and level 99 cadences. Hub checklist: `./gradlew build`, plugin.json fields, no shaded deps.

## 14. Out of scope

Per-item weight in bank/inventory. Custom assets. Anything altering game requests or state.
Multiplayer visibility of effects. Leaderboards.
