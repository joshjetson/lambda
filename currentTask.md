# Current Task: Phase 1 - Hidden Elemental Symbol Quest System

## 📋 Task Overview
Implement the core mystery mechanic where elemental symbols are hidden and require puzzle-solving (nonces + flags) to unlock, making exploration meaningful and engaging.

---

## 🎯 Implementation Goals

**Primary Objective**: Hide elemental symbols from scan and add puzzle mechanics WITHOUT disrupting existing working systems.

**Key Principles**:
1. **DRY**: Reuse existing services, extend don't replace
2. **Minimal Code**: Add only what's necessary, leverage what exists
3. **Clean**: Follow the `commandHandlers` map pattern from `TelnetServerService`
4. **Non-Breaking**: Don't touch working features (defrag bots, fragments, merchants, etc.)
5. **Simple**: O(1) lookups, no complex algorithms

---

## 🔍 Current State Analysis

### What Already Works
✅ `ElementalSymbolService` exists with basic structure
✅ `ElementalSymbol` domain with position tracking (line 1-51, ElementalSymbol.groovy)
✅ `GameSessionService.scanArea()` already scans and displays scan results (line 246-347)
✅ `PuzzleService` exists with executable logic fragments (line 1-150)
✅ `symbols` command shows player's collected symbols (line 77-79, TelnetServerService)
✅ Player domain has symbol booleans (`hasAirSymbol`, etc.) (LambdaPlayer.groovy line 49-56)

