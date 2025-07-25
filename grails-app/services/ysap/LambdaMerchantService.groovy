package ysap

import grails.gorm.transactions.Transactional
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

@Transactional
class LambdaMerchantService {

    def spawnRandomMerchant(Integer matrixLevel) {
        // Only spawn one merchant per level
        def existingMerchant = LambdaMerchant.findByMatrixLevelAndIsActive(matrixLevel, true)
        if (existingMerchant) {
            return existingMerchant
        }
        
        // Random position in matrix
        def positionX = (Math.random() * 10).toInteger()
        def positionY = (Math.random() * 10).toInteger()
        
        // Don't spawn in safe zones (0,0), (0,1), (1,0), (1,1)
        if (positionX <= 1 && positionY <= 1) {
            positionX = 2 + (Math.random() * 8).toInteger()
            positionY = 2 + (Math.random() * 8).toInteger()
        }
        
        def merchantNames = [
            "Zeta the Code Broker", "Alpha Fragment Dealer", "Binary Bill", 
            "The Logic Peddler", "Gamma Exchange", "Data Stream Dave",
            "The Bit Baron", "Null Pointer Nancy", "Stack Overflow Sam"
        ]
        
        def merchant = new LambdaMerchant(
            merchantName: merchantNames[(Math.random() * merchantNames.size()).toInteger()],
            matrixLevel: matrixLevel,
            positionX: positionX,
            positionY: positionY,
            merchantType: "FRAGMENT_TRADER",
            isActive: true,
            spawnedDate: new Date(),
            inventory: generateMerchantInventory(matrixLevel)
        )
        
        merchant.save(failOnError: true)
        return merchant
    }
    
    def getMerchantAt(Integer matrixLevel, Integer positionX, Integer positionY) {
        return LambdaMerchant.findByMatrixLevelAndPositionXAndPositionYAndIsActive(
            matrixLevel, positionX, positionY, true
        )
    }
    
    def handleMerchantInteraction(LambdaMerchant merchant, String command, LambdaPlayer player) {
        def result = [:]
        def trimmedCommand = command.trim().toLowerCase()
        
        if (trimmedCommand == "shop" || trimmedCommand == "browse") {
            result.success = true
            result.output = generateShopDisplay(merchant, player)
            result.action = "browse"
            return result
        }
        
        if (trimmedCommand.startsWith("buy ")) {
            return handlePurchase(merchant, trimmedCommand, player)
        }
        
        if (trimmedCommand.startsWith("sell ")) {
            return handleSale(merchant, trimmedCommand, player)
        }
        
        result.success = false
        result.output = "Available commands: shop, buy <item>, sell <fragment>"
        result.action = "help"
        return result
    }
    
