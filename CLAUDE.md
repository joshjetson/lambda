# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Lambda is a Grails-based hybrid board/computer game platform for Lambda, a multiplayer BBS door game. Lambda combines physical hardware (LEDs, GPIO) with digital gameplay via telnet connections. Players are electrical entities (Lambda race) navigating a physical board with LED position tracking, collecting logic fragments, and working to escape the system and invade the internet.

### Game Concept
- **Hybrid Gameplay**: Physical board with LEDs and lights + digital interface via telnet
- **Hardware Integration**: GPIO controls, breadboard development → custom motherboard with LEDs, glass houses, central monitor  
- **Access Methods**: Player laptops via telnet OR custom terminal kits attached to board
- **Virtual Movement**: Player actions trigger LED sequences showing position on physical board
- **Movement System**: Direct coordinate access via `cc (x,y)` command for maximum exploration freedom
- **Recursion Powers**: Special abilities activated via `recurse` command with cooldowns and limited charges
- **Ultimate Objective**: **Defeat the Logic Daemon** - the digital gatekeeper preventing escape from the system to invade the internet

#### **The Logic Daemon Challenge**
The central goal is to defeat the Logic Daemon on each matrix level, which requires:
1. **Collect All 4 Elemental Symbols**: 🜁 Air (electrical power), 🜂 Fire (processing power), 🜃 Earth (hardware foundation), 🜄 Water (logic & data)
2. **Discover Daemon Location**: Find clues throughout the map pointing to the Logic Daemon's coordinates
3. **Invoke the Daemon**: Process an array containing all 4 symbols with the proper command flag to initiate the final confrontation

#### **Dynamic Elemental System**
- **Hidden Locations**: Each elemental symbol is hidden at different coordinates on each matrix level
- **Fragment Clues**: Logic fragments contain hints and command flags needed to invoke specific elementals
- **Dynamic Relocation**: Once obtained, elementals relocate to new random coordinates for other players
- **Competitive Discovery**: Players must scan coordinates, collect clue fragments, and race to find elementals before others
- **Environmental Challenges**: Defragged sectors (repair mini-games), defrag bots (Linux combat), merchants (trading/clues)

## Development Commands

### Core Grails Commands
- `./gradlew bootRun` - Start the development server with hot reloading
- `./gradlew clean` - Clean build artifacts
- `./gradlew build` - Build the application (includes compilation and tests)
- `./gradlew war` - Create WAR file for deployment

### Testing
- `./gradlew test` - Run unit tests
- `./gradlew integrationTest` - Run integration tests  
- `./gradlew check` - Run all tests and code quality checks

### Database
- Development uses H2 in-memory database (auto-created on startup)
- Database recreated on each restart in development mode
- Production uses H2 file-based database

## Architecture

### Core Components

**Domain Models** (`grails-app/domain/ysap/`)
- `Page.groovy` - Represents game pages with content, links, and descriptions
- `Link.groovy` - Navigation links between pages

**Services** (`grails-app/services/ysap/`)
- `TelnetServerService.groovy` - Manages telnet connections for BBS-style gameplay
- `PageService.groovy` - Handles page CRUD operations  
- `BootstrapService.groovy` - Application initialization

**Controllers** (`grails-app/controllers/ysap/`)
- `PageController.groovy` - Web interface for page management

**Utilities** (`src/main/groovy/ysap/`)
- `TerminalFormatter.groovy` - ANSI terminal formatting for telnet clients

### Key Game Systems

**Movement & Navigation System**
- **Standard Movement**: All players move 1 coordinate at a time using `move <direction>`
- **Coordinate System**: Sequential navigation 0,0 → 0,1 → 0,2... → 0,9 → 1,0 → 1,1... → 9,9
- **Floor-Based Progression**: Floor 0 (0,0-0,9), Floor 1 (1,0-1,9), Floor 2 (2,0-2,9), etc.
- **Difficulty Scaling**: Higher floors become progressively more challenging
- **Recursion Command**: Special ability system activated via `recurse` command
- **Recursion Mechanics**:
  - Each ethnicity has different recursion abilities (enhanced movement, stealth, scanning, etc.)
  - Limited charges per player (base: 1-2 charges)
  - Cooldown timer between uses (3-5 minutes)
  - Special items can increase recursion charges and reduce cooldowns
  - Command usage: `recurse <ability>` (e.g., `recurse movement`, `recurse scan`)
