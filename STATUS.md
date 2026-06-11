# Lambda — Verified Status & Feature Matrix

> **This file is the source of truth for what actually works.** It was produced by a
> code-level audit (not by trusting comments or older docs). `CLAUDE.md` and `README.md`
> contain aspirational/marketing claims that are frequently ahead of the code — when they
> disagree with this file, **this file wins**. Original audit: 2026-06-07.

---

## ⚡ MAJOR UPDATE (2026-06-10) — much of the matrix below is now OUT OF DATE

A large amount was built/fixed since the original audit. Corrections to the matrix below:
- **`move <dir> <count>` + `dados` dice movement EXISTS** (Phase 10) — the matrix's "does not exist /
  no turn-or-dice system" rows are stale. Roll `dados` → per-axis move budget; 10s auto-roll
  (multiplayer-only); turn rotation for Node multiplayer.
- **`recurse <ability>` is a real charge/cooldown economy** (not UI-only). The 4 elemental symbols are
  collectible and the **Logic Daemon endgame is winnable** (`invoke` with all 4).
- **Crash family fixed**: the telnet thread no longer dies on a handler exception (guarded dispatch +
  `finally` cleanup); ghost sessions gone; defrag StaleStateException race fixed; pm/trade delivery
  reaches the live recipient.
- **Defrag combat works end-to-end** incl. the kill+reward and the auto-resolve timer.

### Cluster Mode — 7v7 hidden true/decoy Lambda (NEW, working)
A complete, winnable team mode. `cluster create` / `join` / `start` (bot-fills to a real 7v7) /
`status` / `leave`. Each of the 6 ethnicities is a Cluster role with a team verb:
`scan all` (Circuit tracker — location, never identity), `lock` (Current), `spam` (Ghost),
`deploy bot` (Binary), `transfer` (Lambda football). The two Lambdas are byte-identical to enemies
(firewall); only behavior (escort geometry + public heap item-flow) leaks the true one. Movement is
dice-paced (no `cc` teleport in a match); the 4 symbols are seeded on the board and a Lambda collects
on arrival (scan reveals the field); only the **true Lambda** with all 4 can `invoke` to win.
**Bots** move/escort/hunt/collect on a 7s tick and can win — a solo human (always the true Lambda for
agency) faces a real, losable race. Owns: `ClusterMatchService`, `ClusterRoleService`,
`ClusterBotService`, domains `ClusterMatch/Team/Membership`. Design: `CLUSTER_DESIGN.md`;
build log: `CLUSTER_PLAN.md`. Verified by ~25 `GameplayHarnessSpec` steps + live play to a win.

---

## Quick facts (corrects stale docs)

| Thing | Reality | Stale docs say |
|---|---|---|
| Telnet port | **23** (`BootStrap.groovy` → `startServer(23)`) | 8181 |
| Movement command | **`cc <x>,<y>`** only — teleport to any coord 0–9, no cost/adjacency | `move north/south/east/west` (does **not** exist) |
| Web interface | Vestigial (legacy `Page`/`Link`). Game is 100% telnet | "http://localhost:8080" |
| DB (dev) | H2 in-memory, `create-drop` — wiped every restart | — |
| Run | `./gradlew bootRun`, then `telnet localhost 23` | — |

---

## Architecture (one paragraph)

Grails 6 app whose real entry point is `BootStrap.init` → `TelnetServerService.startServer(23)`.
A `ServerSocket` accepts clients **thread-per-connection**. Each line of input is dispatched
through a `commandHandlers` `Map<String,Closure>` in `TelnetServerService`, which delegates to
~19 services (one per game system). Because telnet threads are **not** Grails web requests, every
DB access must be wrapped in `DomainClass.withTransaction {}`. There are **two rendering modes**:
classic line-scroll (`TerminalFormatter` ANSI) and **HUD mode** (full-screen TUI using the alt-screen
buffer + absolute cursor positioning + a 2D screen buffer, in `HudService`). Governance rule from
`CLAUDE.md`: **do not create new services** — extend existing ones.

---

## Feature matrix

Legend: ✅ works · 🔶 partial · 🐛 buggy · ❌ stub/missing

