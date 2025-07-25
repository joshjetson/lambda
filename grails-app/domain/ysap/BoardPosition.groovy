package ysap

class BoardPosition {
    Integer matrixLevel
    Integer sector
    Integer positionX
    Integer positionY
    String ringTheme
    String description
    Boolean hasLogicFragment = false
    Boolean requiresCooperation = false
    Integer difficultyLevel = 1
    
    Date lastUpdated = new Date()
    
    static constraints = {
        matrixLevel min: 1, max: 10
        sector min: 1, max: 12
        positionX min: 0
        positionY min: 0
        ringTheme blank: false, maxSize: 100
        description maxSize: 500
        difficultyLevel min: 1, max: 10
    }
    
    static mapping = {
        description type: 'text'
    }
    
    String toString() {
        return "Matrix Level ${matrixLevel}, Sector ${sector} (${positionX},${positionY})"
    }
    
    String getFullDescription() {
        return "${ringTheme}: ${description}"
    }
}