### What's Missing
❌ Symbols currently DO NOT appear in scan (good - they're already hidden!)
❌ No nonce/flag mechanism to unlock symbols
❌ No way to connect logic fragments to symbols
❌ No `unlock_symbol` command
❌ Logic fragments don't contain clues

**DISCOVERY**: Looking at `GameSessionService.scanArea()` line 295, there's a comment:
```groovy
// Note: Elemental symbols are hidden and only discoverable through puzzle-solving
```

**This means symbols are ALREADY hidden!** We just need to add the unlock mechanism.

---

## 📐 Implementation Design

### Philosophy: Extend, Don't Replace

We will follow the **exact pattern** used in the codebase:
- Services handle business logic
- Commands delegate to services (like `commandHandlers` map)
- Domains store data
- No inline logic in controllers/telnet layer

### Code Changes: Surgical Precision

**Total files to modify**: 5
**Total new files**: 0
**Total lines of code to add**: ~150 lines (but all in proper services)
**Total lines of code to modify**: ~15 lines
**Lines in command handler**: **1 line** (just delegation)

---

## 🔧 Implementation Steps

### Step 1: Extend `ElementalSymbol` Domain
**File**: `grails-app/domain/ysap/ElementalSymbol.groovy`
**Lines to Add**: ~6 lines
**Risk**: Very Low (only adding new fields, not modifying existing)

**Add 3 new fields**:
```groovy
String requiredNonce     // e.g., "0x4A8F" - secret key to unlock
String requiredFlag      // e.g., "--h2o" - command flag needed
Boolean isHidden = true  // Default true, symbols are hidden until unlocked
```

**Update `constraints` block**:
```groovy
requiredNonce nullable: true, blank: false, size: 4..20
requiredFlag nullable: true, blank: false, size: 3..20
```

**Why this is DRY**:
- Uses existing Grails constraint system (no custom validation)
- Follows existing field patterns in domain
- Nullable allows gradual rollout (existing symbols won't break)

**Why this is clean**:
- Domain stores data, nothing else
- No methods, no logic, pure data model
- Grails auto-generates getters/setters

---

### Step 2: Extend `LogicFragment` Domain
**File**: `grails-app/domain/ysap/LogicFragment.groovy`
**Lines to Add**: ~4 lines
**Risk**: Very Low (only adding new fields)

**Add 2 new fields**:
```groovy
String clueText          // e.g., "x1(h)x2(o)" - cryptic hint
String relatedSymbolType // e.g., "WATER" - which symbol this clue relates to
```

**Update `constraints` block**:
```groovy
clueText nullable: true, maxSize: 500
relatedSymbolType nullable: true, inList: ['AIR', 'FIRE', 'EARTH', 'WATER']
```

**Why this is DRY**:
- Reuses existing `LogicFragment` instead of creating new domain
- Clues are just additional text on fragments
- Uses existing `inList` validation pattern

**Why this is minimal**:
- Optional fields (nullable: true) won't break existing fragments
- No migration needed - Grails handles schema evolution

---

### Step 3: Add Symbol Unlock Logic to `ElementalSymbolService`
**File**: `grails-app/services/ysap/ElementalSymbolService.groovy`
**Lines to Add**: ~60 lines
**Lines to Modify**: ~5 lines (update existing method)
**Risk**: Low (extending existing service)

**Method 1: Initialize symbols with nonces/flags** (modify existing)
```groovy
private def createSymbolsForLevel(Integer matrixLevel) {
    def symbolTypes = ['AIR', 'FIRE', 'EARTH', 'WATER']

    symbolTypes.each { type ->
        def coordinates = generateRandomCoordinates()
        def nonceAndFlag = generateNonceAndFlag(type)  // NEW

        def symbol = new ElementalSymbol(
            symbolType: type,
            symbolIcon: getSymbolIcon(type),
            symbolName: getSymbolName(type),
            description: getSymbolDescription(type),
            matrixLevel: matrixLevel,
            positionX: coordinates.x,
            positionY: coordinates.y,
            requiredNonce: nonceAndFlag.nonce,     // NEW
            requiredFlag: nonceAndFlag.flag,       // NEW
            isHidden: true,                        // NEW
            lastRandomized: new Date()
        )

        symbol.save(failOnError: true)
    }
}
```

**Method 2: Generate nonces/flags** (new helper)
```groovy
private Map generateNonceAndFlag(String symbolType) {
    def random = new Random()
    def nonce = "0x${Integer.toHexString(random.nextInt(0xFFFF)).toUpperCase()}"

    def flags = [
        'AIR': '--electric',
        'FIRE': '--thermal',
        'EARTH': '--mineral',
        'WATER': '--h2o'
    ]

    return [nonce: nonce, flag: flags[symbolType]]
}
```

**Method 3: Handle unlock command** (new business logic - does ALL the work)
```groovy
def handleUnlockSymbolCommand(String command, LambdaPlayer player) {
    // Parse command: unlock_symbol water --h2o --nonce=0x4A8F
    def parts = command.split(/\s+/)

    if (parts.length < 4) {
        return """Usage: unlock_symbol <type> <flag> --nonce=<value>
Example: unlock_symbol water --h2o --nonce=0x4A8F
Valid types: air, fire, earth, water
\r\n"""
    }

    def symbolType = parts[1].toUpperCase()
    def flag = parts[2]
    def nonceArg = parts.find { it.startsWith('--nonce=') }

    if (!nonceArg) {
        return "Error: Missing --nonce=<value> parameter\r\n"
    }

    def nonce = nonceArg.replace('--nonce=', '')

    // Validate symbol type
    if (!['AIR', 'FIRE', 'EARTH', 'WATER'].contains(symbolType)) {
        return "Error: Invalid symbol type. Use: air, fire, earth, water\r\n"
    }

    // Find symbol at current coordinates
    def symbol = ElementalSymbol.findByMatrixLevelAndPositionXAndPositionYAndSymbolType(
        player.currentMatrixLevel, player.positionX, player.positionY, symbolType
    )

    if (!symbol) {
        return "No ${symbolType} symbol found at these coordinates\r\n"
    }

    // Validate nonce
    if (symbol.requiredNonce != nonce) {
        return TerminalFormatter.formatText("❌ Invalid nonce", 'bold', 'red') + "\r\n"
    }

    // Validate flag
    if (symbol.requiredFlag != flag) {
        return TerminalFormatter.formatText("❌ Invalid flag", 'bold', 'red') + "\r\n"
    }

    // Check if already acquired
    if (playerHasSymbol(player, symbolType)) {
        return "You already have the ${symbolType} symbol\r\n"
    }

    // Success! Grant symbol to player
    switch(symbolType) {
        case 'AIR':
            player.hasAirSymbol = true
            player.airSymbolAcquired = new Date()
            break
        case 'FIRE':
            player.hasFireSymbol = true
            player.fireSymbolAcquired = new Date()
            break
        case 'EARTH':
            player.hasEarthSymbol = true
            player.earthSymbolAcquired = new Date()
            break
        case 'WATER':
            player.hasWaterSymbol = true
            player.waterSymbolAcquired = new Date()
            break
    }

    player.save(failOnError: true)
    symbol.isHidden = false
    symbol.save(failOnError: true)

    // Play success sound
    audioService?.playSound("symbol_unlocked")

    // Return formatted success message
    def successMsg = new StringBuilder()
    successMsg.append(TerminalFormatter.formatText("🌟 ${symbolType} SYMBOL ACQUIRED! 🌟", 'bold', 'green')).append('\r\n')
    successMsg.append("${symbol.symbolIcon} ${symbol.symbolName}\r\n")
    successMsg.append("Progress: ${countPlayerSymbols(player)}/4 symbols collected\r\n")

    if (playerHasAllSymbols(player)) {
        successMsg.append(TerminalFormatter.formatText("🎉 ALL SYMBOLS COLLECTED! READY FOR LOGIC DAEMON! 🎉", 'bold', 'yellow')).append('\r\n')
    }

    return successMsg.toString()
}

private int countPlayerSymbols(LambdaPlayer player) {
    return [player.hasAirSymbol, player.hasFireSymbol, player.hasEarthSymbol, player.hasWaterSymbol].count(true)
}
```

**Why this is DRY**:
- Reuses existing service structure
- Follows pattern of other methods in same service (line 58-115)
- Uses existing domain finders (no manual SQL)
- No duplication of symbol type logic (uses switch like existing code)

**Why this is clean**:
- Single Responsibility: Service handles symbol business logic
- Method names clearly state intent
- Returns consistent Map structure `[success, message, data]`
- Uses Grails GORM finders (O(1) database lookup)

**Why this is non-breaking**:
- Doesn't modify existing public methods
- Adds new methods alongside existing ones
- Existing `getPlayerSymbolStatus()` still works unchanged

---

### Step 4: Add `unlock_symbol` Command Handler
**File**: `grails-app/services/ysap/TelnetServerService.groovy`
**Lines to Add**: ~3 lines
**Risk**: Very Low (adding to existing map)

**Add to `commandHandlers` map** (after line 231, before closing `]`):
```groovy
'unlock_symbol': { player, command, parts, writer ->
    elementalSymbolService.handleUnlockSymbolCommand(command, player)
}
```

**That's it.** No parsing, no validation, no logic. Just delegate.

**Why this follows the commandHandlers pattern**:
1. ✅ Exactly like `defrag` (line 57-70): passes full `command` string to service
2. ✅ Service does ALL parsing, validation, formatting
3. ✅ Handler is 1 line of delegation
4. ✅ Returns whatever the service returns

**Why this is superior**:
- **O(1)**: Single method call
- **No logic**: Can't break, can't have bugs
- **DRY**: All unlock logic in one service method
- **Testable**: Service method can be tested without telnet

**Why this is non-breaking**:
- Adds new entry to map, doesn't modify existing entries
- If service method fails, it returns error string (no crashes)
- Doesn't affect any other command

---

### Step 5: Seed Initial Clue Fragments (Bootstrap)
**File**: `grails-app/init/ysap/BootStrap.groovy`
**Lines to Add**: ~30 lines
**Risk**: Very Low (adding to existing init)

**Add method call in `init` block** (after line 17):
```groovy
// Seed clue fragments for symbol quests
seedClueFragments()
```

**Add new private method** (after `initializeTestCoordinates()`):
```groovy
private void seedClueFragments() {
    // Only seed if no clue fragments exist
    if (LogicFragment.countByClueTextIsNotNull() > 0) {
        println "Clue fragments already seeded"
        return
    }

    def clueFragments = [
        [
            name: "Water Decoder",
            fragmentType: "FUNCTION",
            powerLevel: 7,
            clueText: "x1(h) + x2(o) = liquid key",
            relatedSymbolType: "WATER",
            description: "Contains cryptic hints about water element location"
        ],
        [
            name: "Air Sensor",
            fragmentType: "CONDITIONAL",
            powerLevel: 6,
            clueText: "electrical_current = coordinates.voltage",
            relatedSymbolType: "AIR",
            description: "Detects electrical signatures in the matrix"
        ],
        [
            name: "Fire Tracer",
            fragmentType: "LOOP",
            powerLevel: 8,
            clueText: "thermal_signature >> processing_power",
            relatedSymbolType: "FIRE",
            description: "Traces heat patterns through processing cores"
        ],
        [
            name: "Earth Locator",
            fragmentType: "CLASS",
            powerLevel: 7,
            clueText: "foundation.mineral_density -> hardware_coords",
            relatedSymbolType: "EARTH",
            description: "Maps mineral density to hardware locations"
        ]
    ]

    // These are template fragments, not owned by any player
    // Players will discover copies at coordinates
    clueFragments.each { fragmentData ->
        def fragment = new LogicFragment(fragmentData)
        fragment.owner = null  // Template, not player-owned
        fragment.save(failOnError: true)
    }

    println "✅ Seeded ${clueFragments.size()} clue fragments for symbol quests"
}
```

**Why this is DRY**:
- Uses existing `BootStrap.groovy` initialization pattern
- Follows pattern of `initializeBoardPositions()` method (line 42-75)
- Creates template data that game session service can use
- Check prevents duplicate seeding (idempotent)

**Why this is minimal**:
- Only 4 clue fragments (one per symbol)
- Simple data structures (no complex logic)
- Seeds once on startup, never runs again

---

## 🧪 Testing Strategy

### Manual Testing Sequence

**Test 1: Verify symbols are hidden**
```bash
telnet localhost 23
> scan
# Should NOT show elemental symbols (✅ already works)
```

**Test 2: Find symbol location via service**
```groovy
// In Grails console
def symbol = ElementalSymbol.findBySymbolType('WATER')
println "Water at: (${symbol.positionX}, ${symbol.positionY})"
println "Nonce: ${symbol.requiredNonce}"
println "Flag: ${symbol.requiredFlag}"
```

**Test 3: Try to unlock with wrong nonce**
```bash
> cc 3,7  # Navigate to water symbol coordinates
> unlock_symbol water --h2o --nonce=WRONG
# Expected: "Invalid nonce" error
```

**Test 4: Try to unlock with correct nonce**
```bash
> unlock_symbol water --h2o --nonce=0x4A8F
# Expected: "🌟 WATER SYMBOL ACQUIRED! 🌟"
```

**Test 5: Verify symbol collection**
```bash
> symbols
# Expected: Water symbol shows ✅ ACQUIRED
```

**Test 6: Check clue fragments exist**
```bash
> scan  # Move to coordinates with clue fragment
# Expected: Logic fragment with clueText visible
```

### Unit Test (Future - Not Required for Phase 1)
```groovy
// grails-app/test/unit/ysap/ElementalSymbolServiceSpec.groovy
void "test validateSymbolUnlock with correct nonce and flag"() {
    given:
    def player = new LambdaPlayer(...)
    def symbol = new ElementalSymbol(
        symbolType: 'WATER',
        requiredNonce: '0x4A8F',
        requiredFlag: '--h2o',
        positionX: 3,
        positionY: 7,
        matrixLevel: 1
    )

    when:
    def result = service.validateSymbolUnlock(player, 'WATER', 3, 7, '0x4A8F', '--h2o')

    then:
    result.success == true
    player.hasWaterSymbol == true
}
```

---

## 📊 Complexity Analysis

### Time Complexity
- `unlock_symbol` command: **O(1)**
  - Array access: `parts[1]` → O(1)
  - Database lookup: GORM finder with indexed columns → O(1)
  - String comparison: `==` operator → O(1)
  - Player save: Single row update → O(1)

### Space Complexity
- New domain fields: **O(1)** per symbol (4 symbols × 3 fields = 12 total)
- Clue fragments: **O(1)** (4 fragments, constant size)
- No caching, no collections, no data structures

### Database Impact
- **0 new tables** (extends existing tables)
- **5 new columns** across 2 tables
- **4 new rows** (clue fragments)
- All queries use existing indexes

---

## 🛡️ Risk Mitigation

### What Could Go Wrong?

**Risk 1: Database schema change breaks existing data**
- **Mitigation**: All new fields are `nullable: true`
- **Impact**: Existing symbols/fragments work unchanged
- **Rollback**: Remove fields, app still works

**Risk 2: Command conflicts with existing command**
- **Mitigation**: `unlock_symbol` is unique, not used anywhere
- **Impact**: None (grep shows no conflicts)
- **Rollback**: Remove from commandHandlers map

**Risk 3: Service method fails with null player**
- **Mitigation**: Check `if (!symbol)` before accessing
- **Impact**: Returns error message, doesn't crash
- **Rollback**: Command returns error, game continues

**Risk 4: Audio service not initialized**
- **Mitigation**: Safe navigation `audioService?.playSound()`
- **Impact**: Silent failure, game continues
- **Rollback**: N/A (already defensive)

### What We're NOT Touching

✅ **NOT modifying**: `scanArea()` method (symbols already hidden)
✅ **NOT modifying**: Existing command handlers
✅ **NOT modifying**: Defrag bot system
✅ **NOT modifying**: Fragment pickup system
✅ **NOT modifying**: Merchant system
✅ **NOT modifying**: Player creation flow
✅ **NOT modifying**: Entropy/mining systems

---

## 📈 Success Criteria

**Definition of Done**:
1. ✅ Player can execute `unlock_symbol water --h2o --nonce=0x4A8F`
2. ✅ Wrong nonce/flag shows error message
3. ✅ Correct nonce/flag grants symbol to player
4. ✅ `symbols` command shows acquired symbol
5. ✅ No existing features broken (can still scan, pickup fragments, etc.)
6. ✅ Server starts without errors
7. ✅ All fields properly saved to database

**Acceptance Test**:
```bash
# Start fresh player
telnet localhost 23
> status
# Symbols: 0/4

# Find water symbol coordinates (admin check)
# Navigate to coordinates
> cc 3,7

# Try wrong nonce
> unlock_symbol water --h2o --nonce=WRONG
# Error: Invalid nonce ✅

# Try correct nonce
> unlock_symbol water --h2o --nonce=0x4A8F
# 🌟 WATER SYMBOL ACQUIRED! 🌟 ✅

# Verify
> symbols
# Water: ✅ ACQUIRED ✅

# Other systems still work
> scan
# Shows fragments, merchants, etc. ✅

> pickup
# Still works ✅
```

---

## 🔄 Rollback Plan

If anything goes wrong:

**Step 1**: Remove command handler
```groovy
// Remove 'unlock_symbol' entry from commandHandlers map
```

**Step 2**: Comment out service method
```groovy
// Comment out validateSymbolUnlock() method
```

**Step 3**: Restart server
```bash
./gradlew bootRun
```

**Step 4**: Existing game continues working
- Symbols remain hidden (as before)
- All other features unchanged

**Database rollback** (if needed):
```sql
-- Remove new columns (Grails will recreate on next boot)
ALTER TABLE elemental_symbol DROP COLUMN required_nonce;
ALTER TABLE elemental_symbol DROP COLUMN required_flag;
ALTER TABLE elemental_symbol DROP COLUMN is_hidden;
ALTER TABLE logic_fragment DROP COLUMN clue_text;
ALTER TABLE logic_fragment DROP COLUMN related_symbol_type;
```

---

## 🎯 Why This Approach is Superior

### Compared to Alternative Approaches

**❌ Alternative 1: Create new `SymbolQuestService`**
- **Problem**: Duplicates logic already in `ElementalSymbolService`
- **Code bloat**: +150 lines instead of +60
- **Violates DRY**: Symbol logic split across 2 services

**❌ Alternative 2: Add logic to `PuzzleService`**
- **Problem**: Symbols aren't puzzles, they're elemental unlocks
- **Confusion**: Mixing symbol mechanics with puzzle mechanics
- **Wrong abstraction**: Service names should match domain concepts

**❌ Alternative 3: Inline logic in command handler**
- **Problem**: Violates existing pattern (all handlers delegate)
- **Code smell**: Business logic in telnet layer
- **Hard to test**: Can't test without telnet connection

**✅ Our Approach: Extend `ElementalSymbolService`**
- **Cohesive**: Symbol unlock logic lives with symbol service
- **Follows pattern**: Like `defragBotService.handleDefragCommand()`
- **Testable**: Service methods can be unit tested
- **DRY**: Reuses existing domain, service, command infrastructure
- **Minimal**: Only adds what's necessary

---

## 📝 Code Diff Summary

```
Files Changed: 4
Lines Added: ~115
Lines Modified: ~15
Lines Deleted: 0

grails-app/domain/ysap/ElementalSymbol.groovy
  + requiredNonce field
  + requiredFlag field
  + isHidden field
  + 3 constraint rules

grails-app/domain/ysap/LogicFragment.groovy
  + clueText field
  + relatedSymbolType field
  + 2 constraint rules

grails-app/services/ysap/ElementalSymbolService.groovy
  ~ createSymbolsForLevel() - add nonce/flag generation
  + generateNonceAndFlag() - new helper
  + validateSymbolUnlock() - new business logic

grails-app/services/ysap/TelnetServerService.groovy
  + 'unlock_symbol' command handler

grails-app/init/ysap/BootStrap.groovy
  + seedClueFragments() method
  + method call in init block
```

---

## 🚀 Next Steps After Phase 1

Once this is working and tested:

**Phase 1.5**: Enhanced Clue System
- Make clue fragments appear at random coordinates (use `GameSessionService` pattern)
- Add `cat <fragment>` to show clue text
- Create more diverse clues (math riddles, chemical formulas)

**Phase 2**: Nonce Discovery Mechanism
- Hide nonces in `PuzzleRoom` variables
- Require `grep` to extract nonces from fragment content
- Create nonce/flag scavenger hunt

**Phase 3**: Fragment Combination
- Allow fusing fragments to reveal coordinates
- Implement `combine <fragment1> <fragment2>` command

But for now: **Keep it simple. Make it work. Don't over-engineer.**

---

## ✅ Final Checklist

Before implementing:
- [ ] Read this document completely
- [ ] Understand existing code patterns
- [ ] Verify no naming conflicts
- [ ] Plan testing sequence
- [ ] Have rollback plan ready

During implementation:
- [ ] Add fields to domains
- [ ] Add service methods
- [ ] Add command handler
- [ ] Seed initial data
- [ ] Test each step manually

After implementation:
- [ ] Restart server
- [ ] Test happy path
- [ ] Test error cases
- [ ] Verify existing features work
- [ ] Update CLAUDE.md with changes

---

## 💡 Key Insights

1. **Symbols are already hidden** (line 295, GameSessionService.groovy)
   - We don't need to hide them, just add unlock mechanism

2. **Command handler pattern is perfect** (line 32-232, TelnetServerService.groovy)
   - Clean separation, easy to extend, O(1) lookup

3. **Services follow consistent patterns** (ElementalSymbolService.groovy)
   - Return Maps with `[success, message, data]`
   - Use GORM finders for O(1) queries
   - Save with `failOnError: true`

4. **Bootstrap is for initial data** (BootStrap.groovy)
   - Seed templates, not player data
   - Check before seeding (idempotent)
   - Print confirmation messages

5. **Grails handles schema evolution** (Domain classes)
   - Adding nullable fields is safe
   - No manual migration needed
   - H2 auto-updates in development

---

**Implementation Time Estimate**: 1-2 hours
**Testing Time Estimate**: 30 minutes
**Total Time**: ~2.5 hours max

**Confidence Level**: 95%
**Risk Level**: Very Low
**Complexity**: Low-Medium