### Movement & navigation
| Feature | Status | Evidence | Notes |
|---|---|---|---|
| `cc <x>,<y>` coordinate jump | ✅ | `CoordinateStateService` ~260 | Bounds 0–9 enforced; accessibility check; teleport anywhere |
| `move <dir>` directional step | ❌ | — | **Does not exist.** Only `cc`. Docs lie. |
| Floor progression gating (must finish floor before advancing) | ❌ | `CoordinateStateService:40` | Comment claims it; zero enforcement |
| Safe zones (0,0)(0,1)(1,0)(1,1) | ✅ | `CoordinateStateService:372` | No bot spawns / damage there |
| `recurse <ability>` (6 ethnicity powers) | 🔶 | `LambdaPlayerService:840` | **UI text only** — no charges, cooldowns, or state. TODO at :848 |
| Ethnicity bonus fields (6) | 🔶 | `LambdaPlayer:32-38` | Only `fragmentDetectionBonus`, `miningEfficiencyBonus`, `stealthBonus`, `fusionSuccessBonus` are read; `defragResistanceBonus` & `movementRangeBonus` stored but **never used** |
| Turn / dice / action-economy | ❌ | — | **None anywhere.** Real-time + `Math.random()` rolls only |

### Defrag-bot combat
| Feature | Status | Evidence | Notes |
|---|---|---|---|
| Floor-scaled encounter spawn | ✅ | `CoordinateStateService:345` | 2%→20% by floor |
| `defrag -h` / `cat /proc/defrag/<id>` / `grep -o <pid>` / `kill -9 <pid>` | ✅ | `DefragBotService` 96–286 | Full chain works end-to-end |
| Rewards on kill (bits/items/fragments/nonces) | ✅ | `DefragBotService:486-802` | Special item drop currently **forced** (testing) at :508 |
| Passive bit-drain during encounter | ✅ | `DefragBotService:81-94` | −5 bits/5s; at 0 bits → reset to (0,0) |
| Combat countdown timer | 🐛 | `DefragBotService:472` | `timeLimit` displays but `defragTimerExpired()` is **never called** — purely cosmetic |
| AutoDefrag live coordinate destruction | ✅ | `AutoDefragService` | Background scheduler destroys 1–3 coords/min, evicts players, broadcasts |
| `defrag_status` / `autdefrag` | ✅ | `AutoDefragService` | |
| **Fork-to-join** (notify level, others join attacked player, timer reset) | ❌ | `lambda.md:14-20` | Designed, **zero code** |

### Fragments, fusion, puzzles
| Feature | Status | Evidence | Notes |
|---|---|---|---|
| `scan` reveals fragments (hides symbols) | ✅ | `GameSessionService:246` | |
| `pickup` + quantity stacking | ✅ | `LambdaPlayerService:1095` | Respawns fragment elsewhere; prevents re-pickup |
| `cat <fragment>` / `cat fragment_file` | ✅ | `LambdaPlayerService:560,608` | |
| `fusion`/`fuse` (RNG, daily cap, +1 power) | ✅ | `EntropyService:333` | base 30%, cap 95%, 20h reset |
| `execute` / `chmod` / puzzle rooms | 🔶 | `PuzzleService` | Wired, but **no clear path** from normal play to reach/solve them |
| `CompetitivePuzzleService`, `PuzzleRandomizationService` | 🔶 | — | Run internally via scan; no direct player command |
| `puzzle_market`/`pmarket` knowledge trading | ❌ | `PuzzleKnowledgeTradingService` | `executePuzzleKnowledgeTrade()` is **dead code**, never invoked |

### Elemental symbols / endgame
| Feature | Status | Evidence | Notes |
|---|---|---|---|
| Symbols placed & hidden from scan | ✅ | `ElementalSymbolService` | |
| `symbols` status display | ✅ | `TelnetServerService:77` | |
| `unlock_symbol` (nonce/flag) | ❌ | `currentTask.md` | **Planned, not built.** `ElementalSymbol` lacks `requiredNonce`/`requiredFlag`/`isHidden` fields |
| `ElementalNonce` domain | 🔶 | `PuzzleRandomizationService:151,209`, `PuzzleService:393,413`, `PuzzleKnowledgeTradingService:275` | **Load-bearing, not an orphan** — created/persisted/queried by the puzzle subsystem. Real gap: that subsystem has no player entry path |
| `HiddenVariable` domain | 🔶 | — | Only touched by `collect_var` |
| **Logic Daemon boss fight** | ❌ | `CompetitivePuzzleService:361` | Only a congratulatory string. No entity, command, or mechanics |

