# Economy

**English** · [Español](Economia-es)

The plugin can use your server economy through **Vault**, or its own wallet, decided by
`economy.provider`.

| Value | Behaviour |
|---|---|
| `auto` (default) | Tries the engines of `economy.auto-order`, in order |
| `sbank` | The **bank accounts** of the sBank plugin |
| `vault` | Whatever economy the server registers through Vault |
| `internal` | The plugin's own `balances.json`, ignoring every other plugin |

Whichever value you pick, the plugin ends up with a usable wallet: if the named engine is
missing or refuses to answer, it warns in the console and falls back to the next one, and the
internal wallet is always last. The casino can never fail to start because of money.

## Which engine, in which order

```yaml
economy:
  provider: auto
  auto-order: [sbank, vault, internal]
```

`auto` walks that list. The shipped order puts the **bank first**, because a server that
installs sBank keeps its players' money in bank accounts; a server that would rather gamble
with the wallet it hands out with Vault just swaps it:

```yaml
  auto-order: [vault, sbank, internal]
```

Picking one engine directly is the other option: `provider: sbank` still falls back to the
internal wallet if sBank is not there, and `provider: internal` ignores every other plugin.

## sBank bank accounts

The bridge to [sBank](https://github.com/SlimefunNewHorizons) uses its public API, so the casino
moves the same number the bank shows:

- **Reads** come from the in-memory bank of online players and from the bank database for
  anybody offline, which is what lets a group round pay a player who disconnected.
- **Writes** follow the bank's own rules: the balance is rounded to two decimals like the
  bank does, and every movement is persisted immediately with `SBank.persistBank`, so a crash
  cannot resurrect an old balance. If the bank cannot store the movement, the casino refuses
  the payout instead of handing out money nobody can hold.
- **Audit**: each bet and each payout is written to the bank audit log as `CASINO_BET`,
  `CASINO_PAYOUT` or `CASINO_ADMIN`, so an administrator can reconcile the casino against the
  bank afterwards.
- **Accounts are the bank's job.** sBank opens one for every player who joins, with its own
  starting money, so the casino never creates or funds one.

The bridge is loaded through reflection: the casino still starts on servers without sBank,
and if a future sBank version changes its API the plugin logs a warning and simply uses the
next engine.

## Internal wallet

`balances.json` holds `uuid → balance`. The welcome balance comes from
`economy.starting-balance` (1000 by default) and is credited the first time a player is seen,
with a message. Balances are saved every `data.save-every-minutes` minutes and on shutdown.

Administration commands:

```
/mvgam give <player> <amount>
/mvgam take <player> <amount>
/mvgam set  <player> <amount>
```

They work with either provider. When Vault is active the plugin calls Vault, so your economy
plugin keeps its own limits, logs and hooks.

## Amounts and formatting

`economy.format` decides how money is printed everywhere (`&6{amount} &7{currency}` by
default): messages, menus, signs and the balance commands. Amounts typed in commands accept a
comma or a dot, so both `1000.50` and `1000,50` work.

## Data files

| File | Contents | Safe to delete? |
|---|---|---|
| `balances.json` | Internal wallet; ignored when sBank or Vault is used | Only to reset the internal economy |
| `stats.json` | Games played, wins, wagered, biggest win and the rankings | Yes, players just lose their statistics |
| `fairness.json` | Current server secret, previous secret and the per player client seeds | Yes, but every player's seed changes |
| `languages.json` | The language each player chose | Yes, everyone falls back to `language.default` |
| `pending-items.yml` | Item winnings waiting for players who left mid round (see [Item Bets](Item-Bets)) | No: those players lose their items |

## How the plugin protects money

- **One settlement per bet.** A `Wager` is created when the stake is taken and can be settled
  once: a second `pay()`, `refund()` or `lose()` does nothing. Paying the same round twice is
  not possible, not just unlikely.
- **The pot owns the money.** In group games the stakes live in a `Pot` from the betting window
  until the payout, so a disconnect cannot leave money half way: it either comes back with the
  refund or stays winnable by the others.
- **One clock.** `SessionManager` schedules everything, and `shutdownAll()` settles every open
  game on shutdown, so a restart does not eat anybody's stake.
- **The house edge is data, not code.** `game.house-edge` is applied by the engine tables and
  the suite refuses to build a table that returns more than it takes.
