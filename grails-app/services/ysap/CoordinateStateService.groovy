package ysap

import grails.gorm.transactions.Transactional

@Transactional
class CoordinateStateService {

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
}