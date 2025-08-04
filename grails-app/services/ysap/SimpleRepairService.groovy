package ysap

import grails.gorm.transactions.Transactional
import ysap.helpers.BoxBuilder
import ysap.helpers.DigitCycler

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Transactional
class SimpleRepairService {
    
    def coordinateStateService
    def gameSessionService
    
    // Active repair sessions by player username
    private static Map<String, RepairSession> activeSessions = new ConcurrentHashMap<>()
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3)

    static class RepairSession {
        String playerUsername
        Integer matrixLevel
        Integer targetX
        Integer targetY
        String repairCode
        List<Integer> lockedDigits = []
        Integer currentSlot = 0
        Map<Integer, Integer> cyclingValues = [:]  // NEW
        Boolean isActive = true
        PrintWriter playerWriter
        ScheduledFuture<?> cyclingTask

        Boolean isComplete() {
            return lockedDigits.size() >= repairCode.length()
        }

        Boolean isCorrect() {
            return isComplete() && lockedDigits.join("") == repairCode
        }

        String getCurrentDisplay() {
            def display = []
            for (int i = 0; i < repairCode.length(); i++) {
                if (i < lockedDigits.size()) {
                    display.add(lockedDigits[i].toString())
                } else if (cyclingValues.containsKey(i)) {
                    display.add(cyclingValues[i].toString())
                } else {
                    display.add("-")
                }
            }
            return display.join(" ")
        }

        Integer getCyclingSpeed() {
            switch(currentSlot) {
                case 0: return 300
                case 1: return 300
                case 2: return 300
                case 3: return 300
                default: return 300
            }
        }
    }


    def initiateRepair(LambdaPlayer player, Integer targetX, Integer targetY, PrintWriter playerWriter) {
        def result = [success: false, message: '']
        
        // Validate repair conditions
        def canRepair = coordinateStateService.canPlayerRepairCoordinate(player, targetX, targetY)
        if (!canRepair.canRepair) {
            result.message = canRepair.reason
            return result
        }

        // Assess coordinate value
        def coordinateValue = assessCoordinateValue(player.currentMatrixLevel, targetX, targetY)
        def codeLength = coordinateValue.isHighValue ? 4 : 3
        def repairCode = generateRandomCode(codeLength)
        
        // Create session
        def session = new RepairSession(
                playerUsername: player.username,
                matrixLevel: player.currentMatrixLevel,
                targetX: targetX,
                targetY: targetY,
                repairCode: repairCode,
                playerWriter: playerWriter,
                cyclingValues: [(0): new Random().nextInt(10)]
        )
        
        activeSessions[player.username] = session
        
        // Start cycling first slot
        startSlotCycling(session)
        
        result.success = true
        result.message = buildInitialDisplay(session, coordinateValue)
        
        return result
    }
    def handleSpaceBarPress(String playerUsername) {
        def session = activeSessions[playerUsername]
        if (!session || !session.isActive) {
            return [success: false, message: "No active repair session", continueGame: false]
        }

        // Cancel active cycling task
        if (session.cyclingTask) {
            session.cyclingTask.cancel(false)
        }

        def valueToLock = session.cyclingValues[session.currentSlot]
        session.lockedDigits.add(valueToLock)
        session.cyclingValues.remove(session.currentSlot)
        session.currentSlot++

        println "DEBUG: Locked value ${valueToLock}, now at slot ${session.currentSlot}"

        // If complete, resolve repair
        if (session.isComplete()) {
            return completeRepair(session)
        } else {
            startSlotCycling(session)
            return [
                    success: true,
                    message: "Digit ${valueToLock} locked! Slot ${session.currentSlot + 1}/${session.repairCode.length()} cycling...",
                    continueGame: true
            ]
        }
    }

    private void startSlotCycling(RepairSession session) {
        if (session.currentSlot >= session.repairCode.length()) {
            return
        }

        def cycler = new DigitCycler(session.currentSlot, session, scheduler)
        cycler.start()
    }


    private Map completeRepair(RepairSession session) {
        // Stop cycling
        if (session.cyclingTask) {
            session.cyclingTask.cancel(false)
        }

        def result = [success: session.isCorrect(), continueGame: false]
        def lockCode = session.repairCode.split('').join(' ')
        def keyCode = session.lockedDigits.join(' ')
        def x = session.targetX
        def y = session.targetY

        if (session.isCorrect()) {
            // Successful repair
            CoordinateState.withTransaction {
                coordinateStateService.repairCoordinate(session.matrixLevel, session.targetX, session.targetY, 100)
            }

            // Using BoxBuilder
            def box = new BoxBuilder(40)
                    .addCenteredLine("REPAIR SUCCESSFUL!")
                    .addSeparator()
                    .addLine("  Target Code: ${lockCode}")
                    .addLine("  Your Result: ${keyCode}")
                    .addEmptyLine()
                    .addLine("  ✅ (${x},${y}) is now accessible!")
                    .addLine("     Repair protocols completed.")
                    .build()

            result.message = box
            result.gameWon = true

        } else {
            // Failed repair
            def box = new BoxBuilder(40)
                    .addCenteredLine("REPAIR FAILED!")
                    .addSeparator()
                    .addLine("  Target Code: ${lockCode}")
                    .addLine("  Your Result: ${keyCode}")
                    .addEmptyLine()
                    .addLine("  ❌ Sequence mismatch detected!")
                    .addLine(" Type 'repair ${x} ${y}' to try again.")
                    .build()

            result.message = box
            result.gameWon = false
        }

        // Clean up
        stopRepairSession(session.playerUsername)

        return result
    }





    def isPlayerInRepairSession(String playerUsername) {
        def session = activeSessions[playerUsername]
        def result = session && session.isActive
        println "DEBUG: isPlayerInRepairSession(${playerUsername}) -> session=${session}, isActive=${session?.isActive}, result=${result}"
        return result
    }
    
    def stopRepairSession(String playerUsername) {
        def session = activeSessions[playerUsername]
        if (session) {
            session.isActive = false
            if (session.cyclingTask) {
                session.cyclingTask.cancel(false)
            }
            activeSessions.remove(playerUsername)
        }
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
        
        // Check for special coordinates
        if (x >= 7 && y >= 7 && matrixLevel >= 3) {
            isHighValue = true
            valueDescription = "Critical system coordinate"
        }
        
        if (!valueDescription) {
            valueDescription = "Standard coordinate"
        }
        
        return [
            isHighValue: isHighValue,
            description: valueDescription
        ]
    }
    
    private String generateRandomCode(Integer length) {
        def random = new Random()
        def code = ""
        for (int i = 0; i < length; i++) {
            code += random.nextInt(10).toString()
        }
        return code
    }

    private String buildInitialDisplay(RepairSession session, Map coordinateValue) {
        def codeDisplay = session.repairCode.split('').join(' ')

        def box = new BoxBuilder(40)
                .addCenteredLine("🔧 COORDINATE REPAIR MINI-GAME 🔧")
                .addSeparator()
                .addLine("  Target: (${session.targetX},${session.targetY}) Matrix Level ${session.matrixLevel}")
                .addLine("  ${coordinateValue.description}")
                .addEmptyLine()
                .addLine("  REPAIR CODE: ${codeDisplay}")
                .addEmptyLine()
                .addLine("  🎰 SLOT MACHINE STARTING...")
                .addLine("     Press ENTER KEY to stop digits!")
                .addEmptyLine()
                .addLine("  Type 'exit' to quit mini-game")
                .build()

        return box + "\r\n\r\n" +
                "🎮 SLOT MACHINE ACTIVE - Watch the numbers cycle!\r\n" +
                "Target: ${codeDisplay}  |  Current: ${session.getCurrentDisplay()}  |  ENTER = Lock Digit\r\n"
    }

    // Admin methods
    def forceStopAllSessions() {
        activeSessions.values().each { session ->
            if (session.cyclingTask) {
                session.cyclingTask.cancel(false)
            }
        }
        activeSessions.clear()
        return "All repair sessions stopped"
    }
    
    def getActiveSessionCount() {
        return activeSessions.size()
    }

}
