# Lambda — Cluster Mode Build Plan (Architect, MODE A)

> Authority: rules come from `CLUSTER_DESIGN.md`. Code-reality comes from the files (verified
> below), not from `CLAUDE.md` marketing. `STATUS.md` is the feature matrix; where it lagged the
> code (turn/dice movement) this plan records the correction.
> Directive: "build it all piece by piece. Keep it DRY. Don't let files build into god files.
> Test your changes to ensure they work. Play to ensure everything renders fine and is functional."

---

## 0. Evidence base (greps run, 2026-06-10)

| Claim | Verified | Evidence |
|---|---|---|
| TelnetServerService / LambdaPlayerService / HudService / DefragBotService are god files | yes | `wc -l`: 1606 / 1448 / 1211 / 1049 lines |
| Dispatch is an O(1) `Map<String,Closure> commandHandlers` → single `dispatchCommand(command,player,writer)` | yes | `TelnetServerService:38`, `:884` |
| Ethnicity is stored as `avatarSilhouette` ∈ {CLASSIC_LAMBDA, CIRCUIT_PATTERN, GEOMETRIC_ENTITY, FLOWING_CURRENT, DIGITAL_GHOST, BINARY_FORM} | yes | `LambdaPlayer:6`, `LambdaPlayerService:230-235` |
| 6 ethnicities map 1:1 to the 6 Cluster roles | yes | design contract row table; enum count matches |
| No team / role / match / decoy / cluster concept exists in code | yes | grep `team|role|cluster|match|decoy|infiltrat` → only i18n + unrelated `match` vars |
| `GameSession` is NOT a roster/match — it's a per-map puzzle-seed holder | yes | `GameSession.groovy:9-13` (mapSeed1..10), no players/teams |
| Turn/dice movement **already exists** (STATUS.md said "none" — STALE) | yes | `dados`/`move` handlers `TelnetServerService:53-58`; `CoordinateStateService:473-578` DICE/TURN block; global `moveTurnOrder` rotation `TelnetServerService:33,394-463`; `DiceRenderer` helper |
| Heap broadcasts trades/payments publicly (leak signal #2 substrate) | yes | `ChatService` echo/pay/trade (`STATUS.md` Economy rows) |
| Recursion is a real charge/cooldown economy now (the single-player stub of team verbs) | yes | `LambdaPlayer:40-46` fields; harness Phase-2 steps assert charge spend |
| Integration harness: `@Stepwise` Spock, real socket `LambdaTelnetClient`, `bot.command()` + `PROMPT` regex, test port 2323 | yes | `GameplayHarnessSpec.groovy`, `LambdaTelnetClient:27,128` |

---

## 1. Module strategy — resolving "NO NEW SERVICES" vs "no god files"

### The tension, stated honestly
- `CLAUDE.md` rule **NO NEW SERVICES** exists to stop the *FFA sprawl* — services that overlap,
  scatter one concern across many files, and grow unboundedly. It is a guard against **incoherent**
  growth, not a literal ban on every new file regardless of cohesion.
- The human now says: **don't let files build into god files.** Our four biggest files
  (1606/1448/1211/1049 lines) are already god files. Cluster adds match lifecycle, teams,
  roles, true/decoy truth, deduction intel, occupancy rules, and five team verbs
  (`spam`/`lock`/`deploy bot`/`scan all`/`transfer`). Cramming that into `TelnetServerService`
  or `LambdaPlayerService` would push them past 2000 lines and make them un-reviewable — the
  exact failure the new directive forbids.

### Why this is genuinely a *new bounded context* (not extendable into an existing service)
Reuse audit shows **no existing service owns "a multiplayer match of teams with hidden roles."**
`GameSession` is a puzzle-seed bag (`mapSeed1..10`), not a roster. `ChatService` owns the heap.
`CoordinateStateService` owns movement/occupancy of the *board*, not team rules. There is no
home; hosting match logic in any of them violates Doctrine #3 (code goes where it logically
belongs) and #4 (DRY — match rules would be smeared across services).

