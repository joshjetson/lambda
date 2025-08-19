package ysap

import grails.gorm.transactions.Transactional
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Transactional
class AutoDefragService {
    
    def coordinateStateService
    def chatService
    
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)
    private volatile boolean isRunning = false
    private volatile int destructionCycle = 0
    
    def startAutoDefragSystem() {
        if (isRunning) {
            println "Auto-defrag system already running"
            return
        }
        
        isRunning = true
        destructionCycle = 0
        
        println "🤖 Starting auto-defrag system with 1-minute destruction cycles"
        
        // Schedule the defrag cycle
        scheduler.scheduleAtFixedRate({
            try {
                executeDefragCycle()
            } catch (Exception e) {
                println "Error in auto-defrag cycle: ${e.message}"
                e.printStackTrace()
            }
        }, 30, 60, TimeUnit.SECONDS) // Start after 30 seconds, then every 60 seconds
        
        println "✅ Auto-defrag system started successfully"
    }
    
    def stopAutoDefragSystem() {
        if (!isRunning) {
            return
        }
        
        isRunning = false
        scheduler.shutdown()
        println "🛑 Auto-defrag system stopped"
    }
    
    private void executeDefragCycle() {
        if (!isRunning) return
        
        destructionCycle++
        println "🤖 Auto-defrag cycle ${destructionCycle} executing..."
        
        // Destroy 1-3 random coordinates per cycle
        def coordinatesDestroyed = destroyRandomCoordinates()
        
        // Broadcast destruction to all players
        if (coordinatesDestroyed > 0) {
            def message = "🤖 AUTO-DEFRAG: ${coordinatesDestroyed} coordinate(s) destroyed by system defrag bots!"
            broadcastDefragAlert(message)
        }
        
        // After every 3 cycles, take a 1-minute break
        if (destructionCycle % 3 == 0) {
            println "🤖 Auto-defrag taking 1-minute break after 3 cycles..."
            
            // Schedule break end
            scheduler.schedule({
                println "🤖 Auto-defrag break ended, resuming destruction cycles"
                broadcastDefragAlert("⚠️  AUTO-DEFRAG: System defrag bots resume coordinate destruction!")
            }, 60, TimeUnit.SECONDS)
        }
    }
    
    private int destroyRandomCoordinates() {
        def destroyed = 0
        def random = new Random()
        
        // Destroy 1-3 coordinates per cycle
        def coordinatesToDestroy = 1 + random.nextInt(3)
        
        coordinatesToDestroy.times {
            def matrixLevel = 1 + random.nextInt(3) // Focus on levels 1-3
            def x = random.nextInt(10)
            def y = random.nextInt(10)
            
            // Skip safe zones (0,0), (0,1), (1,0), (1,1)
            if ((x <= 1 && y <= 1)) {
                return // Skip this iteration
            }
            
            // Wrap database operations in transaction for background thread
            CoordinateState.withTransaction {
                try {
                    // Check if coordinate is already wiped
                    def currentHealth = coordinateStateService.getCoordinateHealth(matrixLevel, x, y)
                    if (currentHealth.health <= 0) {
                        return // Skip already wiped coordinates
                    }
                    
                    // Destroy the coordinate (massive damage)
                    coordinateStateService.damageCoordinate(matrixLevel, x, y, 100)
                    destroyed++
                    
                    println "🤖 Auto-defrag destroyed coordinate (${x},${y}) on Matrix Level ${matrixLevel}"
                    
                    // Check if any players are at this coordinate and force them to move
                    evictPlayersFromCoordinate(matrixLevel, x, y)
                } catch (Exception e) {
                    println "Error destroying coordinate (${x},${y}): ${e.message}"
                }
            }
        }
        
        return destroyed
    }
    
    private void evictPlayersFromCoordinate(Integer matrixLevel, Integer x, Integer y) {
        // Wrap database operations in transaction for background thread
        LambdaPlayer.withTransaction {
            try {
                // Find any players at this coordinate
                def playersAtCoordinate = LambdaPlayer.findAllByCurrentMatrixLevelAndPositionXAndPositionY(matrixLevel, x, y)
                
                playersAtCoordinate.each { player ->
                    // Move player to nearest safe coordinate
                    def safeCoordinate = findNearestSafeCoordinate(matrixLevel, x, y)
                    
                    player.positionX = safeCoordinate.x
                    player.positionY = safeCoordinate.y
                    player.save(failOnError: true)
                    
                    println "🤖 Evicted player ${player.username} from destroyed coordinate (${x},${y}) to safe coordinate (${safeCoordinate.x},${safeCoordinate.y})"
                }
            } catch (Exception e) {
                println "Error evicting players from coordinate (${x},${y}): ${e.message}"
            }
        }
    }
    
    private Map findNearestSafeCoordinate(Integer matrixLevel, Integer startX, Integer startY) {
        // Find nearest coordinate that isn't wiped
        def searchRadius = 1
        def safeCoordinate = null
        
        // Wrap database operations in transaction for background thread
        CoordinateState.withTransaction {
            try {
                while (searchRadius <= 5 && !safeCoordinate) {
                    for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                        for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                            def checkX = Math.max(0, Math.min(9, startX + dx))
                            def checkY = Math.max(0, Math.min(9, startY + dy))
                            
                            def health = coordinateStateService.getCoordinateHealth(matrixLevel, checkX, checkY)
                            if (health.health > 0 && health.accessible) {
                                safeCoordinate = [x: checkX, y: checkY]
                                return safeCoordinate // Exit early when found
                            }
                        }
                    }
                    searchRadius++
                }
            } catch (Exception e) {
                println "Error finding safe coordinate: ${e.message}"
            }
        }
        
        // Fallback to origin if no safe coordinates found
        return safeCoordinate ?: [x: 0, y: 0]
    }
    
    private void broadcastDefragAlert(String message) {
        try {
            // Broadcast to mingle chamber if available
            chatService?.broadcastSystemMessage(message)
        } catch (Exception e) {
            println "Could not broadcast defrag alert: ${e.message}"
        }
    }
    
    def getAutoDefragStatus() {
        return [
            isRunning: isRunning,
            currentCycle: destructionCycle,
            nextBreak: destructionCycle % 3 == 0 ? "Now" : "${3 - (destructionCycle % 3)} cycles",
            systemInfo: "1-minute destruction cycles with 1-minute breaks every 3 cycles"
        ]
    }
    
    def forceDefragCycle() {
        if (!isRunning) {
            return "Auto-defrag system is not running. Start it first."
        }
        
        executeDefragCycle()
        return "Manual defrag cycle executed."
    }
    
    /**
     * Shows the current status of the auto-defrag system
     * @return Formatted status display with system information and proper telnet line endings
     */
    String showAutoDefragStatus() {
        def status = getAutoDefragStatus()
        def display = new StringBuilder()
        
        display.append(TerminalFormatter.formatText("=== AUTO-DEFRAG SYSTEM STATUS ===", 'bold', 'cyan')).append('\r\n\r\n')
        
        display.append("System Status: ")
        if (status.isRunning) {
            display.append(TerminalFormatter.formatText("ACTIVE", 'bold', 'red')).append('\r\n')
        } else {
            display.append(TerminalFormatter.formatText("INACTIVE", 'bold', 'green')).append('\r\n')
        }
        
        if (status.isRunning) {
            display.append("Current Cycle: ${status.currentCycle}\r\n")
            display.append("Next Break: ${status.nextBreak}\r\n")
            display.append("Pattern: ${status.systemInfo}\r\n\r\n")
            
            display.append(TerminalFormatter.formatText("⚠️  DANGER:", 'bold', 'yellow'))
                .append(" System defrag bots are actively destroying coordinates!\r\n")
            display.append("Use 'repair scan' to check for damaged adjacent coordinates.\r\n")
            display.append("Use 'repair <x> <y>' to restore wiped coordinates.\r\n\r\n")
            
            display.append(TerminalFormatter.formatText("🔧 REPAIR TIPS:", 'bold', 'cyan')).append('\r\n')
            display.append("• Wiped coordinates may contain valuable resources\r\n")
            display.append("• You can only repair coordinates adjacent to your position\r\n")
            display.append("• Stay on functional coordinates to perform repairs\r\n")
        } else {
            display.append("\r\n${TerminalFormatter.formatText('System defrag bots are currently inactive.', 'bold', 'green')}\r\n")
        }
        
        return display.toString()
    }
}