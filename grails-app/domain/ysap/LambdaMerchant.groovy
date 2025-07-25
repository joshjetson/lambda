package ysap

class LambdaMerchant {
    String merchantName
    Integer matrixLevel
    Integer positionX
    Integer positionY
    String merchantType = "FRAGMENT_TRADER"
    Boolean isActive = true
    Date spawnedDate = new Date()
    String inventory // JSON string of available items
    
    static constraints = {
        merchantName blank: false, size: 3..50
        matrixLevel min: 1, max: 10
        positionX min: 0, max: 9
        positionY min: 0, max: 9
        merchantType inList: ['FRAGMENT_TRADER', 'SPECIAL_ITEMS', 'BIT_EXCHANGE']
        inventory maxSize: 2000
    }
    
    static mapping = {
        inventory type: 'text'
    }
    
    String toString() {
        return "${merchantName} (${merchantType}) at Level ${matrixLevel}"
    }
}