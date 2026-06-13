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
    def lambdaPlayerService

    static final int REPAIR_REWARD_BITS = 30
    
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
        // Concurrent: the DigitCycler scheduler thread writes this every ~300ms while the telnet/render
        // thread reads it (getCurrentDisplay / repairPanelLines) and removes a key on lock. Plain HashMap
        // here is a real data race.
        Map<Integer, Integer> cyclingValues = new ConcurrentHashMap<>()
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
                playerWriter: playerWriter
        )
        session.cyclingValues[0] = new Random().nextInt(10)   // seed slot 0 (field is a ConcurrentHashMap)

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


    // Package-visible (not private) so integration tests can drive winner/loser branches deterministically.
    Map completeRepair(RepairSession session) {
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
            // Atomically claim the coordinate — only the FIRST entity to finish wins it.
            def won = coordinateStateService.tryClaimRepair(session.matrixLevel, session.targetX, session.targetY)

            if (!won) {
                // Someone else repaired it first: kick this entity out with no prize (the HEAD-commit TODO).
                result.success = false
                result.message = new BoxBuilder(40)
                        .addCenteredLine("REPAIR PRE-EMPTED")
                        .addSeparator()
                        .addLine("  Another entity completed the")
                        .addLine("  repair of (${x},${y}) first.")
                        .addEmptyLine()
                        .addLine("  No prize awarded.")
                        .build()
                result.gameWon = false
                stopRepairSession(session.playerUsername)
                return result
            }

            // Winner: grant the bit reward through the single chokepoint (honors BIT_MULTIPLIER).
            def reward = 0
            def player = LambdaPlayer.findByUsername(session.playerUsername)
            if (player) {
                reward = lambdaPlayerService.addBits(player, REPAIR_REWARD_BITS)
            }

            def box = new BoxBuilder(40)
                    .addCenteredLine("REPAIR SUCCESSFUL!")
                    .addSeparator()
                    .addLine("  Target Code: ${lockCode}")
                    .addLine("  Your Result: ${keyCode}")
                    .addEmptyLine()
                    .addLine("  ✅ (${x},${y}) is now accessible!")
                    .addLine("  💰 Reward: +${reward} bits")
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





    /**
     * Fixed, plain-ASCII panel lines for the HUD repair display, built from LIVE session state so the
     * spinning digit (read fresh each render) animates IN PLACE — no scrolling history. The YOURS row
     * comes straight from getCurrentDisplay() (the single source: locked + spinning + dashes); we only
     * add a caret marking the active slot. ASCII only — the HUD screen buffer is one glyph per cell, so
     * emoji/ANSI would desync the grid. Returns null when there's no active session.
     */
    List<String> repairPanelLines(String playerUsername) {
        def s = activeSessions[playerUsername]
        if (!s || !s.isActive) return null
        int n = s.repairCode.length()
        def cells = s.getCurrentDisplay().split(' ')   // e.g. "2 8 -" → ["2","8","-"]
        def target = new StringBuilder("  CODE  ")
        def yours  = new StringBuilder("  YOU   ")
        def caret  = new StringBuilder("        ")
        for (int i = 0; i < n; i++) {
            target.append(String.format("%3s", s.repairCode.charAt(i) as String))
            yours.append(String.format("%3s", i < cells.length ? cells[i] : "-"))
            caret.append(i == s.currentSlot ? "  ^" : "   ")
        }
        int slot = Math.min(s.currentSlot + 1, n)
        return [
            "",
            "  >> SECTOR REPAIR <<",
            "  Coordinate (${s.targetX},${s.targetY})".toString(),
            "  ----------------------",
            target.toString(),
            yours.toString(),
            caret.toString() + " spinning",
            "",
            "  Slot ${slot} of ${n} - match the code".toString(),
            "",
            "  [ENTER] lock the spinning digit",
            "  [exit]  abort repair"
        ]
    }

    def isPlayerInRepairSession(String playerUsername) {
        def session = activeSessions[playerUsername]
        return session && session.isActive
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
