package ysap

class CoordinateState {
    Integer matrixLevel
    Integer coordinateX
    Integer coordinateY
    Integer health = 100          // 100 = fully operational, 0 = completely wiped
    Boolean isAccessible = true   // false when health reaches 0
    Date lastDamaged
    Date lastRepaired
    Date createdDate = new Date()
    
    // Degradation tracking
    Integer degradationRate = 1   // Health lost per degradation cycle
    Date nextDegradation = new Date()
    
    static constraints = {
        matrixLevel min: 1, max: 10
        coordinateX min: 0, max: 9
        coordinateY min: 0, max: 9
        health min: 0, max: 100
        degradationRate min: 1, max: 10
        lastDamaged nullable: true
        lastRepaired nullable: true
        nextDegradation nullable: true
    }
    
    static mapping = {
        // Composite index for fast coordinate lookups
        coordinateX index: 'coordinate_idx'
        coordinateY index: 'coordinate_idx'
        matrixLevel index: 'coordinate_idx'
    }
    
    String toString() {
        return "Coordinate (${coordinateX},${coordinateY}) Level ${matrixLevel} - Health: ${health}%"
    }
    
    // Helper methods
    Boolean isHealthy() {
        return health > 75
    }
    
    Boolean isDamaged() {
        return health <= 75 && health > 25
    }
    
    Boolean isCritical() {
        return health <= 25 && health > 0
    }
    
    Boolean isWiped() {
        return health <= 0
    }
    
    String getHealthStatus() {
        if (isHealthy()) return "OPERATIONAL"
        if (isDamaged()) return "DAMAGED"
        if (isCritical()) return "CRITICAL"
        return "WIPED"
    }
    
    String getHealthColor() {
        if (isHealthy()) return "green"
        if (isDamaged()) return "yellow"
        if (isCritical()) return "red"
        return "white"
    }
}