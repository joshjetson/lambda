package ysap

class PuzzleLogicFragment {
    String name
    String description
    String fragmentType  // CALCULATOR, DECODER, LOCATOR, VALIDATOR
    Integer powerLevel
    String functionCode  // Groovy code that can be executed
    String requiredVariable  // Variable name this function needs
    String expectedOutput   // Pattern of expected output
    Boolean isExecutable = true
    Date createdDate = new Date()
    
    // Relationships
    LambdaPlayer owner
    
    static hasMany = [
        executionHistory: PuzzleExecution
    ]
    
    static constraints = {
        name blank: false, maxSize: 100, unique: true
        description blank: false, maxSize: 500
        fragmentType inList: ['CALCULATOR', 'DECODER', 'LOCATOR', 'VALIDATOR']
        powerLevel min: 1, max: 10
        functionCode blank: false, maxSize: 2000
        requiredVariable nullable: true, maxSize: 50
        expectedOutput nullable: true, maxSize: 200
        owner nullable: true
    }
    
    static mapping = {
        functionCode type: 'text'
        description type: 'text'
        expectedOutput type: 'text'
    }
    
    String toString() {
        return "${name} (${fragmentType} Level ${powerLevel})"
    }
    
    // Helper methods
    Boolean requiresVariable() {
        return requiredVariable != null && !requiredVariable.trim().isEmpty()
    }
    
    String getExecutionHint() {
        if (requiresVariable()) {
            return "This function requires variable: ${requiredVariable}"
        }
        return "This function can be executed without parameters"
    }
    
    String getFunctionSignature() {
        if (requiresVariable()) {
            return "execute(${requiredVariable})"
        }
        return "execute()"
    }
}