### Economy & social
| Feature | Status | Evidence | Notes |
|---|---|---|---|
| Bits earn/spend (transactional) | ✅ | `EntropyService`, `LambdaMerchantService`, `ChatService` | |
| Merchants `shop`/`buy`/`sell` | ✅ | `LambdaMerchantService` | One per level, JSON inventory |
| `heap`/`mingle` chat | ✅ | `ChatService` | |
| `echo` broadcast (real-time multi-client) | ✅ | `ChatService:161` | |
| `pay <entity> <bits>` | ✅ | `ChatService:299` | Atomic transfer |
| `pm <entity> <msg>` | ✅ | `ChatService:359` | |
| `list`/`who` | ✅ | `ChatService:255` | |
| `trade <entity>` | 🔶 | `ChatService:389,429` | Menu's **read side is live** (`getTradeablePuzzleKnowledge`). Missing: an `offer` branch in the heap if-chain + the already-written `executePuzzleKnowledgeTrade()` (zero callers). Wiring, not rewrite |

### Entropy / mining / items / audio
| Feature | Status | Evidence | Notes |
|---|---|---|---|
| Entropy decay (2%/hr offline) + daily refresh bonus | ✅ | `EntropyService:20-112` | Computed on-demand |
| Mining (`mine`/`mining`) | ✅ | `EntropyService:114` | Manual collect, 24h cap, entropy-scaled |
| Special item: **Scanner Boost** | ✅ | `SpecialItemService:186` | Scans 9 adjacent |
| Special item: **Defrag Detector** | ✅ | `SpecialItemService:252` | 3×3 bot scan |
| Special item: **Matrix Mapper** | ✅ | `SpecialItemService:289` | 5×5 grid |
| Special item: **Fragment Magnet** | ✅ | `SpecialItemService:325` | 5×5 fragment scan |
| Special item: **Stealth Cloak** | 🔶 | `SpecialItemService:217` | Returns flag; **effect never checked** |
| Special item: **Bit Multiplier** | 🔶 | `:225` | Flag unused |
| Special item: **Respawn Cache** | 🔶 | `:233` | Defrag always resets to (0,0) regardless |
| Special item: **Swap Space** | 🔶 | `:244` | Flag unused |
| Special item: **Logic Amplifier** | 🔶 | `:281` | Flag unused |
| Special item: **Entropy Stabilizer** | 🔶 | `:317` | Decay computed on-demand; never blocked |
| Special item: **Matrix Clipper**, **Instant Repair Kit** | ❌ | `:526,535` | Defined but **never dropped** (not in drop pool) |
| Audio synthesis (Java Sound) | ✅* | `AudioService` | Real — but plays to **server speakers**, telnet clients can't hear it. Intended for physical board |

### HUD mode (current branch)
| Feature | Status | Evidence | Notes |
|---|---|---|---|
| Enter via `hud`, alt-screen split layout | ✅ | `HudService` | |
| Terminal-size detection (ANSI query) | ✅ | `HudService:33` | |
| Map / chat / repair panels | ✅ | `HudService` | |
| Command coverage in HUD | ✅ | `HudService` (Phase 9) | HUD now delegates to the shared `commandHandlers` map — text commands (entropy/recurse/symbols/shop/use/…) work in HUD. **Known limitation:** `defrag` combat stays classic-only (HUD shows "switch to classic"); repair has its own HUD handler |
| HUD command-history scroll | ❌ | `HudService:1127` | `storeCommandOutput` is an empty stub |
| PM/pay/trade inside HUD heap | ❌ | — | Only `echo` wired |

### Repair mini-game
| Feature | Status | Evidence | Notes |
|---|---|---|---|
| Slot-machine digit-lock repair | ✅ | `SimpleRepairService`, `DigitCycler` | Cycling digits, ENTER locks, match code → restore coord |
| Reward on success | ❌ | `SimpleRepairService:147` | Only repairs coord; **no bits/fragments/prize** |
| **Race condition** (two players repair same coord) | ❌ | HEAD commit | **Unresolved** — both win, no kick, no optimistic lock |

---

## Real command reference (classic mode)

**Top-level handlers:** `autdefrag buy cat cc chmod clear collect_var defrag defrag_status entropy
ex execute fuse fusion heap help history hud i inventory ls m map mine mining pickup pinv pmarket
pprog puzzle_inventory puzzle_market puzzle_progress recurse repair s sc scan sell session shop
status symbols use`

