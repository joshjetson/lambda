# Lambda — Phased Implementation Plan

> Authored by the Lambda Architect. Ordering principle (per the human): **finish the work
> already started first, then move to greenfield.** Every half-baked item below was
> re-verified against real source (file:line + the greps shown), not against comments or docs.
> Doctrine is binding: **NO NEW SERVICES**, map/O(1) dispatch over switch/if-chains, reuse
> (especially dead code) before writing new, business logic in the owning service, command
> handlers are 1-line delegations, and **each phase must leave `GameplayHarnessSpec` GREEN
> before the next phase starts.**
>
> Harness run command (the gate, every phase): `./gradlew integrationTest --tests ysap.GameplayHarnessSpec`
> Current harness baseline: 7 steps (create → status → `cc` → scan → inventory → unknown cmd → help),
> verified in `src/integration-test/groovy/ysap/GameplayHarnessSpec.groovy:33-92`.

---

## Verification corrections to STATUS.md (found during audit)

Before the plan, the facts STATUS.md got wrong (code wins):

1. **`ElementalNonce` is NOT an orphan.** STATUS.md:79 says "Defined, queried nowhere (orphan)."
   In reality it is created, persisted, and queried:
   - `PuzzleRandomizationService.groovy:151` `new ElementalNonce(...)`, `:209` / `:431` finders.
   - `PuzzleService.groovy:393` `ElementalNonce.findByNonceName`, `:413` `addToDiscoveredNonces`.
   - `PuzzleKnowledgeTradingService.groovy:275-305` `ElementalNonce.get` + `addToDiscoveredNonces`.
   The real problem is not "orphan domain" but "**the puzzle subsystem that owns nonces has no
   discoverable player entry path**" — same root cause as STATUS.md item #11. Do **not** delete
   `ElementalNonce`; it is load-bearing for the nonce economy. Same applies to `HiddenVariable`
   (`PuzzleRandomizationService:187`, `CompetitivePuzzleService:144`, `collect_var` path).
   → Plan treats these as **wire-in/expose**, not remove.

2. **`trade` is more wired than STATUS.md implies.** STATUS.md:93 says the menu is read-only.
   True, but the menu **already calls** `puzzleKnowledgeTradingService.getTradeablePuzzleKnowledge(player)`
   (`ChatService.groovy:429`) — so the *read* side of the dead service is live. Only the *write*
   side (`executePuzzleKnowledgeTrade()`, `PuzzleKnowledgeTradingService.groovy:173`, **zero callers**)
   and an `offer` command are missing. This makes wiring cheaper than a rewrite.

3. **The heap inner-command dispatch is an if-chain, not a map.** `ChatService.groovy:191-265`
   is a sequence of `if (trimmedCommand...startsWith(...))`. There is **no `offer` branch** — so
   typing `offer ...` after `trade` currently falls through to "broadcast as chat / unknown".
   This is the exact dispatch shape the doctrine says to convert to a map.

4. **The `INITIATE_REPAIR:` signal-parse is duplicated 3×** (`TelnetServerService.groovy:185`,
   `HudService.groovy:601`, `HudService.groovy:1022`) — a DRY violation to fix while in the area.

Everything else in STATUS.md's matrix matched the code on re-verification.

---

## Cross-cutting design decisions (apply throughout)

### D1. The "active effect" problem (blocks several items)
Six special items and all recurse abilities return an `effect:`/text flag that **nothing reads**
(`SpecialItemService.groovy:217-323`; `LambdaPlayerService.groovy:861-887`). The consumers
(encounter roll, bit grant, pickup, entropy calc, defrag resolution) have no way to ask "does this
player have an active X effect right now?"

**Decision — reuse the existing `SpecialItem` row as the effect-state carrier; do NOT add a new
domain or service.** `SpecialItem` already has `isActive`, `expiresAt`, `itemType`, and
`useSpecialItem` already flips `isActive=true`/sets `expiresAt` when `duration>0`
(`SpecialItemService.groovy:90-93`). The only missing piece is a **single O(1) query helper** on the
service that already owns items:

```groovy
// SpecialItemService — ONE new public method, the single consumption gate.
// O(1)-ish: indexed find by owner+type+active, no business logic in callers.
boolean hasActiveEffect(LambdaPlayer player, String itemType) {
    SpecialItem.withTransaction {
        def it = SpecialItem.findByOwnerAndItemTypeAndIsActive(player, itemType, true)
        if (it && it.expiresAt && it.expiresAt < new Date()) { it.isActive = false; it.save(failOnError:true); return false }
        it != null
    }
}
// consumeEffect(player, itemType): set isActive=false (for one-shot effects like bit_multiplier).
```

For **one-shot, durationless** effects (Bit Multiplier, Swap Space, Respawn Cache, Logic Amplifier,
Stealth Cloak-as-next-move), give those item definitions a small `duration` so `useSpecialItem`
already marks them active, and the consumer calls `hasActiveEffect` then `consumeEffect`. This keeps
**all** effect bookkeeping inside `SpecialItemService` (the owner), with consumers doing a 1-line
O(1) ask. No new service, no new domain, no scattering.

