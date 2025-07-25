package ysap

class HiddenVariable {
    String variableName     // Name of the variable (matches requiredVariable in PuzzleLogicFragment)
    String variableValue    // The actual value (could be number, string, coordinates, etc.)
    String variableType     // COORDINATE, NUMERIC, STRING, ENCODED, CHEMICAL_FORMULA
    String description      // What this variable represents
    Integer matrixLevel
    String elementType          // Which elemental symbol this room unlocks
    Integer mapNumber       // Which map (1-10) this variable belongs to
    String gameSessionId    // Which game session this belongs to
    Integer positionX
    Integer positionY
    Boolean isCollected = false
    Date collectedDate
    String collectedBy      // Username who collected it
    String discoveryHint    // Clue about what this variable is for
    
    static constraints = {
        variableName blank: false, maxSize: 50
        variableValue blank: false, maxSize: 200
        variableType inList: ['COORDINATE', 'NUMERIC', 'STRING', 'ENCODED', 'CHEMICAL_FORMULA']
        elementType inList: ['AIR', 'FIRE', 'EARTH', 'WATER']
        description blank: false, maxSize: 400
        matrixLevel min: 1, max: 10
        mapNumber min: 1, max: 10
        gameSessionId blank: false, maxSize: 50
        positionX min: 0, max: 9
        positionY min: 0, max: 9
        collectedDate nullable: true
        collectedBy nullable: true, maxSize: 50
        discoveryHint nullable: true, maxSize: 300
    }
    
    static mapping = {
        description type: 'text'
        discoveryHint type: 'text'
        // Composite index for coordinate lookups
        positionX index: 'variable_coordinate_idx'
        positionY index: 'variable_coordinate_idx'
        matrixLevel index: 'variable_coordinate_idx'
        // Session/map index
        gameSessionId index: 'variable_session_idx'
        mapNumber index: 'variable_session_idx'
    }
    
    String toString() {
        return "${variableName} = ${variableValue} (${variableType})"
    }
    
    // Helper methods
    Boolean isAtCoordinate(Integer level, Integer x, Integer y) {
        return matrixLevel == level && positionX == x && positionY == y
    }
    
    String getTypeIcon() {
        switch(variableType) {
            case 'COORDINATE': return '📍'
            case 'NUMERIC': return '🔢'
            case 'STRING': return '📝'
            case 'ENCODED': return '🔐'
            case 'CHEMICAL_FORMULA': return '🧪'
            default: return '❓'
        }
    }
    
    String getDisplayValue() {
        switch(variableType) {
            case 'ENCODED':
                return "***ENCODED*** (use decoder fragment)"
            case 'COORDINATE':
                return "Location: ${variableValue}"
            default:
                return variableValue
        }
    }
    
    String getScanDescription() {
        return "${getTypeIcon()} Hidden variable detected: ${variableName}"
    }
    
    String getCollectionMessage() {
        return "Variable acquired: ${variableName} = ${getDisplayValue()}"
    }
    
    String getUsageHint() {
        if (discoveryHint) {
            return "💡 ${discoveryHint}"
        }
        return "Try using this variable with puzzle logic fragments"
    }
}