    private String generateMerchantInventory(Integer matrixLevel) {
        def inventory = [:]
        
        // Mix of legacy logic fragments and new puzzle-oriented items based on matrix level
        def fragments = []
        
        // Legacy logic fragments (early levels)
        if (matrixLevel <= 5) {
            fragments.addAll([
                [name: "Data Types", price: 50 + (matrixLevel * 10), rarity: "common"],
                [name: "Conditional Logic", price: 75 + (matrixLevel * 15), rarity: "common"],
                [name: "Loop Control", price: 100 + (matrixLevel * 20), rarity: "uncommon"],
                [name: "Function Definition", price: 150 + (matrixLevel * 25), rarity: "uncommon"],
                [name: "Exception Handling", price: 200 + (matrixLevel * 30), rarity: "rare"],
                [name: "Import System", price: 125 + (matrixLevel * 20), rarity: "uncommon"]
            ])
        }
        
        // Advanced fragments for puzzle solving (higher levels)
        if (matrixLevel >= 3) {
            fragments.addAll([
                [name: "Atmospheric Processor Fragment", price: 200 + (matrixLevel * 30), rarity: "rare", description: "Processes air elemental data"],
                [name: "Thermal Decoder Fragment", price: 250 + (matrixLevel * 35), rarity: "rare", description: "Decodes fire elemental signatures"],
                [name: "Geological Survey Fragment", price: 180 + (matrixLevel * 25), rarity: "uncommon", description: "Locates earth elemental sources"],
                [name: "Hydro-Chemical Fragment", price: 220 + (matrixLevel * 32), rarity: "rare", description: "Validates water elemental presence"]
            ])
        }
        
        // Class Structure only available at high levels
        if (matrixLevel >= 6) {
            fragments.add([name: "Class Structure", price: 300 + (matrixLevel * 40), rarity: "epic"])
        }
        
        // Randomly select 3-5 fragments for this merchant
        def shuffled = fragments.shuffled()
        def selectedCount = 3 + (Math.random() * 3).toInteger()
        inventory.fragments = shuffled.take(selectedCount)
        
        // Special items - enhanced selection based on level
        def specialItems = [
            [name: "Scanner Boost", price: 80 + (matrixLevel * 10), description: "Enhances area scanning (3 uses)"],
            [name: "Bit Multiplier", price: 120 + (matrixLevel * 15), description: "Doubles next bit reward (1 use)"],
            [name: "Stealth Cloak", price: 200 + (matrixLevel * 25), description: "75% defrag bot avoidance (1 use)"]
        ]
        
        // Advanced special items for higher levels
        if (matrixLevel >= 4) {
            specialItems.addAll([
                [name: "Defrag Detector", price: 150 + (matrixLevel * 20), description: "Shows defrag bot locations (5 uses)"],
                [name: "Matrix Mapper", price: 180 + (matrixLevel * 22), description: "5x5 grid visual display (2 uses)"]
            ])
        }
        
        if (matrixLevel >= 6) {
            specialItems.addAll([
                [name: "Logic Amplifier", price: 250 + (matrixLevel * 30), description: "+1 power level to next fragment (1 use)"],
                [name: "Fragment Magnet", price: 200 + (matrixLevel * 25), description: "Locates fragments in 5x5 area (3 uses)"]
            ])
        }
        
        if (matrixLevel >= 8) {
            specialItems.addAll([
                [name: "Entropy Stabilizer", price: 400 + (matrixLevel * 40), description: "Prevents entropy decay for 1 hour (EPIC)"],
                [name: "Swap Space", price: 300 + (matrixLevel * 35), description: "Blocks defrag attack, converts to +50 bits"],
                [name: "Respawn Cache", price: 280 + (matrixLevel * 30), description: "Custom respawn point for 1 hour"]
            ])
        }
        
        // Randomly select 2-3 special items
        def selectedSpecialCount = 2 + (Math.random() * 2).toInteger()
        inventory.specialItems = specialItems.shuffled().take(selectedSpecialCount)
        
        return new JsonBuilder(inventory).toString()
    }
    
    private String generateShopDisplay(LambdaMerchant merchant, LambdaPlayer player) {
        def jsonSlurper = new JsonSlurper()
        def inventory = jsonSlurper.parseText(merchant.inventory)
        
        // Refresh player to get current bits amount
        def currentPlayer = LambdaPlayer.get(player.id)
        def currentBits = currentPlayer?.bits ?: player.bits
        
        def display = new StringBuilder()
        display.append("╔════════════════════════════════════════════════╗\n")
        display.append("║           ${merchant.merchantName.center(42)}           ║\n")
        display.append("║                FRAGMENT TRADER                 ║\n")
        display.append("╠════════════════════════════════════════════════╣\n")
        display.append("║ Your Bits: ${currentBits.toString().padRight(36)} ║\n")
        display.append("╠════════════════════════════════════════════════╣\n")
        display.append("║                 LOGIC FRAGMENTS                ║\n")
        display.append("╠════════════════════════════════════════════════╣\n")
        
        inventory.fragments.eachWithIndex { fragment, index ->
            def line = "║ ${(index + 1).toString().padRight(2)} ${fragment.name.padRight(25)} ${fragment.price.toString().padLeft(6)} bits ║"
            display.append(line).append('\n')
        }
        
        display.append("╠════════════════════════════════════════════════╣\n")
        display.append("║                 SPECIAL ITEMS                  ║\n")
        display.append("╠════════════════════════════════════════════════╣\n")
        
        inventory.specialItems.eachWithIndex { item, index ->
            def itemNum = index + inventory.fragments.size() + 1
            def line = "║ ${itemNum.toString().padRight(2)} ${item.name.padRight(25)} ${item.price.toString().padLeft(6)} bits ║"
            display.append(line).append('\n')
        }
        
        display.append("╚════════════════════════════════════════════════╝\n")
        display.append("Commands: buy <number> | sell <fragment_name> | exit")
        
        return display.toString()
    }