### D2. Recurse needs persistent state — add fields to `LambdaPlayer`, logic stays in services
`LambdaPlayer` has **zero** recursion fields (grep for `recurse|cooldown|charges|recursion` in
`LambdaPlayer.groovy` → no hits). The recurse handler's own comment admits it:
`LambdaPlayerService.groovy:848 "// Check recursion cooldown and charges (TODO: implement tracking fields)"`.
Add data-only fields (`recursionCharges`, `maxRecursionCharges`, `lastRecursionUse`,
`activeRecursionEffect`, `recursionEffectExpires`) to the domain (data + constraints only — doctrine
§3). All charge/cooldown logic stays in `LambdaPlayerService.handleRecurseCommand`.

### D3. Effect/ability dispatch must be maps, not switches
- `SpecialItemService.executeItemAbility` (`:156-182`) is a **switch** → convert to
  `Map<String,Closure> itemAbilityHandlers` keyed by `itemType`.
- `LambdaPlayerService.handleRecurseCommand` (`:852-891`) is a **switch** → convert to
  `Map<String,Closure> recurseHandlers` keyed by ability, each closure validating ethnicity +
  applying the effect.
- `HudService.processGameCommandForHud` (`:733`) is a **switch** duplicating ~12 classic commands →
  refactor to reuse `TelnetServerService.commandHandlers` (Phase 9).
- The new heap `offer` parsing routes through a `Map<String,Closure>` prefix-handler in `ChatService`
  (Phase 5), replacing the if-chain at `:191-265`.

---

# PHASES

Each phase is independently `git reset --hard`-able and ends harness-green.

---

## PHASE 0 — Lock in the regression net (no behavior change)  ✅ DONE (architect-approved; GameplayHarnessSpec 12/12)

**Why first:** the gate is only as good as its coverage. Before changing systems, extend the harness
with assertions for the commands the later phases touch, so regressions surface immediately.

- **Reuse audit:** `LambdaTelnetClient` already drives login + `command()` + prompt-wait
  (`src/integration-test/groovy/ysap/LambdaTelnetClient.groovy`). No new harness infra needed.
- **Approach:** add benign steps to `GameplayHarnessSpec` that exercise (and pin current behavior of)
  `symbols`, `use <bogus>`, `recurse` (no-arg usage), `heap`→`trade self`→`exit`, `repair 9 9`
  (expect "not damaged"/usage). These assert the *current* responses so a later phase that changes
  them must consciously update the assertion.
- **Placement:** test file only.
- **Test gate:** `./gradlew integrationTest --tests ysap.GameplayHarnessSpec` green with the new steps.
- **Risk/rollback:** zero production code touched; revert the spec.

---

## PHASE 1 — Un-force the defrag item drop + make the combat timer real  ✅ DONE (architect-approved; +DefragTimerSpec 3/3)

Smallest, highest-confidence "finish what's started." Two independent one-liners + one wiring.

### 1a. Remove the forced testing drop
- **Evidence:** `DefragBotService.groovy:507-508` — `// TEMPORARY: Force special item drop for testing`
  then unconditional `rewards.specialItem = generateRandomSpecialItem()`, which overrides the real
  probability roll at `:502-505`.
- **Approach:** delete lines 507-508. The probabilistic path at `:502-505` already exists.
- **Placement:** `DefragBotService` only.
- **Test gate:** harness step kills a bot is not yet feasible (encounter is RNG); instead assert via a
  targeted check: spawn is RNG so add a harness step that repeatedly `cc`'s across non-safe coords and,
  IF an encounter occurs, runs the full `defrag -h`→`cat`→`grep`→`kill -9` chain and asserts a reward
  line. Because RNG may not trigger, gate this 1a on the **existing** combat chain still working
  (no assertion regression) + a manual transcript noting drops are now occasional. State explicitly:
  drop-rate change is **not** harness-deterministic; verified by reading the restored `:502-505` branch.
- **Risk/rollback:** trivial; re-add the two lines.

### 1b. Wire the cosmetic combat timer
- **Evidence:** `DefragBotService.groovy:472 defragTimerExpired(...)` exists but has **zero callers**
  (grep confirmed); timer is displayed at `CoordinateStateService.groovy:387` only.
- **Reuse audit:** `AutoDefragService` already owns a background scheduler — reuse that scheduling
  pattern; do **not** create a timer service. The simplest correct wiring: when an encounter spawns
  (`CoordinateStateService.groovy:379`), schedule a one-shot via the existing scheduler that calls
  `defragBotService.defragTimerExpired(bot, player)` after `bot.timeLimit` seconds; cancel it when the
  bot is killed (`kill -9` path) or the player leaves the coord.
- **Approach:** store the scheduled future keyed by `botId` in an in-memory map on `DefragBotService`
  (mirrors how `SimpleRepairService` holds `activeSessions`). Cancel on kill.
- **Placement:** `DefragBotService` (owns bots + the already-written expiry method).
- **Test gate:** add a harness step only if an encounter is forced. Since spawn is RNG, gate via a
  short unit-style assertion is not available in this harness; instead the verification is: encounter
  during the Phase-1 chain, wait > timeLimit without acting, assert the buffer-clear/reset message.
  If RNG prevents a deterministic encounter in CI, document as **manual telnet transcript** + the
  cancel-on-kill path covered by the existing kill step.
- **Risk/rollback:** the scheduled task only fires if the player ignores the bot; cancel-on-kill keeps
  current happy path identical. Revert the scheduling block.

---

## PHASE 2 — Recurse abilities become real (charges, cooldowns, applied effects)  ✅ DONE (architect-approved; +RecurseSpec 6/6, GameplayHarnessSpec 15/15)

