# Lambda — Full Gameplay QA Sweep (2026-06-09)

Live play-through of every feature on a real telnet connection (port 23), driven through
tmux with `tools/qa.sh`; UI verified via `freeze` screenshots in `uishots/`.

## Verdict
Most features **work and render well**. One **critical** defect (connection-killing crash in
defrag combat) and a closely-related **session-cleanup leak** undermine combat + targeted
social messaging. A handful of minor display bugs and one stale doc command.

---

## ✅ Works + looks good
| Feature | Result |
|---|---|
| Character creation / login (new + existing) | ✅ clean status box, avatar art, prompt |
| status / inventory / map / help / ls | ✅ all render well; `map` grid is excellent |
| scan | ✅ detects fragments/entities/health |
| cc teleport | ✅ |
| dados dice roll + 4s animation | ✅ ASCII dice + pips render cleanly |
| move <dir> <count> per-axis economy | ✅ lock-per-axis, over-budget reject, roll-first guard |
| 10s auto-roll | ✅ "⏱ 10s elapsed — auto-rolling..." fires + animates |
| turn rotation gate (Stage 2b) | ✅ |
| repair mini-game (slot machine) | ✅ start/cycle/lock, **win + fail paths**, +30 bits persists, coord restored |
| fragments: cat / pickup / fragment_file | ✅ (one display bug, below) |
| fusion guard | ✅ "Need at least 3 identical fragments" |
| entropy status / refresh, mining | ✅ guards + display correct |
| heap: enter (`heap`), echo broadcast, pay transfer | ✅ broadcasts reach everyone |
| trade: interface, offer, **accept completes the trade** | ✅ logic end-to-end |
| recurse (usage + ethnicity-gated activation) | ✅ defend activated, others correctly refused |
| symbols / unlock_symbol / invoke guards | ✅ 0/4 gates render cleanly |
| HUD mode: enter, command parity, position tracking, exit | ✅ |
| defrag: encounter spawn, `defrag -h`, `cat /proc/defrag/<id>` | ✅ render + analysis correct |

Endgame full win-path and fusion-success are covered by integration tests
(DaemonSpec, SymbolUnlockSpec, fusion specs).

---

## 🔴 CRITICAL — defrag combat crashes the player's connection
- **Symptom:** mid-combat (observed on `grep`), the session goes dead — no output, no prompt,
  every later command ignored. The whole telnet connection is hung.
- **Server log:** `HibernateOptimisticLockingFailureException` / `StaleStateException` updating
  `defrag_bot`, thrown out of `DefragBotService.handleDefragEncounter` →
  `TelnetServerService.processGameCommand:807` → the client thread (`Thread-12`) → dies uncaught.
- **Root causes (two, structural):**
  1. **`processGameCommand` (TelnetServerService:555) has no try/catch.** Any exception in any
     command handler propagates out and kills the connection thread.
  2. **The defrag bit-drain re-saves a long-lived *detached* `DefragBot`** held in
     `activeDefragSessions` every 5s (DefragBotService:147-157), across transactions, racing
     the auto-defrag background thread → optimistic-lock version mismatch → `StaleStateException`.
- **Impact:** defrag combat (a headline feature) is unwinnable once the race fires; the kill +
  reward path could not be exercised live.

## 🔴 CRITICAL — session cleanup leak → ghosts (same family)
- **Symptom:** "Total clients connected: 4" with only 2 live clients; `scan` reports phantom
  "entities nearby"; **`pm` and trade-offer notifications never reach the recipient** even though
  the sender sees "sent". `pay`/`echo`/system broadcasts DO arrive.
- **Root cause:** cleanup (`playerSessions.remove(writer)`, `leaveMoveRotation`, etc.) sits
  **after** the main `while` loop (TelnetServerService:584) — **not in a `finally`**. When a
  thread dies (see above) the session is never removed. Targeted delivery
  (`writerForUsername` / `playerSessions.find { ... username }`) then resolves to a **dead ghost
  writer**, so private/targeted messages vanish; broadcasts iterate all sessions so they still work.
- **Fix direction:** wrap the loop body so one bad command can't kill the connection; move cleanup
  into a `finally`; (optionally) prune dead writers before targeted delivery.

---

## 🟡 Minor / cosmetic
1. **Debug `println` spam** — `DEBUG: Checking repair session ...`, `DEBUG: isPlayerInRepairSession`,
   `DEBUG: BEFORE TRY :#5`, `DEBUG: INSIDE TRY :#6`, `DEBUG: Locked value ...` print on **every
   command** (223 lines this session). Should be removed / behind a log level.
2. **Fragment Python code shows a literal `\n`** instead of a line break, in both `cat <fragment>`
   and `cat fragment_file` (e.g. `...'granted':\n    unlock_matrix_level()`).
3. **`mingle` is "Unknown command"** — docs/help (CLAUDE.md "command: `mingle`") are stale; the
   real heap-entry command is `heap`. Add an alias or fix the docs.
