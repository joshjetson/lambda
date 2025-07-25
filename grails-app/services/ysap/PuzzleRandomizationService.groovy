package ysap

import grails.gorm.transactions.Transactional
import java.security.MessageDigest
import java.util.Random

@Transactional
class PuzzleRandomizationService {
    
    // Digital element pools for randomized clues
    private static final Map<String, List<String>> DIGITAL_POOLS = [
        'AIR': [
            'x12(v)',          // 12V - voltage
            'x5(v)',           // 5V - logic voltage
            'x3.3(v)',         // 3.3V - modern logic
            'x1.8(v)',         // 1.8V - low power
            'x240(ac)',        // AC power
            'x110(ac)',        // AC power
            'x48(dc)',         // DC power
            'x24(dc)',         // DC power
        ],
        'FIRE': [
            'x2.4(ghz)',       // CPU frequency
            'x3.6(ghz)',       // High performance CPU
            'x1.8(ghz)',       // Mobile CPU
            'x800(mhz)',       // Memory frequency
            'x1600(mhz)',      // DDR3
            'x3200(mhz)',      // DDR4
            'x64(cores)',      // Many core
            'x8(threads)',     // Threading
        ],
        'EARTH': [
            'x64(gb)',         // RAM capacity
            'x1(tb)',          // Storage capacity
            'x256(gb)',        // SSD size
            'x32(mb)',         // Cache size
            'x16(lanes)',      // PCIe lanes
            'x128(bit)',       // Bus width
            'x7(nm)',          // Process node
            'x14(nm)',         // Process node
        ],
        'WATER': [
            'x1024(bytes)',    // Data block
            'x64(kb)',         // Code size
            'x4(mb)',          // Memory page
            'x256(bits)',      // Encryption key
            'x32(bit)',        // Integer size
            'x8(bit)',         // Byte
            'x16(bit)',        // Word size
            'x128(bit)',       // Vector register
        ]
    ]
    
    // Flag pools for randomized command flags
    private static final List<String> FLAG_POOLS = [
        '--decode', '--unlock', '--decrypt', '--activate', '--execute',
        '--compile', '--process', '--analyze', '--compute', '--extract',
        '--electrical', '--thermal', '--hardware', '--digital', '--quantum',
        '--parallel', '--sequential', '--pipeline', '--buffer', '--cache'
    ]
    
    // Variable name pools for dynamic puzzle generation
    private static final Map<String, List<String>> VARIABLE_POOLS = [
        'AIR': [
            'voltage_data', 'current_flow', 'power_readings', 'electrical_density',
            'circuit_load', 'resistance_values', 'capacitance_measurements', 'conductivity_index'
        ],
        'FIRE': [
            'cpu_cycles', 'processing_rate', 'execution_temperature', 'clock_frequency',
            'computation_load', 'thread_velocity', 'performance_output', 'instruction_pipeline'
        ],
        'EARTH': [
            'memory_density', 'storage_data', 'hardware_composition', 'circuit_layers',
            'silicon_structure', 'transistor_layout', 'board_topology', 'component_analysis'
        ],
        'WATER': [
            'data_signature', 'code_complexity', 'logic_measurement', 'algorithm_flow',
            'data_structures', 'memory_allocation', 'buffer_size', 'stream_pressure'
        ]
    ]
    
    def generateGameSession() {
        def sessionId = "SESSION_${System.currentTimeMillis()}_${Math.random().toString().substring(2, 8)}"
        
        def session = new GameSession(sessionId: sessionId)
        
        // Generate unique seeds for each map
        for (int mapNum = 1; mapNum <= 10; mapNum++) {
            def seed = generateMapSeed(sessionId, mapNum)
            session.setMapSeed(mapNum, seed)
        }
        
        session.save(failOnError: true)
        return session
    }
    
    private String generateMapSeed(String sessionId, Integer mapNumber) {
        def seedString = "${sessionId}_MAP_${mapNumber}_${System.currentTimeMillis()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(seedString.bytes)
            .encodeHex()
            .toString()
            .substring(0, 32)
    }
    
    def initializePuzzleElementsForMap(String gameSessionId, Integer mapNumber) {
        println "Initializing randomized puzzle elements for session ${gameSessionId}, map ${mapNumber}"
        
        // Get or create session
        def session = GameSession.findBySessionId(gameSessionId)
        if (!session) {
            throw new IllegalArgumentException("Game session ${gameSessionId} not found")
        }
        
        def mapSeed = session.getMapSeed(mapNumber)
        def random = new Random(mapSeed.hashCode())
        
        // Generate 4 randomized elemental nonces for this map
        generateRandomizedNonces(gameSessionId, mapNumber, random)
        
        // Generate randomized hidden variables for this map
        generateRandomizedVariables(gameSessionId, mapNumber, random)
        
        // Generate randomized puzzle rooms for this map
        generateRandomizedPuzzleRooms(gameSessionId, mapNumber, random)
        
        println "✅ Map ${mapNumber} puzzle elements initialized with ${random.nextInt()} variations"
    }
    