> Carryover follow-ups (non-blocking, tracked): (1) remove now-unused `writer` param from
> `handleRecurseCommand` + its `commandHandlers['recurse']` call site in a later sweep;
> (2) Phase 10's `move` system MUST consume `movementRangeBonus` or `recurse movement` stays inert.
> Also done alongside: swept 2 pre-existing `broadcastSystemMessage` latent crashes → `sendSystemMessage`.


Completes STATUS.md TODO #1 and revives `defragResistanceBonus`/`stealthBonus` consumption.

- **Reuse audit:**
  - The encounter roll **already reads `player.stealthBonus`** at `CoordinateStateService.groovy:375`
    (`def totalAvoidanceBonus = (player.stealthBonus ?: 0.0)`). So `recurse stealth` only needs to
    *set* a temporary `stealthBonus` and the existing consumer honors it — no consumer code needed.
  - `defragResistanceBonus` is read **only** in two display methods
    (`LambdaPlayerService.groovy:474,1031`) — NOT in the encounter/combat path. The combat resolution
    in `DefragBotService` is where `recurse defend` must apply. Add the read there (1 line in the
    encounter-chance calc, alongside the existing stealth term).
  - `EntropyService` fusion (`:333`) already has a `fusionSuccessBonus` field plumbed; `recurse fusion`
    sets it temporarily. `EntropyService` mining honors `miningEfficiencyBonus`; `recurse mine` sets it.
- **Approach (map dispatch):** convert the recurse switch (`LambdaPlayerService.groovy:852-891`) to
  `Map<String,Closure> recurseHandlers` keyed by ability. Each closure: (1) check
  `managedPlayer.recursionCharges > 0` and cooldown via `lastRecursionUse`; (2) verify ethnicity;
  (3) set the relevant temporary bonus field + `activeRecursionEffect`/`recursionEffectExpires`;
  (4) decrement charges, set `lastRecursionUse`. A shared `private grantRecursion(player, effectClosure)`
  helper does the charge/cooldown bookkeeping once (DRY) — no per-ability duplication.
- **State (domain, data-only):** add to `LambdaPlayer` — `Integer recursionCharges = 2`,
  `Integer maxRecursionCharges = 2`, `Date lastRecursionUse`, `String activeRecursionEffect`,
  `Date recursionEffectExpires` (all nullable where apt; constraints only). Charges refill on the
  existing daily/entropy refresh tick (reuse `EntropyService` daily-reset logic — already runs on login).
- **Effect expiry:** reuse the same on-demand expiry pattern entropy uses — when a consumer reads a
  temporary bonus, if `recursionEffectExpires < now`, zero the bonus. Centralize in **one** helper
  `LambdaPlayerService.clearExpiredRecursion(player)` called at the top of status/encounter reads.
- **Placement:** logic in `LambdaPlayerService` (owns recurse); the single `defragResistanceBonus`
  read added in `DefragBotService`/`CoordinateStateService` encounter calc; bonus *setters* only.
  `commandHandlers['recurse']` stays a 1-line delegation (already is — `TelnetServerService`).
- **Test gate:** harness steps: `recurse` (no arg → usage list), `recurse stealth` for the bot's
  ethnicity (assert "activated"), then `status` asserts charges decremented (e.g. `1/2` or
  "Recursion Charges"). Because the *effect* (avoidance) is statistical, the deterministic assertion
  is **charge consumption + cooldown message on immediate re-use** (`recurse stealth` again →
  "on cooldown"/"no charges"). Run the gate.
- **Risk/rollback:** new nullable domain fields are additive (H2 create-drop, no migration). If a
  bonus setter misfires it only makes the player slightly stronger; bounded by domain `max:`
  constraints. Revert domain fields + the map.

---

## PHASE 3 — Wire the 6 stub special-item effects + drop the 2 unreachable items  ✅ DONE (architect-approved; +SpecialItemEffectsSpec 7/7, +DefragTimerSpec SWAP_SPACE case → 4/4)

> Gate caught 3 real bugs during dev: pre-existing null-collection NPE in `findPlayerItem`;
> Groovy `0`-is-falsy truthiness (wiped coord read as healthy); `applyBitModifiers` called on the
> wrong service (guaranteed `MissingMethodException` on the SWAP_SPACE branch, found by an architect probe).


Completes STATUS.md TODOs #2 and #3. Depends on D1 (`hasActiveEffect`/`consumeEffect`) and the
map-dispatch refactor of `executeItemAbility`.

### 3a. Convert `executeItemAbility` switch → `itemAbilityHandlers` map
- **Evidence:** `SpecialItemService.groovy:156-182` switch.
- **Approach:** `Map<String,Closure> itemAbilityHandlers = ['SCANNER_BOOST': this.&executeScannerBoost, ...]`.
  O(1), open/closed. Keeps each `executeX` method; only the selection changes.
- **Test gate:** existing `use scanner_boost` behavior unchanged (covered by Phase 0 `use` step).

### 3b. Wire each of the 6 flag-only effects into its real consumer (the heart)
For each, the consumer asks `specialItemService.hasActiveEffect(player, TYPE)` (O(1)) and, on use,
`consumeEffect`. **No consumer holds business logic beyond the ask** — the effect's meaning lives in
`SpecialItemService`.

