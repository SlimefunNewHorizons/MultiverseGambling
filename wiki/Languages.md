# Languages

**English** · [Español](Idiomas-es)

The plugin translates **itself inside the game**. Nothing to restart, nothing to download:
each player picks what they read and the choice is saved.

```
/mvgam language es      → this player reads Spanish from now on
/mvgam language en      → back to English
/mvgam language         → what am I reading, and what is available?
/mvgam language reset   → forget my choice, follow my Minecraft client again
```

Loose input is accepted: `es`, `ES`, `es_es`, `es-AR`, `spanish` and `español` all mean
Spanish. An unknown code is rejected with the list of available ones.

## Where the texts live

```
plugins/MultiverseGambling/lang/
├── en.yml     ← English (written on first start)
└── es.yml     ← Spanish (written on first start)
```

Both files are created automatically and never overwritten once they exist, so your edits
survive updates. A legacy `messages.yml` is migrated into `lang/en.yml` on the first start of
a modern build.

## Which language a player gets

The lookup order is:

1. the language that player chose with `/mvgam language`;
2. their **Minecraft client locale**, when `language.follow-client` is `true` and the plugin
   ships that language;
3. `language.default` from `config.yml`;
4. English, always, as the last resort.

The console, the logs, and the world signs use `language.default`.

Player choices are stored per UUID in `languages.json`, so they survive restarts and are
independent of the server language.

## What is translated

| Piece | Translated |
|---|---|
| Chat messages (wins, losses, rounds, duels, fairness, world, language, commands) | yes |
| Menus: hub, statistics, bet selector | yes |
| Game catalogue names and descriptions | yes, through `catalog.<game-id>.*` |
| World signs | yes, with the default language |
| Admin answers (`/mvgam give`, `/mvgam reload`, `/mvgam world build`...) | yes, in the language of whoever ran them |
| The panels drawn **inside** each game (the "CASH OUT" button, "Bet:" lines, item lore, the lines a menu prints in the chat) | yes, under `panel.<game-id>.*` |
| The in round lines of the group games (pots, draws, bombs, races) | yes, under `group.<game-id>.*` |

The translation reaches the last button: window titles, item names, item lore and the chat
lines printed by a menu all come from `panel.*`, with one section per game. A test compares
`lang/en.yml` against `lang/es.yml` and fails the build when a key or a placeholder is missing
from either side, so the two files cannot drift apart unnoticed.

## Adding another language

1. Copy `lang/en.yml` to `lang/<code>.yml`, for example `lang/fr.yml`.
2. Translate the values (never the keys).
3. That is it: the code shows up in `/mvgam language` and in the tab completion as soon as
   the file exists. `/mvgam reload` picks it up without a restart.

If a key is missing, the plugin looks for it in the player's language and then in English;
if it is missing everywhere it prints `&c[missing message: <key>]` instead of breaking. A
partial translation is therefore always safe to ship.

Panel keys are named after the game: `panel.slots.title`, `panel.crash.cash-out`,
`panel.blackjack.stand`... Shared labels live in `panel.common.*` and shared symbols in
`panel.symbols.*`, so a translator has one place to fix "Close" or "Pays" for every game.

A language you drop in yourself cannot be picked up automatically from a Minecraft client:
following the client only resolves codes the plugin ships (`en`, `es`). Custom languages are
selected explicitly, with `/mvgam language <code>`.

## Translating game names

Game names and descriptions are written in the code in English, and any language file can
override them:

```yaml
catalog:
  roulette:
    name: 'Ruleta Clasica'
    description:
      - '&7Rojo, negro, docenas, columnas o un'
      - '&7numero exacto: de &f2x &7a &f36x&7.'
```

`lang/es.yml` ships the whole Spanish catalogue as a working example. Note the key is
`catalog.<game-id>`, deliberately different from the `games:` section of messages, which holds
the generic game messages. Descriptions are lists: each line becomes a line of the lore, so
you can reorder or add lines freely.

## Placeholders

Curly braces are filled in by the plugin; keep them as they are:

```
{bet} {prize} {profit} {amount} {game} {player} {min} {max} {seconds} {round}
{current} {balance} {seed} {value} {usage} {language} {languages} {native}
{world} {hash} {state} {description} {games} {wins} {rate} {wagered}
{returned} {rtp} {biggest} {favourite} {provider} {limit} {edge} {name}
```

Panels add their own values: {pot} {multiplier} {spot} {colour} {number} {card}
{cards} {hand} {dice} {tile} {tiles} {bombs} {alive} {revealed} {safe} {count}
{percent} {chance} {picks} {picked} {faces} {pair} {floors} {floor} {levels}
{steps} {marks} {path} {rows} {level} {bucket} {bullets} {chambers} {laps} {horse}
{tickets} {total} {staked} {price} {side} {result} {target} {direction} {crash}
{list} {payouts} {leaders} {from} {to} {players} {symbol} {subcommand}

The safe rule is simple: never translate a `{...}` block, and never delete one.

## Colours

The classic ampersand format works everywhere: `&6` gold, `&7` grey, `&c` red, `&a` green,
`&l` bold, `&m` strikethrough. Lines starting with a dash inside a key such as
`gui.stats.lore` are a list: each entry is one line of the item description.
