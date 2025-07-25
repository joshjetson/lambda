package ysap

class SpecialItem {
    String name
    String description
    String itemType
    Integer usesRemaining = 1
    Integer maxUses = 1
    Integer duration = 0  // Duration in seconds for temporary effects
    Boolean isActive = false
    Boolean isPermanent = false
    String rarity = 'COMMON'
    Date obtainedDate = new Date()
    Date expiresAt
    Date lastUsed
    
    LambdaPlayer owner
    
    static belongsTo = [owner: LambdaPlayer]
    
    static constraints = {
        name blank: false, size: 3..50
        description maxSize: 500
        itemType inList: [
            'RESPAWN_CACHE',
            'SWAP_SPACE',
            'BIT_MULTIPLIER',
            'SCANNER_BOOST',
            'STEALTH_CLOAK',
            'DEFRAG_DETECTOR',
            'LOGIC_AMPLIFIER',
            'MATRIX_MAPPER',
            'ENTROPY_STABILIZER',
            'FRAGMENT_MAGNET',
            'MATRIX_CLIPPER',
            'INSTANT_REPAIR_KIT'
        ]
        usesRemaining min: 0
        maxUses min: 1
        duration min: 0
        rarity inList: ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY']
        expiresAt nullable: true
        lastUsed nullable: true
    }
    
    static mapping = {
        description type: 'text'
    }
    
    String toString() {
        return "${name} (${itemType})"
    }
    
    Boolean isTimeBased() {
        return duration > 0
    }
    
    Boolean canTrade() {
        return !isActive && usesRemaining > 0 && ['RESPAWN_CACHE', 'SWAP_SPACE', 'BIT_MULTIPLIER', 'SCANNER_BOOST', 'DEFRAG_DETECTOR'].contains(itemType)
    }
    
    Boolean isExpired() {
        return expiresAt != null && expiresAt < new Date()
    }
    
    Boolean isUsable() {
        return usesRemaining > 0 && !isExpired()
    }
}