package ysap

class PuzzleRoom {
    String roomName
    String description
    Integer matrixLevel
    Integer mapNumber           // Which map (1-10) this room belongs to
    String gameSessionId        // Which game session this belongs to
    Integer positionX
    Integer positionY
    String elementType          // Which elemental symbol this room unlocks
    String executableFile       // The Python/Groovy file content
    String fileName            // Name of the executable file
    String requiredFlag        // Command flag needed for execution
    String requiredNonce       // Nonce value needed for execution
    Boolean isActive = true
    Boolean isExecutable = false // Whether file has been chmod +x'd by player
    String successMessage      // Message shown when puzzle is solved
    String failureMessage     // Message shown when execution fails
    Date createdDate = new Date()
    
    // Execution tracking
    Integer attemptCount = 0
    Date lastAttempt
    String lastAttemptBy
    
    static constraints = {
        roomName blank: false, maxSize: 100
        description blank: false, maxSize: 500
        matrixLevel min: 1, max: 10
        mapNumber min: 1, max: 10
        gameSessionId blank: false, maxSize: 50
        positionX min: 0, max: 9
        positionY min: 0, max: 9
        elementType inList: ['AIR', 'FIRE', 'EARTH', 'WATER']
        executableFile blank: false, maxSize: 3000
        fileName blank: false, maxSize: 50
        requiredFlag blank: false, maxSize: 50
        requiredNonce blank: false, maxSize: 100
        successMessage nullable: true, maxSize: 400
        failureMessage nullable: true, maxSize: 400
        lastAttempt nullable: true
        lastAttemptBy nullable: true, maxSize: 50
    }
    
    static mapping = {
        executableFile type: 'text'
        description type: 'text'
        successMessage type: 'text'
        failureMessage type: 'text'
        // Composite index for coordinate lookups
        positionX index: 'puzzle_room_coordinate_idx'
        positionY index: 'puzzle_room_coordinate_idx'
        matrixLevel index: 'puzzle_room_coordinate_idx'
        // Session/map index
        gameSessionId index: 'puzzle_room_session_idx'
        mapNumber index: 'puzzle_room_session_idx'
    }
    
    String toString() {
        return "${roomName} (${elementType}) at (${positionX},${positionY})"
    }
    
    // Helper methods
    Boolean isAtCoordinate(Integer level, Integer x, Integer y) {
        return matrixLevel == level && positionX == x && positionY == y
    }
    
    String getElementIcon() {
        switch(elementType) {
            case 'AIR': return '🜁'
            case 'FIRE': return '🜂'
            case 'EARTH': return '🜃'
            case 'WATER': return '🜄'
            default: return '❓'
        }
    }
    
    String getScanDescription() {
        return "${getElementIcon()} Puzzle chamber detected - File: ${fileName}"
    }
    
    String getExecutionHint() {
        return "Execute with: execute ${requiredFlag} <nonce> ${fileName}"
    }
    
    Boolean validateExecution(String flag, String nonce) {
        return requiredFlag.equals(flag) && requiredNonce.equals(nonce)
    }
    
    String getAttemptInfo() {
        if (attemptCount > 0) {
            def formatter = new java.text.SimpleDateFormat('HH:mm')
            return "Previous attempts: ${attemptCount} (Last: ${formatter.format(lastAttempt)})"
        }
        return "No previous attempts"
    }
    
    def recordAttempt(String username) {
        attemptCount++
        lastAttempt = new Date()
        lastAttemptBy = username
    }
    
    String getSuccessResponse() {
        return successMessage ?: "✨ Elemental symbol unlocked: ${getElementIcon()} ${elementType}"
    }
    
    String getFailureResponse() {
        return failureMessage ?: "❌ Execution failed. Incorrect flag or nonce."
    }
    
    // File permission methods
    Boolean makeExecutable() {
        this.isExecutable = true
        return true
    }
    
    String getPermissionString() {
        return isExecutable ? "-rwxr-xr-x" : "-rw-r--r--"
    }
    
    String getFileListEntry() {
        def permissions = getPermissionString()
        def size = executableFile?.length() ?: 0
        def dateFormatter = new java.text.SimpleDateFormat('MMM dd HH:mm')
        def dateStr = dateFormatter.format(createdDate)
        return "${permissions}  1 lambda lambda ${size.toString().padLeft(8)} ${dateStr} ${fileName}"
    }
}