# Lambda Game Development Roadmap
**Focus: Core Fun Mechanics for Nerdy Players**

This document contains prioritized features organized by implementation complexity and fun-factor impact. Each section is designed to be implemented independently in small, testable increments.

---

## 🎯 PRIORITY 1: Core Solo Gameplay (Node Mode) - Make the Basic Game FUN

These are the foundational mechanics that make the single-player experience engaging BEFORE adding multiplayer complexity.

### 1.1 🗺️ Hidden Elemental Symbol Quest System
**Status**: Partially implemented, needs completion
**Fun Factor**: ⭐⭐⭐⭐⭐ (This is THE core objective)
**Complexity**: Medium

**Current Problem**:
- Elemental symbols are visible on scan (they shouldn't be)
- No puzzle/mystery around finding them
- No reason to explore or solve puzzles

**Implementation Plan**:
1. **Hide symbols completely** - Remove from scan output unless special conditions met
2. **Fragment-based clue system** - Logic fragments contain hints about symbol locations
3. **Nonce & Flag mechanic** - Require collecting secret keys to unlock symbols

**How It Works**:
```
Player Journey:
1. Collect logic fragments while exploring
2. Some fragments contain cryptic clues: "x1(h)x2(o)" → water symbol hint
3. Find "nonce" values hidden in puzzle rooms (special variables)
4. Discover "command flags" through fragment examination
5. Execute: `execute water.py --flag=<discovered_flag> --nonce=<discovered_nonce>`
6. Symbol unlocks at current coordinates (only if all conditions met)
```

**Technical Requirements**:
- `ElementalSymbol` domain: Add `isHidden`, `requiredNonce`, `requiredFlag` fields
- `LogicFragment` domain: Add `clueText`, `containsNonce` fields
- `PuzzleService`: New method `validateSymbolUnlock(player, symbolType, nonce, flag)`
- Scan command: Remove elemental symbol detection (except with special items)

---

### 1.2 🧩 Enhanced Puzzle & Logic Fragment System
**Status**: Basic framework exists, needs depth
**Fun Factor**: ⭐⭐⭐⭐⭐ (Smart puzzles = nerdy player engagement)
**Complexity**: Medium-High

**Core Concept**: Logic fragments aren't just collectibles - they're puzzle pieces that unlock secrets.

**Fragment Types & Purposes**:

**Type A - Clue Fragments**:
- Contain cryptic hints about symbol locations
- Example: `if_water.py` → when executed prints "Flow to coordinates where h2o forms"
- Hint format: Mathematical, chemical, or code-based riddles

**Type B - Function Fragments**:
- Executable Python/Groovy code that processes nonces
- Example: `decode_symbol.py` → accepts nonce as parameter, returns symbol type hint
- Usage: `execute decode_symbol.py <nonce_value>`

**Type C - Variable Fragments**:
- Contain hidden variables that are inputs to other fragments
- Must be "extracted" via grep or cat commands
- Example: `cat memory_dump.txt` reveals `SECRET_NONCE=4f8a9b2c`

**Type D - Conditional Fragments**:
- Reveal information only when combined with other fragments
- Example: Fragment A + Fragment B → reveals coordinates
- Usage: `fuse conditional_a.py conditional_b.py` (new command)

**Implementation Breakdown**:
```
Phase 1: Clue System
- Add `clueType` enum to LogicFragment: LOCATION_HINT, NONCE_HINT, FLAG_HINT, SYMBOL_TYPE
- Create 20+ unique riddles/clues stored in fragment descriptions
- Implement `cat <fragment>` to display clues embedded in Python comments

Phase 2: Executable Fragments
- Sandboxed Python executor (Jython or process isolation)
- Accept command-line arguments: `execute fragment.py arg1 arg2`
- Return values stored in player session for chaining

Phase 3: Fragment Combination
- New command: `combine <fragment1> <fragment2>`
- Check compatibility matrix (stored in database)
- Successful combination → new insight or coordinate reveal
```

**Example Puzzle Flow**:
```
1. Player finds "conditional_water.py" fragment
2. Examines it: `cat conditional_water.py`
   Output: "# This fragment accepts a nonce. Provide the liquid key."
3. Player must find nonce elsewhere (hidden in different room)
4. Player collects "memory_leak.txt" from another coordinate
5. Grep for nonce: `grep -o "NONCE" memory_leak.txt` → reveals "0x4A8F"
6. Execute: `execute conditional_water.py 0x4A8F`
   Output: "Water symbol resides where x=3, y=7. Use flag --h2o"
7. Navigate to (3,7) and execute: `unlock_symbol water --h2o --nonce=0x4A8F`
8. Symbol acquired!
```

---

### 1.3 🕵️ Player-vs-Player Theft Mechanics (Slot Machine Mini-Game)
**Status**: Not implemented
**Fun Factor**: ⭐⭐⭐⭐⭐ (High-stakes PvP = adrenaline rush)
**Complexity**: Medium

**Core Concept**: Players can steal from each other using Linux command-line skills + slot machine challenge.

**Theft Flow**:
```
Step 1: DETECTION
- Player A scans coordinates, detects Player B adjacent or same coordinate
- Output: "🚨 Lambda entity 'NerdyPlayer42' detected at (4,5)"

Step 2: RECONNAISSANCE
- Player A executes: `scan NerdyPlayer42`
- System displays target's player file (username.groovy)
- Output shows public attributes but NOT inventory
  ```
  class NerdyPlayer42 {
      String displayName = "The Shadow"
      String tagline = "I steal in silence"
      // [ENCRYPTED INVENTORY - PID REQUIRED]
  }
  ```

Step 3: PID EXTRACTION
- Player A must grep for the process ID: `grep -o "PID" NerdyPlayer42.groovy`
- System reveals: "PID: 8472"
- This PID becomes the slot machine target

Step 4: SLOT MACHINE ATTACK
- Player A initiates: `steal NerdyPlayer42 8472`
- Slot machine mini-game launches (15 second timer)
- Player must match 4 digits by stopping spinning slots
- Each slot cycles: 0-9 continuously

Step 5: SUCCESS/FAILURE
- SUCCESS (all 4 match PID): Player A sees victim's inventory, chooses 1 item to steal
- PARTIAL (2-3 match): Player A gets random common item
- FAILURE (0-1 match): Player A loses random item to defender, 5-minute cooldown
- DEFENDER: If logged in, receives notification "You've been attacked by PlayerX!"

Step 6: IMMUNITY
- Successful attacker: 3 min immunity from same victim
- Successful defender: 5 min immunity from all attacks
- Cooldown: Can only attack once every 2 minutes
```

**Anti-Griefing Mechanics**:
- Defenders can't be attacked more than once every 5 minutes
- Theft only works on adjacent/same coordinates (encourages movement)
- Failed attacks penalize attacker (lose random item)
- Offline players have 50% theft resistance (item locked away)

**Implementation Requirements**:
- New domain: `TheftAttempt` (attacker, victim, success, timestamp, itemStolen)
- Service: `TheftService.initiateTheft(attacker, victim, pid)`
- Service: `SlotMachineService.launchMinigame(targetPID, attackerWriter)`
- Mini-game: Terminal-based slot animation with real-time input
- Command: `steal <username> <pid>`

**Slot Machine Technical Design**:
```groovy
// Pseudo-code for slot machine
def launchSlotMachine(targetPID, attackerSocket) {
    def slots = [0, 0, 0, 0] // 4 slots
    def spinning = [true, true, true, true]
    def targetDigits = targetPID.toString().padLeft(4, '0').collect { it.toInteger() }

    // Animate spinning
    Thread.start {
        while(spinning.any { it }) {
            slots.eachWithIndex { val, idx ->
                if (spinning[idx]) {
                    slots[idx] = new Random().nextInt(10)
                }
            }
            displaySlots(attackerSocket, slots)
            sleep(100)
        }
    }

    // Listen for spacebar presses (stop each slot)
    // Player presses space 4 times to stop each slot
    // Compare final slots to targetDigits
}
```

---

### 1.4 📂 Player Groovy File System (username.groovy)
**Status**: Not implemented
**Fun Factor**: ⭐⭐⭐⭐ (Programmable identity = nerd heaven)
**Complexity**: Medium-High

**Core Concept**: Each player has an editable Groovy class file that defines their identity, traps, and behaviors.

**What Players Can Do**:
1. **Customize Identity**:
   - `displayName` - shown to other players
   - `tagline` - flavor text (max 80 chars)
   - `publicBio` - visible on scan

2. **Create Traps/Easter Eggs**:
   - Define public methods that other players can execute
   - Methods can "steal" bits, items, or trigger effects
   - Example:
   ```groovy
   class PlayerX {
       String displayName = "Hackerman"

       // Public method - other players can call this
       public String greet(String name) {
           // Easter egg: first caller loses 10 bits!
           if (!hasBeenTriggered) {
               stealBits(caller, 10)
               hasBeenTriggered = true
               return "Thanks for the bits, $name!"
           }
           return "Hello $name!"
       }

       // Private - not visible to other players
       private Boolean hasBeenTriggered = false
   }
   ```

3. **Set Permissions**:
   - Mark attributes as `public` (visible on scan) or `private` (hidden)
   - Other players see method names but NOT implementation
   - Scanning shows: `public String greet(String name)` but NOT the code inside

**Commands**:
- `edit username.groovy` - Opens nano-like editor in terminal
- `compile` - Validates Groovy syntax, saves changes
- `scan <username>` - Views another player's public interface
- `execute <username>.method(args)` - Calls public method (at your own risk!)

**Example Player File**:
```groovy
package lambda.players

class NerdyPlayer42 {
    // Public Identity
    String displayName = "The Shadow"
    String tagline = "Those who debug together, stay together"
    String publicBio = "Lambda enthusiast | Fragment collector | Puzzle solver"

    // Public Stats (visible on scan)
    Integer reputation = 47
    Integer puzzlesSolved = 12

    // Private attributes (hidden)
    private Integer secretStash = 500  // bits hidden from scans
    private Boolean hasTrapTriggered = false

    // Public method - other players can call
    public String shareWisdom(String question) {
        if (question.contains("secret")) {
            // Trap! Asking about secrets costs you
            return "TRAP TRIGGERED: Curiosity costs 25 bits"
        }
        return "Seek fragments in the shadows of defragged sectors"
    }

    // Public method - helpful alliance building
    public String trade(String item, Integer bits) {
        return "Use the heap space 'mingle' command for safe trading"
    }
}
```

**Implementation Plan**:
1. **Phase 1**: Basic file storage
   - Store Groovy code as TEXT in `LambdaPlayer.playerGroovyFile` field
   - Commands: `cat username.groovy`, `edit username.groovy`

2. **Phase 2**: Editor implementation
   - Terminal-based text editor (line-by-line editing)
   - Commands: `i` (insert mode), `esc` (command mode), `:wq` (save/quit)

3. **Phase 3**: Groovy compilation & execution
   - Use GroovyShell to compile player files
   - Sandbox execution (limit CPU, memory, no file system access)
   - Track method calls for anti-abuse

4. **Phase 4**: Inter-player execution
   - Command: `execute <username>.greet("hello")`
   - Invoke method on target's compiled class
   - Apply bit transfers, item effects, etc.

**Security Considerations**:
- Whitelist allowed Groovy classes (no File, no Process, no System)
- CPU timeout: 2 seconds max per method execution
- Rate limiting: Max 10 method calls per minute per player
- Bit transfer limits: Max 50 bits per method call

---

### 1.5 🎰 Fix & Enhance Slot Machine Mini-Game
**Status**: Broken, needs repair
**Fun Factor**: ⭐⭐⭐ (Fun mini-game, not core to progression)
**Complexity**: Low

**Current Issues**:
- Implementation incomplete or buggy
- Not integrated with theft mechanic

**Fixes Needed**:
1. Terminal animation rendering (smooth slot spinning)
2. Input handling (spacebar to stop slots)
3. Timing mechanism (15-second countdown)
4. Success/failure logic (compare slots to target PID)

**Integration Points**:
- Used in theft mechanic (see 1.3)
- Potential use in merchant gambling (optional future feature)

---

## 🎯 PRIORITY 2: Enhanced Solo Experience - Polish & Depth

These features make the single-player game deeper and more engaging.

### 2.1 🚫 Logic Fragment Visibility Control
**Status**: Needs implementation
**Fun Factor**: ⭐⭐⭐⭐ (Mystery + special items = rewarding exploration)
**Complexity**: Low

**Problem**: Fragments are too easy to find via scan - no mystery, no challenge.

**Solution**:
- **DEFAULT**: Scan does NOT reveal logic fragments
- **SPECIAL ITEM**: "Fragment Detector" (rare item, 3 uses)
  - Activates: `use fragment_detector`
  - Effect: Reveals fragments in adjacent 8 coordinates for 10 seconds
  - Display: ASCII radar pulse animation with countdown
  - After 10s: Fragments hidden again

**Implementation**:
1. Modify `gameSessionService.scanArea()`:
   - Remove fragment detection from default output
   - Check if player has `fragmentDetectorActive` buff

2. Add `SpecialItem` type: `FRAGMENT_DETECTOR`
   - Uses remaining: 3
   - Duration: 10 seconds
   - Cooldown: None (consumable)

3. Create buff tracking:
   - `LambdaPlayer.fragmentDetectorActive` (Boolean)
   - `LambdaPlayer.fragmentDetectorExpiry` (Date)
   - Check expiry on each scan command

**UI/UX**:
```
Player uses detector:
> use fragment_detector

╔════════════════════════════════════╗
║  FRAGMENT DETECTOR ACTIVATED       ║
║  Scanning adjacent coordinates...  ║
║  Duration: 10 seconds              ║
╚════════════════════════════════════╝

[Radar pulse animation]

FRAGMENTS DETECTED:
📦 (3,4) - loop_construct.py [POWER: 5]
📦 (4,3) - conditional_logic.py [POWER: 3]

> scan
Scanning coordinates (3,5)...
🔍 Fragment detector: 7s remaining
📦 Fragment detected: function_def.py [POWER: 4]

[After 10 seconds]
⏱️ Fragment detector deactivated
```

---

### 2.2 🔗 Git Bare Repo Save System (Checkpoints)
**Status**: Not implemented
**Fun Factor**: ⭐⭐⭐⭐ (Git mechanics = nerdy love, save system = QoL)
**Complexity**: High

**Core Concept**: Players can create "save points" using Git commands at special coordinates. If defeated, they can restore to last save instead of going back to (0,0).

**How It Works**:

**Finding Bare Repos**:
- Special coordinates (7,7), (8,8), (9,9) on each level have bare repos
- Scan reveals: "📂 Bare Git Repository detected: 'fragbot_destroyer_3000.git'"

**Creating Save Point**:
```bash
> cc 7,7
You've entered a bare repository zone.
Repository: fragbot_doom_bringer.git

> git status
On branch master
Nothing to commit, working tree clean

> git add .
Staged current inventory (5 items, 347 bits, 12 fragments)

> git commit -m "Pre boss fight save"
[master a4f8e2b] Pre boss fight save
 - Inventory snapshot saved
 - Position: (7,7) Level 3
 - Timestamp: 2025-11-12 14:32:01

> git push origin fragbot_doom_bringer
✅ Save point created!
WARNING: This repo can be pulled by other players!
Branches: 1, Last commit: a4f8e2b
```

**Restoration After Death**:
```bash
[You've been defeated by Defrag Bot Level 5]
[Respawning at (0,0)...]

> git log
Repository: fragbot_doom_bringer.git
Last commit: a4f8e2b - "Pre boss fight save" (5 minutes ago)

> git reset --hard HEAD
Restoring from save point...
✅ Inventory restored (5 items, 347 bits, 12 fragments)
✅ Position restored: (7,7) Level 3
✅ Defrag bot timer reset

[Teleported back to (7,7)]
```

**PvP Risk - Repo Stealing**:
```bash
# Another player discovers your repo
> scan
📂 Bare Git Repository: fragbot_doom_bringer.git
👤 Branch by: NerdyPlayer42 (5 min ago)

> git pull origin fragbot_doom_bringer
Pulling branch...
✅ Repository synchronized
⚠️ Original save point DESTROYED

# Now if NerdyPlayer42 tries to restore:
> git reset --hard HEAD
ERROR: Repository has been pulled
Origin is lost. Cannot restore.
No save point available.
```

**Implementation Requirements**:

1. **Domain: GitRepository**
```groovy
class GitRepository {
    String repoName              // "fragbot_doom_bringer.git"
    Integer matrixLevel
    Integer coordinateX
    Integer coordinateY
    Long playerId               // Owner of the save
    String branchName           // "master"
    String commitHash           // "a4f8e2b"
    String commitMessage
    Date commitTimestamp
    String inventorySnapshot    // JSON serialized inventory
    Integer savedPositionX
    Integer savedPositionY
    Boolean hasBeenPulled       // If true, owner can't restore
    Long pulledByPlayerId       // Who stole the save
}
```

2. **Service: GitService**
- `gitStatus(player, coordinates)` - Show repo status
- `gitAdd(player)` - Stage current inventory
- `gitCommit(player, message)` - Create save snapshot
- `gitPush(player, repoName)` - Finalize save point
- `gitLog(player)` - Show save history
- `gitReset(player)` - Restore from save
- `gitPull(player, repoName)` - Steal another player's save

3. **Commands**:
- `git status` - Check if in repo zone, show staged changes
- `git add .` - Stage inventory for save
- `git commit -m "<message>"` - Create commit
- `git push origin <repo>` - Push save to repo
- `git log` - View save history
- `git reset --hard HEAD` - Restore from save
- `git pull origin <repo>` - Steal someone's save (if at same coordinates)

**Anti-Abuse**:
- Maximum 1 active save per player per level
- Repo zones are rare (only 3 per level)
- Pulling a repo requires being at exact coordinates
- 10-minute cooldown between save creations
- Repos expire after 24 hours if not used

---

### 2.3 🆘 Defrag Bot Co-op Fork System
**Status**: Not implemented
**Fun Factor**: ⭐⭐⭐⭐ (Co-op moments = memorable experiences)
**Complexity**: Medium

**Core Concept**: When a player encounters a difficult defrag bot, other players can "fork" to join the fight. Winner takes all loot.

**How It Works**:

**Player A Encounters Bot**:
```bash
[Player A at coordinates (5,7) Level 4]
⚠️ DEFRAG BOT ENCOUNTERED!
Difficulty: LEVEL 4 (High threat)
Time to defeat: 45 seconds

BROADCAST SENT:
📡 Lambda 'PlayerA' under attack at (5,7) Level 4
   Type 'fork (5,7)' to assist (or compete!)
```

**Player B Sees Notification**:
```bash
[Player B at coordinates (6,3) Level 4]
📡 FORK REQUEST from PlayerA
   Location: (5,7) - 4 coordinates away
   Threat: Level 4 Defrag Bot

Accept fork? This will teleport you to (5,7)
> fork 5,7

⚡ Forking repository...
✅ Teleported to (5,7)
🎯 Joining defrag encounter with PlayerA
⏱️ Timer extended: +30 seconds (now 75s total)
```

**Co-op Battle**:
```bash
[Two players now at (5,7) fighting same bot]
Defrag Bot PID: 8472
Time remaining: 72 seconds

PlayerA: > cat /proc/defrag/bot_8472
PlayerB: > grep -o "8472"

[Both players racing to execute kill command]
PlayerB: > kill -9 8472

✅ PlayerB defeated the bot first!
💰 REWARDS: 150 bits, Scanner Boost (rare item)
📦 PlayerA receives consolation: 50 bits

PlayerA: "Nice steal!"
PlayerB: "Thanks for the fork 😎"
```

**Implementation Plan**:

1. **Broadcasting System**:
- When defrag bot spawns, check for other players on same level
- Filter: Only notify players at coordinates >= attacker (ahead on map)
- Send notification to qualifying players
- Store active fork requests in memory (expires after 60s)

2. **Fork Command**:
```groovy
// Service: DefragBotService
def handleForkRequest(player, targetX, targetY) {
    // Validate fork request exists
    def activeEncounter = DefragBot.findByMatrixLevelAndPositionXAndPositionY(
        player.currentMatrixLevel, targetX, targetY
    )

    if (!activeEncounter) {
        return "No active defrag encounter at those coordinates"
    }

    // Teleport player
    player.positionX = targetX
    player.positionY = targetY
    player.save(failOnError: true)

    // Extend timer
    activeEncounter.timeRemaining += 30
    activeEncounter.save(failOnError: true)

    // Track participants
    activeEncounter.addParticipant(player.id)

    return "Forked to (${targetX},${targetY})! Good luck!"
}
```

3. **Reward Distribution**:
- Track who executes final `kill -9` command
- Winner gets 100% of rewards
- Other participants get 30% consolation prize
- Encourages competition even in co-op

**Anti-Abuse**:
- Forking costs 20 bits (prevents spam)
- Sets player back on map (teleport has consequences)
- Maximum 3 players can fork to same encounter
- Cooldown: Can only fork once every 10 minutes

---

### 2.4 🕰️ History Puzzle Easter Egg
**Status**: Not implemented
**Fun Factor**: ⭐⭐⭐⭐ (Mind-bending puzzle = nerdy delight)
**Complexity**: Low

**Concept**: "Those who do not know their history are doomed to repeat it"

**Puzzle Flow**:
1. Player finds special fragment: `history_lesson.py`
2. Fragment contains cryptic message: "Those who forget their past commands will never progress"
3. Player executes: `history`
4. **TWIST**: Command history shows commands they NEVER typed!
5. Hidden commands reveal clues or coordinates

**Example**:
```bash
> execute history_lesson.py
"Check your command history. You may have forgotten something important."

> history
1. status
2. scan
3. move north
4. cc 3,7          # ← Player never typed this!
5. execute unlock_water.py --flag=h2o  # ← Hidden clue!
6. move east
7. pickup
```

**Implementation**:
- When player picks up `history_lesson.py` fragment
- Secretly inject 2-3 fake commands into their `CommandHistory`
- Commands contain puzzle solutions or coordinates
- Player must recognize they didn't type those commands
- Following the "ghost commands" leads to secrets

**Technical**:
```groovy
// When history_lesson.py is picked up
def injectGhostCommands(player) {
    // Find elemental symbol this player needs
    def neededSymbol = elementalSymbolService.getNextUncollectedSymbol(player)

    // Inject hint commands
    new CommandHistory(
        player: player,
        command: "cc ${neededSymbol.coordinateX},${neededSymbol.coordinateY}",
        timestamp: new Date() - 3 // Make it look like it was typed 3 minutes ago
    ).save(failOnError: true)

    new CommandHistory(
        player: player,
        command: "execute unlock_${neededSymbol.type}.py --flag=${neededSymbol.flag}",
        timestamp: new Date() - 2
    ).save(failOnError: true)
}
```

---

## 🎯 PRIORITY 3: Multiplayer - Cluster Mode (7v7 Team Battles)

**⚠️ NOTE**: Only implement these AFTER Priority 1 & 2 are solid and fun. Cluster mode is the endgame feature.

### 3.1 🏁 Cluster Mode Foundation
**Status**: Not implemented
**Fun Factor**: ⭐⭐⭐⭐⭐ (Team strategy = highest replayability)
**Complexity**: Very High

**Core Rules**:
- 7v7 maximum (two teams)
- Each race can only be chosen once per team (except Lambda - 2 allowed)
- First team to collect 4 elemental symbols and defeat Logic Daemon wins
- All teammates advance to next level together

**Game Setup**:
```bash
> mode cluster
Cluster Mode selected.
Creating 7v7 match...

Select your role:
1. Lambda (Collector) - Can pickup elemental symbols [2/2 available]
2. Ghost (Saboteur) - Invisible, console spam attacks
3. Geo (Scout) - Cannot be trapped, visible to all
4. Circuit (Tracker) - See all player locations
5. Binary (Trapper) - Place defrag bots
6. Current (Disruptor) - Electric lock traps

> 1
You are now Lambda (Collector) on TEAM ALPHA
Waiting for 6 more players...

[Match starts when both teams have 7 players]
```

**Coordinate Occupancy Rules**:
- Maximum 4 players per coordinate
- Maximum 2 per team at same coordinate
- Exception: Geo can be 5th player (special ability)

---

### 3.2 🎭 Race-Specific Abilities (Cluster Mode Only)

Each race has unique tactical abilities that shine in team play.

**LAMBDA (Collector)**:
- **Only race** that can pickup elemental symbols
- **Only race** that can fight Logic Daemon
- Can transfer symbols to teammates (like passing a football)
- Teammates hold symbols for max 5 minutes before auto-return
- Vulnerable to theft (starts with 0 bits)
- Strategy: One Lambda acts as decoy, other collects

**GHOST (Saboteur)**:
- **Console spam attack**: `spam <username>`
  - Floods target's terminal with junk text for 10 seconds
  - Makes it nearly impossible to type commands
  - Cooldown: 2 minutes
  - Only works on adjacent coordinates
- **Invisibility**: Never detected by scan (unless same coordinate)
- When same coordinate: Other players see "You feel an ominous presence..."
- **Weakness**: Easiest to steal from when detected (50% theft resistance)

**GEO (Scout)**:
- **Trap immunity**: Electric locks, coordinate traps have no effect
- **Visible to all**: Always shows up on Circuit scans, no stealth
- **Cannot enter defragged coordinates** until repaired
- **Special**: Can be 5th player at full coordinates (breaks 4-player limit)
- **Temporary cloak**: Can use "Cloaking Device" item (rare) for 30s invisibility

**CIRCUIT (Tracker)**:
- **Radar vision**: `scan all` shows ALL enemy player locations
  - Displays coordinate map with player markers
  - Ghosts NOT visible (unless same coordinate)
  - Updates every 10 seconds
  - Cooldown: 1 minute between scans
- **Strategic value**: Primary intelligence gatherer for team
- **Weakness**: No combat/disruption abilities

**BINARY (Trapper)**:
- **Place defrag bot**: `deploy bot <x> <y>`
  - Places a defrag bot at specified coordinates
  - Bot difficulty scales with player's level
  - Only 1 bot active at a time
  - Cooldown: 5 minutes after previous bot is defeated
  - Immune to own bot (invisible to placer)
- **Strategy**: Block enemy Lambda's path or guard symbols

**CURRENT (Disruptor)**:
- **Electric lock**: `lock <username>`
  - Target cannot move for 60 seconds
  - Must be on adjacent coordinate to lock
  - Only 1 lock active at a time (can't lock another until first expires)
  - Cooldown: 2 minutes after lock expires
  - Lock appears as: "⚡ LOCKED - 47s remaining"
- **Counter-play**: Victim can break lock with mini-game (match 3 digits instead of 4)

---

### 3.3 🎯 Cluster Mode Win Condition

**Objective**: Collect 4 elemental symbols + defeat Logic Daemon

**Team Coordination Required**:
1. **Symbol Collection** (Lambda only):
   - Lambda players find and collect symbols
   - Teammates scout, protect, distract enemy team

2. **Symbol Protection**:
   - Lambda can transfer symbol to teammate: `transfer water_symbol <username>`
   - Teammate holds symbol (acts as carrier)
   - Carrier can be stolen from (high-stakes gameplay)
   - Carrier must return symbol to Lambda within 5 minutes or it auto-returns

3. **Logic Daemon Fight**:
   - Only Lambda can initiate: `invoke daemon`
   - Requires all 4 symbols in inventory
   - Entire team can contribute to puzzle solving
   - Daemon presents multi-stage puzzle (5 minutes time limit)
   - Team coordination required to solve

4. **Victory**:
   - First team to defeat Logic Daemon wins match
   - ALL team members advance to next level together
   - Losing team stays on current level

**Match Duration**:
- Player chooses at start: 1 level, 3 levels, 5 levels, or 10 levels (full game)
- Each level is a round
- Team that wins most rounds wins match

---

## 📋 IMPLEMENTATION ORDER RECOMMENDATION

Based on fun-factor, complexity, and dependency chains:

### Phase 1 - Core Mystery (2-3 weeks)
1. Hide elemental symbols from scan ✅
2. Fragment clue system ✅
3. Nonce & flag mechanic ✅
4. Enhanced fragment examination ✅

### Phase 2 - PvP Theft (1-2 weeks)
1. Fix slot machine mini-game ✅
2. Player detection on scan ✅
3. PID extraction mechanic ✅
4. Theft command & inventory stealing ✅

### Phase 3 - Player Identity (2-3 weeks)
1. username.groovy file storage ✅
2. Basic editor implementation ✅
3. Groovy compilation ✅
4. Inter-player method execution ✅

### Phase 4 - Co-op & Polish (1-2 weeks)
1. Defrag bot fork system ✅
2. Git bare repo save system ✅
3. History puzzle easter egg ✅
4. Fragment visibility toggle ✅

### Phase 5 - Cluster Mode (4-6 weeks)
1. Team matchmaking system ✅
2. Race ability implementation ✅
3. Coordinate occupancy rules ✅
4. Logic Daemon team fight ✅

---

## 🎨 NERDY DESIGN PRINCIPLES

Every feature should embody these principles:

1. **Linux/Unix Authenticity**: Use real command syntax (grep, cat, git, kill)
2. **Easter Eggs**: Hidden jokes, references, and surprises
3. **Smart Puzzles**: Require thinking, not grinding
4. **Risk/Reward**: High-stakes decisions with meaningful consequences
5. **Emergent Gameplay**: Systems that interact in unexpected ways
6. **Terminal Aesthetics**: Beautiful ASCII art, ANSI colors, retro vibes
7. **Community Moments**: Shared experiences that players talk about

---

## 🚫 FEATURE CREEP PREVENTION

**DO NOT IMPLEMENT** until Phase 5 is complete:
- HUD mode enhancements
- Advanced UI/UX polish
- Additional game modes
- Cosmetic features
- Mobile clients
- Web dashboards

**Focus**: Make the core telnet experience **incredibly fun** first.