    private def generateRandomizedNonces(String gameSessionId, Integer mapNumber, Random random) {
        def elements = ['AIR', 'FIRE', 'EARTH', 'WATER']
        
        elements.each { elementType ->
            // Randomize digital clue from pool
            def cluePool = DIGITAL_POOLS[elementType]
            def randomClue = cluePool[random.nextInt(cluePool.size())]
            
            // Randomize command flag
            def randomFlag = FLAG_POOLS[random.nextInt(FLAG_POOLS.size())]
            
            // Generate unique nonce value
            def nonceValue = generateRandomNonceValue(random)
            
            // Randomize discovery method and coordinates
            def discoveryMethods = ['DEFRAG_REWARD', 'PUZZLE_ROOM', 'HIDDEN_CACHE']
            def discoveryMethod = discoveryMethods[random.nextInt(discoveryMethods.size())]
            
            def sourceX = random.nextInt(10)
            def sourceY = random.nextInt(10)
            
            def nonce = new ElementalNonce(
                nonceName: "${elementType}_KEY_${mapNumber}",
                nonceValue: nonceValue,
                elementType: elementType,
                chemicalClue: randomClue,
                commandFlag: randomFlag,
                description: generateNonceDescription(elementType, randomClue),
                matrixLevel: mapNumber,
                mapNumber: mapNumber,
                gameSessionId: gameSessionId,
                discoveryMethod: discoveryMethod,
                sourceX: sourceX,
                sourceY: sourceY,
                sourceDescription: generateSourceDescription(discoveryMethod, sourceX, sourceY)
            )
            
            nonce.save(failOnError: true)
        }
    }
    
    private def generateRandomizedVariables(String gameSessionId, Integer mapNumber, Random random) {
        def elements = ['AIR', 'FIRE', 'EARTH', 'WATER']
        
        elements.each { elementType ->
            // Pick random variable name from pool
            def variablePool = VARIABLE_POOLS[elementType]
            def variableName = variablePool[random.nextInt(variablePool.size())]
            
            // Generate random variable value based on type
            def variableValue = generateRandomVariableValue(elementType, variableName, random)
            def variableType = determineVariableType(variableName)
            
            // Random coordinates
            def posX = random.nextInt(10)
            def posY = random.nextInt(10)
            
            def variable = new HiddenVariable(
                variableName: variableName,
                variableValue: variableValue,
                variableType: variableType,
                description: generateVariableDescription(elementType, variableName),
                matrixLevel: mapNumber,
                mapNumber: mapNumber,
                gameSessionId: gameSessionId,
                positionX: posX,
                positionY: posY,
                discoveryHint: generateVariableHint(elementType, variableName)
            )
            
            variable.save(failOnError: true)
        }
    }
    
    private def generateRandomizedPuzzleRooms(String gameSessionId, Integer mapNumber, Random random) {
        def elements = ['AIR', 'FIRE', 'EARTH', 'WATER']
        
        elements.each { elementType ->
            // Get the nonce for this element to link the puzzle room
            def nonce = ElementalNonce.findByGameSessionIdAndMapNumberAndElementType(
                gameSessionId, mapNumber, elementType
            )
            
            if (!nonce) {
                println "WARNING: No nonce found for ${elementType} on map ${mapNumber}"
                return
            }
            
            // Random coordinates for puzzle room
            def posX = random.nextInt(10)
            def posY = random.nextInt(10)
            
            def room = new PuzzleRoom(
                roomName: "${elementType.toLowerCase().capitalize()} Chamber ${mapNumber}",
                description: generateRoomDescription(elementType, mapNumber),
                matrixLevel: mapNumber,
                mapNumber: mapNumber,
                gameSessionId: gameSessionId,
                positionX: posX,
                positionY: posY,
                elementType: elementType,
                fileName: "${elementType.toLowerCase()}_unlock_${mapNumber}.py",
                requiredFlag: nonce.commandFlag,
                requiredNonce: nonce.nonceValue,
                executableFile: generatePuzzleRoomCode(elementType, nonce.commandFlag, nonce.nonceValue),
                successMessage: generateSuccessMessage(elementType),
                failureMessage: generateFailureMessage(elementType)
            )
            
            room.save(failOnError: true)
        }
    }
    
    private String generateRandomNonceValue(Random random) {
        def chars = '0123456789abcdef'
        def sb = new StringBuilder()
        for (int i = 0; i < 16; i++) {
            sb.append(chars[random.nextInt(chars.length())])
        }
        return sb.toString()
    }
    