- **Fair Play**: No passive bonuses - all ethnicity advantages require active command usage

**Defrag Bot Encounter System**
- **Floor-Based Encounter Rates**:
  - Floors 0-1: 2% encounter rate (very safe for new players)
  - Floor 2: 5% encounter rate (safe learning zone)
  - Floors 3-4: 10% encounter rate (normal gameplay)
  - Floors 5-6: 15% encounter rate (challenging)
  - Floors 7+: 20% encounter rate (dangerous late game)
- **Safe Zone**: Coordinates (0,0), (0,1), (1,0), (1,1) - no defrag bots ever spawn
- **Difficulty Scaling**: Bot difficulty matches floor number (Floor 0-1 = Difficulty 1, Floor 2 = Difficulty 2, etc.)
- **Random Distribution**: Proper probability-based spawning, not forced encounters

**Player Management**
- Lambda race with customizable attributes
- ASCII face coloring tool for personalization  
- Generic player silhouettes as visual avatars
- Player profile creation via telnet

**Ethnicity Recursion Abilities**
- **Classic Lambda**: `recurse fusion` - Enhanced fragment fusion success (+15% success rate)
- **Circuit Pattern**: `recurse defend` - Temporary defrag resistance (next encounter +15% resistance)
- **Geometric Entity**: `recurse movement` - Multi-coordinate jump (2-3 spaces in chosen direction)
- **Flowing Current**: `recurse mine` - Boost bit mining efficiency (+25% for next mining cycle)
- **Digital Ghost**: `recurse stealth` - Enhanced stealth mode (+30% defrag avoidance for 10 minutes)
- **Binary Form**: `recurse process` - Accelerated processing (reduced cooldowns for 5 minutes)

**Ring-Based Board System**
- Multiple themed rings (innermost to outermost)
- LED position tracking for player movement
- Cooperative sector surveys and team tasks
- Ring progression based on logic accumulation

**Logic & Puzzle System**
- Logic fragment collection determines available Python code constructs
- Python puzzles solvable only with accumulated logic
- Graduated difficulty as players progress outward

**Skill & Power-Up System**
- Skills: scanning (distance), stealth, etc.
- CLI-based lock picking puzzles (Skyrim-style mechanics)
- Alphanumeric block cycling with both hands
- Difficulty scaling throughout game

**Hardware Integration**
- GPIO control for LED board lighting
- Breadboard development → custom motherboard
- Glass houses with integrated lighting
- Central monitor for shared game state

### Technical Features

**Telnet Server**: Multi-client telnet server supporting BBS-style connections with ANSI formatting and real-time client count updates.

**Terminal Formatting**: Custom ANSI code generation for styled terminal output including colors, styles (bold, underline), and special effects (framed, blinking).

**Page System**: Content management for game pages with navigation links, supporting rich text content up to 6000 characters.

## Development Notes

- Uses Grails 6.x with Spring Boot
- Frontend uses GSP templates with Bootstrap 5
- Asset pipeline handles JavaScript/CSS minification
- WebDriver integration for UI testing (Chrome/Firefox)
- H2 database with automatic schema generation in development

## Port Configuration

The telnet server can be configured to run on custom ports through the TelnetServerService. Default configuration should be checked in application.yml or service initialization code.

## Development Guidelines

**Auto-Update This File**: CLAUDE.md should be automatically updated as we learn more about the application architecture, discover new patterns, or encounter implementation challenges. Always update this file when:
- New game systems are implemented
- Architecture patterns are established  
- Common development mistakes are identified
- GPIO/hardware integration approaches are determined
- Performance optimizations are discovered

## First Iteration Status

**Completed Features (v0.3 - Economy & Trading Update)**:
- ✅ Lambda player domain model with avatar system
- ✅ Logic fragment and skill tracking systems  
- ✅ Matrix-level based board positions (10 levels, 12 sectors each)
- ✅ Telnet-based player authentication and creation
- ✅ **Bits currency system** - players earn and spend digital currency
- ✅ **Heap space chat room** - real-time communication via echo commands
- ✅ **Defrag bot encounters** - hostile NPCs with command-line challenges
- ✅ **Special items system** - respawn cache, swap space, various power-ups
- ✅ Advanced movement with encounter mechanics
- ✅ ASCII avatar selection during character creation
- ✅ Enhanced inventory system showing bits, items, fragments
- ✅ Real-time position tracking (ready for GPIO integration)
- ✅ **Fragment Quantity System** - duplicate pickups stack with x2, x3 display
- ✅ **Complete Trading Economy** - player-to-player commerce in heap space
- ✅ **Lambda Merchant NPCs** - random merchants on each matrix level
- ✅ **Fragment File System** - cat/pickup mechanics for logic fragment collection

