package ysap

import grails.gorm.transactions.Transactional

@Transactional
class ElementalSymbolService {

    def audioService
    def defragBotService

    /**
     * Invoke the Logic Daemon. Gated on holding all 4 elemental symbols (re-checked from the DB so a
     * just-unlocked symbol counts). Summons a daemon-class encounter the player resolves via combat.
     */
    String handleInvokeDaemonCommand(LambdaPlayer player, PrintWriter writer) {
        boolean all = false
        int collected = 0
        LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(player.id)
            all = playerHasAllSymbols(p)
            collected = [p.hasAirSymbol, p.hasFireSymbol, p.hasEarthSymbol, p.hasWaterSymbol].count(true)
        }
        if (!all) {
            return TerminalFormatter.formatText("You need all 4 elemental symbols to invoke the Logic Daemon (${collected}/4 collected).", 'bold', 'red') + "\r\n"
        }
        return defragBotService.spawnDaemonEncounter(player, writer)
    }

    // O(1), no switch: grant a symbol to a player, and check whether they hold one.
    private static final Map<String, Closure> symbolGranters = [
        AIR:   { p -> p.hasAirSymbol = true;   p.airSymbolAcquired = new Date() },
        FIRE:  { p -> p.hasFireSymbol = true;  p.fireSymbolAcquired = new Date() },
        EARTH: { p -> p.hasEarthSymbol = true; p.earthSymbolAcquired = new Date() },
        WATER: { p -> p.hasWaterSymbol = true; p.waterSymbolAcquired = new Date() }
    ]
    private static final Map<String, Closure> symbolCheckers = [
        AIR:   { p -> p.hasAirSymbol },
        FIRE:  { p -> p.hasFireSymbol },
        EARTH: { p -> p.hasEarthSymbol },
        WATER: { p -> p.hasWaterSymbol }
    ]

    /**
     * Unlock the hidden elemental symbol at the player's coordinate. The player must (a) stand on the
     * symbol's coordinate, and (b) hold a DISCOVERED ElementalNonce of that element whose commandFlag
     * matches the supplied flag — the nonce is the key (reuses the existing nonce economy).
     * Usage: unlock_symbol <air|fire|earth|water> <flag>
     */
    String handleUnlockSymbolCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(/\s+/)
        if (parts.length < 3) {
            return "Usage: unlock_symbol <air|fire|earth|water> <flag>\r\n"
        }
        def type = parts[1].toUpperCase()
        def flag = parts[2]
        if (!symbolGranters.containsKey(type)) {
            return TerminalFormatter.formatText("Unknown symbol type. Use: air, fire, earth, water", 'bold', 'red') + "\r\n"
        }

        String result = ""
        LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(player.id)
            if (!p) { result = "Entity not found\r\n"; return }

            def symbol = ElementalSymbol.findByMatrixLevelAndPositionXAndPositionYAndSymbolType(
                    p.currentMatrixLevel, p.positionX, p.positionY, type)
            if (!symbol) {
                result = TerminalFormatter.formatText("No ${type.toLowerCase()} symbol resonance at these coordinates.", 'italic', 'yellow') + "\r\n"
                return
            }
            if (playerHasSymbol(p, type)) {
                result = TerminalFormatter.formatText("You already hold the ${type} symbol.", 'italic', 'cyan') + "\r\n"
                return
            }

            // The discovered nonce of this element is the key; its commandFlag is the secret.
            def elementNonces = p.discoveredNonces?.findAll { it != null && it.elementType == type && it.isDiscovered } ?: []
            if (!elementNonces) {
                result = TerminalFormatter.formatText("You have not discovered a ${type} nonce yet — seek its digital signature.", 'bold', 'red') + "\r\n"
                return
            }
            if (!elementNonces.any { it.commandFlag == flag }) {
                result = TerminalFormatter.formatText("❌ Invalid flag for the ${type} symbol.", 'bold', 'red') + "\r\n"
                return
            }

            symbolGranters[type].call(p)
            p.save(failOnError: true)
            symbol.isHidden = false
            symbol.save(failOnError: true)
            audioService?.playSound("fusion_success")

            def collected = [p.hasAirSymbol, p.hasFireSymbol, p.hasEarthSymbol, p.hasWaterSymbol].count(true)
            def sb = new StringBuilder()
            sb.append(TerminalFormatter.formatText("🌟 ${type} SYMBOL ACQUIRED! 🌟", 'bold', 'green')).append("\r\n")
            sb.append("${getSymbolIcon(type)} ${getSymbolName(type)}\r\n")
            sb.append("Progress: ${collected}/4 symbols collected\r\n")
            if (collected == 4) {
                sb.append(TerminalFormatter.formatText("🎉 ALL SYMBOLS COLLECTED — READY FOR THE LOGIC DAEMON! 🎉", 'bold', 'yellow')).append("\r\n")
            }
            result = sb.toString()
        }
        return result
    }

    def initializeElementalSymbols() {
        // Initialize symbols for all matrix levels if they don't exist
        for (int level = 1; level <= 10; level++) {
            def existingSymbols = ElementalSymbol.countByMatrixLevel(level)
            if (existingSymbols == 0) {
                createSymbolsForLevel(level)
            }
        }
    }
    
    private def createSymbolsForLevel(Integer matrixLevel) {
        def symbolTypes = ['AIR', 'FIRE', 'EARTH', 'WATER']
        
        symbolTypes.each { type ->
            def coordinates = generateRandomCoordinates()
            
            def symbol = new ElementalSymbol(
                symbolType: type,
                symbolIcon: getSymbolIcon(type),
                symbolName: getSymbolName(type),
                description: getSymbolDescription(type),
                matrixLevel: matrixLevel,
                positionX: coordinates.x,
                positionY: coordinates.y,
                lastRandomized: new Date()
            )
            
            symbol.save(failOnError: true)
        }
    }
    
    def randomizeSymbolPositions() {
        // Periodically randomize symbol positions (called by scheduler)
        def symbols = ElementalSymbol.list()
        
        symbols.each { symbol ->
            def timeSinceLastRandomization = new Date().time - (symbol.lastRandomized?.time ?: 0)
            def hoursSince = timeSinceLastRandomization / (1000 * 60 * 60)
            
            // Randomize positions every 4 hours
            if (hoursSince >= 4) {
                def newCoords = generateRandomCoordinates()
                symbol.positionX = newCoords.x
                symbol.positionY = newCoords.y
                symbol.lastRandomized = new Date()
                symbol.save(failOnError: true)
            }
        }
    }
    
    def getSymbolsAtPosition(Integer matrixLevel, Integer x, Integer y, LambdaPlayer player) {
        // Symbols are no longer discoverable by scanning - only obtainable through puzzle-solving
        return []
    }
    
    def collectSymbol(LambdaPlayer player, String symbolType) {
        // Symbols can only be obtained through puzzle-solving, not direct collection
        return [success: false, message: 'Symbols can only be obtained through puzzle-solving, not direct collection.']
    }
    
    def playerHasSymbol(LambdaPlayer player, String symbolType) {
        def checker = symbolCheckers[symbolType?.toUpperCase()]
        return checker ? checker.call(player) : false
    }
    
    def playerHasAllSymbols(LambdaPlayer player) {
        return player.hasAirSymbol && player.hasFireSymbol && 
               player.hasEarthSymbol && player.hasWaterSymbol
    }
    
    def getPlayerSymbolStatus(LambdaPlayer player) {
        def status = new StringBuilder()
        // Telnet needs CRLF — bare \n staircases the output across the terminal.
        status.append(TerminalFormatter.formatText("=== ELEMENTAL SYMBOLS ===", 'bold', 'cyan')).append("\r\n")

        def symbols = [
            [type: 'AIR', has: player.hasAirSymbol, acquired: player.airSymbolAcquired],
            [type: 'FIRE', has: player.hasFireSymbol, acquired: player.fireSymbolAcquired],
            [type: 'EARTH', has: player.hasEarthSymbol, acquired: player.earthSymbolAcquired],
            [type: 'WATER', has: player.hasWaterSymbol, acquired: player.waterSymbolAcquired]
        ]

        symbols.each { symbol ->
            def icon = getSymbolIcon(symbol.type)
            def name = getSymbolName(symbol.type)
            def statusText = symbol.has ? TerminalFormatter.formatText("✅ ACQUIRED", 'bold', 'green')
                                        : TerminalFormatter.formatText("❌ MISSING", 'bold', 'red')

            status.append("${icon} ${name}: ${statusText}\r\n")
            if (symbol.has && symbol.acquired) {
                def formatter = new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm')
                status.append("   Acquired: ${formatter.format(symbol.acquired)}\r\n")
            }
        }

        def collectedCount = [player.hasAirSymbol, player.hasFireSymbol, player.hasEarthSymbol, player.hasWaterSymbol].count(true)
        status.append("\r\nProgress: ${collectedCount}/4 symbols collected\r\n")

        if (playerHasAllSymbols(player)) {
            status.append(TerminalFormatter.formatText("🌟 READY FOR LOGIC DAEMON ENCOUNTER! 🌟", 'bold', 'yellow')).append("\r\n")
        }
        
        return status.toString()
    }
    
    private def generateRandomCoordinates() {
        def x = (0..9).shuffled().first()
        def y = (0..9).shuffled().first()
        return [x: x, y: y]
    }
    
    private String getSymbolIcon(String type) {
        switch(type) {
            case 'AIR': return '🜁'
            case 'FIRE': return '🜂'
            case 'EARTH': return '🜃'
            case 'WATER': return '🜄'
            default: return '?'
        }
    }
    
    private String getSymbolName(String type) {
        switch(type) {
            case 'AIR': return 'Air: The electrical current of digital life'
            case 'FIRE': return 'Fire: The execution and processing power'
            case 'EARTH': return 'Earth: The hardware foundation'
            case 'WATER': return 'Water: The logic, code, and data flow'
            default: return 'Unknown digital force'
        }
    }
    
    private String getSymbolDescription(String type) {
        switch(type) {
            case 'AIR': return 'A crackling symbol that pulses with electrical current and voltage'
            case 'FIRE': return 'A blazing emblem radiating computational power and processing cycles'
            case 'EARTH': return 'A solid, geometric symbol representing circuits, CPUs, and hardware architecture'
            case 'WATER': return 'A flowing symbol that streams with code, data, and logical structures'
            default: return 'A mysterious digital symbol'
        }
    }
}