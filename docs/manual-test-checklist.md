# Manual test checklist

Run each with default config unless stated. Note anything odd.

## Methods

| Method | Location | Chat line | Model + arc | Sound | Tide Log row |
|---|---|---|---|---|---|
| Small net (shrimps, anchovies) | Lumbridge Swamp | | | | |
| Bait (sardine, herring) | Draynor | | | | |
| Fly (trout, salmon) | Barbarian Village | | | | |
| Cage (lobster) | Musa Point | | | | |
| Harpoon (tuna, swordfish, shark) | Catherby / Fishing Guild | | | | |
| Big net (bass, casket, oyster, seaweed) | Catherby | | | | |
| Barbarian rod (leaping fish) | Otto's Grotto | | | | |
| Karambwan vessel | Tai Bwo Wannai | | | | |
| Aerial fishing (bluegill etc.) | Lake Molch | | | | |
| Drift net | Fossil Island | | | | |
| Tempoross (harpoonfish) | Tempoross Cove | | | | |
| Camdozaal (guppy, cavefish, tetra, catfish) | Camdozaal | | | | |
| Minnows | Fishing Guild platform | | | | |
| Anglerfish | Port Piscarilius | | | | |
| Dark crabs | Wilderness Resource Area | | | | |
| Infernal eels | Mor Ul Rek | | | | |
| Sacred eels | Zul-Andra | | | | |
| Fishing Trawler reward chest | Port Khazard | inventory-only | | | |
| Any fish not in fish.json (new content) | wherever | fallback range | | | |

## Cadence

- [ ] Level 3 fly fishing: full jump + hold visible, no overlap.
- [ ] 99 fishing with 3-tick catches: fish still visible, never two at once.
- [ ] Idle for a minute then catch: governor resets to configured values.

## Toggles

- [ ] Master effects off: chat still works, no model, no sound.
- [ ] Fish model off, sound on.
- [ ] Sound volume 0.
- [ ] Hats: "Unlock all hats" preview shows a hat on every catch.
- [ ] Animation override on while fishing, reverts when not fishing.
- [ ] FPS floor 60: effects suppressed on a slow machine.
- [ ] Panel toggle removes and restores the sidebar button.

## Tide Log

- [ ] Rows sort correctly for every sort option.
- [ ] Search filters.
- [ ] Silhouettes off hides uncaught species.
- [ ] Tooltip shows grade histogram and location bests.
- [ ] Double-click opens the wiki page.
- [ ] Copy CSV puts data on the clipboard.
- [ ] Log persists across logout/login and client restart.
- [ ] Second account on same client has its own log.

## Chat

- [ ] `!fish` prints totals.
- [ ] `!fish shark` prints the shark record.
- [ ] Icons render for tradeable and untradeable fish.

## Integrations (opt-in)

- [ ] Discord webhook posts on Trophy+.
- [ ] Party broadcast reaches a party member with the plugin.
- [ ] Screenshot saved under `.runelite/screenshots/Kaimoana`.
