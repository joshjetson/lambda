package ysap

import grails.gorm.transactions.Transactional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Transactional
class SlotMachineService {
    
    // Active slot machine sessions by session ID
    private static Map<String, SlotMachineSession> activeSessions = new ConcurrentHashMap<>()
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10)
    
    /**
     * Generic slot machine session that can be used for any mini-game
     */
    static class SlotMachineSession {
        String sessionId
        String playerUsername
        String targetCode                    // Code player needs to match
        List<Integer> lockedDigits = []      // Digits that have been locked
        List<Integer> currentCyclingValues = [] // Current values for each position
        Integer currentSlotIndex = 0         // Which slot is currently cycling
        Boolean isActive = true
        Boolean gameCompleted = false
        Long startTime = System.currentTimeMillis()
        ScheduledFuture<?> cyclingTask
        PrintWriter playerWriter             // Direct reference to send updates
        
        // Configurable parameters
        Map<String, Object> config = [:]    // Custom configuration per session
        Closure onComplete                   // Callback when game completes
        Closure onDigitLocked               // Callback when digit is locked
        Closure onUpdate                    // Callback for real-time updates
        
        // Default cycling speeds (can be overridden in config)
        Integer getCurrentCyclingSpeed() {
            def speeds = config.cyclingSpeeds ?: [300, 200, 150, 100, 75]
            def index = Math.min(currentSlotIndex, speeds.size() - 1)
            return speeds[index]
        }
        
        String getDisplayString() {
            def display = []
            for (int i = 0; i < targetCode.length(); i++) {
                if (i < lockedDigits.size()) {
                    // Show locked digit
                    display.add(lockedDigits[i].toString())
                } else if (i == currentSlotIndex && currentCyclingValues.size() > i) {
                    // Show currently cycling digit
                    display.add(currentCyclingValues[i].toString())
                } else {
                    // Show empty slot
                    display.add(config.emptySlotChar ?: "-")
                }
            }
            return display.join("")
        }
        
        Boolean isComplete() {
            return lockedDigits.size() >= targetCode.length()
        }
        
        Boolean isCorrect() {
            if (!isComplete()) return false
            return lockedDigits.join("") == targetCode
        }
        
        void initializeCyclingValues() {
            currentCyclingValues = []
            def random = new Random()
            for (int i = 0; i < targetCode.length(); i++) {
                currentCyclingValues.add(random.nextInt(10))
            }
        }
        
        Map getSessionInfo() {
            return [
                sessionId: sessionId,
                playerUsername: playerUsername,
                targetCode: targetCode,
                currentDisplay: getDisplayString(),
                currentSlot: currentSlotIndex + 1,
                totalSlots: targetCode.length(),
                isComplete: isComplete(),
                isCorrect: isCorrect(),
                elapsedTime: System.currentTimeMillis() - startTime
            ]
        }
    }
    
    /**
     * Start a new slot machine mini-game
     * @param sessionId Unique identifier for this session
     * @param playerUsername Username of the player
     * @param targetCode The code the player needs to match (e.g., "123", "7849")
     * @param playerWriter PrintWriter to send real-time updates
     * @param config Configuration map with optional settings
     * @param onComplete Callback closure when game completes (receives session and result)
     * @return Map with success flag and initial display message
     */
    def startSlotMachine(String sessionId, String playerUsername, String targetCode, 
                        PrintWriter playerWriter, Map config = [:], 
                        Closure onComplete = null, Closure onDigitLocked = null, Closure onUpdate = null) {
        
        // Stop any existing session for this player
        stopSlotMachine(sessionId)
        
        // Create new session
        def session = new SlotMachineSession(
            sessionId: sessionId,
            playerUsername: playerUsername,
            targetCode: targetCode,
            playerWriter: playerWriter,
            config: config,
            onComplete: onComplete,
            onDigitLocked: onDigitLocked,
            onUpdate: onUpdate
        )
        
        // Initialize cycling values
        session.initializeCyclingValues()
        
        activeSessions[sessionId] = session
        
        // Start the real-time cycling
        startRealTimeCycling(session)
        
        return [
            success: true,
            session: session,
            message: buildInitialDisplay(session)
        ]
    }
    
    /**
     * Handle space bar press to lock current digit
     */
    def handleSpaceBarPress(String sessionId) {
        def session = activeSessions[sessionId]
        if (!session || !session.isActive || session.gameCompleted) {
            return [success: false, message: "No active slot machine session"]
        }
        
        // Lock the current cycling digit
        if (session.currentSlotIndex < session.currentCyclingValues.size()) {
            def lockedValue = session.currentCyclingValues[session.currentSlotIndex]
            session.lockedDigits.add(lockedValue)
            session.currentSlotIndex++
            
            // Stop current cycling task
            if (session.cyclingTask) {
                session.cyclingTask.cancel(false)
            }
            
            // Call digit locked callback
            if (session.onDigitLocked) {
                session.onDigitLocked.call(session, lockedValue)
            }
            
            // Check if game is complete
            if (session.isComplete()) {
                session.gameCompleted = true
                return completeSlotMachineGame(session)
            } else {
                // Start cycling the next digit
                startRealTimeCycling(session)
                return [
                    success: true,
                    message: "Digit ${lockedValue} locked! Next slot cycling...",
                    continueGame: true,
                    session: session
                ]
            }
        }
        
        return [success: false, message: "Unable to lock digit"]
    }
    
    /**
     * Stop a slot machine session
     */
    def stopSlotMachine(String sessionId) {
        def session = activeSessions[sessionId]
        if (session) {
            session.isActive = false
            session.gameCompleted = true
            
            // Stop any cycling task
            if (session.cyclingTask) {
                session.cyclingTask.cancel(false)
            }
            
            activeSessions.remove(sessionId)
        }
    }
    
    /**
     * Check if a session is active
     */
    def isSessionActive(String sessionId) {
        def session = activeSessions[sessionId]
        return session && session.isActive && !session.gameCompleted
    }
    
    /**
     * Get session information
     */
    def getSession(String sessionId) {
        return activeSessions[sessionId]
    }
    
    /**
     * Get all active sessions (for debugging/admin)
     */
    def getAllActiveSessions() {
        return activeSessions.values().collect { it.getSessionInfo() }
    }
    
    // Private methods for internal slot machine mechanics
    
    private void startRealTimeCycling(SlotMachineSession session) {
        if (session.currentSlotIndex >= session.targetCode.length()) {
            return
        }
        
        def cyclingSpeed = session.getCurrentCyclingSpeed()
        
        session.cyclingTask = scheduler.scheduleAtFixedRate({
            if (!session.isActive || session.gameCompleted) {
                return
            }
            
            // Cycle the current slot's digit 0-9
            if (session.currentSlotIndex < session.currentCyclingValues.size()) {
                session.currentCyclingValues[session.currentSlotIndex] = 
                    (session.currentCyclingValues[session.currentSlotIndex] + 1) % 10
                
                // Send real-time update
                sendRealTimeUpdate(session)
            }
            
        }, 0, cyclingSpeed, TimeUnit.MILLISECONDS)
    }
    
    private void sendRealTimeUpdate(SlotMachineSession session) {
        if (session.playerWriter && !session.gameCompleted) {
            try {
                // Call custom update callback if provided
                if (session.onUpdate) {
                    session.onUpdate.call(session)
                } else {
                    // Default real-time update
                    session.playerWriter.print("\r" + buildLiveDisplay(session))
                    session.playerWriter.flush()
                }
            } catch (Exception e) {
                // Writer might be closed, stop the session
                stopSlotMachine(session.sessionId)
            }
        }
    }
    
    private Map completeSlotMachineGame(SlotMachineSession session) {
        // Stop any cycling
        if (session.cyclingTask) {
            session.cyclingTask.cancel(false)
        }
        
        def result = [
            success: session.isCorrect(),
            targetCode: session.targetCode,
            playerResult: session.lockedDigits.join(""),
            isCorrect: session.isCorrect(),
            session: session
        ]
        
        // Call completion callback if provided
        if (session.onComplete) {
            def callbackResult = session.onComplete.call(session, result)
            if (callbackResult instanceof Map) {
                result.putAll(callbackResult)
            }
        }
        
        // Clean up session
        stopSlotMachine(session.sessionId)
        
        return result
    }
    
    private String buildInitialDisplay(SlotMachineSession session) {
        def title = session.config.title ?: "SLOT MACHINE MINI-GAME"
        def codeDisplay = session.targetCode.split('').join(' ')
        
        return """
╔════════════════════════════════════════╗
║      🎰 ${title.toUpperCase().padRight(26)} 🎰  ║
╠════════════════════════════════════════╣
║                                        ║
║  TARGET CODE: ${codeDisplay.padRight(27)} ║
║                                        ║
║  🎰 SLOT MACHINE STARTING...           ║
║     Press SPACE BAR to stop digits!    ║
║                                        ║
║  Type 'exit' to quit mini-game         ║
╚════════════════════════════════════════╝

🎮 SLOT MACHINE ACTIVE - Watch the numbers cycle!
Target: ${codeDisplay}  |  Current: ${session.getDisplayString().split('').join(' ')}  |  SPACE = Lock Digit
""".trim()
    }
    
    private String buildLiveDisplay(SlotMachineSession session) {
        def codeDisplay = session.targetCode.split('').join(' ')
        def currentDisplay = session.getDisplayString().split('').join(' ')
        def slotStatus = "Slot ${session.currentSlotIndex + 1}/${session.targetCode.length()}"
        
        return "Target: ${codeDisplay}  |  Current: ${currentDisplay}  |  ${slotStatus}  |  SPACE = Lock"
    }
    
    /**
     * Helper method to generate random codes of specified length
     */
    def generateRandomCode(Integer length) {
        def random = new Random()
        def code = ""
        for (int i = 0; i < length; i++) {
            code += random.nextInt(10).toString()
        }
        return code
    }
    
    /**
     * Admin/debug methods
     */
    def forceStopAllSessions() {
        activeSessions.keySet().each { sessionId ->
            stopSlotMachine(sessionId)
        }
        return "All slot machine sessions stopped"
    }
    
    def getActiveSessionCount() {
        return activeSessions.size()
    }
}