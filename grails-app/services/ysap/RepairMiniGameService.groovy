package ysap

import grails.gorm.transactions.Transactional

@Transactional
class RepairMiniGameService {
    
    def coordinateStateService
    def gameSessionService
    def slotMachineService
    
    // Store repair-specific context while slot machine handles the game mechanics
    private static Map<String, RepairContext> repairContexts = [:]
    
    static class RepairContext {
        String playerUsername
        Integer matrixLevel
        Integer targetX
        Integer targetY
        String sessionId
        Boolean isHighValue
        String valueDescription
    }
    
    def initiateRepair(LambdaPlayer player, Integer targetX, Integer targetY, PrintWriter playerWriter) {
        def result = [success: false, message: '']
        
        // Validate repair conditions first
        def canRepair = coordinateStateService.canPlayerRepairCoordinate(player, targetX, targetY)
        if (!canRepair.canRepair) {
            result.message = canRepair.reason
            return result
        }
        
        // Stop any existing repair session for this player
        stopRepairSession(player.username)
        
        // Assess coordinate value to determine repair code complexity
        def coordinateValue = assessCoordinateValue(player.currentMatrixLevel, targetX, targetY)
        def repairCodeLength = coordinateValue.isHighValue ? 4 : 3
        def repairCode = slotMachineService.generateRandomCode(repairCodeLength)
        
        // Create session ID for this repair
        def sessionId = "repair_${player.username}_${System.currentTimeMillis()}"
        
        // Store repair context
        def repairContext = new RepairContext(
            playerUsername: player.username,
            matrixLevel: player.currentMatrixLevel,
            targetX: targetX,
            targetY: targetY,
            sessionId: sessionId,
            isHighValue: coordinateValue.isHighValue,
            valueDescription: coordinateValue.description
        )
        repairContexts[sessionId] = repairContext
        
        // Configure slot machine for repair mini-game
        def config = [
            title: "COORDINATE REPAIR",
            cyclingSpeeds: [300, 200, 150, 100], // Progressive speeds for repair
            emptySlotChar: "-"
        ]
        
        // Start slot machine with repair-specific callbacks
        def slotResult = slotMachineService.startSlotMachine(
            sessionId,
            player.username,
            repairCode,
            playerWriter,
            config,
            { session, gameResult -> onRepairComplete(session, gameResult) }, // onComplete
            { session, lockedDigit -> onRepairDigitLocked(session, lockedDigit) }, // onDigitLocked
            { session -> onRepairUpdate(session) } // onUpdate
        )
        
        if (slotResult.success) {
            result.success = true
            result.message = buildRepairInitialDisplay(repairContext, slotResult.session)
            result.isMinigame = true
        } else {
            result.message = "Failed to start repair mini-game: ${slotResult.message}"
        }
        
        return result
    }
    
    private Map assessCoordinateValue(Integer matrixLevel, Integer x, Integer y) {
        def isHighValue = false
        def valueDescription = ""
        
        // Check for logic fragments
        def fragment = gameSessionService.getFragmentAtCoordinates(matrixLevel, x, y)
        if (fragment) {
            if (fragment.powerLevel >= 7 || fragment.fragmentType in ['CLASS', 'FUNCTION']) {
                isHighValue = true
                valueDescription = "High-value logic fragment detected"
            } else {
                valueDescription = "Standard logic fragment detected"
            }
        }
        
        // Check for elemental symbols (placeholder - would check actual elemental symbol locations)
        // This would integrate with ElementalSymbolService when available
        if (x >= 7 && y >= 7 && matrixLevel >= 3) {
            isHighValue = true
            valueDescription = "Potential elemental symbol proximity"
        }
        
        // Check for special coordinate patterns
        if ((x + y) % 10 == 0 && matrixLevel >= 5) {
            isHighValue = true
            valueDescription = "Critical system coordinate"
        }
        
        if (!valueDescription) {
            valueDescription = "Standard coordinate"
        }
        
        return [
            isHighValue: isHighValue,
            description: valueDescription,
            codeLength: isHighValue ? 4 : 3
        ]
    }
    
    private String generateRandomRepairCode(Integer length) {
        def random = new Random()
        def code = ""
        for (int i = 0; i < length; i++) {
            code += random.nextInt(10).toString()
        }
        return code
    }
    
    // Callback methods for slot machine integration
    
