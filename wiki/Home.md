<p align="center"><img src="https://raw.githubusercontent.com/SlimefunNewHorizons/MultiverseGambling/main/assets/banner.svg" alt="MultiverseGambling" width="100%"/></p>

# MultiverseGambling wiki

**English** · [Español](Home-es)

Chance and betting engine for **Paper 1.21.11, 26.1 and 26.2** with **22 minigames**, its own **casino
world** and **in-game translation** into English or Spanish.

| | |
|---|---|
| Version | 1.0.4 |
| Server | Paper 1.21.11, 26.1 and 26.2 (also loads on any 1.21.x) |
| Java | 21 |
| Soft dependency | Vault (optional) |
| Author | **Chagui68** |
| Organisation | **Drakes Labs** |
| Repository | [SlimefunNewHorizons/MultiverseGambling](https://github.com/SlimefunNewHorizons/MultiverseGambling) |

## Start here

| Page | What it answers |
|---|---|
| [Installation](Installation) | How to drop the jar in and what files it creates |
| [Commands](Commands) | Every `/mvgam` subcommand, with examples |
| [Permissions](Permissions) | The three permissions and what they open |
| [Configuration](Configuration) | Every key of `config.yml` |
| [World](Casino-World) | The separate world, the plaza, the arenas and the roads |
| [Languages](Languages) | Self translation, `/mvgam language` and adding a new language |
| [Solo Games](Games-Solo) | The 12 games against the house |
| [Group Games](Games-Group) | The 10 games with automatic rounds, also against the house |
| [Poker](Poker) | The Texas hold'em table, chips for items and how items are valued |
| [Item Bets](Item-Bets) | Staking vanilla or custom items and how many come back |
| [Fairness](Fairness) | Provably fair rolls and `/mvgam verify` |
| [Economy](Economy) | sBank, Vault or the internal wallet, and the data files |
| [Troubleshooting](Troubleshooting) | The usual suspects |
| [Development](Development) | Build, test and project layout |

## The three ideas behind the plugin

1. **The maths live outside Bukkit.** Everything that decides money is plain Java in
   `com.chagui68.multiversegambling.engine`, so it is unit tested in milliseconds and the
   plugin can only use those formulas.
2. **Money can be settled once.** Bets are wrapped in a `Wager` that refuses a second
   payout; group games use a `Pot` that holds one wager per player.
3. **Randomness that decides money is provably fair.** A server secret plus your own seed
   produce every roll, and `/mvgam verify` lets anybody recompute them.

## Quick tour

```
/mvgam                      → main menu (solo / group tabs)
/mvgam play roulette        → play a game by its exact id
/mvgam play plinko          → then "❖ Bet items" to stake items instead of money
/mvgam world                → travel to the casino world
/mvgam language es          → read everything in Spanish from now on
/mvgam verify               → audit the fairness of every roll
/mvgam world build          → rebuild the plaza, roads and arenas (admin)
/mvgam world info           → what the casino world is missing (admin)
```
