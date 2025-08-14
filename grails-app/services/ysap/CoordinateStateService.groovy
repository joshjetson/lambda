package ysap

import grails.gorm.transactions.Transactional

@Transactional
class CoordinateStateService {
    def audioService
    def lambdaPlayerService
    def defragBotService
    def telnetServerService

    def getOrCreateCoordinateState(Integer matrixLevel, Integer x, Integer y) {
        def coordinate = CoordinateState.findByMatrixLevelAndCoordinateXAndCoordinateY(matrixLevel, x, y)
        if (!coordinate) {
            coordinate = new CoordinateState(
                matrixLevel: matrixLevel,
                coordinateX: x,
                coordinateY: y,
                health: 100,
                isAccessible: true,
                degradationRate: calculateDegradationRate(matrixLevel, x, y),
                nextDegradation: calculateNextDegradation()
            )
            coordinate.save(failOnError: true)
        }
        return coordinate
    }
    
    def isCoordinateAccessible(Integer matrixLevel, Integer x, Integer y) {
        def coordinate = getOrCreateCoordinateState(matrixLevel, x, y)
        return coordinate.isAccessible && coordinate.health > 0
    }
    
    def canPlayerMoveToCoordinate(LambdaPlayer player, Integer targetX, Integer targetY) {
        // Check if coordinate is accessible
        if (!isCoordinateAccessible(player.currentMatrixLevel, targetX, targetY)) {
            return [allowed: false, reason: "Coordinate (${targetX},${targetY}) has been wiped by defrag processes"]
        }
        
        // With coordinate change system, players can move to any accessible coordinate
        // This makes the game more challenging since everything is randomized
        return [allowed: true, reason: ""]
    }
    
    def damageCoordinate(Integer matrixLevel, Integer x, Integer y, Integer damage = 10) {
        CoordinateState.withTransaction {
            def coordinate = getOrCreateCoordinateState(matrixLevel, x, y)
            coordinate.health = Math.max(0, coordinate.health - damage)
            coordinate.lastDamaged = new Date()
            
            if (coordinate.health <= 0) {
                coordinate.isAccessible = false
            }
            
            coordinate.save(failOnError: true)
            return coordinate
        }
    }
    
    def repairCoordinate(Integer matrixLevel, Integer x, Integer y, Integer repairAmount = 100) {
        CoordinateState.withTransaction {
            def coordinate = getOrCreateCoordinateState(matrixLevel, x, y)
            coordinate.health = Math.min(100, coordinate.health + repairAmount)
            coordinate.lastRepaired = new Date()
            
            if (coordinate.health > 0) {
                coordinate.isAccessible = true
            }
            
            coordinate.save(failOnError: true)
            return coordinate
        }
    }
    
    def processDefragDegradation() {
        def now = new Date()
        def coordinatesToDegrade = CoordinateState.findAllByNextDegradationLessThanAndHealthGreaterThan(now, 0)
        
        coordinatesToDegrade.each { coordinate ->
            // Random chance of degradation (30% per cycle)
            if (Math.random() < 0.3) {
                coordinate.health = Math.max(0, coordinate.health - coordinate.degradationRate)
                coordinate.lastDamaged = now
                
                if (coordinate.health <= 0) {
                    coordinate.isAccessible = false
                }
                
                // Schedule next degradation
                coordinate.nextDegradation = calculateNextDegradation()
                coordinate.save(failOnError: true)
            }
        }
        
        return coordinatesToDegrade.size()
    }
    
    def getCoordinateHealth(Integer matrixLevel, Integer x, Integer y) {
        def coordinate = getOrCreateCoordinateState(matrixLevel, x, y)
        return [
            health: coordinate.health,
            status: coordinate.getHealthStatus(),
            color: coordinate.getHealthColor(),
            accessible: coordinate.isAccessible,
            lastDamaged: coordinate.lastDamaged,
            nextDegradation: coordinate.nextDegradation
        ]
    }
    
    def getMatrixLevelHealth(Integer matrixLevel) {
        def coordinates = CoordinateState.findAllByMatrixLevel(matrixLevel)
        def healthMap = [:]
        
        for (x in 0..9) {
            for (y in 0..9) {
                def coord = coordinates.find { it.coordinateX == x && it.coordinateY == y }
                if (coord) {
                    healthMap["${x},${y}"] = [
                        health: coord.health,
                        status: coord.getHealthStatus(),
                        accessible: coord.isAccessible
                    ]
                } else {
                    healthMap["${x},${y}"] = [
                        health: 100,
                        status: "OPERATIONAL", 
                        accessible: true
                    ]
                }
            }
        }
        
        return healthMap
    }
    
    
    private Integer calculateDegradationRate(Integer matrixLevel, Integer x, Integer y) {
        // Higher matrix levels degrade faster
        // Coordinates closer to center degrade slower (safe zones)
        def baseRate = 1 + (matrixLevel / 5).intValue()
        def distanceFromOrigin = Math.sqrt(x * x + y * y)
        
        if (distanceFromOrigin <= 2) {
            return Math.max(1, baseRate - 1) // Safe zone protection
        }
        
        return baseRate
    }
    
