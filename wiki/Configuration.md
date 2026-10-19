# Configuration

**English** · [Español](Configuracion-es)

Everything lives in `plugins/MultiverseGambling/config.yml`. The file is written in English
and commented; this page is the reference. After editing it run `/mvgam reload`: running
games are untouched, and the new values apply to the next round.

## `economy`

| Key | Default | Meaning |
|---|---|---|
| `provider` | `auto` | Which money the casino plays with: `auto` (try the engines below in order), `sbank` (bank accounts), `vault` (the Vault economy) or `internal` (only the plugin wallet). A missing engine falls back to the next one |
| `auto-order` | `[sbank, vault, internal]` | Engines tried when `provider` is `auto`. Swap it to `[vault, sbank, internal]` to gamble with the wallet instead of the bank |
| `currency` | `coins` | Name shown by the `{currency}` placeholder |
| `format` | `&6{amount} &7{currency}` | How every amount is printed |
| `starting-balance` | `1000` | Welcome balance for the internal wallet (ignored with sBank or Vault) |

## `game`

| Key | Default | Meaning |
|---|---|---|
| `house-edge` | `0.02` | Average edge (2%). Games whose edge is fixed by the wheel, like roulette, ignore it |
| `min-bet` | `10` | Lowest allowed stake |
| `max-bet` | `100000` | Highest allowed stake |
| `announce-wins` | `true` | Broadcast big wins to the server |
| `announce-threshold` | `50000` | Only wins paying at least this amount are announced |

Per game you can override the limits with `games.<id>.min-bet` and `games.<id>.max-bet`, and
turn a game off with `games.<id>.enabled: false`. A disabled game disappears from the menus,
the catalogue and the casino world is built without its arena.

## `fairness`

| Key | Default | Meaning |
|---|---|---|
| `provably-fair` | `true` | Publishes the hash of the secret and lets anybody audit every roll. Leaving it on is strongly recommended |

## `data`

| Key | Default | Meaning |
|---|---|---|
| `save-every-minutes` | `5` | How often balances, statistics and seeds are flushed to disk |

## `language`

| Key | Default | Meaning |
|---|---|---|
| `default` | `en` | Language of the console, of players with no preference and of the world signs |
| `follow-client` | `true` | A player who never picked a language follows their Minecraft client when the plugin ships that language |

See [Languages](Languages).

## `item-bets`

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Lets the six item capable games take items as the stake |
| `max-items` | `1728` | Most items a single bet can hold |
| `blocked` | `*SHULKER_BOX`, `*BUNDLE` | Items that can never be staked: exact names, `*SUFFIX` or `PREFIX*` |

See [Item Bets](Item-Bets).

## `world`

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Creates (or loads) the casino world on start |
| `name` | `mvgam_casino` | World folder name. Point it at an existing world to reuse it |
| `size` | `500` | Side of the square world, in blocks (200-2000). It is grown automatically if the layout does not fit |
| `build-structures` | `true` | Builds the plaza, the roads and the arenas the first time the world is used |
| `teleport-on-join` | `false` | Sends players to the casino world when they join the server |

See [World](Casino-World).

## `group`

Shared by the nine group games.

| Key | Default | Meaning |
|---|---|---|
| `betting-seconds` | `20` | How long the betting window stays open |
| `max-players` | `24` | Seat limit per round |
| `countdown` | `5` | Countdown before the round starts |
| `house-commission` | `0.0` | What the house keeps from the jackpot, the raffle and dice poker |

Per game:

| Key | Default | Meaning |
|---|---|---|
| `group.bomb-board.tiles` | `36` | Tiles on the board |
| `group.bomb-board.bombs` | `4` | Bombs hidden on the board |
| `group.bomb-board.seconds-per-turn` | `10` | Time to reveal a tile |
| `group.hot-bomb.min-seconds` / `max-seconds` | `5` / `30` | Range of the drawn fuse |
| `group.russian-roulette.chambers` / `bullets` | `6` / `1` | Revolver setup |
| `group.russian-roulette.extra-bullet-every-laps` | `3` | One more bullet every this many laps of the table (`0` = never) |
| `group.race.horses` / `steps` | `8` / `60` | Horses in the race and steps per horse |
| `group.raffle.ticket-price` / `max-tickets` | `100` / `20` | Ticket price and per player cap |
| `group.duel.accept-seconds` | `30` | Time to accept a challenge before it expires |

## `games`

One block per game, all of them with `enabled` and the shared bet limits.

| Game | Extra keys |
|---|---|
| `roulette` | `american` (adds the 00 and drops the return), `spin-ticks` (animation length) |
| `slots` | `spin-ticks` |
| `crash` | `double-every-seconds`, `max-multiplier` |
| `mines` | `tiles`, `default-mines`, `max-mines` |
| `towers` | `levels`, `tiles`, `bombs` |
| `blackjack` | `decks`, `dealer-hits-soft-17` |
| `high-low` | `max-steps` |
| `plinko` | `rows`, `max-multiplier` (safety cap) |
| `scratch` | `picks` |
| `lucky-wheel` | `segments` (multiplier per segment, all equally likely) |
| `dice`, `coin-flip`, `color-roulette`, `jackpot`, `hot-bomb`, `bomb-board`, `russian-roulette`, `race`, `duel`, `raffle`, `dice-poker` | only `enabled` and the bet limits |

## Changing a prize table

The return of slots, plinko, scratch cards and the lucky wheel is pinned by automatic tests.
If you change weights, payouts, segments or rows, run the suite:

```bash
mvn test
```

If a table now returns more than it takes, the build fails and tells you which one. This is
deliberate: an accidental 120% return on a public server is money printed out of thin air.
