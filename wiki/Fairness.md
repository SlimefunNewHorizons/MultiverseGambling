# Fairness

**English** · [Español](Justicia-es)

Every roll that decides money comes from `FairnessService`, and every roll can be recomputed
by anyone afterwards.

## The idea

```
roll = HMAC-SHA256(serverSecret, clientSeed:nonce:cursor)
```

- The **server secret** is generated on start and its **hash is published** before you play.
  While the secret is running you cannot know the rolls, but the published hash commits the
  server to them.
- The **client seed** is yours. You can set it to any text with `/mvgam verify <text>`, so the
  server cannot pick a result after seeing your bet.
- The **nonce** counts the rolls issued, and the **cursor** separates the several random values
  a single game may need.

When the secret rotates, the previous secret is revealed. The hash you were shown before now
matches the secret you can read: anyone can redo the maths and check that nothing was changed.

## Auditing

```
/mvgam verify
```

Shows the current secret hash, the previous secret (once rotated), the hash left by that
previous secret, your client seed and how many rolls have been issued in this session.

```
/mvgam verify my-lucky-text
```

Changes your client seed. Do it whenever you like; the change applies to the rolls that come
after it.

## Rules the service follows

- **The roll is uniform.** 100,000 rolls spread over 10 buckets land at 10% each, and the test
  suite checks exactly that.
- **It is deterministic.** The same secret, seed, nonce and cursor always give the same roll,
  which is what makes the audit possible.
- **Money never uses the animation RNG.** The plain `Rng` only paints wheels and reels; the
  result the payout uses always comes from the fairness service.
- **Group rounds are auditable too.** They are attributed to the fixed casino identity (zero
  UUID), so a hot bomb round or a horse race can be recomputed step by step.
- **Turning it off is possible but not recommended.** `fairness.provably-fair: false` makes the
  plugin use the plain generator; the audit then cannot prove anything.

## What it does not do

Provably fair does not mean the house cannot win: the edge is in the prize tables and it is
documented (see [Real returns](https://github.com/SlimefunNewHorizons/MultiverseGambling#real-returns)).
What it proves is that **the outcomes are the ones the published table implies**, and that a
player cannot be singled out with a worse roll than anybody else.
