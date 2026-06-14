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
    // Per-player COMMON-item purchase history: JSON {"<playerId>":["Data Types",...]}. A common item a
    // player has bought is hidden from THAT player here but stays buyable for others. UNIQUE items are
    // instead removed from `inventory` globally on purchase, so they never need recording here. Left null
    // on existing rows (read as '{}'); naturally resets when a merchant respawns with a fresh row.
    String playerPurchases

    static constraints = {
        merchantName blank: false, size: 3..50
        matrixLevel min: 1, max: 10
        positionX min: 0, max: 9
        positionY min: 0, max: 9
        merchantType inList: ['FRAGMENT_TRADER', 'SPECIAL_ITEMS', 'BIT_EXCHANGE']
        inventory maxSize: 2000
        playerPurchases nullable: true, maxSize: 4000
    }

    static mapping = {
        inventory type: 'text'
        playerPurchases type: 'text'
    }
    
    String toString() {
        return "${merchantName} (${merchantType}) at Level ${matrixLevel}"
    }
}