    private Date calculateNextDegradation() {
        // Random degradation timing: 30 minutes to 2 hours
        def randomMinutes = 30 + (Math.random() * 90).intValue()
        return new Date(System.currentTimeMillis() + (randomMinutes * 60 * 1000))
    }
    
    // Repair system for adjacent players
    def canPlayerRepairCoordinate(LambdaPlayer player, Integer targetX, Integer targetY) {
        // Check if target coordinate is wiped
        def targetHealth = getCoordinateHealth(player.currentMatrixLevel, targetX, targetY)
        if (targetHealth.health > 0) {
            return [canRepair: false, reason: "Coordinate (${targetX},${targetY}) is not wiped (${targetHealth.health}% health)"]
        }
        
        // Check if player is adjacent to the target coordinate
        def playerX = player.positionX
        def playerY = player.positionY
        
        def isAdjacent = isAdjacentCoordinate(playerX, playerY, targetX, targetY)
        if (!isAdjacent) {
            return [canRepair: false, reason: "You must be adjacent to coordinate (${targetX},${targetY}) to repair it. You are at (${playerX},${playerY})"]
        }
        
        // Check if player's current coordinate is accessible
        def playerHealth = getCoordinateHealth(player.currentMatrixLevel, playerX, playerY)
        if (!playerHealth.accessible) {
            return [canRepair: false, reason: "Cannot repair from a wiped coordinate. You must be on a functional coordinate."]
        }
        
        return [canRepair: true, reason: "Ready to repair coordinate (${targetX},${targetY})"]
    }
    
    def repairCoordinateByPlayer(LambdaPlayer player, Integer targetX, Integer targetY) {
        def result = [success: false, message: '']
        
        CoordinateState.withTransaction {
            // Validate repair conditions
            def canRepair = canPlayerRepairCoordinate(player, targetX, targetY)
            if (!canRepair.canRepair) {
                result.message = canRepair.reason
                return result
            }
            
            // Perform the repair
            def coordinate = repairCoordinate(player.currentMatrixLevel, targetX, targetY, 100)
            
            result.success = true
            result.message = "✅ Coordinate (${targetX},${targetY}) successfully repaired!"
            result.coordinateHealth = coordinate.health
            result.coordinateStatus = coordinate.getHealthStatus()
            
            println "Player ${player.username} repaired coordinate (${targetX},${targetY}) on Matrix Level ${player.currentMatrixLevel}"
        }
        
        return result
    }
    