    private Map handlePurchase(LambdaMerchant merchant, String command, LambdaPlayer player) {
        def result = [:]
        def parts = command.trim().split(/\s+/)

        if (parts.size() < 2) {
            return [success: false, output: "Usage: buy <item_number>"]
        }

        def itemNumber
        try {
            itemNumber = Integer.parseInt(parts[1])
        } catch (NumberFormatException e) {
            return [success: false, output: "Invalid item number: ${parts[1]}"]
        }

        def inventory
        try {
            inventory = new JsonSlurper().parseText(merchant.inventory)
        } catch (Exception e) {
            return [success: false, output: "Merchant inventory error. Please try again later."]
        }

        def fragments = inventory?.fragments ?: []
        def specialItems = inventory?.specialItems ?: []

        if (!(fragments instanceof List) || !(specialItems instanceof List)) {
            return [success: false, output: "Merchant inventory is corrupted. Please contact support."]
        }

        def allItems = fragments + specialItems

        if (itemNumber < 1 || itemNumber > allItems.size()) {
            return [success: false, output: "Item number out of range (1-${allItems.size()})"]
        }

        def selectedItem = allItems[itemNumber - 1]

        if (!selectedItem?.name || selectedItem?.price == null) {
            return [success: false, output: "Selected item is invalid. Please try a different item."]
        }

        // Check if player has enough bits BEFORE starting transaction
        def currentPlayer = LambdaPlayer.get(player.id)
        if (!currentPlayer) {
            return [success: false, output: "Player not found in database."]
        }
        
        if (currentPlayer.bits < selectedItem.price) {
            return [success: false, output: "Insufficient bits. Need ${selectedItem.price}, you have ${currentPlayer.bits}"]
        }

        // Perform purchase in a transaction
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (!managedPlayer) {
                throw new IllegalStateException("Player not found in database.")
            }

            // Double-check bits amount in transaction (race condition protection)
            if (managedPlayer.bits < selectedItem.price) {
                throw new IllegalStateException("Insufficient bits - race condition detected")
            }

            managedPlayer.bits -= selectedItem.price

            boolean isFragment = itemNumber <= fragments.size()
            def fragmentType = getFragmentType(selectedItem.name)

            if (isFragment) {
                if (fragmentType in ['CALCULATOR', 'DECODER', 'LOCATOR', 'VALIDATOR']) {
                    // Puzzle logic fragment
                    def existing = managedPlayer.puzzleLogicFragments?.find { it?.name == selectedItem.name }
                    if (!existing) {
                        def fragment = new PuzzleLogicFragment(
                                name: selectedItem.name,
                                description: "Purchased from ${merchant.merchantName}",
                                fragmentType: fragmentType,
                                powerLevel: getPowerLevel(selectedItem.name),
                                functionCode: getPythonCapability(selectedItem.name),
                                requiredVariable: getRequiredVariable(selectedItem.name),
                                expectedOutput: getExpectedOutput(selectedItem.name),
                                owner: managedPlayer
                        )
                        fragment.save(failOnError: true)
                        managedPlayer.addToPuzzleLogicFragments(fragment)
                    }
                } else {
                    // Legacy logic fragment
                    def existing = managedPlayer.logicFragments?.find { it?.name == selectedItem.name }
                    if (existing) {
                        existing.quantity += 1
                        existing.save(failOnError: true)
                    } else {
                        def fragment = new LogicFragment(
                                name: selectedItem.name,
                                description: "Purchased from ${merchant.merchantName}",
                                fragmentType: fragmentType,
                                powerLevel: getPowerLevel(selectedItem.name),
                                pythonCapability: getPythonCapability(selectedItem.name),
                                quantity: 1,
                                isActive: true,
                                discoveredDate: new Date(),
                                owner: managedPlayer
                        )
                        fragment.save(failOnError: true)
                        managedPlayer.addToLogicFragments(fragment)
                    }
                }
            } else {
                // Special item
                def specialItem = new SpecialItem(
                        name: selectedItem.name,
                        itemType: getItemTypeConstant(selectedItem.name), // Convert display name to itemType constant
                        description: selectedItem.description ?: "Purchased from ${merchant.merchantName}",
                        usesRemaining: getItemMaxUses(selectedItem.name),
                        maxUses: getItemMaxUses(selectedItem.name),
                        rarity: getItemRarity(selectedItem.name),
                        obtainedDate: new Date(),
                        isPermanent: false,
                        owner: managedPlayer
                )
                specialItem.save(failOnError: true)
                managedPlayer.addToSpecialItems(specialItem)
            }

            managedPlayer.save(failOnError: true)
        }

