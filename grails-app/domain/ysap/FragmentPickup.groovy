package ysap

class FragmentPickup {
    
    String playerUsername
    Integer matrixLevel
    Integer positionX
    Integer positionY
    String fragmentName
    Date pickedUpAt = new Date()
    
    static constraints = {
        playerUsername blank: false
        matrixLevel min: 1, max: 10
        positionX min: 0, max: 9
        positionY min: 0, max: 9
        fragmentName blank: false
        pickedUpAt nullable: false
    }
    
    static mapping = {
        table 'fragment_pickups'
        version false
    }
    
    // Composite key to ensure one pickup per player per coordinate
    static mapping_unique = [playerUsername: 'string', matrixLevel: 'integer', positionX: 'integer', positionY: 'integer']
}