    def getAdjacentCoordinates(Integer centerX, Integer centerY) {
        def adjacent = []
        
        // Check all 8 adjacent coordinates (including diagonals)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue // Skip center coordinate
                
                def adjX = centerX + dx
                def adjY = centerY + dy
                
                // Ensure coordinates are within bounds
                if (adjX >= 0 && adjX <= 9 && adjY >= 0 && adjY <= 9) {
                    adjacent.add([x: adjX, y: adjY])
                }
            }
        }
        
        return adjacent
    }
    
    def getRepairableCoordinatesForPlayer(LambdaPlayer player) {
        def repairable = []
        def adjacent = getAdjacentCoordinates(player.positionX, player.positionY)
        
        adjacent.each { coord ->
            def health = getCoordinateHealth(player.currentMatrixLevel, coord.x, coord.y)
            if (health.health <= 0 && !health.accessible) {
                repairable.add([
                    x: coord.x,
                    y: coord.y,
                    health: health.health,
                    status: health.status,
                    lastDamaged: health.lastDamaged
                ])
            }
        }
        
        return repairable
    }
    
    private Boolean isAdjacentCoordinate(Integer x1, Integer y1, Integer x2, Integer y2) {
        def dx = Math.abs(x1 - x2)
        def dy = Math.abs(y1 - y2)
        
        // Adjacent includes all 8 surrounding coordinates (including diagonals)
        return dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0)
    }
    private void updatePlayerPositionOnBoard(LambdaPlayer player) {
        // TODO: Implement GPIO LED updates for physical board
        // This will light up the LED corresponding to the player's position
        println "Updating board position for ${player.displayName} at Matrix Level ${player.currentMatrixLevel} (${player.positionX},${player.positionY})"
    }
    String handleCoordinateChange(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command.trim().split(' ')

        // Validate command format: cc (x,y) or cc x,y or cc x y
        if (parts.length < 2) {
            return "Usage: cc (x,y) - Change coordinates to specific position"
        }

        def coordinateString = parts[1..-1].join(' ').trim()
        def newX, newY

        // Parse different coordinate formats
        if (coordinateString.startsWith('(') && coordinateString.endsWith(')')) {
            // Format: cc (2,6)
            coordinateString = coordinateString.substring(1, coordinateString.length() - 1)
        }

        if (coordinateString.contains(',')) {
            // Format: cc 2,6 or cc (2,6)
            def coords = coordinateString.split(',')
            if (coords.length != 2) {
                return "Invalid coordinate format. Use: cc (x,y) or cc x,y"
            }
            try {
                newX = Integer.parseInt(coords[0].trim())
                newY = Integer.parseInt(coords[1].trim())
            } catch (NumberFormatException e) {
                return "Invalid coordinates. Use numbers only: cc (x,y)"
            }
        } else if (parts.length >= 3) {
            // Format: cc 2 6
            try {
                newX = Integer.parseInt(parts[1].trim())
                newY = Integer.parseInt(parts[2].trim())
            } catch (NumberFormatException e) {
                return "Invalid coordinates. Use numbers only: cc x y"
            }
        } else {
            return "Invalid coordinate format. Use: cc (x,y), cc x,y, or cc x y"
        }

        // Constrain coordinates to matrix bounds (0-9)
        if (newX < 0 || newX > 9 || newY < 0 || newY > 9) {
            return "Coordinates must be within matrix bounds (0-9). Requested: (${newX},${newY})"
        }

        // Check if coordinate change is allowed (accessibility)
        def movementCheck = this.canPlayerMoveToCoordinate(player, newX, newY)
        if (!movementCheck.allowed) {
            return TerminalFormatter.formatText("Coordinate change blocked: ${movementCheck.reason}", 'bold', 'red')
        }

        // Calculate movement direction for audio feedback
        def deltaX = newX - player.positionX
        def deltaY = newY - player.positionY

        // Play appropriate movement sound based on primary direction
        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            if (deltaX > 0) {
                audioService.playSound("move_east")
            } else {
                audioService.playSound("move_west")
            }
        } else {
            if (deltaY > 0) {
                audioService.playSound("move_north")
            } else {
                audioService.playSound("move_south")
            }
        }

        lambdaPlayerService.movePlayer(player, player.currentMatrixLevel, newX, newY)

        // Update the player object in the session with new coordinates
        LambdaPlayer.withTransaction {
            def updatedPlayer = LambdaPlayer.get(player.id)
            if (updatedPlayer) {
                telnetServerService.playerSessions[writer] = updatedPlayer
                player.positionX = updatedPlayer.positionX
                player.positionY = updatedPlayer.positionY
            }
        }

        updatePlayerPositionOnBoard(player)

        // Check for defrag bot encounter with floor-based difficulty scaling
        def floorNumber = newX // Floor 0 = coordinates 0,0-0,9, Floor 1 = 1,0-1,9, etc.

        // Floor-based encounter rates: Early floors are much safer
        def baseEncounterChance
        switch (floorNumber) {
            case 0:
            case 1:
                baseEncounterChance = 0.02 // 2% chance on floors 0-1 (very safe)
                break
            case 2:
                baseEncounterChance = 0.05 // 5% chance on floor 2 (safe)
                break
            case 3:
            case 4:
                baseEncounterChance = 0.10 // 10% chance on floors 3-4 (normal)
                break
            case 5:
            case 6:
                baseEncounterChance = 0.15 // 15% chance on floors 5-6 (challenging)
                break
            default:
                baseEncounterChance = 0.20 // 20% chance on floors 7+ (dangerous)
                break
        }

        // Safe zone: No defrag bots in starting area
        def inSafeZone = (newX <= 1 && newY <= 1)

        // Apply recursion stealth bonus (if active) and roll for encounter
        def totalAvoidanceBonus = (player.stealthBonus ?: 0.0) // Only active recursion bonuses count
        def encounterChance = baseEncounterChance * (1.0 - Math.min(0.8, totalAvoidanceBonus))

        if (!inSafeZone && Math.random() < encounterChance) {
            def defragBot = defragBotService.spawnDefragBot(player.currentMatrixLevel, 1, newX, newY)
            if (defragBot) {
                telnetServerService.activeDefragSessions[writer] = defragBot

                def encounter = new StringBuilder()
                encounter.append(TerminalFormatter.formatText("Lambda entity changed coordinates to (${newX},${newY})", 'bold', 'green')).append('\r\n')
                encounter.append(TerminalFormatter.formatText("⚠️  DEFRAG BOT ENCOUNTERED!", 'bold', 'red')).append('\r\n')
                encounter.append(TerminalFormatter.formatText("System defragmentation process ${defragBot.botId} detected", 'italic', 'yellow')).append('\r\n')
                encounter.append(TerminalFormatter.formatText("Time limit: ${defragBot.timeLimit} seconds", 'bold', 'red')).append('\r\n')
                encounter.append("Type 'defrag -h' to analyze the defrag process or face system buffer clearing!\r\n")

                return encounter.toString()
            }
        }

        // Logic gate encounters on floors 7+ at coordinates >= (7,1)
        if (newX >= 7 && newY >= 1) {
            def gateCheck = Math.random()
            if (gateCheck < 0.15) { // 15% chance of logic gate
                return TerminalFormatter.formatText("Lambda entity moved to (${newX},${newY})\r\n⚡ LOGIC GATE DETECTED - Implementation pending", 'bold', 'cyan')
            }
        }

        def moveMessage = "Lambda entity changed coordinates to (${newX},${newY})\r\n"
        return TerminalFormatter.formatText(moveMessage, 'bold', 'green')
    }
}