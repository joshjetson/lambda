package ysap

import grails.gorm.transactions.Transactional

@Transactional
class GameSessionService {
    
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
}