package ysap

class PlayerSkill {
    String skillName
    Integer level = 1
    Integer experience = 0
    Boolean isPoweredUp = false
    Date lastUsed
    Date acquiredDate = new Date()
    
    LambdaPlayer player
    
    static belongsTo = [player: LambdaPlayer]
    
    static constraints = {
        skillName inList: [
            'SCANNING',
            'STEALTH', 
            'PROCESSING',
            'LOCK_PICKING',
            'CODE_ANALYSIS',
            'SYSTEM_NAVIGATION',
            'ALLIANCE_MANAGEMENT',
            'LOGIC_SYNTHESIS'
        ]
        level min: 1, max: 20
        experience min: 0
        lastUsed nullable: true
    }
    
    String toString() {
        return "${skillName} Level ${level}"
    }
    
    Integer getNextLevelExperience() {
        return level * 100
    }
    
    Boolean canLevelUp() {
        return experience >= getNextLevelExperience()
    }
    
    void levelUp() {
        if (canLevelUp()) {
            experience -= getNextLevelExperience()
            level++
        }
    }
}