| Item (def) | Consumer site to wire | Effect applied |
|---|---|---|
| `STEALTH_CLOAK` (`:217`) | `CoordinateStateService.groovy:375-376` encounter roll (already sums `stealthBonus`) | add `hasActiveEffect('STEALTH_CLOAK')` → ×0.75 avoidance for next move, then consume |
| `BIT_MULTIPLIER` (`:225`) | the **single** bit-grant path — `EntropyService` reward/mining grant + `DefragBotService` reward grant | if active, double the next bit grant, then consume. Centralize so it's one check, not scattered |
| `RESPAWN_CACHE` (`:233`) | `DefragBotService` defeat/reset to (0,0) (`DefragBotService:81-94` drain-reset + reward path) | if active, set respawn to cached coord instead of (0,0) |
| `SWAP_SPACE` (`:244`) | same defrag-attempt path | if active, block the defrag and grant +50 bits instead of damage |
| `LOGIC_AMPLIFIER` (`:281`) | `LambdaPlayerService` pickup (`:1095`) | if active, +1 powerLevel on the picked-up fragment, consume |
| `ENTROPY_STABILIZER` (`:317`) | `EntropyService` decay calc (`:20-112`) | if active + unexpired, skip decay for the window |
- **Reuse note:** `BIT_MULTIPLIER` exposes that **bit-granting is currently duplicated** across
  `EntropyService`, `DefragBotService`, `LambdaMerchantService`, `ChatService`. To honor DRY, route the
  multiplier check through **one** place. Minimal-risk option for this phase: a single
  `SpecialItemService.applyBitModifiers(player, baseAmount)` helper that the reward sites call when
  granting (they already each call `player.bits += x` — replace with `+= applyBitModifiers(...)`).
  This is the one acceptable touch across multiple files because it *removes* divergence rather than
  adding it. (Full bit-grant unification is out of scope — outline only.)

### 3c. Add the 2 unreachable items to the drop pool
- **Evidence:** `MATRIX_CLIPPER`/`INSTANT_REPAIR_KIT` defined (`SpecialItem.groovy:35-36`,
  `SpecialItemService.groovy:526,535`) but absent from `generateRandomSpecialItem`’s pool.
- **Approach:** add both to the weighted pool in `generateRandomSpecialItem` (DefragBotService) with
  appropriate rarity. Give them real consumers: `INSTANT_REPAIR_KIT` → call
  `coordinateStateService.repairCoordinate(...)` directly (reuse Phase-7 reward path); `MATRIX_CLIPPER`
  → bypass one accessibility check on next `cc` (reuse the `cc` accessibility gate in
  `CoordinateStateService.handleCoordinateChange`).
- **Placement:** `DefragBotService` (pool), `SpecialItemService` (effect), consumers as above.
- **Test gate:** harness — give the bot player an item deterministically is hard via telnet; instead
  test the **consumer** path: for effects reachable by command (`use entropy_stabilizer` then `status`
  shows entropy held; `use logic_amplifier` then `pickup` shows +1 power), assert over telnet. For
  encounter-only effects (stealth/respawn/swap), gate on the Phase-1 combat chain + manual transcript,
  stated explicitly as non-deterministic. Run the gate.
- **Risk/rollback:** each wire is guarded by `hasActiveEffect` returning false by default, so absent an
  active item, behavior is identical to today. Revert per-consumer.

---

## PHASE 4 — Repair mini-game: reward + two-player race condition (the HEAD-commit TODO)  ✅ DONE (pre-AND-post architect review; +RepairRaceSpec 3/3 → suite 35/35)

> Pre-implementation review caught a real design flaw: the planned "re-check health + optimistic lock"
> race fix would NOT fire under H2 READ_COMMITTED (both players still win). Replaced with an atomic
> conditional UPDATE (`... where health <= 0`, rows-affected = the claim) in `tryClaimRepair`.


Completes STATUS.md TODOs #8. This is the open item in the latest commit message.

- **Evidence:**
  - No reward: `SimpleRepairService.groovy:147-198 completeRepair` only calls
    `coordinateStateService.repairCoordinate(...)` (`:162`) and renders a box — no bits/items/fragments.
  - Race: `activeSessions` is a per-username map; two players can each open a session on the same coord
    (`startRepair`/`handleSpaceBarPress`), both reach `completeRepair`, both `isCorrect()`, both call
    `repairCoordinate`, both "win." No re-check of coord state at completion, no optimistic lock, no
    loser eviction.
- **Reuse audit:**
  - Reward granting already exists in `DefragBotService` reward generation + `SpecialItemService`/
    `EntropyService` bit grants. Reuse `specialItemService.applyBitModifiers` (Phase 3b) + the existing
    `player.bits +=` pattern; optionally drop a fragment via the existing
    `generateRandomLogicFragment` helper. **No new reward service.**
  - `CoordinateState` is the contended row; GORM gives optimistic locking via a `version` column by
    default — reuse it. The completion check becomes: re-load the `CoordinateState`, verify it is
    **still damaged** (`health <= 0` / not yet repaired); the first committer wins, the second's
    `save` either sees it already repaired (guard) or throws `OptimisticLockingFailureException`
    (catch → "another entity completed this repair first").
- **Approach:**
  1. In `completeRepair` success branch, wrap in `CoordinateState.withTransaction`, re-fetch the coord,
     `if (coord.health > 0) → loser path` (someone already fixed it): show "repair already completed by
     another entity," **stop their session**, no reward. Else repair + **grant reward** + win.
  2. Catch `OptimisticLockingFailureException`/stale-state → same loser path. This satisfies the commit
     TODO: "the other should be kicked out immediately upon repair and not get the prize."
  3. Reward = bits (via `applyBitModifiers`) + chance of a fragment/special item, mirroring defrag rewards.
