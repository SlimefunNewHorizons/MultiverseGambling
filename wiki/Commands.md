# Commands

**English** · [Español](Comandos-es)

The command is `/mvgam` and it has **no aliases**: each subcommand has exactly one spelling.
Everything is tab completed, and every answer is written in the language of whoever ran the
command.

Every identifier the plugin adds to the server carries the `mvgam_` prefix, so nothing can
collide with another plugin: the permissions `mvgam_play`, `mvgam_top` and `mvgam_admin`, and
the casino world `mvgam_casino`.

## Playing

| Command | What it does |
|---|---|
| `/mvgam` | Opens the main menu: a solo tab and a group tab |
| `/mvgam menu` | Same as above |
| `/mvgam games [solo\|group]` | Lists the catalogue with the betting limits of each game |
| `/mvgam play <id>` | Plays a game by its exact id (`roulette`, `slots`, `lucky-wheel`...) |
| `/mvgam play <id> house` | Plays a group game straight against the house (what the chat button runs) |
| `/mvgam action <action>` | Entry point of the **chat buttons** (`shoot`, `reveal`, `horse 3`, `accept`, `decline`) |

Inside a game, every action has its own menu: the bet selector lets you halve, double or
stake everything, and the game panels expose cash out, stand, double down or shoot.

## Your account

| Command | What it does |
|---|---|
| `/mvgam balance [player]` | Shows your balance, or somebody else's |
| `/mvgam stats [player]` | Games played, wins, wagered, real return and favourite game |
| `/mvgam top [profit\|wagered\|prize]` | Server ranking by net profit, volume or biggest win |

`/mvgam top` needs `mvgam_top`, which everybody has by default.

## Fairness

| Command | What it does |
|---|---|
| `/mvgam verify` | Shows the current secret hash, the previous secret and your seed |
| `/mvgam verify <text>` | Changes your client seed to that text |

See [Fairness](Fairness) for the full explanation.

## Casino world

| Command | What it does |
|---|---|
| `/mvgam world` | Teleports you to the casino world (fails gracefully when it is not ready) |
| `/mvgam world build` | Rebuilds the plaza, the roads and every arena (**`mvgam_admin`**) |
| `/mvgam world info` | Reports the state of the world: exists, terrain, size, ground, arenas built and what is missing (**`mvgam_admin`**) |

## Languages

| Command | What it does |
|---|---|
| `/mvgam language` | Shows your language and the available ones |
| `/mvgam language <code>` | Switches to that language (`en`, `es`, `ES`, `es-AR`, `spanish`, `español`...) |
| `/mvgam language reset` | Forgets your choice and follows your Minecraft client again |


## Administration

| Command | What it does |
|---|---|
| `/mvgam info` | Active economy, provider, registered games and current secret |
| `/mvgam reload` | Reloads `config.yml` and every `lang/*.yml` without touching running games |
| `/mvgam give <player> <amount>` | Adds balance |
| `/mvgam take <player> <amount>` | Removes balance |
| `/mvgam set <player> <amount>` | Sets the balance to an exact value |
| `/mvgam cancel <player>` | Closes somebody's game and refunds their money |

Amounts accept a comma or a dot: `1000`, `1000.50`, `1000,50`.

All of them need `mvgam_admin`, which operators have. See [Permissions](Permissions).

## Arguments, not aliases

Some subcommands take a word to choose a mode. They are arguments, not aliases, so there is
only one way to write each command:

| Written as | Meaning |
|---|---|
| `/mvgam games solo` / `/mvgam games group` | Filter the catalogue by category |
| `/mvgam top profit` | Ranking by net profit (default) |
| `/mvgam top wagered` | Ranking by volume |
| `/mvgam top prize` | Ranking by biggest win |
| `/mvgam verify <text>` | Sets your client seed to that text |
| `/mvgam language es` / `<code>` / `reset` | Picks a language, or goes back to automatic |
| `/mvgam world build` | Rebuilds the casino world (admin) |
| `/mvgam action house` | Takes on the house when you are alone in a group room |
| `/mvgam world info` | Reports what the casino world is missing (admin) |

## Examples

```
/mvgam play crash            → open crash and pick your bet
/mvgam play race             → join the next race round
/mvgam action shoot          → used by the russian roulette button
/mvgam top prize             → ranking of biggest wins
/mvgam language es           → everything you read is now Spanish
/mvgam world build           → rebuild the casino world from scratch
/mvgam world info            → check what the casino world is missing (console friendly)
```

`play` only accepts the exact ids listed by `/mvgam games`: the horse race id is `race`, not
`horse-race`.