    private Map onRepairComplete(slotSession, gameResult) {
        def repairContext = repairContexts[slotSession.sessionId]
        if (!repairContext) {
            return [message: "Repair context not found"]
        }
        
        if (gameResult.isCorrect) {
            // Successful repair - actually repair the coordinate
            CoordinateState.withTransaction {
                coordinateStateService.repairCoordinate(
                    repairContext.matrixLevel, 
                    repairContext.targetX, 
                    repairContext.targetY, 
                    100
                )
            }
            
            def message = """
╔════════════════════════════════════════╗
║           REPAIR SUCCESSFUL!           ║
╠════════════════════════════════════════╣
║  Target Code: ${gameResult.targetCode.split('').join(' ')}                        ║
║  Your Result: ${gameResult.playerResult.split('').join(' ')}                        ║
║                                        ║
║  ✅ Coordinate (${repairContext.targetX},${repairContext.targetY}) is now accessible!  ║
║     Repair protocols completed.        ║
╚════════════════════════════════════════╝
""".trim()
            
            // Clean up repair context
            repairContexts.remove(slotSession.sessionId)
            
            return [
                success: true,
                message: message,
                isMinigame: false,
                continueGame: false
            ]
            
        } else {
            // Failed repair
            def message = """
╔════════════════════════════════════════╗
║            REPAIR FAILED!              ║
╠════════════════════════════════════════╣
║  Target Code: ${gameResult.targetCode.split('').join(' ')}                        ║
║  Your Result: ${gameResult.playerResult.split('').join(' ')}                        ║
║                                        ║
║  ❌ Sequence mismatch detected!        ║
║     Type 'repair ${repairContext.targetX} ${repairContext.targetY}' to try again.    ║
╚════════════════════════════════════════╝
""".trim()
            
            // Clean up repair context
            repairContexts.remove(slotSession.sessionId)
            
            return [
                success: false,
                message: message,
                isMinigame: false,
                continueGame: false
            ]
        }
    }
    
    private void onRepairDigitLocked(slotSession, lockedDigit) {
        // Could add repair-specific feedback here
        // For now, just let the slot machine handle it
    }
    
    private void onRepairUpdate(slotSession) {
        // Custom real-time display for repair mini-game
        def repairContext = repairContexts[slotSession.sessionId]
        if (repairContext && slotSession.playerWriter) {
            def codeDisplay = slotSession.targetCode.split('').join(' ')
            def currentDisplay = slotSession.getDisplayString().split('').join(' ')
            def slotStatus = "Slot ${slotSession.currentSlotIndex + 1}/${slotSession.targetCode.length()}"
            
            def updateText = "🔧 REPAIR: ${codeDisplay} | Current: ${currentDisplay} | ${slotStatus} | SPACE = Lock"
            slotSession.playerWriter.print("\r" + updateText)
            slotSession.playerWriter.flush()
        }
    }
    
    def handleSpaceBarPress(String playerUsername) {
        // Find the session ID for this player
        def sessionId = repairContexts.find { k, v -> v.playerUsername == playerUsername }?.key
        if (!sessionId) {
            return [success: false, message: "No active repair session"]
        }
        
        // Delegate to slot machine service
        return slotMachineService.handleSpaceBarPress(sessionId)
    }
    
    def isPlayerInRepairSession(String playerUsername) {
        // Check if player has an active repair session
        def sessionId = repairContexts.find { k, v -> v.playerUsername == playerUsername }?.key
        return sessionId && slotMachineService.isSessionActive(sessionId)
    }
    
    def stopRepairSession(String playerUsername) {
        // Find and stop the player's repair session
        def sessionId = repairContexts.find { k, v -> v.playerUsername == playerUsername }?.key
        if (sessionId) {
            slotMachineService.stopSlotMachine(sessionId)
            repairContexts.remove(sessionId)
        }
    }
    
    private String buildRepairInitialDisplay(RepairContext repairContext, slotSession) {
        def codeDisplay = slotSession.targetCode.split('').join(' ')
        
        return """
╔════════════════════════════════════════╗
║      🔧 COORDINATE REPAIR MINI-GAME 🔧  ║
╠════════════════════════════════════════╣
║  Target: (${repairContext.targetX},${repairContext.targetY}) Matrix Level ${repairContext.matrixLevel}       ║
║  ${repairContext.valueDescription.padRight(38)} ║
║                                        ║
║  REPAIR CODE: ${codeDisplay.padRight(29)} ║
║                                        ║
║  🎰 SLOT MACHINE STARTING...           ║
║     Press SPACE BAR to stop digits!    ║
║                                        ║
║  Type 'exit' to quit mini-game         ║
╚════════════════════════════════════════╝

🎮 SLOT MACHINE ACTIVE - Watch the numbers cycle!
Target: ${codeDisplay}  |  Current: ${slotSession.getDisplayString().split('').join(' ')}  |  SPACE = Lock Digit
""".trim()
    }
    
    // Admin/debug methods
    def forceStopAllSessions() {
        repairContexts.keySet().each { sessionId ->
            slotMachineService.stopSlotMachine(sessionId)
        }
        repairContexts.clear()
        return "All repair sessions stopped"
    }
    
    def getActiveSessionCount() {
        return repairContexts.size()
    }
}