**Inside heap/mingle only:** `echo <msg>`, `pay <entity> <bits>`, `pm <entity> <msg>`,
`trade <entity>`, `list`/`who`, `exit`

---

## TODO backlog (verified gaps)

### A. Make existing systems actually do what they claim
1. **Recursion is fake** — `recurse <ability>` prints text but applies nothing. Add charge/cooldown
   fields + actually set the temporary bonus. (`LambdaPlayerService:840`)
2. **6 stub special items** — Stealth Cloak, Bit Multiplier, Respawn Cache, Swap Space, Logic
   Amplifier, Entropy Stabilizer return flags nothing reads. Wire each effect into its consumer.
3. **2 unreachable items** — Matrix Clipper, Instant Repair Kit defined but never dropped.
4. **`defragResistanceBonus` / `movementRangeBonus`** stored but never read.
5. **Combat timer is cosmetic** — `defragTimerExpired()` never fires.
6. **Defrag item drop forced** — un-force the testing drop at `DefragBotService:508`.
7. **`trade` can't complete** — add the `offer`/accept handler (service already exists but dead).
8. **Repair gives no reward** + **simultaneous-repair race condition** unresolved.

### B. Finish half-wired subsystems
9. **`unlock_symbol`** (nonce/flag) — the documented Phase-1 quest is unbuilt (`currentTask.md`).
10. **Logic Daemon endgame** — collecting 4 symbols leads to a string, not a fight.
11. **Puzzle rooms** exist but have no discoverable path from normal play.
12. **HUD parity** — ~37 commands unavailable in HUD; history-scroll stub.
13. **Expose the nonce/variable economy** — `ElementalNonce`/`HiddenVariable` are wired into the
    puzzle subsystem but players have no path to reach it (same root cause as #11). Wire-in, don't delete.

### C. Planned but 100% greenfield (from `lambda.md` / `gameNotes.md`)
14. **Turn-based / dice movement** ← user-requested top priority (see proposal below)
15. **Theft mechanic** — scan player → grep pid → mini-game → steal item
16. **Player-editable `username.groovy`** class files w/ public/private attrs + trap methods
17. **Git bare-repo save points** — add/commit/push checkpoints, reset-to-respawn, pull-to-steal
18. **Game modes** — Node (FFA) vs Cluster (7v7 teams, infiltrators, decoys, scouts)
19. **`vim`** editor (only `ls`/`cat` exist; filesystem is read-only hardcoded output)
20. **GPIO / LED hardware** — not even stubs

---

## How to play & test

### Manual
- Run: `./gradlew bootRun` then `telnet localhost 23` (port is now configurable —
  `lambda.telnet.port` in `application.yml`).
- Create char: Enter → username → display name (3–30 chars) → avatar (1–6).
- Movement is `cc <x>,<y>` (NOT `move`). Example: `cc 3,7`.

### Automated gameplay harness (Spock integration)
A scripted telnet **player-bot** drives a real socket against the running server and
asserts on responses — this is the regression net for the game.

- **Client:** `src/integration-test/groovy/ysap/LambdaTelnetClient.groovy` — handles telnet
  IAC negotiation, strips ANSI, drives login/creation, runs `command()` and waits for the
  `level:(x,y) >` prompt. Reusable; can also point at a live dev server on port 23.
- **Spec:** `src/integration-test/groovy/ysap/GameplayHarnessSpec.groovy` — `@Stepwise`
  scenario: create character → status → `cc` move → scan → inventory → unknown cmd → help.
- **Run:** `./gradlew integrationTest --tests ysap.GameplayHarnessSpec`
- The test env boots the telnet server on port **2323** (`environments.test.lambda.telnet.port`)
  so it never collides with a dev server on 23 or needs root.
- **Note:** the webdriver-binaries plugin fails on Apple Silicon; `build.gradle` disables its
  driver-config tasks so `integrationTest` runs. Old `test_repair.sh` / `repair_test.groovy`
  are stale (wrong port 8181 + nonexistent `move north`) — superseded by this harness.

> To add coverage for a feature: add a `command(...)` step to `GameplayHarnessSpec` and assert
> on the returned text. HUD mode (ANSI alt-screen) is not scriptable this way — target classic mode.
</content>
</invoke>
