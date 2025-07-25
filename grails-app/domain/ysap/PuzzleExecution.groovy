package ysap

class PuzzleExecution {
    String executionId
    Date executionDate = new Date()
    String executedBy          // Username
    String fragmentName        // Which puzzle fragment was executed
    String inputVariable       // Variable value used as input
    String outputResult        // Result of the execution
    Boolean wasSuccessful = false
    String errorMessage        // If execution failed
    Integer matrixLevel        // Where execution happened
    Integer positionX
    Integer positionY
    
    // Relationships
    LambdaPlayer player
    PuzzleLogicFragment fragment
    
    static belongsTo = [fragment: PuzzleLogicFragment]
    
    static constraints = {
        executionId blank: false, maxSize: 50, unique: true
        executedBy blank: false, maxSize: 50
        fragmentName blank: false, maxSize: 100
        inputVariable nullable: true, maxSize: 200
        outputResult nullable: true, maxSize: 500
        errorMessage nullable: true, maxSize: 300
        matrixLevel min: 1, max: 10
        positionX min: 0, max: 9
        positionY min: 0, max: 9
        player nullable: true
    }
    
    static mapping = {
        outputResult type: 'text'
        errorMessage type: 'text'
        executionDate index: 'execution_date_idx'
    }
    
    String toString() {
        return "${fragmentName} executed by ${executedBy} - ${wasSuccessful ? 'SUCCESS' : 'FAILED'}"
    }
    
    // Helper methods
    String getExecutionSummary() {
        def formatter = new java.text.SimpleDateFormat('HH:mm:ss')
        def status = wasSuccessful ? "✅ SUCCESS" : "❌ FAILED"
        return "[${formatter.format(executionDate)}] ${fragmentName}: ${status}"
    }
    
    String getDetailedResult() {
        def result = new StringBuilder()
        result.append("Execution: ${fragmentName}\n")
        result.append("Input: ${inputVariable ?: 'none'}\n")
        result.append("Output: ${outputResult ?: 'none'}\n")
        result.append("Status: ${wasSuccessful ? 'SUCCESS' : 'FAILED'}\n")
        if (errorMessage) {
            result.append("Error: ${errorMessage}\n")
        }
        return result.toString()
    }
    
    Boolean isRecentExecution() {
        def timeDiff = new Date().time - executionDate.time
        return timeDiff < (5 * 60 * 1000) // 5 minutes
    }
    
    String getLocationInfo() {
        return "(${positionX},${positionY}) Level ${matrixLevel}"
    }
}