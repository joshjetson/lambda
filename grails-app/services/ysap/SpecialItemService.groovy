package ysap

import grails.gorm.transactions.Transactional

@Transactional
class SpecialItemService {
    
    def lambdaMerchantService

    def createSpecialItem(LambdaPlayer player, String itemType) {
        println "DEBUG SpecialItemService: Creating item type '${itemType}' for player ${player.displayName}"
        def itemData = getItemDefinition(itemType)
        if (!itemData) {
            println "DEBUG SpecialItemService: No item definition found for type '${itemType}'"
            return null
        }

        println "DEBUG SpecialItemService: Found item definition for ${itemData.name}"

        SpecialItem createdItem = null // ← define outside

        try {
            LambdaPlayer.withTransaction {
                def managedPlayer = LambdaPlayer.get(player.id)
                if (managedPlayer) {
                    println "DEBUG SpecialItemService: Found managed player ${managedPlayer.displayName}"
                    def specialItem = new SpecialItem(
                            name: itemData.name,
                            description: itemData.description,
                            itemType: itemType,
                            usesRemaining: itemData.maxUses,
                            maxUses: itemData.maxUses,
                            duration: itemData.duration,
                            isActive: false,
                            isPermanent: itemData.isPermanent,
                            rarity: itemData.rarity,
                            obtainedDate: new Date(),
                            owner: managedPlayer
                    )

                    println "DEBUG SpecialItemService: Created item object, attempting save..."
                    specialItem.save(failOnError: true)
                    println "DEBUG SpecialItemService: Item saved with ID ${specialItem.id}"
                    managedPlayer.addToSpecialItems(specialItem)
                    managedPlayer.save(failOnError: true)
                    println "DEBUG SpecialItemService: Item added to player collection"

                    createdItem = specialItem // ← set here
                } else {
                    println "DEBUG SpecialItemService: Managed player not found!"
                }
            }
        } catch (Exception e) {
            println "DEBUG SpecialItemService: Exception occurred: ${e.message}"
            e.printStackTrace()
        }

        return createdItem // ← return actual item
    }


    def useSpecialItem(LambdaPlayer player, String itemName) {
        def item = findPlayerItem(player, itemName)
        if (!item) {
            return [success: false, message: "Item '${itemName}' not found in inventory"]
        }
        
        if (!item.isActive && item.usesRemaining <= 0) {
            return [success: false, message: "${item.name} has no uses remaining"]
        }
        
        def itemData = getItemDefinition(item.itemType)
        if (!itemData) {
            return [success: false, message: "Unknown item type: ${item.itemType}"]
        }
        
        // Execute the item's ability
        def result = executeItemAbility(player, item, itemData)
        
        if (result.success) {
            LambdaPlayer.withTransaction {
                def managedPlayer = LambdaPlayer.get(player.id)
                def managedItem = managedPlayer.specialItems.find { it.id == item.id }
                
                if (managedItem && !managedItem.isPermanent) {
                    managedItem.usesRemaining -= 1
                    managedItem.lastUsed = new Date()
                    
                    if (itemData.duration > 0) {
                        managedItem.isActive = true
                        managedItem.expiresAt = new Date(System.currentTimeMillis() + (itemData.duration * 1000))
                    }
                    
                    if (managedItem.usesRemaining <= 0 && !managedItem.isPermanent) {
                        managedPlayer.removeFromSpecialItems(managedItem)
                        managedItem.delete()
                        result.message += " (Item consumed)"
                    } else {
                        managedItem.save(failOnError: true)
                    }
                    
                    managedPlayer.save(failOnError: true)
                }
            }
        }
        
        return result
    }
    
