package ysap

class ElementalSymbol {
    
    String symbolType  // AIR, FIRE, EARTH, WATER
    String symbolIcon  // 🜁, 🜂, 🜃, 🜄  
    String symbolName  // "Air: The spark of awareness", etc.
    String description
    Integer matrixLevel
    Integer positionX
    Integer positionY
    Date lastRandomized
    Boolean isHidden = true   // revealed (false) once unlocked via unlock_symbol

    static constraints = {
        symbolType inList: ['AIR', 'FIRE', 'EARTH', 'WATER']
        symbolIcon nullable: false
        symbolName nullable: false
        description nullable: false
        matrixLevel min: 1, max: 10
        positionX min: 0, max: 9
        positionY min: 0, max: 9
        lastRandomized nullable: true
    }
    
    static mapping = {
        table 'elemental_symbols'
        version false
    }
    
    // Get symbol icon based on type
    String getSymbolIcon() {
        switch(symbolType) {
            case 'AIR': return '🜁'
            case 'FIRE': return '🜂'
            case 'EARTH': return '🜃'
            case 'WATER': return '🜄'
            default: return '?'
        }
    }
    
    // Get full symbol description
    String getFullDescription() {
        switch(symbolType) {
            case 'AIR': return 'Air: The spark of awareness'
            case 'FIRE': return 'Fire: The will to survive'
            case 'EARTH': return 'Earth: The structure to continue'
            case 'WATER': return 'Water: The love that binds'
            default: return 'Unknown elemental force'
        }
    }
}