package ysap

import grails.gorm.transactions.Transactional

@Transactional
class ElementalSymbolService {

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
        switch(symbolType) {
            case 'AIR': return player.hasAirSymbol
            case 'FIRE': return player.hasFireSymbol
            case 'EARTH': return player.hasEarthSymbol
            case 'WATER': return player.hasWaterSymbol
            default: return false
        }
    }
    
    def playerHasAllSymbols(LambdaPlayer player) {
        return player.hasAirSymbol && player.hasFireSymbol && 
               player.hasEarthSymbol && player.hasWaterSymbol
    }
    
    def getPlayerSymbolStatus(LambdaPlayer player) {
        def status = new StringBuilder()
        status.append("=== ELEMENTAL SYMBOLS ===\n")
        
        def symbols = [
            [type: 'AIR', has: player.hasAirSymbol, acquired: player.airSymbolAcquired],
            [type: 'FIRE', has: player.hasFireSymbol, acquired: player.fireSymbolAcquired],
            [type: 'EARTH', has: player.hasEarthSymbol, acquired: player.earthSymbolAcquired],
            [type: 'WATER', has: player.hasWaterSymbol, acquired: player.waterSymbolAcquired]
        ]
        
        symbols.each { symbol ->
            def icon = getSymbolIcon(symbol.type)
            def name = getSymbolName(symbol.type)
            def statusText = symbol.has ? "✅ ACQUIRED" : "❌ MISSING"
            
            status.append("${icon} ${name}: ${statusText}\n")
            if (symbol.has && symbol.acquired) {
                def formatter = new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm')
                status.append("   Acquired: ${formatter.format(symbol.acquired)}\n")
            }
        }
        
        def collectedCount = [player.hasAirSymbol, player.hasFireSymbol, player.hasEarthSymbol, player.hasWaterSymbol].count(true)
        status.append("\nProgress: ${collectedCount}/4 symbols collected\n")
        
        if (playerHasAllSymbols(player)) {
            status.append("🌟 READY FOR LOGIC DAEMON ENCOUNTER! 🌟\n")
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