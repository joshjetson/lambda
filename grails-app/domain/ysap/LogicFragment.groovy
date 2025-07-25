package ysap

class LogicFragment {
    String name
    String description
    String fragmentType
    Integer powerLevel = 1
    String pythonCapability
    Integer quantity = 1
    Boolean isActive = true
    Date discoveredDate = new Date()
    
    LambdaPlayer owner
    
    static belongsTo = [owner: LambdaPlayer]
    
    static constraints = {
        name blank: false, size: 3..50
        description maxSize: 500
        fragmentType inList: [
            'RECURSION',
            'CONDITIONAL', 
            'DATA_TYPE',
            'LOOP',
            'EXCEPTION_HANDLING',
            'IMPORT',
            'FUNCTION',
            'CLASS',
            'ASYNC',
            'REGEX'
        ]
        powerLevel min: 1, max: 10
        quantity min: 1
        pythonCapability maxSize: 1000
    }
    
    static mapping = {
        description type: 'text'
        pythonCapability type: 'text'
    }
    
    String toString() {
        return "${name} (${fragmentType})"
    }
}