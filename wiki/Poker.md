# Poker

**English** · [Español](Poker-es)

**Texas hold'em for up to 8 players** (`/mvgam play poker`), at a big oval table lying flat
in the middle of its pavilion, with a dealer standing behind the shoe.

- **Sit down**: walk up to a free chair and click its **✚ SIT** hologram, pick your buy-in
  (in big blinds) and you are seated on the chair. Sneak or press **⏏ Leave** to stand up.
- **Private cards**: your two cards lie face down for the whole room; only you see them
  face up, plus a larger copy floating in front of you. Nobody can read your hand by walking
  round the table.
- **Your own buttons**: on your turn floating buttons appear in front of your chair, for
  you alone: **Fold**, **Check / Call**, **All in**, and **◀ Raise to ▶** to size the raise
  (minimum, a third of the pot, half, three quarters, pot...). The same moves come as chat
  buttons. You have `action-seconds` (30) to act; after that the table checks or folds for you.
- **The dealer** shuffles with the provably fair generator, deals the cards flying from the
  shoe, burns before every street, moves the dealer button and pushes the pot to the winner.
- **Real rules**: blinds, heads-up with the button on the small blind, minimum raises,
  all-ins that do not reopen the betting, uncalled bets returned, **side pots**, split pots
  with the odd chip left of the button.
- **Against the house**: alone at the table, press **⚑ Play the house** and the house sits
  down with as many chips as you. It plays by estimating its odds and comparing them with
  the pot, and keeps `house-rake` (5%) of each pot after the flop while it plays.

## Chips for items

Poker is played with money, but you can **buy chips with items**: vanilla, **Slimefun** and
**MultiverseCreatures** items and the items of **every Slimefun addon**, mixed in one chest. Each one is worth its value in
[item-values.yml](https://github.com/SlimefunNewHorizons/MultiverseGambling/blob/main/src/main/resources/item-values.yml), and when you stand up you get your
items back first (the most valuable ones first, as long as your chips still cover them) and
the rest as money. How the values were set:

| Source | How it is valued |
|---|---|
| Vanilla | Against the anchor **1 diamond = 100 coins**: how hard the item is to get |
| Slimefun (514 items) | Generated from the Slimefun recipes: its ingredients, times what the machine or ritual adds (enhanced crafting table ×1.05, smeltery ×1.08, magic workbench ×1.15, heated pressure chamber ×1.15, ancient altar ×1.40), plus 0.5 coins per research level, divided by how many the recipe makes. World resources (sifted ore, uranium, oil) have fixed values |
| MultiverseCreatures (59 items) | A drop is worth how hard its creature is divided by its drop chance (Chaos Orb: 150 / 60% = 250); crafted items are worth their ingredients ×1.10, the legendary weapons, armour and relics ×1.65; merchant items are worth their trade (Excalibur: 16 Star Cores + 32 netherite) |
| Slimefun addons (all installed) | Valued on every server start from the recipes Slimefun really registered, with the same rule: DynaTech, Supreme, Galactifun, ExoticGarden, Networks, LiteXpansion, FluffyMachines and any other addon. Found items (drops, GEO resources) and items without a recipe start at 25 coins. The result is written to `plugins/MultiverseGambling/item-values-addons.yml`, grouped by addon; copy a line under `addons:` in item-values.yml to change it |

An item is recognised by the id its plugin stores in it, never by its name, so a renamed
item cannot pass for a custom one; a vanilla or MultiverseCreatures item that is not in the file is refused. Edit any
value, or `scale` to resize them all, and `/mvgam reload`.

## Configuration

```yaml
games:
  poker:
    min-bet: 200            # smallest buy-in
    max-bet: 1000000        # largest stack at the table
    small-blind: 5
    big-blind: 10
    action-seconds: 30      # time to act before the table checks or folds for you
    next-hand-seconds: 6
    rake: 0.0               # share of each pot after the flop, players against players
    rake-cap: 0
    house-rake: 0.05        # while the house plays
    house-players: 1        # how many house players sit down
    item-buy-in: true
```

## Safety

- Money leaves the balance only when the buy-in is confirmed; closing a menu costs nothing.
- Every change of the seats is written to `poker-table.yml`: after a crash, each player gets
  back the stack they had when the hand started, in items and money.
- Players who disconnect or stand up mid hand fold (or check) on their turns and are paid out
  when the hand ends. Two timeouts in a row stand a player up after the hand.
