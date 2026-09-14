# Kaimoana Fishing

A cosmetic RuneLite plugin for fishing in Old School RuneScape. Every catch gets a weight and a
grade, the fish jumps out of the water into your hand, and everything you land is recorded in a
permanent per-profile **Tide Log** with rare unlocks. No gameplay advantage, everything toggleable.

Works with every fish in the game, including ones released after this plugin, because fish are
resolved by name from the live game cache rather than a hardcoded ID list.

## Features

- **Weights and grades.** Each catch rolls a realistic weight for its species. Grades in ascending
  order: Common, Fine, Prime, Trophy, Big One, Taniwha.
- **Fish in hand.** The caught fish's model pops out of the fishing spot, arcs to your hand and is
  held for a few ticks. Hold time shrinks automatically when you catch fast, so 99 Fishing never
  stacks fish.
- **Sound.** Splash-style bloop on every catch, a tinkle on Trophy and above. Volume and sound IDs
  are configurable.
- **Chat line.** `[icon] You catch a trout. 1.34 kg (Fine)` with grade colour, flavour text on
  Trophy and above.
- **Shiny fish.** 1 in 512 catches is shiny: gold model, badge in the Tide Log, golden hat unlock.
- **Hats.** Catch 50 of a species to unlock its hat, shown on the held fish.
- **Tide Log panel.** Sortable grid (Recent, Heaviest, Most caught, Rarest, A-Z), search, greyed
  silhouettes for uncaught species, per-location personal bests, grade histogram, CSV export,
  double-click to open the wiki.
- **Titles.** First Trophy, First Taniwha, Shiny Hunter, 50 Species, All Species, 10,000 Catches,
  Local Legend.
- **Milestones.** 100th, 1000th and 10,000th of a species get a longer jump and hold.
- **`!fish`** and **`!fish <name>`** show your totals or a species record, to you only.
- **Opt-in integrations.** Discord webhook for Trophy+, Party plugin broadcast, wiki facts,
  screenshot on Trophy+.

## Configuration

| Section | Options |
|---|---|
| Effects | master toggle, fish model, jump ticks, hold ticks, hats, splash pop, sound + volume + IDs, animation override + ID, FPS floor |
| Weights | grades on/off, shiny on/off |
| Chat | icon, weight, grade, flavour, `!fish` command |
| Tide Log | panel on/off, silhouettes |
| Integrations | Discord webhook URL, party broadcast, wiki facts, screenshot |

## Catch detection

Primary source is the game message (`You catch a trout.`). Fallback is an inventory delta while
you are interacting with any NPC named `Fishing spot`, which covers minigames that suppress the
message. Duplicate events within one tick are merged.

Fish not in the bundled registry still work with a default weight range scaled from their High
Alch value. To add a species, append one line to `src/main/resources/com/kaimoana/fish.json`.

## Development

```
./gradlew build          # compile + unit tests
./gradlew run            # launch RuneLite in developer mode with the plugin loaded
```

Java 11 or newer. Uses the RuneLite `example-plugin` layout.

## Notes

- Sound effect defaults are `2581` (pick-plant bloop) and `3924` (GE coin tinkle). Change them in
  config if you prefer a different in-game sound.
- The animation override default `618` is the harpoon animation. It is off by default.

## Licence

BSD 2-Clause.