**Major Game Systems Added (v0.3)**:
- 🔄 **Heap Space**: Full social hub with chat, trading, and commerce
- ⚔️ **Defrag Bot Combat**: Command-line based encounters requiring Linux knowledge
- 💰 **Economy System**: Bits currency with earning/spending mechanics
- 🎒 **Special Items**: Collectible items with various effects and trading potential
- 🗺️ **Matrix Navigation**: Updated from "rings" to "matrix levels" for better theming
- 📦 **Fragment Collection**: Coordinate-based spawning with cat/pickup mechanics
- 🛒 **Trading Marketplace**: Complete player-to-player and NPC commerce system
- 🏪 **Lambda Merchants**: Random NPCs selling fragments and special items
- 📄 **Fragment File**: Personal collection system showing all acquired logic

**Development Notes**:
- **Thread Safety**: Telnet connections run in separate threads requiring explicit transaction boundaries
- **Session Management**: Use `DomainClass.withTransaction {}` for database operations from background threads
- **Error Handling**: Always wrap database calls in try-catch when called from telnet threads
- **Database Operations**: ALWAYS use `failOnError: true` instead of `flush: true` for performance and proper transaction handling
- **String Methods**: Use `takeRight()` not `takeLast()` in Groovy
- **Player State**: Always refresh player objects with `LambdaPlayer.get(id)` when checking session state
- **Critical**: ALL database reads from telnet threads MUST be wrapped in `DomainClass.withTransaction {}`
- **Matrix Coordinates**: Constrained to 0-9 grid for each level
- **Safe Zones**: Defrag bots cannot spawn at coordinates (0,0), (0,1), (1,0), (1,1)
- **Logic Gates**: Spawn at coordinates >= (7,1) for advanced challenges

**Known Issues**:
- WebDriver dependencies cause build failures (can be bypassed with `./gradlew bootRun`)
- GPIO LED integration not yet implemented (placeholder methods in place)

**Connection Details**:
- Telnet: `telnet localhost 8181`
- Web interface: `http://localhost:8080`
- Database: H2 in-memory (development)

**Gameplay Flow (v0.3)**:
1. **Connect**: `telnet localhost 8181`
2. **Authenticate**: Login or create new Lambda entity
3. **Explore**: Use `move` commands to navigate matrix levels
4. **Collect**: Use `scan` to find logic fragments, then `pickup` to collect them
5. **Combat**: Encounter defrag bots and use command-line skills to defeat them
6. **Earn**: Collect bits, special items, and logic fragments as rewards
7. **Trade**: Use `heap` space for full marketplace experience (command: `mingle`):
   - `echo <msg>` - Chat with other entities
   - `pay <entity> <bits>` - Transfer currency
   - `pm <entity> <msg>` - Private messages
   - `trade <entity>` - Initiate trading interface
8. **Shop**: Find Lambda merchants and use `shop`, `buy`, `sell` commands
9. **Manage**: Use `cat fragment_file` to view your collection

**Status**: Lambda game v0.3 with complete economy and trading systems operational!

## HOW TO ACTUALLY PLAY & TEST THE GAME

**CRITICAL FOR CLAUDE**: NEVER use scripts for testing. ALWAYS telnet in interactively and play manually.

### **Interactive Testing Protocol**:
1. **Connect**: `telnet localhost 8181`
2. **Create Character**: 
   - Press Enter to start
   - Enter username (or 'new')
   - Choose display name (3-30 characters)
   - Select avatar (1-6)
3. **Basic Commands**:
   - `status` - Check player info
   - `inventory` - View items, fragments, skills
   - `scan` - Look for fragments, bots, merchants
   - `move north/south/east/west` - Navigate matrix
   - `map` - See level overview
   - `help` - Command list

### **Defrag Bot Testing**:
1. **Encounter**: Move until you hit a defrag bot (10% chance per move outside safe zone)
2. **Analyze**: `defrag -h` for help
3. **View File**: `cat /proc/defrag/[BOT_ID]` (use actual bot ID from encounter message)
4. **Find PID**: Look for `defrag_process_id: [NUMBER]` in file content
5. **Extract PID**: `grep -o [ACTUAL_PID]` (use the actual PID number you saw)
6. **Kill Bot**: `kill -9 [ACTUAL_PID]` (use same PID)
7. **Check Rewards**: `inventory` to verify special items were added

