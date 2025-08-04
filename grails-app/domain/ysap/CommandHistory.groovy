package ysap

class CommandHistory {
    String command
    Date executedAt
    
    static belongsTo = [player: LambdaPlayer]
    
    static constraints = {
        command maxSize: 500, blank: false
        executedAt nullable: false
    }
    
    static mapping = {
        command type: 'text'
        executedAt column: 'executed_at'
    }
    
    String toString() {
        return command
    }
}