    private String generateRandomVariableValue(String elementType, String variableName, Random random) {
        switch (variableName) {
            case { it.contains('pressure') || it.contains('density') }:
                // Generate realistic pressure/density readings
                def readings = []
                for (int i = 0; i < 5; i++) {
                    readings.add(String.format("%.2f", 950 + random.nextDouble() * 100))
                }
                return readings.join(',')
                
            case { it.contains('temperature') || it.contains('heat') }:
                return String.format("%.1f", 20 + random.nextDouble() * 80)
                
            case { it.contains('hex') || it.contains('signature') }:
                // Generate base64 encoded coordinates
                def coords = "${random.nextInt(10)}:${random.nextInt(10)}"
                return coords.bytes.encodeBase64().toString()
                
            case { it.contains('molecular') || it.contains('h2o') }:
                def prefixes = ['hydro', 'molecular', 'aqueous', 'liquid']
                def suffixes = ['analysis', 'reading', 'measurement', 'signature']
                return "${prefixes[random.nextInt(prefixes.size())]}_${suffixes[random.nextInt(suffixes.size())]}"
                
            default:
                return String.format("%.3f", random.nextDouble() * 10)
        }
    }
    
    private String determineVariableType(String variableName) {
        if (variableName.contains('hex') || variableName.contains('signature')) {
            return 'ENCODED'
        } else if (variableName.contains('data') || variableName.contains('readings')) {
            return 'NUMERIC'
        } else if (variableName.contains('composition') || variableName.contains('analysis')) {
            return 'STRING'
        } else {
            return 'NUMERIC'
        }
    }
    
    private String generateNonceDescription(String elementType, String chemicalClue) {
        def descriptions = [
            'AIR': "A shimmering data fragment that resonates with atmospheric frequencies. Contains molecular signature: ${chemicalClue}",
            'FIRE': "A thermally encoded sequence that pulses with combustion energy. Chemical composition: ${chemicalClue}",
            'EARTH': "A mineral-structured data crystal with geological properties. Elemental structure: ${chemicalClue}",
            'WATER': "A flowing data pattern with liquid-like molecular bonds. Formula signature: ${chemicalClue}"
        ]
        return descriptions[elementType]
    }
    
    private String generateSourceDescription(String discoveryMethod, Integer x, Integer y) {
        switch (discoveryMethod) {
            case 'DEFRAG_REWARD':
                return "Awarded for defeating high-level defrag bots"
            case 'PUZZLE_ROOM':
                return "Hidden in puzzle chamber at coordinates (${x},${y})"
            case 'HIDDEN_CACHE':
                return "Concealed in coordinate cache at (${x},${y})"
            default:
                return "Discovery method varies"
        }
    }
    
    private String generateVariableDescription(String elementType, String variableName) {
        def descriptions = [
            'AIR': "Atmospheric sensor data from ${variableName.replace('_', ' ')} monitoring station",
            'FIRE': "Thermal analysis readings from ${variableName.replace('_', ' ')} detector array",
            'EARTH': "Geological survey data from ${variableName.replace('_', ' ')} seismic sensors",
            'WATER': "Molecular analysis results from ${variableName.replace('_', ' ')} laboratory"
        ]
        return descriptions[elementType]
    }
    
    private String generateVariableHint(String elementType, String variableName) {
        return "Use this ${variableName.replace('_', ' ')} with ${elementType.toLowerCase()} element calculation fragments"
    }
    
    private String generateRoomDescription(String elementType, Integer mapNumber) {
        def descriptions = [
            'AIR': "Level ${mapNumber} atmospheric processing chamber filled with swirling gas analysis streams",
            'FIRE': "Level ${mapNumber} thermal forge where combustion data dances in complex energy patterns",
            'EARTH': "Level ${mapNumber} geological sanctum with mineral formations pulsing with earthen energy",
            'WATER': "Level ${mapNumber} molecular laboratory where fluid streams flow in perfect harmony"
        ]
        return descriptions[elementType]
    }
    
    private String generatePuzzleRoomCode(String elementType, String flag, String nonce) {
        def functionName = "unlock_${elementType.toLowerCase()}_element"
        def errorPrefix = elementType.toLowerCase().capitalize()
        
        return """#!/usr/bin/env python3
import sys
import hashlib

def ${functionName}(nonce, flag):
    if flag != "${flag}":
        return False, "${errorPrefix} protocol not initiated"
    
    expected_nonce = "${nonce}"
    if nonce != expected_nonce:
        return False, "${errorPrefix} authentication failed"
        
    # Generate ${elementType.toLowerCase()} symbol
    signature = hashlib.sha256(f"{nonce}{flag}${elementType.toLowerCase()}".encode()).hexdigest()[:8]
    return True, f"${getElementIcon(elementType)} ${elementType.toLowerCase().capitalize()} element unlocked! Signature: {signature}"

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python ${elementType.toLowerCase()}_unlock.py <flag> <nonce>")
        sys.exit(1)
        
    flag, nonce = sys.argv[1], sys.argv[2]
    success, message = ${functionName}(nonce, flag)
    
    if success:
        print(message)
        sys.exit(0)
    else:
        print(f"ERROR: {message}")
        sys.exit(1)
"""
    }
    