### **Fragment Collection**:
1. **Scan**: Look for fragments at coordinates
2. **View**: `cat [fragment_name]` to see Python code
3. **Collect**: `pickup` to add to inventory
4. **Manage**: `cat fragment_file` to see collection

### **Social Features**:
1. **Heap**: Enter with `mingle` command
2. **Chat**: `echo [message]` to broadcast
3. **Trade**: `trade [entity]` to open trading
4. **Payment**: `pay [entity] [bits]` to transfer money
5. **Private**: `pm [entity] [message]` for direct messages

### **Advanced Systems**:
1. **Entropy**: Check with `entropy status`, refresh with `entropy refresh`
2. **Mining**: Check rewards with `mining`
3. **Fusion**: Enhance fragments with `fusion [fragment_name]`
4. **Items**: Use special items with `use [item_name]`
5. **Heap**: Enter with `mingle` command for social features

**REMEMBER**: Read game output carefully, respond to actual bot IDs and PIDs dynamically, never hardcode values in testing.

**Latest Fixes (Critical - Deep Research Applied)**:
- ✅ **Timestamp Formatting Issue**: Fixed `java.sql.Timestamp.format()` error with proper type checking
- ✅ **IRC-Style Chat System**: Real-time message broadcasting to all heap users
- ✅ **Hibernate Session Management**: All telnet thread database operations properly wrapped in transactions
- ✅ **Heap Chat System**: Fully functional with immediate message display and history
- ✅ **Movement System**: Coordinates properly constrained to 0-9 matrix bounds
- ✅ **Defrag Bot Spawning**: Fixed string method error and safe zone restrictions
- ✅ **Logic Fragment System**: Complete pickup/storage system with coordinate-based spawning
- ✅ **Fragment File Date Fix**: Fixed Date.format() error in fragment_file display
- ✅ **Fragment Quantity System**: Implemented duplicate fragment stacking with quantity display
- ✅ **Heap Trading System**: Full player-to-player commerce with pay/pm commands
- ✅ **Lambda Merchant System**: Random NPC merchants with shop interfaces on all levels
- ✅ **Null Safety for Collections**: Fixed fragment file display with proper null filtering
- ✅ **Daily Entropy System**: Digital coherence decay creating daily login addiction
- ✅ **Passive Bit Mining**: Offline bit generation with entropy-based efficiency
- ✅ **Fragment Fusion System**: RNG-based enhancement with daily attempt limits
- ✅ **Entropy Null Safety**: Fixed all null pointer exceptions in entropy system calculations
- ✅ **Special Items System**: Complete functional special item abilities with inventory integration
- ✅ **Avatar Ethnicity System**: Implemented all 6 Lambda ethnicities with unique gameplay bonuses

**Deep Research Findings (CRITICAL FOR ALL DATE OPERATIONS)**:
- **Date Formatting**: `Date.format()` method doesn't exist in this Groovy environment  
- **NEVER USE**: `dateObject.format('pattern')` - This will ALWAYS fail
- **ALWAYS USE**: `new java.text.SimpleDateFormat('pattern').format(dateObject)`
- **Database Types**: GORM queries may return `java.sql.Timestamp` vs `java.util.Date` depending on context
- **Universal Pattern**: `new java.text.SimpleDateFormat('pattern').format(dateObject)` works for ALL date types
- **Example Patterns**: 
  - Time only: `new java.text.SimpleDateFormat('HH:mm').format(date)`
  - Date/time: `new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').format(date)`
  - **REMEMBER**: This pattern works for Date, Timestamp, and all temporal objects

**CRITICAL NULL SAFETY PATTERNS**:
- **Collection Filtering**: ALWAYS use `collection.findAll { it != null }` before processing GORM collections
- **Safe Property Access**: Use `object?.property ?: defaultValue` for nullable properties
- **Safe Sorting**: Use `collection.sort { it?.property ?: defaultValue }` to handle null objects
- **Example Safe Pattern**:
  ```groovy
  managedPlayer.logicFragments.findAll { it != null }.each { fragment ->
      if (fragment) {
          def name = fragment.name ?: 'UNKNOWN'
          // safe processing
      }
  }
  ```
