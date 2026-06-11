# Lambda — Cluster Mode Information Model (design contract)

The spec everything else hangs off. Defines exactly what each player can *know*, so the decoy
deduction is well-formed. Status: design, nothing built yet.

## The pivot (decided)
- **One game: 7v7 Cluster.** No separate Node/solo ruleset. Empty seats are filled by **bots**.
- **Solo = you are dropped in as a RANDOM role** on a team of bots vs a team of bots. You might be the
  collector Lambda, an escort, a tracker, a saboteur — anything. Consequence: bots must be able to
  play *every* role convincingly, including a believable Lambda.
- The old "solo" mechanics (hidden symbols, theft/grep, `username.groovy`, co-op bot forks) are **not
  deleted** — they become the substrate every player, human or bot, operates in.

## The load-bearing invariant: one TRUE Lambda, one fully-capable decoy
- There **is** a true Lambda and a decoy, **fixed at match start** (a hidden truth the system holds;
  known to the team, never to the enemy).
- The decoy is **fully capable** — it moves, scans, **collects and carries symbols**, `transfer`s,
  fights, uses powers — *everything* the true Lambda can do, with **one exception: it cannot harness
  the symbols' power to win** (only the true Lambda can cash the 4 symbols into a Logic-Daemon
  victory).
- That single exception is what makes the decoy a **credible** threat: it can genuinely gather all
  four symbols, so the enemy can never write it off. The decoy isn't feinting empty movement — it can
  do the *real collection work* as cover.
- To any **enemy** sensor, the two Lambdas return byte-identical readouts — same `Λ` marker, same
  role-class, same name-class. Symbols and symbol-possession are **invisible to enemies** (already
  true — hidden from scan). The true/decoy bit is never exposed in any *direct* channel.
- The team can route the *visible collection labor* through **either** Lambda to muddy the read — but
  because only the true Lambda can cash the symbols in, **the win must converge on it.** So the
  endgame (`invoke` → the Logic-Daemon fight) is the moment the true Lambda is finally **forced to
  expose itself**: the climactic reveal, and the enemy's last interception window.

→ The real Lambda is therefore **inferred** from emergent correlations during play, and only
**confirmed** at the invoke — never directly read before then. The two correlations:

## The two leak signals (this IS the deduction game)
1. **Protection geometry** — escorts cluster near whoever the team is actually protecting. Surfaced
   by tracking (enemy positions over time) plus the occupancy rules (4 per coordinate, 2 per team)
   that make formations legible. The system never says "this one is real"; you correlate
   bodies-to-Λ yourself.
2. **Item flow** — the heap broadcasts trades and payments publicly. Goods/bits flowing toward one Λ
   betray it. The team obfuscates by *also* supplying the decoy — the **economic decoy tax**.

Both signals come from already-visible data (positions, public trades). The decoy layer adds **no new
identity data** — it is pure inference. The team's job is to corrupt both correlations at once; the
enemy's job is to find the asymmetry. The real Lambda is revealed by *two* correlations (bodies near
it AND goods flowing to it), so a convincing decoy must absorb believable amounts of **both**.

## Intel is partial and cooperative ("relative to the Lambda, not all")
- No single power shows the whole board. Each role is a **partial sensor**; the enemy picture only
  emerges when teammates **pool** their sightings — this is what forces real cooperation.
- Tracking returns **locations, never identity.** The richest team read is *relative*: e.g. "enemy
  bodies within 2 tiles of Λ#1: 3, of Λ#2: 1" — a correlation, not an answer.
- **Ghosts are invisible** to all tracking unless you share their coordinate, so the picture is
  *never* complete. Deduction always carries uncertainty: an escort blob may screen a hidden Ghost; a
  seemingly-lone Λ may be the real one running dark.

## Per-role see / do matrix (information-first)
| Role (race) | SEES | DOES (info verbs) | Role in the deduction game |
|---|---|---|---|
| **Lambda** (Collector) | own scan only | collect symbols (enemy-invisible), `transfer` to a carrier | The objective. Identical to the other Lambda to enemies; 0 bits, theft-vulnerable. |
| **Circuit** (Tracker) | `scan all` → enemy positions + role-class (Λ generic; Ghosts excluded; ~10s refresh, ~1m cd) | — | Team's primary deduction sensor. Reads location, not identity. No combat. |
| **Geo** (Scout) | normal scan | — | Always **visible to all** (anti-stealth beacon/bait); trap-immune; can't enter wiped coords; 5th-slot exception. Frontline eyes. |
| **Ghost** (Saboteur) | normal scan; **invisible to all tracking** unless same coord | `spam <user>` (adjacent: floods their terminal = comms/disruption denial) | Injects the uncertainty that keeps deduction from being solved. |
| **Binary** (Trapper) | normal scan | `deploy bot <x,y>` (1 bot, cd, self-immune) | Area denial — block the enemy collector's path or guard a Λ approach. |
| **Current** (Disruptor) | normal scan | `lock <user>` (adjacent, 60s immobilize, counter mini-game) | Hard CC to pin a *suspected* Lambda — and pinning the **decoy** wastes it. |

The six races above already exist as the six ethnicities in the code; their current `recurse <ability>`
is the single-player stub of these team verbs.

## Economy decision (the "funner" call): a real, public heap market
**Decided: a living market, not a funnel.** Bots are real market participants (post offers, buy, sell
with simple valuation) *and* prioritize supplying their own Lambda. Rationale:
- The heap is already the most fun part with other people; making it the **contested economic heart**
  builds on that strength.
- Public trades *are* leak signal #2 — a funnel would delete the second deduction layer.
- A human dropped in solo still has a real economic role (trade with bots) regardless of their seat.
- It creates a sabotage surface: **buy out** the supply a Lambda needs, **rob carriers in transit**,
  **spam the heap**, manipulate prices.

## What the read-test (b) checks before anything is built
With Lambdas indistinguishable, location-only tracking, and public item-flow: **can a hunter actually
deduce the real Lambda from geometry + flow, and is the bluff tense for the defenders?** Cheapest test
is Wizard-of-Oz — hand-drive a formation (2 Λ + escorts + heap flow), play the hunter, and judge
whether the inference lands and feels good — *before* building teams, tracking, or bot AI.

## Open items this surfaces (not yet decided)
- Movement model under always-14-players (turn-based queue does not fit; likely dice-as-real-time-
  budget — see PLAYTEST.md).
- Bot AI tiers: dumb bots are enough for *geometry* fun; the *bluff* (faking/reading a decoy) is the
  hard, coordination-heavy part and may make solo-vs-bots thinner than 7-human play.
- Whether teammates' client should *mark* their own real Lambda (yes, for coordination) while enemies
  see them identical — confirm there's no channel where that marker can leak to an enemy.