    private String getElementIcon(String elementType) {
        switch(elementType) {
            case 'AIR': return '🜁'
            case 'FIRE': return '🜂'
            case 'EARTH': return '🜃'
            case 'WATER': return '🜄'
            default: return '❓'
        }
    }
    
    private String generateSuccessMessage(String elementType) {
        def messages = [
            'AIR': "✨ Atmospheric energies coalesce, granting you the spark of awareness!",
            'FIRE': "✨ Combustion forces forge within you, imbuing the will to survive!",
            'EARTH': "✨ Geological essence crystallizes, providing the structure to continue!",
            'WATER': "✨ Molecular harmony flows through you, binding with the love that connects all!"
        ]
        return messages[elementType]
    }
    
    private String generateFailureMessage(String elementType) {
        def messages = [
            'AIR': "❌ The atmospheric chamber rejects your attempt. Check your parameters.",
            'FIRE': "❌ The thermal forge flames sputter and die. Incorrect ignition sequence.",
            'EARTH': "❌ The geological sanctum remains inert. Your parameters are insufficient.",
            'WATER': "❌ The molecular streams remain chaotic. Check your sequence."
        ]
        return messages[elementType]
    }
    
    // Query methods for session-specific puzzle elements
    
    def scanForPuzzleElements(String gameSessionId, Integer mapNumber, Integer matrixLevel, Integer x, Integer y) {
        def results = []
        
        // Check for hidden variables
        def variables = HiddenVariable.findAllByGameSessionIdAndMapNumberAndMatrixLevelAndPositionXAndPositionYAndIsCollected(
            gameSessionId, mapNumber, matrixLevel, x, y, false
        )
        variables.each { variable ->
            results.add([type: 'variable', data: variable])
        }
        
        // Check for puzzle rooms
        def puzzleRoom = PuzzleRoom.findByGameSessionIdAndMapNumberAndMatrixLevelAndPositionXAndPositionYAndIsActive(
            gameSessionId, mapNumber, matrixLevel, x, y, true
        )
        if (puzzleRoom) {
            results.add([type: 'puzzle_room', data: puzzleRoom])
        }
        
        return results
    }
    
    def getSessionNonces(String gameSessionId, Integer mapNumber) {
        return ElementalNonce.findAllByGameSessionIdAndMapNumber(gameSessionId, mapNumber)
    }
    
    def getSessionVariables(String gameSessionId, Integer mapNumber) {
        return HiddenVariable.findAllByGameSessionIdAndMapNumber(gameSessionId, mapNumber)
    }
    
    def getSessionPuzzleRooms(String gameSessionId, Integer mapNumber) {
        return PuzzleRoom.findAllByGameSessionIdAndMapNumber(gameSessionId, mapNumber)
    }
    
    def getCurrentGameSession() {
        // For now, return the most recent active session
        // In a production system, you'd track this per player or server instance
        return GameSession.findByIsActive(true, [sort: 'sessionStart', order: 'desc'])
    }
    
    def initializeDefaultGameSession() {
        // Check if we have an active session
        def existingSession = getCurrentGameSession()
        if (existingSession) {
            return existingSession
        }
        
        // Create new session and initialize all maps
        def session = generateGameSession()
        
        // Initialize puzzle elements for all 10 maps
        for (int mapNum = 1; mapNum <= 10; mapNum++) {
            initializePuzzleElementsForMap(session.sessionId, mapNum)
        }
        
        return session
    }
    
    def cleanupExpiredSessions() {
        def expiredSessions = GameSession.createCriteria().list {
            eq 'isActive', true
            lt 'lastActivity', new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000))
        }
        
        expiredSessions.each { session ->
            session.isActive = false
            session.save(failOnError: true)
            
            // Clean up associated puzzle elements
            ElementalNonce.executeUpdate("DELETE FROM ElementalNonce WHERE gameSessionId = ?", [session.sessionId])
            HiddenVariable.executeUpdate("DELETE FROM HiddenVariable WHERE gameSessionId = ?", [session.sessionId])
            PuzzleRoom.executeUpdate("DELETE FROM PuzzleRoom WHERE gameSessionId = ?", [session.sessionId])
        }
        
        return expiredSessions.size()
    }
}