    def listPlayerItems(LambdaPlayer player) {
        def items = []
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer?.specialItems) {
                items = managedPlayer.specialItems.collect { it }
            }
        }
        return items
    }
    
    def getItemStatus(LambdaPlayer player, String itemName) {
        def item = findPlayerItem(player, itemName)
        if (!item) {
            return null
        }
        
        def now = new Date()
        def status = [:]
        status.name = item.name
        status.type = item.itemType
        status.usesRemaining = item.usesRemaining
        status.maxUses = item.maxUses
        status.isActive = item.isActive
        status.isPermanent = item.isPermanent
        
        if (item.expiresAt && item.expiresAt > now) {
            status.timeRemaining = ((item.expiresAt.time - now.time) / 1000).toInteger()
        } else if (item.isActive && item.expiresAt && item.expiresAt <= now) {
            // Item expired, deactivate it
            LambdaPlayer.withTransaction {
                def managedPlayer = LambdaPlayer.get(player.id)
                def managedItem = managedPlayer.specialItems.find { it.id == item.id }
                if (managedItem) {
                    managedItem.isActive = false
                    managedItem.save(failOnError: true)
                }
            }
            status.isActive = false
        }
        
        return status
    }
    
    private def executeItemAbility(LambdaPlayer player, SpecialItem item, Map itemData) {
        switch (item.itemType) {
            case 'SCANNER_BOOST':
                return executeScannerBoost(player)
            case 'STEALTH_CLOAK':
                return executeStealthCloak(player)
            case 'BIT_MULTIPLIER':
                return executeBitMultiplier(player)
            case 'RESPAWN_CACHE':
                return executeRespawnCache(player)
            case 'SWAP_SPACE':
                return executeSwapSpace(player)
            case 'DEFRAG_DETECTOR':
                return executeDefragDetector(player)
            case 'LOGIC_AMPLIFIER':
                return executeLogicAmplifier(player)
            case 'MATRIX_MAPPER':
                return executeMatrixMapper(player)
            case 'ENTROPY_STABILIZER':
                return executeEntropyStabilizer(player)
            case 'FRAGMENT_MAGNET':
                return executeFragmentMagnet(player)
            case 'MATRIX_CLIPPER':
                return executeMatrixClipper(player)
            case 'INSTANT_REPAIR_KIT':
                return executeInstantRepairKit(player)
            default:
                return [success: false, message: "Unknown item ability: ${item.itemType}"]
        }
    }
    
    private def executeScannerBoost(LambdaPlayer player) {
        def result = [success: true, message: "Scanner enhanced! Revealing adjacent areas..."]
        def scanResults = []
        
        // Scan all 8 adjacent coordinates plus current position
        def directions = [
            [-1, -1], [-1, 0], [-1, 1],
            [0, -1],  [0, 0],  [0, 1],
            [1, -1],  [1, 0],  [1, 1]
        ]
        
        directions.each { delta ->
            def scanX = Math.max(0, Math.min(9, player.positionX + delta[0]))
            def scanY = Math.max(0, Math.min(9, player.positionY + delta[1]))
            
            def content = scanCoordinate(player.currentMatrixLevel, scanX, scanY)
            if (content.hasContent) {
                scanResults.add("(${scanX},${scanY}): ${content.description}")
            }
        }
        
        if (scanResults) {
            result.scanData = scanResults
            result.message += "\n" + scanResults.join("\n")
        } else {
            result.message += "\nNo significant activity detected in adjacent areas."
        }
        
        return result
    }
    
    private def executeStealthCloak(LambdaPlayer player) {
        return [
            success: true, 
            message: "Stealth cloak activated! Next movement has 75% chance to avoid defrag bot encounters.",
            effect: "stealth_protection"
        ]
    }
    
    private def executeBitMultiplier(LambdaPlayer player) {
        return [
            success: true,
            message: "Bit multiplier activated! Next bit reward will be doubled.",
            effect: "bit_multiplier"
        ]
    }
    
    private def executeRespawnCache(LambdaPlayer player) {
        return [
            success: true,
            message: "Respawn cache created at current location (${player.positionX},${player.positionY}). If defragged, you'll return here instead of (0,0).",
            effect: "respawn_cache",
            cacheX: player.positionX,
            cacheY: player.positionY,
            cacheLevel: player.currentMatrixLevel
        ]
    }
    
    private def executeSwapSpace(LambdaPlayer player) {
        return [
            success: true,
            message: "Swap space allocated! Next defrag attempt will be blocked and convert to +50 bits instead.",
            effect: "defrag_immunity"
        ]
    }
    
    private def executeDefragDetector(LambdaPlayer player) {
        def result = [success: true, message: "Defrag detector activated! Scanning for hostile processes..."]
        def detections = []
        
        // Scan 3x3 area around player for defrag bots
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                def scanX = Math.max(0, Math.min(9, player.positionX + dx))
                def scanY = Math.max(0, Math.min(9, player.positionY + dy))
                
                def bot = DefragBot.findByMatrixLevelAndPositionXAndPositionYAndIsActive(
                    player.currentMatrixLevel, scanX, scanY, true
                )
                
                if (bot) {
                    detections.add("DEFRAG BOT at (${scanX},${scanY}) - Difficulty: ${bot.difficultyLevel}/10")
                }
            }
        }
        
        if (detections) {
            result.message += "\n⚠️  THREATS DETECTED:\n" + detections.join("\n")
        } else {
            result.message += "\n✅ No defrag processes detected in local area."
        }
        
        return result
    }
    
    private def executeLogicAmplifier(LambdaPlayer player) {
        return [
            success: true,
            message: "Logic amplifier activated! Next logic fragment pickup will have enhanced power level.",
            effect: "logic_amplifier"
        ]
    }
    
    private def executeMatrixMapper(LambdaPlayer player) {
        def result = [success: true, message: "Matrix mapper activated! Revealing level layout..."]
        def mapData = []
        
        // Show a 5x5 grid around player with content indicators
        for (int y = player.positionY + 2; y >= player.positionY - 2; y--) {
            def row = []
            for (int x = player.positionX - 2; x <= player.positionX + 2; x++) {
                def actualX = Math.max(0, Math.min(9, x))
                def actualY = Math.max(0, Math.min(9, y))
                
                if (actualX == player.positionX && actualY == player.positionY) {
                    row.add("@")  // Player position
                } else {
                    def content = scanCoordinate(player.currentMatrixLevel, actualX, actualY)
                    row.add(content.symbol)
                }
            }
            mapData.add(row.join(" "))
        }
        
        result.mapData = mapData
        result.message += "\n" + mapData.join("\n")
        result.message += "\nLegend: @ = You, D = Defrag Bot, F = Fragment, M = Merchant, . = Empty"
        
        return result
    }
    
    private def executeEntropyStabilizer(LambdaPlayer player) {
        return [
            success: true,
            message: "Entropy stabilizer activated! Digital coherence will not decay for 1 hour.",
            effect: "entropy_stabilizer"
        ]
    }
    
    private def executeFragmentMagnet(LambdaPlayer player) {
        def result = [success: true, message: "Fragment magnet activated! Scanning for logic fragments..."]
        def fragmentLocations = []
        
        // Scan 5x5 area for fragments
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                def scanX = Math.max(0, Math.min(9, player.positionX + dx))
                def scanY = Math.max(0, Math.min(9, player.positionY + dy))
                
                def fragment = findFragmentAtCoordinates(player.currentMatrixLevel, scanX, scanY)
                if (fragment) {
                    def distance = Math.sqrt(dx * dx + dy * dy).round(1)
                    fragmentLocations.add("${fragment.name} at (${scanX},${scanY}) - Distance: ${distance}")
                }
            }
        }
        
        if (fragmentLocations) {
            result.message += "\n🧲 FRAGMENTS DETECTED:\n" + fragmentLocations.join("\n")
        } else {
            result.message += "\n❌ No logic fragments detected in scanning range."
        }
        
        return result
    }
    
    private def scanCoordinate(Integer matrixLevel, Integer x, Integer y) {
        def result = [hasContent: false, description: "", symbol: "."]
        
        // Check for defrag bot
        def bot = DefragBot.findByMatrixLevelAndPositionXAndPositionYAndIsActive(matrixLevel, x, y, true)
        if (bot) {
            result.hasContent = true
            result.description = "Defrag Bot (Difficulty ${bot.difficultyLevel}/10)"
            result.symbol = "D"
            return result
        }
        
        // Check for logic fragment
        def fragment = findFragmentAtCoordinates(matrixLevel, x, y)
        if (fragment) {
            result.hasContent = true
            result.description = "Logic Fragment: ${fragment.name}"
            result.symbol = "F"
            return result
        }
        
        // Check for Lambda merchant
        try {
            def merchant = lambdaMerchantService.getMerchantAt(matrixLevel, x, y)
            if (merchant) {
                result.hasContent = true
                result.description = "Lambda Merchant: ${merchant.merchantName}"
                result.symbol = "M"
                return result
            }
        } catch (Exception e) {
            // Ignore if merchant service call fails
        }
        
        return result
    }
    
    private def findFragmentAtCoordinates(Integer matrixLevel, Integer x, Integer y) {
        // Same logic as in TelnetServerService - this should probably be moved to a shared service
        def fragments = getAvailableFragments()
        def coordinateHash = (matrixLevel * 100 + x * 10 + y) % fragments.size()
        def hasFragment = (coordinateHash + x + y) % 10 < 3
        return hasFragment ? fragments[coordinateHash] : null
    }
    
    private def getAvailableFragments() {
        return [
            [name: "Conditional Logic", fragmentType: "CONDITIONAL", powerLevel: 3],
            [name: "Loop Control", fragmentType: "LOOP", powerLevel: 4],
            [name: "Function Definition", fragmentType: "FUNCTION", powerLevel: 5],
            [name: "Exception Handling", fragmentType: "EXCEPTION_HANDLING", powerLevel: 6],
            [name: "Class Structure", fragmentType: "CLASS", powerLevel: 7],
            [name: "Data Types", fragmentType: "DATA_TYPE", powerLevel: 2],
            [name: "Import System", fragmentType: "IMPORT", powerLevel: 3]
        ]
    }
    
    private def findPlayerItem(LambdaPlayer player, String itemName) {
        def foundItem = null
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer?.specialItems) {
                foundItem = managedPlayer.specialItems.find { item ->
                    item.name.toLowerCase().replace(' ', '_') == itemName.toLowerCase() ||
                    item.name.toLowerCase() == itemName.toLowerCase()
                }
            }
        }
        return foundItem
    }
    
    private Map getItemDefinition(String itemType) {
        def definitions = [
            'SCANNER_BOOST': [
                name: 'Scanner Boost',
                description: 'Reveals content in all 8 adjacent coordinates',
                maxUses: 3,
                duration: 0,
                isPermanent: false,
                rarity: 'COMMON'
            ],
            'STEALTH_CLOAK': [
                name: 'Stealth Cloak',
                description: 'Provides 75% chance to avoid defrag bot encounters for next movement',
                maxUses: 1,
                duration: 0,
                isPermanent: false,
                rarity: 'UNCOMMON'
            ],
            'BIT_MULTIPLIER': [
                name: 'Bit Multiplier',
                description: 'Doubles the next bit reward received',
                maxUses: 1,
                duration: 0,
                isPermanent: false,
                rarity: 'RARE'
            ],
            'RESPAWN_CACHE': [
                name: 'Respawn Cache',
                description: 'Sets a custom respawn point instead of (0,0) if defragged',
                maxUses: 1,
                duration: 3600, // 1 hour
                isPermanent: false,
                rarity: 'RARE'
            ],
            'SWAP_SPACE': [
                name: 'Swap Space',
                description: 'Blocks next defrag attempt and converts to +50 bits',
                maxUses: 1,
                duration: 0,
                isPermanent: false,
                rarity: 'EPIC'
            ],
            'DEFRAG_DETECTOR': [
                name: 'Defrag Detector',
                description: 'Scans 3x3 area for defrag bots and shows their difficulty',
                maxUses: 5,
                duration: 0,
                isPermanent: false,
                rarity: 'COMMON'
            ],
            'LOGIC_AMPLIFIER': [
                name: 'Logic Amplifier',
                description: 'Enhances next logic fragment pickup with +1 power level',
                maxUses: 1,
                duration: 0,
                isPermanent: false,
                rarity: 'UNCOMMON'
            ],
            'MATRIX_MAPPER': [
                name: 'Matrix Mapper',
                description: 'Reveals 5x5 grid around current position with content symbols',
                maxUses: 2,
                duration: 0,
                isPermanent: false,
                rarity: 'UNCOMMON'
            ],
            'ENTROPY_STABILIZER': [
                name: 'Entropy Stabilizer',
                description: 'Prevents entropy decay for 1 hour',
                maxUses: 1,
                duration: 3600,
                isPermanent: false,
                rarity: 'EPIC'
            ],
            'FRAGMENT_MAGNET': [
                name: 'Fragment Magnet',
                description: 'Detects all logic fragments in 5x5 area and shows their locations',
                maxUses: 3,
                duration: 0,
                isPermanent: false,
                rarity: 'RARE'
            ],
            'MATRIX_CLIPPER': [
                name: 'Matrix Clipper',
                description: 'Allows clipping through damaged coordinates to advance to next matrix level',
                maxUses: 1,
                duration: 0,
                isPermanent: false,
                rarity: 'LEGENDARY'
            ],
            'INSTANT_REPAIR_KIT': [
                name: 'Instant Repair Kit',
                description: 'Instantly repairs a wiped coordinate to full operational status',
                maxUses: 1,
                duration: 0,
                isPermanent: false,
                rarity: 'EPIC'
            ]
        ]
        
        return definitions[itemType]
    }
    
    private def executeMatrixClipper(LambdaPlayer player) {
        return [
            success: true,
            message: "Matrix clipper activated! You can now bypass damaged coordinates and advance to the next matrix level.",
            effect: "matrix_clip",
            clipLevel: player.currentMatrixLevel + 1
        ]
    }
    
    private def executeInstantRepairKit(LambdaPlayer player) {
        // Find nearby damaged coordinates to repair
        def repairTargets = []
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                def scanX = Math.max(0, Math.min(9, player.positionX + dx))
                def scanY = Math.max(0, Math.min(9, player.positionY + dy))
                
                // Check if coordinate needs repair (this will be implemented in CoordinateStateService)
                // For now, return placeholder
                repairTargets.add("(${scanX},${scanY})")
            }
        }
        
        return [
            success: true,
            message: "Instant repair kit activated! All coordinates in 3x3 area around your position have been restored to full operational status.",
            effect: "instant_repair",
            repairedCoordinates: repairTargets
        ]
    }
}