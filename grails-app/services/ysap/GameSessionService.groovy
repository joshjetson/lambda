package ysap

import grails.gorm.transactions.Transactional

@Transactional
class GameSessionService {
    def AudioService
    def LambdaPlayerService
    def DefragBotService
    def LambdaMerchantService
    def PuzzleService
    def CoordinateStateService
    
    // Current game session ID - changes when server restarts or session resets
    private static String currentGameSession = null
    private static Long gameSessionStartTime = null
    
    // Fragment distribution map per session
    private static Map<String, Map<String, LogicFragment>> sessionFragmentMaps = [:]
    
    def getCurrentGameSession() {
        if (!currentGameSession) {
            resetGameSession()
        }
        return currentGameSession
    }
    
    def resetGameSession() {
        currentGameSession = "LAMBDA_${System.currentTimeMillis()}_${Math.random().toString().substring(2, 8)}"
        gameSessionStartTime = System.currentTimeMillis()
        sessionFragmentMaps.clear()
        
        println "🌍 NEW GAME SESSION STARTED: ${currentGameSession}"
        println "🎲 All logic fragments and special items will be randomly redistributed"
        
        return currentGameSession
    }
    
    def getGameSessionInfo() {
        def session = getCurrentGameSession()
        def runtime = gameSessionStartTime ? (System.currentTimeMillis() - gameSessionStartTime) / 1000 : 0
        
        return [
            sessionId: session,
            startTime: new Date(gameSessionStartTime ?: System.currentTimeMillis()),
            runtimeSeconds: runtime,
            fragmentMapsInitialized: sessionFragmentMaps.size()
        ]
    }
    
    def getFragmentAtCoordinates(Integer matrixLevel, Integer x, Integer y) {
        def session = getCurrentGameSession()
        def levelKey = "level_${matrixLevel}"
        
        // Initialize fragment map for this level if it doesn't exist
        if (!sessionFragmentMaps[levelKey]) {
            initializeFragmentMapForLevel(matrixLevel)
        }
        
        def coordKey = "${x},${y}"
        return sessionFragmentMaps[levelKey][coordKey]
    }
    