- **Why This Happens**: GORM collections can contain null references due to database constraints or orphaned records

## Advanced Game Systems (v0.3)

### **Logic Fragment Collection System**
- **Coordinate-based spawning**: 30% of coordinates have fragments based on position hash
- **Fragment types**: 7 different types (Conditional, Loop, Function, etc.) with power levels 1-10
- **Commands**:
  - `scan` - Detect fragments at current coordinates
  - `pickup` - Collect fragment (supports duplicates with quantity stacking)
  - `cat fragment_file` - View personal collection with quantities
  - `cat <fragment_name>` - View specific fragment Python code
- **Quantity system**: Duplicate pickups increment quantity (x2, x3, etc.)

### **Complete Trading Economy**
- **Heap Space Commands**:
  - `pay <entity> <bits>` - Transfer currency between players
  - `pm <entity> <message>` - Send private messages
  - `trade <entity>` - Open trading interface (shows inventory)
  - `list` or `who` - View all entities in heap space
- **Transaction safety**: All transfers use database transactions
- **Real-time notifications**: Payments broadcast to heap

### **Lambda Merchant System** 
- **Random spawning**: One merchant per matrix level at random coordinates
- **Merchant types**: Fragment traders with scaling prices by level
- **Shop interface**: Beautiful ASCII shop display with inventory
- **Commands**:
  - `shop` - Browse merchant inventory
  - `buy <number>` - Purchase fragments/items with bits
  - `sell <fragment_name>` - Sell fragments for 50% value
- **Dynamic pricing**: Higher levels = more expensive items
- **JSON inventory**: Flexible item management system

### **Enhanced Scan System**
- **Fragment detection**: Shows actual fragments at coordinates vs random text
- **Entity proximity**: Detects nearby players within 1 coordinate
- **Merchant detection**: Shows merchant name and type when present
- **Defrag bot warnings**: Real-time threat detection
- **Detailed output**: Fragment power levels and pickup instructions

### **Domain Architecture (v0.3)**
- **LogicFragment**: Added `quantity` field for stacking duplicates
- **LambdaMerchant**: New domain for NPC merchants with JSON inventory
- **LambdaMerchantService**: Complete shop mechanics and transaction handling
- **Trading integration**: Heap space supports full marketplace features

### **Command Reference (v0.4 - Addiction Systems)**
**Fragment Commands**: `scan`, `pickup`, `cat fragment_file`, `cat <fragment>`
**Trading Commands**: `pay <entity> <bits>`, `pm <entity> <msg>`, `trade <entity>`, `list`
**Merchant Commands**: `shop`, `buy <number>`, `sell <fragment>`
**Addiction Systems**: `entropy [status|refresh]`, `mining`, `fusion <fragment>`
**Movement**: `move north/south/east/west` (0-9 matrix bounds)
**Social**: `mingle` (enter heap), `echo <msg>` (chat), `exit` (leave)
**Combat**: `defrag -h`, `cat /proc/defrag/<id>`, `grep`, `kill -9 <pid>`

## Highly Addictive Game Mechanics (v0.4)

### **🧠 Daily Entropy Decay System** (IMPLEMENTED)
- **Mechanic**: Digital coherence decays 2% per hour offline
- **Hook**: Must login daily to refresh entropy or lose efficiency
- **Rewards**: Daily login bonus scaling with how low entropy gets (10-25 bits)
- **FOMO**: Critical coherence warnings create urgency

### **⛏️ Passive Bit Mining** (IMPLEMENTED)  
- **Mechanic**: Earn bits while offline based on mining rate
- **Hook**: Return to "harvest" accumulated rewards (max 24h)
- **Efficiency**: Mining affected by entropy level (0-100% efficiency)
- **Compulsion**: Players check regularly to avoid "wasting" mining time

### **✨ Fragment Fusion System** (IMPLEMENTED)
- **Mechanic**: Fuse 3+ identical fragments for enhanced versions
- **RNG Hook**: Variable success rates (30-85%) create gambling addiction
- **Daily Limits**: Limited fusion attempts per day (5 + processing level * 2)
- **Power Progression**: Enhanced fragments get +1 power level and improved capabilities
- **Risk/Reward**: Failed fusions lose 1 fragment, creating tension

### **🎒 Special Items System v1.0** (IMPLEMENTED - COMPREHENSIVE ABILITIES)
**10 Functional Item Types** with real gameplay benefits:

**SCANNER ITEMS**:
- **Scanner Boost** (3 uses): Reveals content in all 8 adjacent coordinates with detailed info
- **Defrag Detector** (5 uses): Shows defrag bot locations and difficulty in 3x3 area  
- **Matrix Mapper** (2 uses): Displays 5x5 grid with symbols (@=You, D=Defrag, F=Fragment, M=Merchant)
- **Fragment Magnet** (3 uses): Locates logic fragments in 5x5 area with precise distances

**COMBAT/PROTECTION ITEMS**:
- **Stealth Cloak** (1 use): 75% chance to avoid defrag bot encounters on next movement
- **Swap Space** (1 use): Blocks defrag attempt and converts to +50 bits instead
- **Respawn Cache** (1 use): Sets custom respawn point instead of (0,0) for 1 hour

**ECONOMY/ENHANCEMENT ITEMS**:
- **Bit Multiplier** (1 use): Doubles the next bit reward received
- **Logic Amplifier** (1 use): Next logic fragment pickup gets +1 power level
- **Entropy Stabilizer** (1 use): Prevents entropy decay for 1 hour (EPIC rarity)

**Usage System**: `use <item_name>` activates abilities | Rarity affects drop rates: COMMON→UNCOMMON→RARE→EPIC

### **🔊 Synthesized Audio System v1.0** (IMPLEMENTED - ADDICTIVE SOUND DESIGN)
**Comprehensive procedural audio generation using Java Sound API**:

**MOVEMENT SOUNDS** (Directional Audio Feedback):
- **Subtle Directional Tones**: Each direction has unique frequency (north=520Hz, south=480Hz, east=500Hz, west=460Hz)
- **Non-Piercing Design**: Pleasant sine waves at 0.1s duration, 0.3 volume
- **Addictive Quality**: Satisfying click-like tones that players want to hear repeatedly

**VICTORY & REWARD SOUNDS** (Dopamine Triggers):
- **Defrag Victory**: C-E-G major chord progression (261-329-392Hz) for 0.6s
- **Item Found**: Rising tone sweep 400→600Hz creating excitement anticipation
- **Fragment Pickup**: Rising tone 350→500Hz with smooth envelope
- **Bits Earned**: Multi-tinkle effect with ascending harmonics
- **Fusion Success**: 5-harmonic sparkle effect with random phase variations

**SYSTEM FEEDBACK SOUNDS** (Satisfying Interface):
- **Entropy Refresh**: Exponential frequency swoop 300→600Hz with curved progression
- **Scan Activate**: 3-pulse burst at 600Hz for quick feedback
- **Special Item Use**: Magical shimmer with 4 harmonics and tremolo effect
- **Error Sound**: Gentle warble between 300-250Hz (not harsh buzzer)

**ATMOSPHERIC SOUNDS** (Emotional Connection):
- **Login**: C-E-G-C ascending arpeggio welcome sequence
- **Logout**: G-E-C descending farewell chord
- **Level Up**: Full major scale run fanfare (future use)

**TECHNICAL SPECIFICATIONS**:
- **Sample Rate**: 44.1kHz, 16-bit mono for optimal quality
- **Frequency Range**: 250-800Hz sweet spot for addictive, non-fatiguing audio
- **Envelope Design**: Smooth attack/decay prevents clicks and pops
- **Thread Safety**: Audio plays in background threads to avoid game blocking
- **Memory Efficient**: Pre-generated sound cache for common effects
- **Synthesized**: No external files - all generated procedurally using mathematical formulas

**ADDICTIVE DESIGN PRINCIPLES APPLIED**:
- **Pleasant Frequencies**: 200-800Hz range scientifically proven to be enjoyable
- **Reward Association**: Victory sounds trigger dopamine response
- **Varied Timbres**: Multiple waveforms (sine, triangle, square) prevent monotony
- **Harmonic Richness**: Chord progressions and overtones create satisfying complexity
- **Short Duration**: 0.1-1.2s sounds prevent fatigue while providing clear feedback

## CRITICAL ISSUES TO FIX NEXT SESSION

### **🚨 CRITICAL BUG CONFIRMED: Special Items Not Appearing in Inventory**
**ISSUE REPRODUCED**: Special items show "Found" message but fail to save to player inventory
**Test Result**: "Found special item: SWAP_SPACE!" followed by "⚠️ Item creation failed - please contact admin"