4. **Stale bits in `status` right after a repair win** — DB is correct (+30 persists), but the
   in-session `status` shows the pre-reward value until the session player is refreshed. Same
   "refresh session player" footgun called out in CLAUDE.md.
5. **`scan` "Nearby Damage" lists the same coord twice** — e.g. `(0,1): 0% WIPED, (0,1): 0% WIPED`.
6. **Welcome SYSTEM STATUS box** — `Connected entities: N | Status: ONLINE` line isn't padded to
   the border.
7. **Status box internal divider** wobbles a column on the Position/Bits rows.
8. **HUD truncates long messages** at the box width (no wrap), e.g. "Coordinate change blocked:
   Coordinate (3,2) has been" (cut off).
9. **Leaving heap flavor message** renders an empty `[SYSTEM]` line above "Null pointer new memory
   address. Returned to working ram".
10. **`pay` sender confirmation wraps awkwardly** ("Sent 25\n bits to QaB").

## 🟠 Merchant can become unreachable (confirmed root cause)
- The single per-level merchant is rendered at **lowest priority** in the map
  (`LambdaPlayerService.showMatrixMap`, `symbol == "." || symbol == "!"` only, line 852-866). When
  auto-defrag **wipes the merchant's coordinate**, the tile renders as `X` (which takes precedence),
  so the merchant is **invisible on the map AND unreachable** (you can't `cc` into a wiped coord).
  Reproduced on two separate fresh servers — no `M` anywhere, shop/buy/sell live-untestable.
- **Auto-defrag is also very aggressive** — ~36-49% of the level wiped within tens of minutes — and
  floods the heap channel with repeated "AUTO-DEFRAG: ... coordinate destruction!" messages.
- Suggested fixes (not yet done): protect merchant tiles from auto-defrag, relocate the merchant
  when its tile is wiped, and/or render `M` above `X`.

---

## ✅ RESOLUTION (this session) — critical family FIXED + verified
Designed under the lambda-architect (APPROVE), implemented, and verified both by the harness and live:
- **Connection no longer dies on a handler exception** — `processGameCommand` is wrapped; a failing
  command now shows "⚠ Command failed: …" and returns to the prompt.
- **Session cleanup moved into a `finally`** — disconnect/death always removes the session (no ghosts).
- **Targeted delivery consolidated** to one live-writer-preferring resolver (`liveWriterMatching`);
  the 3 first-match lookups collapsed to it (DRY). `pm` + trade offers now reach the live recipient.
- **Defrag StaleStateException race fixed** — the bit-drain no longer re-saves the detached bot
  (in-memory throttle), and `handleKillCommand` / `drainPlayerBits→defragPlayer` now act on a
  freshly-loaded managed bot. **A bot was killed end-to-end live ("Earned 58 bits"), connection alive.**
- **DEBUG println spam removed** (TelnetServerService, SimpleRepairService, DefragBotService).
- **Tests:** full integration suite **68/68 green** (added 2 robustness steps: throwing-handler
  survival + disconnect-no-ghost).

### Puzzle-fragment reward path — FIXED (follow-up resolved)
- Root cause 1: `PuzzleRandomizationService.generateRandomizedVariables` omitted the required
  `elementType` on `HiddenVariable`, so `initializePuzzleSystem` threw — and because the 4-fragment
  seed shares that one `@Transactional` boundary, the seed **rolled back too**, so the reward later
  failed with "not found". Fixed by setting `elementType`. Boot now seeds all 10 maps cleanly.
- Root cause 2 (exposed by the regression test once seeding persisted): `PuzzleLogicFragment.name`
  was globally `unique: true`, but `awardPuzzleFragment` creates a player-owned **copy** with the
  same name → unique violation. Fixed: `name unique: 'owner'` (template owner==null + per-player
  copies coexist) and award now copies from the template via `findByNameAndOwnerIsNull`.
- Verified: full suite **69/69**; new `PuzzleNonceSpec` step proves the seed persists and the award
  succeeds. This also unblocks the broader puzzle/`execute`/`unlock_symbol` content (now seeded).

### Remaining (NOT addressed — follow-ups)
- `playerSessions` is an unsynchronized `LinkedHashMap` read by the resolver while mutated by client
  threads — make it concurrent or lock the touch points (architect note, non-fatal).
- Merchant-unreachable (above) + the minor display bugs (literal `\n`, `mingle` alias, scan dup,
  stale-bits-after-repair, box padding, HUD wrap, pay-confirm wrap, heap-exit `[SYSTEM]` line).

## ⏸️ Could not verify live (blocked, not failing)
- **defrag kill + reward** — blocked by the crash.
- **shop / buy / sell** — merchant coordinate was auto-wiped; hunting risked the defrag crash.
- **special-item `use` effects** — needs an acquired item (defrag/merchant reward); the
  not-owned guard works, and effects are covered by SpecialItemEffectsSpec.
- These should be re-run on a clean reboot once the crash is fixed.