- **Placement:** `SimpleRepairService.completeRepair` (owns the mini-game); reward helpers reused from
  existing services; the still-damaged guard reuses `CoordinateStateService`.
- **Test gate:** single-player harness step: damage a coord (or pick a known-damaged one created by
  `AutoDefragService`), `repair x y`, drive the digit-lock to success, assert "REPAIR SUCCESSFUL" **and**
  a reward line ("+N bits"). The **race** path (two simultaneous players) is not expressible in the
  single-client harness — verify with a **targeted two-client manual transcript** or a small dedicated
  integration step that opens two `LambdaTelnetClient`s on the same coord; state which is used. Run the gate.
- **Risk/rollback:** the still-damaged guard is a pure add — if no contention, the path is identical to
  a normal success plus a reward. Optimistic locking is GORM-native (no schema change beyond the
  `version` column GORM already maintains). Revert `completeRepair`.

---

## PHASE 5 — Make `trade` completable by wiring the dead `executePuzzleKnowledgeTrade()` + `offer`  ✅ DONE (pre+post architect review; +TradeSpec 5/5, +TradeHarnessSpec two-client 1/1; UI verified via two-pane play-through)

> Pre-review caught a grief exploit: wiring the dead method as-is would let A force-charge B's wallet
> with no consent. Added an `offer`→`accept` consent step; nothing moves until the buyer accepts.
> Heap if-chain → `heapHandlers` map. New atomic `LambdaPlayerService.transferFragment` reusing `deductBits`.
> UI follow-up noted: AUTO-DEFRAG system broadcasts now flood the heap chat (the earlier sweep made them
> visible) — consider throttling / not routing them to heap.


Completes STATUS.md TODO #7. **Wire dead code, do not rewrite.**

- **Reuse audit:**
  - `PuzzleKnowledgeTradingService.executePuzzleKnowledgeTrade(seller, buyer, itemType, itemId, price)`
    is **fully written and has zero callers** (`:173`, grep confirmed). It already validates ownership
    (`:191 getTradeablePuzzleKnowledge`), transfers nonces/variables (`:265-305`), and handles bits.
  - The trade menu (`ChatService.handleTradeCommand:389-484`) already lists offer syntax
    (`offer F<num> <quantity> <price>`, `offer PF<num> <price>`, etc.) and already calls the service's
    read side (`:429`). The **only** missing piece is parsing `offer ...` and calling the existing
    write method (plus standard-fragment transfer, which mirrors the existing `pay` atomic transfer at
    `ChatService:299`).
- **Approach (map dispatch — fixes the if-chain):**
  1. Convert the heap inner-command if-chain (`ChatService.groovy:191-265`) to a
     `Map<String,Closure> heapHandlers` keyed by the leading token (`echo`,`pay`,`pm`,`trade`,`list`,
     `who`,`help`,`exit`,**`offer`**,**`cancel`**). O(1), and the new `offer`/`cancel` slot in cleanly.
  2. `offer` closure parses the prefix (`F`/`PF`/`V`/`N`/`S`) + index + price against a held
     **pending-trade context** (store the `targetPlayer` from the last `trade <entity>` in the existing
     in-memory session map `telnetServerService.playerSessions`/a per-user map in `ChatService`, same
     pattern as `SimpleRepairService.activeSessions`). For `PF/V/N/S` → delegate to
     `puzzleKnowledgeTradingService.executePuzzleKnowledgeTrade(...)`. For `F` (standard fragment) →
     reuse the atomic-transfer pattern already in `pay`/the fragment-quantity logic.
  3. `cancel` clears the pending-trade context.
- **Placement:** parsing/dispatch in `ChatService` (owns heap); the actual knowledge transfer stays in
  `PuzzleKnowledgeTradingService` (owns it). No new service.
- **Test gate:** the single-client harness can't trade with a second party deterministically; add a
  **two-client step** (two `LambdaTelnetClient`s both `heap`): client B `trade A`, `offer F1 1 10`,
  assert A receives the fragment and bits move. If two-client is deemed too heavy for the stepwise
  spec, add a focused integration method using two clients and gate on it. Run the gate.
- **Risk/rollback:** converting the if-chain to a map preserves every existing branch verbatim (echo/
  pay/pm/list/help/exit) — Phase 0 pinned those, so any regression fails the harness immediately.
  `offer`/`cancel` are purely additive. Revert the map + offer closure.

---

## PHASE 6 — `unlock_symbol` quest (reconcile `currentTask.md` with the doctrine)  ✅ DONE (pre+post architect review; +SymbolUnlockSpec 4/4 + harness leg → 46/46; UI verified)

> Implemented Option B (architect-approved refinement of the plan): the discovered `ElementalNonce`'s
> `commandFlag` IS the per-element key — +1 field (`isHidden`) instead of +3, no symbol↔nonce creation
> coupling. `unlock_symbol <type> <flag>` at the symbol's coord. switch→`symbolGranters`/`symbolCheckers` maps.
> UI play-through caught a pre-existing staircase bug in the `symbols` command (`\n`→`\r\n`) — fixed.


Completes STATUS.md TODO #9. `currentTask.md` already drafted this; I **revise** its design where it
violates doctrine (see "Doctrine revisions" at bottom).

