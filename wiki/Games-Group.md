# Group games

**English** · [Español](Juegos-Grupo-es)

Nine games with **automatic rounds**. Nobody has to start anything: a player joins with
`/mvgam play <id>` (or by clicking the bomb board in its arena), bets during the window,
and the round plays itself. When it ends the
next betting window opens on its own for whoever wants to join again.

The shared round cycle is: **waiting for players → betting window → in game → payout**.

Where a round needs a choice — betting on a colour or a horse — you can make it in the menu
**or** with the buttons the lobby sends to the chat, so nobody has to keep a menu open while
they watch the show. Colour roulette also accepts `/mvgam action color red`, `black` and
`green`, and every choice can still be changed until the wheel starts.

| Game | Id | Rules | Players | Return |
|---|---|---|---|---|
| **Color Roulette** | `color-roulette` | Everybody bets red, black or green; green is a single pocket, so it pays ~36x | 2-24 | 97.30% |
| **Jackpot** | `jackpot` | Everybody puts money in and tickets are proportional to the stake; one winner takes it all | 2-24 | 100% − commission |
| **Hot Bomb** | `hot-bomb` | TNT passes from hand to hand with a drawn fuse; whoever it catches is out and their money stays in the pot | 2-12 | 100% − commission |
| **Bomb Board** | `bomb-board` | Shared board with hidden bombs; players reveal tiles in turns; the last one standing collects | 2-12 | 100% − commission |
| **Russian Roulette** | `russian-roulette` | In turns each player pulls the trigger with 1 bullet in 6 chambers, one more bullet every 3 laps of the table; the heads of the players hang in front of the muzzle; the survivor takes the pot | 2-8 | 100% − commission |
| **Horse Race** | `race` | 8 horses with published odds; backing the favourite pays little, the outsider pays a lot | 2-24 | 98.00% per horse |
| **Duel 1v1** | `duel` | Challenge somebody for a stake; both put the same in and a coin decides | 2 | 100% − commission |
| **Raffle** | `raffle` | Tickets at a fixed price and a draw of three prizes: 70%, 20% and 10% of the pot (with two players the two prizes share the whole pot). Only whole tickets are charged | 2-24 | 100% − commission |
| **Dice Poker** | `dice-poker` | Five dice each; the best hand wins and ties split the pot | 2-16 | 100% − commission |
| **Poker** | `poker` | Texas hold'em at a table with private cards, hologram buttons and side pots; see [Poker](Poker) | 2-8 | 100% − rake |

## Playing alone against the house

Waiting for a room to fill up is the worst part of a casino, so **every group game can be
played alone against the house**. When the room is empty the bet selector shows a second
button, **Play against the house**; the lobby also sends an offer with a chat button as soon
as you are alone (and again when everybody else leaves), and `/mvgam action house` does the
same. When a round ends every player gets **Play again** and **Against the house** buttons in
chat (`/mvgam play <id> house`). Every duel carries the same house edge as the solo games:

- **Russian Roulette** — the dealer sits in the other chair and you pull first. The cylinder
  is spun once, so the duel is decided by where the bullets sit: with 1 bullet in 6 chambers
  you win half of the time and a win pays **1.96x**, the same edge as any other bet here. The
  table is unit tested: no combination of `chambers` and `bullets` returns more than the house
  edge. The extra bullet of every 3 laps only applies to the group round: the duel keeps the
  bullets it was priced with.
- **Color Roulette** — the wheel pays your colour directly (2x red or black, 36x green)
  instead of paying you out of a pot of one. The duel starts as soon as you pick the colour.
- **Jackpot** and **Raffle** — the house matches your stake (or buys as many tickets as you)
  and a single draw decides: half of the time you win, and a win pays **1.96x**.
- **Hot Bomb** — the bomb passes between you and the dealer; whoever holds it when it blows
  loses. An even game, paid **1.96x**.
- **Bomb Board** — you and the dealer reveal tiles in turns, you first; whoever finds a bomb
  first loses. It is the revolver of the russian roulette with tiles for chambers, so the same
  tested table prices it.
- **Dice Poker** — the dealer rolls a hand too: the better hand wins 1.96x and a tie gives the
  stake back.
- **Horse Race** — the race already pays fixed odds, so alone you simply race against the
  book: the horse you pick pays its own odds. The odds of every horse are drawn when the room
  opens and shown on the buttons, so you pick knowing them.
- **Duel 1v1** — the rival menu has a **Duel against the house** button: the same coin, your
  head against the house, an even toss paid 1.96x.

Everyone else can still join the normal round: the duel is only offered while you are alone.

## Player against player means no house edge

These games move money between players, so by default the house keeps **nothing**
(`group.house-commission: 0.0`). Set it to a small value, say `0.02`, if you want the casino
to take a cut of the jackpot, the raffle and dice poker.

## Rounds and the pot

- The betting window lasts `group.betting-seconds` (20 by default). The lobby announces the
  countdown, then the round starts on its own.
- Money leaves the wallets **when you bet**, not when the round starts: the player's stake is
  held in a `Pot` that keeps one settled-once wager per player.
- A round needs the minimum number of players with a bet; otherwise it is cancelled and
  everybody is refunded.
- If you join while a round is in progress you are queued for the next one and told so.
- **Disconnecting mid round does not return your money.** Your bet stays in the pot and any
  other player can win it, which is what makes rage quitting pointless. Leaving **before** the
  round starts refunds you in full.
- The seat limit is `group.max-players`; when the table is full you are told to try the next
  round.

## Turn based games and chat buttons

Hot Bomb, Bomb Board and Russian Roulette run on turns with a per turn clock
(`group.bomb-board.seconds-per-turn`, and the fuse range for the hot bomb). They announce the
current player and, if they do not act in time, the turn moves on by itself, so a single idle
player cannot freeze the table.

Some actions are also reachable from the chat: clickable buttons send `/mvgam action <action>`
(`reveal`, `horse 3`, `shoot`, `accept`, `decline`...) which is what makes the games playable
from a phone or from chat without opening a menu.

## Fairness

Rounds draw their randomness from the same provably fair source as the solo games, attributed
to the fixed casino identity (zero UUID). That means a whole race or a hot bomb round can be
audited afterwards with `/mvgam verify`. See [Fairness](Fairness).