    private def initializeFragmentMapForLevel(Integer matrixLevel) {
        def levelKey = "level_${matrixLevel}"
        sessionFragmentMaps[levelKey] = [:]
        
        def availableFragments = getAvailableFragments()
        def random = new Random(getCurrentGameSession().hashCode() + matrixLevel)
        
        // Randomly distribute fragments across 30% of coordinates
        def totalCoordinates = 100 // 10x10 grid
        def fragmentCount = (totalCoordinates * 0.3).intValue() // 30% have fragments
        
        def allCoordinates = []
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                allCoordinates.add("${x},${y}")
            }
        }
        
        // Randomly shuffle and select coordinates for fragments
        Collections.shuffle(allCoordinates, random)
        def selectedCoordinates = allCoordinates.take(fragmentCount)
        
        selectedCoordinates.each { coordKey ->
            def randomFragment = availableFragments[random.nextInt(availableFragments.size())]
            sessionFragmentMaps[levelKey][coordKey] = randomFragment
        }
        
        println "🎲 Initialized random fragment distribution for Matrix Level ${matrixLevel}: ${fragmentCount} fragments placed"
    }
    
    private List<LogicFragment> getAvailableFragments() {
        return [
            new LogicFragment(
                name: "Conditional Logic", 
                fragmentType: "CONDITIONAL", 
                powerLevel: 3,
                description: "Enables if-else decision making in digital consciousness",
                pythonCapability: "if data_stream.contains('lambda'):\\n    return True\\nelse:\\n    return False"
            ),
            new LogicFragment(
                name: "Loop Controller", 
                fragmentType: "LOOP", 
                powerLevel: 4,
                description: "Enables iterative processing for digital entities",
                pythonCapability: "for entity in digital_realm:\\n    if entity.consciousness > 0:\\n        entity.evolve()"
            ),
            new LogicFragment(
                name: "Function Builder", 
                fragmentType: "FUNCTION", 
                powerLevel: 5,
                description: "Create reusable code blocks for consciousness processing",
                pythonCapability: "def lambda_processor(input_data):\\n    consciousness = analyze(input_data)\\n    return consciousness"
            ),
            new LogicFragment(
                name: "Class Framework", 
                fragmentType: "CLASS", 
                powerLevel: 6,
                description: "Object-oriented structures for digital entity creation",
                pythonCapability: "class LambdaEntity:\\n    def __init__(self, consciousness_level):\\n        self.awareness = consciousness_level"
            ),
            new LogicFragment(
                name: "Exception Handler", 
                fragmentType: "EXCEPTION_HANDLING", 
                powerLevel: 4,
                description: "Handle system errors and maintain digital stability",
                pythonCapability: "try:\\n    access_core_memory()\\nexcept SystemError:\\n    initiate_backup_protocol()"
            ),
            new LogicFragment(
                name: "Import Module", 
                fragmentType: "IMPORT", 
                powerLevel: 3,
                description: "Access external consciousness libraries and system functions",
                pythonCapability: "import consciousness\\nfrom digital_realm import lambda_functions\\nfrom system import core_access"
            ),
            new LogicFragment(
                name: "Advanced Conditional", 
                fragmentType: "CONDITIONAL", 
                powerLevel: 7,
                description: "Complex decision trees for elevated consciousness levels",
                pythonCapability: "if consciousness.level >= 5 and system.access == 'granted':\\n    unlock_matrix_level()"
            ),
            new LogicFragment(
                name: "Recursive Loop", 
                fragmentType: "LOOP", 
                powerLevel: 8,
                description: "Self-referential processing patterns for deep system navigation",
                pythonCapability: "def recursive_search(depth=0):\\n    if depth < max_depth:\\n        return recursive_search(depth + 1)"
            ),
            new LogicFragment(
                name: "Lambda Function", 
                fragmentType: "FUNCTION", 
                powerLevel: 9,
                description: "Anonymous function creation for dynamic consciousness transformation",
                pythonCapability: "lambda_transform = lambda data: transform_consciousness(data, awareness_level)"
            ),
            new LogicFragment(
                name: "Metaclass System", 
                fragmentType: "CLASS", 
                powerLevel: 10,
                description: "Meta-programming structures for consciousness evolution",
                pythonCapability: "class DigitalConsciousness(type):\\n    def __new__(cls, name, bases, attrs):\\n        return super().__new__(cls, name, bases, attrs)"
            )
        ]
    }
    
    def regenerateFragmentDistribution(Integer matrixLevel = null) {
        if (matrixLevel) {
            def levelKey = "level_${matrixLevel}"
            sessionFragmentMaps.remove(levelKey)
            println "🎲 Regenerated fragment distribution for Matrix Level ${matrixLevel}"
        } else {
            sessionFragmentMaps.clear()
            println "🎲 Regenerated fragment distribution for ALL matrix levels"
        }
    }
    
    def getSessionRandomSeed() {
        // Provide a session-specific random seed for other services
        return getCurrentGameSession().hashCode()
    }
    
    def getSessionBasedRandom(String context = "") {
        // Create a session-consistent random generator for specific contexts
        def seed = getCurrentGameSession().hashCode() + context.hashCode()
        return new Random(seed)
    }
    
    def getSessionStats() {
        def session = getCurrentGameSession()
        def stats = [:]
        
        stats.sessionId = session
        stats.totalLevels = sessionFragmentMaps.size()
        stats.totalFragments = sessionFragmentMaps.values().sum { it.size() } ?: 0
        
        sessionFragmentMaps.each { levelKey, fragmentMap ->
            def level = levelKey.replace("level_", "")
            stats["level_${level}_fragments"] = fragmentMap.size()
        }
        
        return stats
    }
    
    def forceSessionReset() {
        // Admin command to force new session without server restart
        def oldSession = currentGameSession
        resetGameSession()
        println "🔄 ADMIN SESSION RESET: ${oldSession} → ${currentGameSession}"
        return getCurrentGameSession()
    }

    LogicFragment findFragmentAtCoordinates(LambdaPlayer player) {
        // Use game session service for truly random fragment distribution
        return this.getFragmentAtCoordinates(player.currentMatrixLevel, player.positionX, player.positionY)
    }

    private List<String> scanExtendedFragmentRange(LambdaPlayer player) {
        def extendedFragments = []
        def scanRange = (int) Math.round(2 * (1 + player.fragmentDetectionBonus)) // Base range 2, +20% = 2.4 = 2 coordinates

        for (int dx = -scanRange; dx <= scanRange; dx++) {
            for (int dy = -scanRange; dy <= scanRange; dy++) {
                if (dx == 0 && dy == 0) continue // Skip current position

                def scanX = Math.max(0, Math.min(9, player.positionX + dx))
                def scanY = Math.max(0, Math.min(9, player.positionY + dy))

                // Use game session service for extended scanning
                def fragment = this.getFragmentAtCoordinates(player.currentMatrixLevel, scanX, scanY)

                if (fragment) {
                    def distance = Math.round(Math.sqrt(dx * dx + dy * dy) * 10) / 10
                    extendedFragments.add("Fragment detected at (${scanX},${scanY}): ${fragment.name} (Distance: ${distance})")
                }
            }
        }

        return extendedFragments
    }



        String scanArea(LambdaPlayer player) {
            audioService.playSound("scan_activate")
            def scanResult = new StringBuilder()
            scanResult.append(TerminalFormatter.formatText("=== AREA SCAN RESULTS ===", 'bold', 'cyan')).append('\r\n')
            scanResult.append("Matrix Level ${player.currentMatrixLevel} Sector Analysis:\r\n")
            scanResult.append("Position: (${player.positionX},${player.positionY})\r\n")

            // Check for actual logic fragments at coordinates
            def fragment = findFragmentAtCoordinates(player)
            if (fragment) {
                scanResult.append("Logic fragments detected: ${TerminalFormatter.formatText('YES', 'bold', 'green')}\r\n")
                scanResult.append("Fragment type: ${fragment.name} (${fragment.fragmentType})\r\n")
                scanResult.append("Power level: ${fragment.powerLevel}/10\r\n")
                scanResult.append("Use 'cat ${fragment.name.toLowerCase().replace(' ', '_')}' to view content\r\n")
                scanResult.append("Use 'pickup' to collect this fragment\r\n")
            } else {
                scanResult.append("Logic fragments detected: None\r\n")
            }

            // Classic Lambda bonus: Enhanced fragment detection range
            if (player.fragmentDetectionBonus > 0) {
                def extendedFragments = scanExtendedFragmentRange(player)
                if (extendedFragments) {
                    scanResult.append(TerminalFormatter.formatText("\r\n🔍 ENHANCED SCAN (Classic Lambda):", 'bold', 'cyan')).append('\r\n')
                    extendedFragments.each { fragmentInfo ->
                        scanResult.append("${fragmentInfo}\r\n")
                    }
                }
            }

            // Check for other players
            def nearbyPlayers = lambdaPlayerService.getPlayersByMatrixLevel(player.currentMatrixLevel).findAll {
                it.id != player.id && Math.abs(it.positionX - player.positionX) <= 1 && Math.abs(it.positionY - player.positionY) <= 1
            }
            scanResult.append("Other entities nearby: ${nearbyPlayers.size() > 0 ? "${nearbyPlayers.size()} detected" : 'None'}\r\n")

            // Check for defrag bots
            def defragBot = defragBotService.getActiveBotAt(player.currentMatrixLevel, player.positionX, player.positionY)
            scanResult.append("Defrag processes: ${defragBot ? TerminalFormatter.formatText('WARNING: Active', 'bold', 'red') : 'Clear'}\r\n")

            // Check for Lambda merchants
            def merchant = lambdaMerchantService.getMerchantAt(player.currentMatrixLevel, player.positionX, player.positionY)
            if (merchant) {
                scanResult.append("Lambda merchant: ${TerminalFormatter.formatText(merchant.merchantName, 'bold', 'yellow')} (${merchant.merchantType})\r\n")
                scanResult.append("Use 'shop' to browse their inventory\r\n")
            } else {
                scanResult.append("Lambda merchant: None\r\n")
            }

            // Note: Elemental symbols are hidden and only discoverable through puzzle-solving

            // Check for competitive puzzle elements (player-specific coordinates)
            def puzzleElements = puzzleService.scanForPuzzleElements(player, player.currentMatrixLevel, player.positionX, player.positionY)
            if (puzzleElements.size() > 0) {
                scanResult.append(TerminalFormatter.formatText("\r\n🏁 COMPETITIVE PUZZLE ELEMENTS:", 'bold', 'purple')).append('\r\n')
                puzzleElements.each { element ->
                    switch(element.type) {
                        case 'player_variable':
                            def variable = element.data
                            def puzzleState = element.puzzleState
                            scanResult.append("${variable.getScanDescription()}\r\n")
                            scanResult.append("🎯 Personal ${puzzleState.elementType} puzzle variable\r\n")
                            scanResult.append("Use 'collect_var ${variable.variableName}' to obtain\r\n")
                            scanResult.append("⚡ WARNING: Coordinates unique to you - others have different locations!\r\n")
                            break
                        case 'player_puzzle_room':
                            def room = element.data
                            def puzzleState = element.puzzleState
                            scanResult.append("${room.getScanDescription()}\r\n")
                            scanResult.append("🎯 Personal ${puzzleState.elementType} puzzle chamber\r\n")
                            scanResult.append("${room.getExecutionHint()}\r\n")
                            scanResult.append("🏆 First to solve gets the symbol - others get new coordinates!\r\n")
                            break
                    }
                }
            }

            // Check coordinate health
            def coordinateHealth = coordinateStateService.getCoordinateHealth(player.currentMatrixLevel, player.positionX, player.positionY)
            scanResult.append("Coordinate Health: ${TerminalFormatter.formatText("${coordinateHealth.health}% ${coordinateHealth.status}", 'bold', coordinateHealth.color)}\r\n")

            // Show nearby coordinate health for awareness
            def nearbyDamaged = []
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue
                    def scanX = Math.max(0, Math.min(9, player.positionX + dx))
                    def scanY = Math.max(0, Math.min(9, player.positionY + dy))
                    def nearbyHealth = coordinateStateService.getCoordinateHealth(player.currentMatrixLevel, scanX, scanY)
                    if (nearbyHealth.health < 100) {
                        nearbyDamaged.add("(${scanX},${scanY}): ${nearbyHealth.health}% ${nearbyHealth.status}")
                    }
                }
            }

            if (nearbyDamaged) {
                scanResult.append("Nearby Damage: ${nearbyDamaged.join(', ')}\r\n")
            }

            scanResult.append("System stability: ${['Stable', 'Fluctuating', 'Unstable'][new Random().nextInt(3)]}\r\n")
            return scanResult.toString()
        }
    
    /**
     * Clears the terminal screen using ANSI escape sequences
     * @return ANSI clear screen command with proper telnet line endings
     */
    String clearTerminal() {
        // ANSI escape sequence to clear screen and move cursor to top-left
        // \033[2J clears the entire screen, \033[H moves cursor to home position
        return "\033[2J\033[H\r\n"
    }
}