        return [
                success: true,
                output : "✅ Purchased ${selectedItem.name} for ${selectedItem.price} bits!",
                action : "purchase"
        ]
    }



    private Map handleSale(LambdaMerchant merchant, String command, LambdaPlayer player) {
        def result = [:]
        def parts = command.split(' ', 2)
        
        if (parts.length < 2) {
            result.success = false
            result.output = "Usage: sell <fragment_name>"
            return result
        }
        
        def fragmentName = parts[1]
        def playerFragment = null
        
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            playerFragment = managedPlayer.logicFragments?.find { 
                it.name.toLowerCase() == fragmentName.toLowerCase() 
            }
        }
        
        if (!playerFragment) {
            result.success = false
            result.output = "You don't have a fragment named '${fragmentName}'"
            return result
        }
        
        // Calculate sale price (50% of base value)
        def salePrice = (50 + (playerFragment.powerLevel * 15)) / 2
        
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            def managedFragment = LogicFragment.get(playerFragment.id)
            
            if (managedFragment) {
                if (managedFragment.quantity > 1) {
                    managedFragment.quantity -= 1
                    managedFragment.save(failOnError: true)
                } else {
                    managedPlayer.removeFromLogicFragments(managedFragment)
                    managedFragment.delete()
                }
                
                managedPlayer.bits += salePrice
                managedPlayer.save(failOnError: true)
            }
        }
        
        result.success = true
        result.output = "Sold ${fragmentName} for ${salePrice} bits!"
        result.action = "sale"
        return result
    }
    
    private String getFragmentType(String name) {
        def typeMap = [
            "Data Types": "DATA_TYPE",
            "Conditional Logic": "CONDITIONAL", 
            "Loop Control": "LOOP",
            "Function Definition": "FUNCTION",
            "Exception Handling": "EXCEPTION_HANDLING",
            "Class Structure": "CLASS",
            "Import System": "IMPORT",
            // New puzzle fragments
            "Atmospheric Processor Fragment": "CALCULATOR",
            "Thermal Decoder Fragment": "DECODER",
            "Geological Survey Fragment": "LOCATOR", 
            "Hydro-Chemical Fragment": "VALIDATOR"
        ]
        return typeMap[name] ?: "FUNCTION"
    }
    
    private Integer getPowerLevel(String name) {
        def powerMap = [
            "Data Types": 2,
            "Conditional Logic": 3,
            "Loop Control": 4,
            "Function Definition": 5,
            "Exception Handling": 6,
            "Class Structure": 7,
            "Import System": 3,
            // New puzzle fragments with high power levels
            "Atmospheric Processor Fragment": 7,
            "Thermal Decoder Fragment": 8,
            "Geological Survey Fragment": 6,
            "Hydro-Chemical Fragment": 9
        ]
        return powerMap[name] ?: 5
    }
    
    private String getPythonCapability(String name) {
        def capabilityMap = [
            "Data Types": "# Lists, dictionaries, sets\nmy_list = [1, 2, 3]\nmy_dict = {'key': 'value'}",
            "Conditional Logic": "if condition:\n    # execute code\nelse:\n    # alternative code",
            "Loop Control": "for item in collection:\n    # process item\n\nwhile condition:\n    # repeat code",
            "Function Definition": "def function_name(parameters):\n    # function body\n    return result",
            "Exception Handling": "try:\n    # risky code\nexcept Exception as e:\n    # handle error",
            "Class Structure": "class ClassName:\n    def __init__(self):\n        # constructor",
            "Import System": "import module_name\nfrom package import function",
            // New puzzle fragments with elemental processing capabilities  
            "Atmospheric Processor Fragment": "# Atmospheric pressure analysis\ndef processAtmosphericData(pressure_data):\n    readings = pressure_data.split(',')\n    x = sum(readings) // len(readings) % 10\n    y = max(readings) - min(readings) % 10\n    return f'coordinates:{x},{y}'",
            "Thermal Decoder Fragment": "# Thermal signature decoding\ndef decodeThermalHex(thermal_hex):\n    decoded = base64.b64decode(thermal_hex)\n    coords = decoded.split(':')\n    x, y = int(coords[0]) % 10, int(coords[1]) % 10\n    return f'fire_coordinates:{x},{y}'",
            "Geological Survey Fragment": "# Mineral density calculation\ndef locateEarthElement(mineral_density):\n    density = float(mineral_density)\n    x = round(density * 3.7) % 10\n    y = round(density * 2.3) % 10\n    return f'earth_location:{x},{y}:confidence_high'", 
            "Hydro-Chemical Fragment": "# H2O molecular validation\ndef validateWaterSignature(h2o_signature):\n    parts = h2o_signature.split('_')\n    x = (len(parts[0]) + len(parts[1])) % 10\n    y = hash(parts[2]) % 10\n    return f'water_validated:{x},{y}:purity_confirmed'"
        ]
        return capabilityMap[name] ?: "# Advanced programming construct"
    }
    
    def spawnMerchantsForAllLevels() {
        (1..10).each { level ->
            spawnRandomMerchant(level)
        }
    }
    
    def cleanupExpiredMerchants() {
        def expiredMerchants = LambdaMerchant.createCriteria().list {
            eq 'isActive', true
            lt 'spawnedDate', new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000)) // 24 hours
        }
        
        expiredMerchants.each { merchant ->
            merchant.isActive = false
            merchant.save(failOnError: true)
        }
        
        return expiredMerchants.size()
    }
    
    private Integer getItemMaxUses(String itemName) {
        def usesMap = [
            "Scanner Boost": 3,
            "Bit Multiplier": 1,
            "Stealth Cloak": 1,
            "Defrag Detector": 5,
            "Logic Amplifier": 1,
            "Matrix Mapper": 2,
            "Entropy Stabilizer": 1,
            "Fragment Magnet": 3,
            "Swap Space": 1,
            "Respawn Cache": 1
        ]
        return usesMap[itemName] ?: 1
    }
    
    private String getItemRarity(String itemName) {
        def rarityMap = [
            "Scanner Boost": "COMMON",
            "Bit Multiplier": "UNCOMMON", 
            "Stealth Cloak": "RARE",
            "Defrag Detector": "UNCOMMON",
            "Logic Amplifier": "RARE",
            "Matrix Mapper": "UNCOMMON",
            "Entropy Stabilizer": "EPIC",
            "Fragment Magnet": "UNCOMMON",
            "Swap Space": "RARE",
            "Respawn Cache": "RARE"
        ]
        return rarityMap[itemName] ?: "COMMON"
    }
    
    private String getItemTypeConstant(String itemName) {
        def typeMap = [
            "Scanner Boost": "SCANNER_BOOST",
            "Bit Multiplier": "BIT_MULTIPLIER", 
            "Stealth Cloak": "STEALTH_CLOAK",
            "Defrag Detector": "DEFRAG_DETECTOR",
            "Logic Amplifier": "LOGIC_AMPLIFIER",
            "Matrix Mapper": "MATRIX_MAPPER",
            "Entropy Stabilizer": "ENTROPY_STABILIZER",
            "Fragment Magnet": "FRAGMENT_MAGNET",
            "Swap Space": "SWAP_SPACE",
            "Respawn Cache": "RESPAWN_CACHE"
        ]
        return typeMap[itemName] ?: "BIT_MULTIPLIER" // Default fallback
    }
    
    private String getRequiredVariable(String fragmentName) {
        def variableMap = [
            "Atmospheric Processor Fragment": "pressure_data",
            "Thermal Decoder Fragment": "thermal_hex", 
            "Geological Survey Fragment": "mineral_density",
            "Hydro-Chemical Fragment": "h2o_signature"
        ]
        return variableMap[fragmentName] ?: null
    }
    
    private String getExpectedOutput(String fragmentName) {
        def outputMap = [
            "Atmospheric Processor Fragment": "coordinates pattern",
            "Thermal Decoder Fragment": "fire_coordinates pattern",
            "Geological Survey Fragment": "earth_location pattern", 
            "Hydro-Chemical Fragment": "water_validated pattern"
        ]
        return outputMap[fragmentName] ?: "computational result"
    }
}