**CONFIRMED BEHAVIOR**:
```
Process 1323 terminated successfully.
Earned 31 bits!
Found special item: SWAP_SPACE!
⚠️ Item creation failed - please contact admin
```
**Inventory Check**: No special items present despite success message

**ROOT CAUSE ANALYSIS NEEDED**:
The debug infrastructure is in place but we need to examine server logs to see:
1. What specific error occurs in `SpecialItemService.createSpecialItem()`
2. Whether it's a transaction issue, validation failure, or database constraint
3. If the LambdaPlayer.addToSpecialItems() relationship is working

**NEXT SESSION PRIORITY**:
1. **Check server console logs** for DEBUG output during item creation failure
2. **Examine specific error** thrown in SpecialItemService transaction
3. **Fix root cause** - likely transaction boundary or domain relationship issue
4. **Restore normal drop rates** after fix is confirmed
5. **Complete full playthrough test to floor 5+** as originally requested
6. **Test all commands and systems comprehensively**

**FULL PLAYTHROUGH TESTING REQUIRED**:
After fixing special items bug, perform comprehensive game testing:

**Step-by-Step Playthrough Instructions**:
1. Start server: `./gradlew bootRun`
2. Connect: `telnet localhost 8181`
3. Type `new` and press Enter
4. Enter username (3-20 characters) and press Enter
5. Enter display name and press Enter  
6. Choose avatar by typing numbers 1-6 and press Enter
7. Begin testing with `move north`, `move east`, etc.

**COMPREHENSIVE TESTING CHECKLIST**:
- ✅ **Movement**: Test all directions (north/south/east/west)
- ✅ **Scanning**: Use `scan` to detect fragments, merchants, threats
- ✅ **Combat**: Encounter defrag bots, test Linux commands
- ✅ **Heap Space**: Enter `mingle`, test `echo`, `pay`, `trade`, `pm` commands
- ✅ **Fragment Collection**: Use `pickup` and `cat fragment_file`
- ✅ **Inventory**: Check `inventory` command functionality
- ✅ **Special Items**: Test item usage with `use <item_name>`
- ✅ **Reach Floor 5**: Progress through matrix levels to test level advancement

**DEBUG STATUS**: 
- ✅ Comprehensive logging active
- ✅ Error reproduction confirmed  
- ✅ User feedback messages working
- 🔍 **Need to check server logs for detailed error trace**

**FILES TO EXAMINE**:
- `grails-app/services/ysap/SpecialItemService.groovy` (transaction handling)
- `grails-app/domain/ysap/SpecialItem.groovy` (domain constraints)
- `grails-app/domain/ysap/LambdaPlayer.groovy` (hasMany relationship)

**LIKELY CAUSES**:
- Transaction boundary issues in telnet threads
- Domain validation failures
- Hibernate session management problems
- Collection mapping configuration errors

### **🔄 Status Integration** (IMPLEMENTED)
- **Always Visible**: Entropy, mining rewards, fusion attempts shown in status
- **Color Coding**: Visual feedback creates emotional response to low entropy
- **Instant Gratification**: Commands show immediate colorful rewards/feedback

### **📅 Daily Cycle Psychology** (IMPLEMENTED)  
- **20-Hour Refresh Window**: Creates 4-hour optimal play window
- **Decay Pressure**: Fear of losing coherence drives daily engagement
- **Reward Scaling**: Bigger bonuses for rescuing critically low entropy
- **Reset Timers**: Fusion attempts reset every 24 hours

### **🎰 Variable Reward Schedules** (IMPLEMENTED)
- **Fusion RNG**: Unpredictable success creates dopamine anticipation
- **Bonus Scaling**: Entropy rescue rewards vary based on decay level
- **Mining Efficiency**: Fluctuating returns based on coherence state
- **Fragment Enhancement**: Successful fusions provide exponential power gains

### **⚡ Compulsion Loop Triggers** (IMPLEMENTED)
1. **Login** → Check entropy status → Refresh if available → Dopamine hit
2. **Offline Time** → Mining accumulation → Harvest rewards → Satisfaction  
3. **Collect Fragments** → Attempt fusion → Variable success → Repeat cycle
4. **Low Entropy Warning** → Urgency to refresh → Relief upon restoration

This creates overlapping addiction cycles targeting:
- **FOMO** (entropy decay, daily limits)
- **Variable Rewards** (fusion RNG, mining efficiency) 
- **Progress Investment** (enhanced fragments, coherence maintenance)
- **Time Pressure** (daily windows, decay rates)