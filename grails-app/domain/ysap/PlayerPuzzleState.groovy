package ysap

class PlayerPuzzleState {
    String playerId             // LambdaPlayer.id as string
    String gameSessionId        // Which game session this belongs to
    Integer mapNumber           // Which map (1-10) this state is for
    String elementType          // AIR, FIRE, EARTH, WATER
    
    // Player-specific puzzle coordinates (different from global puzzle elements)
    Integer variableCoordinateX    // Where this player should find the variable
    Integer variableCoordinateY
    Integer puzzleRoomCoordinateX  // Where this player should find the puzzle room
    Integer puzzleRoomCoordinateY
    
    // Player-specific calculated coordinates (from executing puzzle fragments)
    String calculatedCoordinates   // Result of their puzzle fragment execution
    Boolean hasCalculatedCoords = false
    Date coordinatesCalculatedAt
    
    // Tracking state
    Boolean hasCollectedVariable = false
    Boolean hasObtainedSymbol = false
    Date symbolObtainedAt
    Date lastCoordinateShift     // When coordinates were last randomized
    Integer shiftCount = 0       // How many times coordinates have been shifted
    
    static constraints = {
        playerId blank: false, maxSize: 50
        gameSessionId blank: false, maxSize: 50
        mapNumber min: 1, max: 10
        elementType inList: ['AIR', 'FIRE', 'EARTH', 'WATER']
        variableCoordinateX min: 0, max: 9
        variableCoordinateY min: 0, max: 9
        puzzleRoomCoordinateX min: 0, max: 9
        puzzleRoomCoordinateY min: 0, max: 9
        calculatedCoordinates nullable: true, maxSize: 100
        coordinatesCalculatedAt nullable: true
        symbolObtainedAt nullable: true
        lastCoordinateShift nullable: true
        shiftCount min: 0
    }
    
    static mapping = {
        // Composite indexes for efficient queries
        playerId index: 'player_puzzle_state_idx'
        gameSessionId index: 'player_puzzle_state_idx'
        mapNumber index: 'player_puzzle_state_idx'
        elementType index: 'player_puzzle_state_idx'
    }
    
    String toString() {
        return "PuzzleState[${playerId}:${elementType}:Map${mapNumber}] - ${hasObtainedSymbol ? 'COMPLETED' : 'ACTIVE'}"
    }
    
    // Helper methods
    Boolean needsCoordinateShift() {
        return !hasObtainedSymbol && (lastCoordinateShift == null || shiftCount == 0)
    }
    
    void recordSymbolObtained() {
        hasObtainedSymbol = true
        symbolObtainedAt = new Date()
    }
    
    void recordCoordinateShift() {
        lastCoordinateShift = new Date()
        shiftCount++
    }
    
    void recordVariableCollection() {
        hasCollectedVariable = true
    }
    
    void recordCoordinateCalculation(String coordinates) {
        calculatedCoordinates = coordinates
        hasCalculatedCoords = true
        coordinatesCalculatedAt = new Date()
    }
    
    String getShiftReason() {
        if (shiftCount == 0) return "Initial puzzle generation"
        return "Coordinate shift #${shiftCount} - another player obtained ${elementType} symbol"
    }
    
    String getCompetitionStatus() {
        if (hasObtainedSymbol) {
            return "✅ ${elementType} symbol obtained!"
        } else if (hasCalculatedCoords) {
            return "🎯 Coordinates calculated, seeking puzzle room..."
        } else if (hasCollectedVariable) {
            return "📦 Variable collected, ready for calculation..."
        } else {
            return "🔍 Searching for puzzle variable..."
        }
    }
}