- **Reuse audit:**
  - `ElementalSymbol` domain lacks `requiredNonce`/`requiredFlag`/`isHidden` (confirmed:
    `grep -n` in `ElementalSymbol.groovy` → not present; STATUS.md:78 correct).
  - **But the nonce/flag economy already exists**: `ElementalNonce` carries `commandFlag`,
    `chemicalClue`, `elementType` (used in trade menu `ChatService:455`), and players accumulate
    `discoveredNonces` via the puzzle/defrag paths (`PuzzleService:413`, `DefragBotService:526`).
    `currentTask.md` invents a fresh `--nonce=0xXXXX` string on the symbol and seeds clue fragments —
    that **duplicates** the existing nonce concept. **Revision:** the symbol's `requiredNonce` should
    reference an **existing `ElementalNonce`** the player must have `discovered`, not a throwaway hex
    string. This wires `ElementalNonce` (the "orphan" STATUS.md miscalled) into the endgame — turning
    two half-finished systems into one finished one.
  - `ElementalSymbolService` already grants symbols + has `symbols` status — extend it (don't add a
    service), exactly as `currentTask.md` concluded.
- **Approach:**
  1. Domain (data only): add `requiredFlag`, `isHidden=true`, and link to the required nonce
     (`String requiredNonceName` referencing `ElementalNonce.nonceName`) on `ElementalSymbol`.
  2. `ElementalSymbolService.handleUnlockSymbolCommand(command, player)` — parse
     `unlock_symbol <type> <flag>` at the symbol's coordinate; validate flag matches `requiredFlag`
     AND the player's `discoveredNonces` contains `requiredNonceName`; on success set the symbol
     boolean + `isHidden=false`. **No switch for symbol type** — drive the four booleans via a
     `Map<String,Closure> symbolGranters = ['AIR': {p-> p.hasAirSymbol=true; p.airSymbolAcquired=new Date()}, ...]`
     (replaces `currentTask.md`'s switch, doctrine §2).
  3. `commandHandlers['unlock_symbol']` = 1-line delegation (as `currentTask.md` Step 4).
- **Placement:** `ElementalSymbolService` (owns symbols); domain fields on `ElementalSymbol`;
  reuse `ElementalNonce` for the secret. **No BootStrap clue-fragment seeding** (revision: clues live
  on existing nonces' `chemicalClue`, already populated by `PuzzleRandomizationService`).
- **Test gate:** harness — `cc` to a symbol coord (test can read a symbol's coord by giving the bot a
  known nonce via the existing grant path, or assert the *negative* paths deterministically):
  `unlock_symbol water --wrongflag` → "Invalid flag"; `unlock_symbol water --h2o` without the nonce →
  "missing nonce"; then `symbols` unchanged. The positive path requires the player to hold the nonce —
  drive via the puzzle/defrag grant or seed one in the test setup, then assert "SYMBOL ACQUIRED" and
  `symbols` shows it. Run the gate.
- **Risk/rollback:** new nullable fields additive; `unlock_symbol` is a new, unique key (grep: no
  conflict). Symbols already hidden from scan, so the only new surface is the command. Revert fields +
  handler + map.

---

## PHASE 7 — Logic Daemon endgame (turn the congratulatory string into a mechanic)  ✅ DONE (pre+post architect review; +DaemonSpec 4/4 + harness leg → 51/51; UI verified)

> Implemented the daemon as an `isDaemon` DefragBot reusing the full combat/reward chain (no new boss
> service). `invoke` gated on all 4 symbols (re-checked from DB). Victory loop: reset 8 symbol fields +
> ascend a level (cap 10 → escaped). Architect pre-review caught 3 binding fixes: timer-can't-win (only
> the kill branch calls `onDaemonDefeated`), safe-zone bypass (`createBot` skips the spawn guard), and
> 8-field reset in one tx. Coordinate-gate dropped (no daemon-location data model existed; 4 symbols ARE
> the gate). NOTE: the post-victory level's *puzzle/nonce* discovery path is Phase 8's job — symbols
> remain independently acquirable via Phase 6 `unlock_symbol`, so the loop is self-sustaining meanwhile.


Completes STATUS.md TODO #10. Depends on Phase 6 (symbols must be acquirable).

- **Evidence:** the entire "boss fight" is one line: `CompetitivePuzzleService.groovy:361
  "🌟 ALL SYMBOLS COLLECTED! Ready for Logic Daemon encounter! 🌟"`. No entity, command, or mechanic.
- **Reuse audit:**
  - The defrag-bot combat chain (`defrag -h`→`cat /proc/...`→`grep`→`kill -9`) is a complete, working
    "solve a process" mechanic (`DefragBotService`). The Daemon is best modeled as a **high-difficulty,
    special defrag-style encounter gated on all 4 symbols** — reuse `DefragBotService`’s spawn/combat/
    reward machinery with a `isDaemon`/`difficulty=10` flag rather than inventing a parallel boss
    system. (`DefragBot` already has difficulty scaling and reward generation.)
  - Symbol-completeness check already exists conceptually in `ElementalSymbolService` (the 4 booleans);
    reuse a `playerHasAllSymbols(player)` predicate there.
- **Approach:** add an `invoke`/`daemon` command (1-line delegation) → `ElementalSymbolService` (owns
  symbols/endgame eligibility) verifies all 4 symbols + correct coordinate (the "daemon location" clue),
  then asks `DefragBotService` to spawn a **daemon-class** encounter the player resolves with the
  existing combat chain. Victory flips a `hasDefeatedDaemon`/level-advance flag. Keep the spawn/reward
  selection in the existing **map**-driven reward generator; no switch.
- **Placement:** eligibility in `ElementalSymbolService`; encounter reuses `DefragBotService`;
  `commandHandlers['invoke']` 1-line delegate.
- **Test gate:** harness negative paths deterministic: `invoke` without 4 symbols → "need all symbols";
  wrong coordinate → "daemon not here." Positive path requires 4 symbols (grant via test setup reusing
  the Phase-6 path) then assert the daemon encounter text + that defeating it (reusing the kill chain)
  reports victory. Run the gate.
- **Risk/rollback:** purely additive command + a flag field; reuses battle-tested combat. Revert the
  command + eligibility method.

---

## PHASE 8 — Expose the puzzle subsystem (give nonces/variables a player path) & retire dead read  ✅ DONE (8a implemented; 8b decided no-change)

> **8(b) DECISION (human, 2026):** keep the elemental endgame intentionally gated behind floor-7+ DefragBot
> rewards — new players grind combat up to the high floors first. No code change. (Options offered were:
> merchant route / lower the DefragBot gate / keep endgame-gated → chose keep.)

> Trace revealed the subsystem is FAR more wired than the plan/STATUS.md assumed (scan already surfaces
> variables + puzzle rooms; player puzzle-state inits lazily on scan). The real defect was narrow:
> **8(a) [DONE]** — `CompetitivePuzzleService.executePlayerPuzzleRoom` granted the symbol but DISCARDED
> the nonce; now it calls `puzzleService.awardNonce(...)` so Path A feeds Path B (`unlock_symbol`) + trade.
> **8(b) [ESCALATED]** — the real reachability problem is a DESIGN choice: nonces + puzzle-fragments drop
> ONLY from floor-7+ DefragBots (no merchant/low-floor route), so a new player can't start the elemental
> quest. 3 options put to the human (keep endgame-gated / merchant route / lower the DefragBot gate).
> Backlog caveat: `ElementalNonce.nonceName` has no unique constraint → `awardNonce`'s `findByNonceName`
> is ambiguous across sessions (pre-existing, shared with DefragBotService caller).


Completes STATUS.md TODO #11/#13 (the real "orphan" fix). This makes Phases 6–7's nonce requirement
actually reachable in normal play.

- **Evidence:** `PuzzleRandomizationService` creates nonces/variables per session/map
  (`:151,187`), `PuzzleService` can grant them (`collect_var` path `:174`, nonce path `:393-413`),
  but STATUS.md:69 confirms "no clear path from normal play to reach/solve them." `scan` mentions
  `collect_var` (`GameSessionService:308`) only when a variable is already placed at the coord.
- **Reuse audit:** all machinery exists — `PuzzleRandomizationService.randomize...`, `PuzzleService`
  execute/chmod, `collect_var`. The gap is **invocation timing**: when/where are puzzles randomized for
  a level, and how does a player discover a puzzle room. No new service needed; wire the existing
  randomizer into level entry (reuse `CoordinateStateService.handleCoordinateChange` / `GameSessionService`).
- **Approach:** ensure `PuzzleRandomizationService.randomize...` runs on first entry to a matrix level
  (reuse the existing GameSession bootstrap), and surface puzzle-room presence in `scan`
  (`GameSessionService.scanArea`) the same way variables/fragments are surfaced today. This is wiring
  call-order, not new logic.
- **Placement:** `GameSessionService`/`CoordinateStateService` (own scan + movement); randomizer stays
  in `PuzzleRandomizationService`.
- **Test gate:** harness — after entering a level and scanning across coords, assert that puzzle/
  variable/nonce hints appear at least at a known seeded coord (seed deterministically in test bootstrap).
  Assert `collect_var <name>` then trade/`unlock_symbol` can consume it. Run the gate.
- **Risk/rollback:** adds a randomizer call + a scan branch; if it misfires, worst case is extra scan
  text. Revert the call-site wiring.

---

## PHASE 9 — HUD command parity via shared `commandHandlers` (kill the duplicate switch)  ✅ DONE (pre+post architect review; classic harness 52/52 stays green; UI verified — entropy/symbols now render in HUD)

> Extracted `TelnetServerService.dispatchCommand` (the shared map dispatch); HUD's duplicate switch →
> `hudOverrides` map (map/m/clear/ls/help/heap/mingle) + delegate-everything-else. ~37 previously-
> "not implemented in HUD" text commands now work. Centralized the 3× `INITIATE_REPAIR` parse into
> `CoordinateStateService.parseRepairInitiation`. Removed orphaned `getAvailableCommands`.
> Architect pre-review caught: `defrag` must NOT delegate (would leak orphaned `activeDefragSessions`
> the HUD loop can't service) → kept as explicit "not in HUD" override. defrag combat stays classic-only.


Completes STATUS.md TODO #12. This is the map-dispatch refactor called out in the brief.

- **Evidence:** `HudService.processGameCommandForHud` (`:725-805`) is a **switch** re-implementing ~12
  of 47 commands, independent of `TelnetServerService.commandHandlers` (`:32`). History-scroll stub at
  `:1127` (`storeCommandOutput` empty). The `INITIATE_REPAIR:` parse is duplicated at `:601` and `:1022`.
- **Reuse audit:** `TelnetServerService.commandHandlers` is the authoritative
  `Map<String,Closure(player,command,parts,writer)>` dispatched at `:756`. HUD should **call the same
  map** instead of duplicating a subset. Where a command's *output* needs HUD-specific framing (alt-
  screen panels), wrap the handler's returned string, but the **dispatch + business logic is reused**.
- **Approach:**
  1. Expose a `dispatchCommand(cmd, player, writer)` on `TelnetServerService` that does the existing
     `commandHandlers.get(cmd)?.call(...)` (extract the body already at `:756-763` into a method — DRY,
     no behavior change for classic).
  2. `HudService.processGameCommandForHud` delegates to `telnetServerService.dispatchCommand(...)` and
     only adds HUD framing — deleting the duplicate switch. Commands HUD can't render full-screen
     (defrag combat, repair) keep their existing special handling, but route through the shared map.
  3. Centralize the `INITIATE_REPAIR:` parse into **one** helper (e.g. on `CoordinateStateService` or a
     `src/main/groovy/ysap` helper) called by all 3 sites.
- **Placement:** `TelnetServerService` (owns the map), `HudService` (owns rendering). No new service.
- **Test gate:** **HUD is alt-screen ANSI — not scriptable by `GameplayHarnessSpec`** (STATUS.md:201).
  Verification: (a) classic harness must stay fully green (proves the extracted `dispatchCommand`
  didn't regress classic dispatch — this is the real safety net); (b) a **manual HUD telnet transcript**
  showing previously-missing commands (defrag/entropy/shop/recurse) now work in HUD. State this split
  explicitly. Run the classic gate; attach the manual HUD transcript.
- **Risk/rollback:** the risky part (shared dispatch) is fully covered by the classic harness. HUD
  framing changes are cosmetic. Revert HUD delegation + restore the switch.

---

# GREENFIELD (newer items) — outline only, sequenced AFTER the above

These are lower-fidelity by instruction. Each still obeys: no new services, map dispatch, harness gate.

## PHASE 10 — Per-player turn phases (ROLL → MOVE → ACTION → END) + real `move <dir>`

- **Shape:** a **phase machine as a `Map<Phase, PhaseDef>`** where each `PhaseDef` holds
  `allowedHandlers` (set of command keys legal in that phase) and a `transition` closure to the next
  phase — **not** a switch. Per-player phase state lives on `LambdaPlayer` (new data fields) or the
  in-memory session map; **asynchronous per-player**, so it coexists with real-time `AutoDefragService`
  + chat (no global lockstep). The dispatcher (`TelnetServerService.dispatchCommand` from Phase 9)
  consults the player's current phase's `allowedHandlers` before delegating.
- **Reuse / revive dead field:** `move <dir>` finally consumes dice-rolled movement points; the
  long-dead `movementRangeBonus` (`LambdaPlayer:35`, read nowhere) becomes the **dice modifier**.
  `cc` remains for teleport-class abilities (Matrix Clipper). Movement validation reuses the existing
  bounds/accessibility checks in `CoordinateStateService.handleCoordinateChange`.
- **Placement:** phase machine + `move` logic in `CoordinateStateService`/`LambdaPlayerService`
  (movement owners); `roll`/`end` are 1-line delegations.
- **Gate:** harness steps `roll` → `move north` (assert points consumed, position changed) → `end`
  (assert phase resets); illegal command in wrong phase → "not allowed this phase."

## PHASE 11+ — (sketch only)
- **Theft mechanic** — scan player → `grep` pid → mini-game → steal item. Reuse the defrag
  `grep`/`kill` chain + repair mini-game scaffolding; no new service.
- **Player-editable `username.groovy` class files** w/ public/private attrs + trap methods — reuse the
  existing hardcoded `ls`/`cat` filesystem surface in `LambdaPlayerService`; persist as text fields.
- **Git bare-repo save points** — checkpoint/reset/pull-to-steal. Bounded external process integration;
  design as a thin adapter, logic in an existing service.
- **Game modes (Node FFA / Cluster 7v7)** — a `Map<Mode, ruleset>` overlay on existing systems.
- **`vim` editor**, **GPIO/LED hardware** — net-new surfaces; design when prioritized.

---

# Doctrine revisions I made to proposed scope

1. **`currentTask.md` invented a throwaway `--nonce=0xXXXX` on the symbol and seeded new clue
   fragments in BootStrap.** REVISED (Phase 6): reference the **existing `ElementalNonce`** the player
   already collects, and use its existing `chemicalClue`/`commandFlag`. This deletes ~30 lines of
   BootStrap seeding, removes a duplicated nonce concept (DRY), and wires the system STATUS.md wrongly
   called an "orphan." Two half-built systems become one finished system.
2. **`currentTask.md`'s symbol-grant `switch`** (its Step 3) → REVISED to a `Map<String,Closure>
   symbolGranters` (doctrine §2).
3. **Brief suggested possibly removing `ElementalNonce`/`HiddenVariable`.** REVISED: do **not** remove —
   they are persisted and queried by the puzzle subsystem (evidence in the corrections section).
   Wire them in (Phases 6–8) instead.
4. **Logic Daemon as a brand-new boss system** → REVISED to **reuse the defrag combat chain** as a
   daemon-class encounter (Phase 7), avoiding a parallel combat engine and a new service.
5. **HUD parity by extending HUD's own switch** → REVISED to **share `TelnetServerService.commandHandlers`
   via an extracted `dispatchCommand`** (Phase 9), deleting the duplicate switch rather than growing it.
6. **`BIT_MULTIPLIER` wired at each bit-grant site** → REVISED to a single
   `SpecialItemService.applyBitModifiers` chokepoint (Phase 3b), since bit-granting is currently
   duplicated and scattering the multiplier would worsen it.