### Decision
1. **Domains are always allowed** (data + constraints only — Doctrine #3). Create focused new
   domains: `ClusterMatch`, `ClusterTeam`, `ClusterMembership`. These add zero behavior risk and
   carry the truth (roster, role, true/decoy bit) that has no current home.

2. **A SMALL number of NEW, tightly-bounded Cluster services is the lesser evil** vs. bloating
   the existing god files. This is the evidence-backed exception the doctrine permits ("no existing
   one can host the concern — show the greps"). Bound it hard to **two** services, each with a
   single concern, neither allowed to grow into a god file:
   - **`ClusterMatchService`** — match/lobby lifecycle, team membership, role + true/decoy
     assignment, occupancy rules (4/coord, 2/team, Geo 5th-slot), win gate (symbols→true Lambda).
     Owns the `ClusterMatch/Team/Membership` domains. This is the "match referee."
   - **`ClusterRoleService`** — the role-keyed team-verb handlers (`scan all`, `spam`, `lock`,
     `deploy bot`, `transfer`) and the **enemy-view identity filter** (two Lambdas read identical
     to enemies). Dispatch is a **`Map<String,Closure> roleVerbs`** keyed by verb → handler, and
     role→capability is a **`Map<role,Set<verb>>`** — O(1), open/closed (Doctrine #2). This is the
     "role abilities + information firewall."

   Two services, drawn on the seam the design contract itself draws: **match truth** vs.
   **information/abilities**. If either approaches ~600 lines, that is the signal to split a verb
   group out — but start at two.

3. **Reuse, do not reinvent, identity.** Role IS `avatarSilhouette` — the 6 ethnicities already in
   `LambdaPlayer`. The Cluster "role" is the existing ethnicity; `ClusterRoleService` keys its maps
   off `player.avatarSilhouette`. The single-player `recurse <ability>` charge/cooldown economy
   (`LambdaPlayer:40-46`) is the substrate the team verbs reuse for cooldown accounting — we do not
   build a second cooldown system.

4. **Command handlers stay one-line delegations** in `commandHandlers` (Doctrine #3). New keys
   (`cluster`, `scan all`, `spam`, `lock`, `deploy`, `transfer`) each delegate one line to the
   appropriate Cluster service. No business logic in the telnet layer.

5. **Shared pure helpers** (formation/occupancy rendering, the "relative-to-Λ" correlation table)
   go in `src/main/groovy/ysap/` alongside `TerminalFormatter`/`DiceRenderer` — not copy-pasted,
   not in a service.

### Boundaries drawn (one-concern-each)
```
ClusterMatch / ClusterTeam / ClusterMembership   domains  → data only (roster, role, isTrueLambda)
ClusterMatchService                              service  → lifecycle, membership, assignment, occupancy, win gate
ClusterRoleService                               service  → roleVerbs map, role→capability map, enemy-view filter
src/main/groovy/ysap/ (helper)                   helper   → formation/relative-correlation rendering (pure)
TelnetServerService.commandHandlers              existing → +1-line delegations only
CoordinateStateService                           existing → occupancy *enforcement hook* called by ClusterMatchService (board owns the board)
ChatService                                      existing → already broadcasts trades = leak signal #2 (reused, untouched)
```

**No edits to DefragBotService / HudService god files in the Cluster MVP path** — `deploy bot`
reuses the existing public bot-spawn path rather than reaching into that 1049-line file's internals.

---

## 2. Phased, piece-by-piece build plan

Principles applied to every piece: small + atomic; independently provable via
`GameplayHarnessSpec`; leaves the game green; **additive — never rips out the working Node/FFA
game** (Cluster is opt-in via a `mode`/`cluster` entry; default play is untouched). Sequenced by
dependency. Movement model decision is **PIECE 8** and deliberately late so nothing earlier depends
on changing working movement code.

| # | Piece | What it proves (one line) |
|---|---|---|
| **1 ✅** | **Match + team + role + true/decoy foundation** (domains + `ClusterMatchService.createMatch/join/assignRoles`, `cluster` command) | **DONE** — create/join/status/leave work; exactly one of two Lambdas is flagged true, visible to self only; harness 24/24; live-verified; architect APPROVE. Watch-items deferred: assignment *timing* → PIECE 2, true-Lambda-leaves orphan → PIECE 11. |
| 2 | Lobby fill with bots + match start gate | Empty seats auto-fill so a solo human starts a 7v7; match transitions lobby→active when both teams full. |
| 3 | Enemy-view identity firewall (`ClusterRoleService.renderForViewer`) | An enemy scanning either Lambda gets byte-identical readout (same `Λ`, role-class, name-class); a teammate sees the true-Lambda marker; no channel leaks the bit to an enemy. |
| 4 | `scan all` (Circuit/Tracker) — location-only intel | Circuit gets enemy *positions + role-class*, Ghosts excluded, with cooldown; output never contains identity, only locations → the deduction sensor exists. |
| 5 | Relative-correlation read (leak signal #1, geometry) | The pooled read shows "enemy bodies within N of Λ#1 vs Λ#2" — a correlation, not an answer — built from positions + occupancy. |
| 6 | Occupancy rules (4/coord, 2/team, Geo 5th-slot) enforced on entry | Movement into a full/over-team coordinate is refused with the right reason; Geo bypasses the 4-cap; formations become legible (feeds signal #1). |
| 7 | Item-flow as leak signal #2 (verify, light-touch) | Public heap trade/pay broadcast already surfaces goods/bits flow; confirm it renders in a cluster match and the "economic decoy tax" is playable (no new economy — reuse `ChatService`). |
| 8 | **Movement-model decision: dice-as-real-time-budget** (decouple from global rotation) | Each player spends a per-player dice *budget* in real time; one player's combat no longer freezes others (kills PLAYTEST bug #1) — Cluster never uses the global lap. |
| 9 | Team verbs: `lock` (Current), `spam` (Ghost) | Adjacent CC/disruption works against a *suspected* Lambda; pinning the **decoy** wastes it (the bluff has teeth). |
| 10 | Team verbs: `deploy bot` (Binary), `transfer` (Lambda→carrier) | Area denial + symbol hand-off (football) work; carrier is theft-vulnerable; reuses existing bot spawn + symbol fields. |
| 11 | Win gate: `invoke` succeeds **only** for the true Lambda holding 4 symbols | Decoy with all 4 symbols is refused at `invoke`; true Lambda with 4 wins → the climactic reveal/interception window is real. |
| 12 | Wizard-of-Oz read-test harness step (from CLUSTER_DESIGN §b) | Hand-driven 2Λ+escorts+flow formation lets a scripted hunter deduce the real Λ — proves the *whole deduction loop* is well-formed before bot AI. |

> Bot AI tiers (CLUSTER_DESIGN open item) are **out of MVP scope** here — pieces 1–12 use
> seat-fill stubs (piece 2) good enough for geometry; the hard "bot fakes/reads a decoy" AI is a
> later epic, not part of getting the loop green.

### Movement-model decision (called now, with reasoning) — informs PIECE 8
**Decision: adopt dice-as-real-time-budget; retire the global turn rotation for Cluster.**
Evidence: PLAYTEST.md findings #1 and #2 — the *global* `moveTurnOrder` (`TelnetServerService:33`)
couples every player and **one player's combat freezes everyone's movement** (#1), and it
"doesn't scale … with 10 you'd wait through 9 others to move one tile" (#2). A 14-player match
makes a single global lap unplayable. Dice stays (the roll is fun, `DiceRenderer` + the
`dados`/`move` budget logic already exist in `CoordinateStateService`), but the **budget is spent
in real time per player** instead of gated by a shared lap. This is additive: Node/FFA can keep its
current behavior; Cluster sets a per-match movement mode that bypasses `joinMoveRotation`. Done at
PIECE 8 (not piece 1) precisely because it *changes working movement code* — everything upstream is
pure-additive and must be green first.

---

## 3. PIECE 1 — full spec (confirmed as the first piece)

**Confirmed.** PIECE 1 = the additive **match + team + role + true/decoy assignment foundation**.
It is the most upstream, smallest genuinely-valuable, independently-testable piece, and it is
**pure-additive**: new domains + one new service + one new one-line command handler. It changes
**no existing behavior** (unlike the movement refactor at PIECE 8), so it cannot break Node/FFA.
Every later deduction piece depends on "there is a match, teams, roles, and a true/decoy bit," so
this is the correct foundation.

### 3.1 Reuse audit (exact greps + findings)
- `grep -rniE "\bteam\b|\brole\b|cluster|\bmatch\b|decoy|infiltrat|isDecoy|trueLambda" grails-app/ src/main/`
  → **no roster/team/role/match/decoy domain or service exists.** Only i18n strings and unrelated
  local `match` variables. → A match construct must be created; nothing can host it.
- `Read grails-app/domain/ysap/GameSession.groovy` → `GameSession` holds `mapSeed1..10`, `totalMaps`,
  activity timestamps. **It is a puzzle-seed bag, not a roster.** Reusing it for teams would smear
  two unrelated concerns into one domain (anti-DRY, wrong placement). → new domains, do not overload
  `GameSession`.
- `LambdaPlayer:6` + `LambdaPlayerService:230-235` → **role identity already exists** as
  `avatarSilhouette` ∈ the 6 ethnicity enums, 1:1 with the 6 Cluster roles. → Cluster role = the
  player's existing ethnicity; **reuse it, store no second "role" string on the player.**
- `LambdaPlayer:57-65` → symbol-possession booleans already on the player. → win-gate and "decoy can
  carry symbols" reuse these; no new symbol storage.
- `TelnetServerService:38, :884` → O(1) `commandHandlers` map + single `dispatchCommand`. → the new
  `cluster` command is one map entry delegating one line.
- `GameplayHarnessSpec.groovy` → `@Stepwise` socket harness with `bot.command()` + `PROMPT`. → PIECE 1
  proves itself by adding steps here.

**Conclusion:** new data domains (always allowed) + ONE new focused service is the evidence-backed
lesser evil; role identity is reused from `avatarSilhouette`, not reinvented.

### 3.2 DRY / O(1) map-dispatch approach
- **`cluster` subcommand dispatch** is a `Map<String,Closure> clusterSubcommands` keyed by subverb
  (`create`, `join`, `status`, `leave`) inside `ClusterMatchService` — O(1), no `switch`
  (Doctrine #2). `commandHandlers['cluster']` is a single line: delegate the raw command to
  `clusterMatchService.handleClusterCommand(command, player, writer)`.
- **True/decoy assignment** is data-driven: collect the two Lambda-role members, pick one index via
  `Random`, set `isTrueLambda=true` on that membership, `false` on the other. No conditional ladder.
- **Role = `avatarSilhouette`** everywhere; a single `Map<String,String> ROLE_LABEL` (ethnicity→
  Collector/Tracker/Scout/Disruptor/Saboteur/Trapper) lives once in the service for display. DRY:
  one source of the role-name mapping.

### 3.3 Exact placement (drawn to avoid god files)
**New domains** (`grails-app/domain/ysap/`, data + constraints only):
- `ClusterMatch` — `matchId` (unique), `state` (LOBBY/ACTIVE/ENDED), `totalMaps`, `createdDate`;
  `static hasMany = [teams: ClusterTeam]`.
- `ClusterTeam` — `name` (ALPHA/BETA), `belongsTo ClusterMatch`, `hasMany = [members: ClusterMembership]`.
- `ClusterMembership` — `belongsTo ClusterTeam`; `String username` (the player); `String role`
  (= the player's `avatarSilhouette`); `Boolean isTrueLambda = false`; `Boolean isBot = false`.
  (Membership references the player by username — keeps `LambdaPlayer` from gaining match-coupling
  fields; the god-aggregate does not grow.)

**New service** (`grails-app/services/ysap/`):
- `ClusterMatchService` — `handleClusterCommand(command,player,writer)` + `clusterSubcommands` map +
  `createMatch`, `joinMatch(player)`, `assignRoles(match)` (sets the true/decoy bit),
  `getClusterStatus(player)`, `leaveMatch(player)`. All DB access wrapped in
  `DomainClass.withTransaction {}` (telnet-thread rule). Bounded to lifecycle/membership/assignment
  only; occupancy + verbs are later pieces in their owning services.

**Existing file, one-line addition** (`TelnetServerService.commandHandlers`, near `:53`):
```
'cluster': { player, command, parts, writer ->
    clusterMatchService.handleClusterCommand(command, player, writer)
},
```
Plus the field injection `def clusterMatchService` alongside the other service injections. **No
other existing file is touched** → no god file grows by more than ~4 lines.

### 3.4 Test gate — the precise harness steps + run command
Add to `GameplayHarnessSpec.groovy` (after the existing Phase-10 step, keeping `@Stepwise` order;
`botuser` was created avatar 1 = CLASSIC_LAMBDA = Collector/Lambda role):

1. **"cluster create starts a LOBBY match and seats the creator"**
   `out = bot.command('cluster create')` →
   assert `out.toLowerCase() =~ /cluster|match|lobby|team/` and `out =~ PROMPT`.
   *Proves:* match created, creator seated, connection survives.

2. **"cluster status shows the player on a team with their role"**
   `out = bot.command('cluster status')` →
   assert `out.toLowerCase() =~ /team|alpha|beta/` and `out.toLowerCase() =~ /collector|lambda|classic/`
   (role derived from `avatarSilhouette`).
   *Proves:* membership + role-from-ethnicity reuse renders.

3. **"the true/decoy bit is assigned and visible only to self"**
   After a state where two Lambda members exist (creator + one seeded second Lambda via the service's
   test seam, or a second `LambdaTelnetClient` joining as avatar 1), `out = bot.command('cluster status')`
   → assert it states whether *this* player is the true Lambda or the decoy
   (`out.toLowerCase() =~ /true lambda|decoy/`), and that exactly one of the two carries the true bit
   (assert via a package-visible `clusterMatchService` test seam, mirroring how
   `telnetServerService.playerSessions` / `moveRotationSize()` are asserted today).
   *Proves:* the load-bearing invariant — one true, one decoy, fixed at start, known to self.

4. **"cluster is a wired command — bare/unknown subverb guides, not 'unknown command'"**
   `out = bot.command('cluster')` → assert `out.toLowerCase() =~ /cluster|create|join|status|usage/`
   and NOT `unknown command`. *Proves:* O(1) dispatch + graceful guidance (mirrors the existing
   `move`/`unlock_symbol` "is-wired" pins).

**Run:** `./gradlew integrationTest --tests ysap.GameplayHarnessSpec`
(webdriver-binaries arch failure already neutralized in `build.gradle`). **Green required before
PIECE 2 starts** (Doctrine #5).

### 3.5 Play-verification over telnet (renders + functional)
After harness green, the implementing agent verifies live (Doctrine: "play to ensure everything
renders"):
- `./gradlew bootRun`, `telnet localhost 23`, create a CLASSIC_LAMBDA character (avatar 1).
- `cluster create` → expect a rendered match/lobby panel (ANSI via `TerminalFormatter`, `\r\n`
  endings) naming a team.
- `cluster status` → expect the team + role line ("Collector / Lambda") and, once two Lambdas exist,
  a self-only line stating "You are the TRUE Lambda" or "You are the DECOY."
- Open a **second** telnet session, create another avatar-1 entity, `cluster join` → confirm it lands
  on a team and that exactly one of the two Lambdas shows TRUE and the other DECOY, **and that
  neither session can see the other's true/decoy bit** (the firewall is piece 3, but piece 1 must
  already not print the enemy's bit). Confirm a normal Node/FFA character that never types `cluster`
  is completely unaffected (additive).

### 3.6 Risk / rollback
- **Risk:** low. Pure-additive — new domains + new service + 1 map entry. No existing handler logic
  changes; Node/FFA path is byte-identical for anyone who never types `cluster`.
- **Schema:** dev H2 is `create-drop` (wiped per restart) — new domains auto-create, no migration risk
  in dev.
- **Rollback:** single atomic commit; `git reset --hard HEAD` removes 3 domains + 1 service + 1 map
  line cleanly (Doctrine #6).
- **Doctrine note honored:** the one new service is justified by the written reuse audit above (no
  existing artifact can host a team-match construct), keeping it the *lesser evil* vs. inflating the
  1606/1448-line god files — exactly the carve-out the doctrine allows.
