package ysap

import grails.gorm.transactions.Transactional
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import ysap.helpers.BoxBuilder

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

        markUniqueByRarity(inventory)
        return new JsonBuilder(inventory).toString()
    }

    /**
     * Tier the freshly-built inventory: rare/epic items become UNIQUE (global first-come-first-served —
     * once any player buys one it's gone for everyone here), everything else stays common (per-player).
     * This is what makes "the first player to reach the merchant gets it, and only that player" real for
     * the high-value stock while bread-and-butter fragments stay broadly available.
     */
    private void markUniqueByRarity(Map inventory) {
        inventory.fragments?.each { if ((it.rarity as String)?.toLowerCase() in ['rare', 'epic']) it.unique = true }
        inventory.specialItems?.each { if (getItemRarity(it.name as String) in ['RARE', 'EPIC']) it.unique = true }
    }
    
    /**
     * The ONE ordered list of items this player can currently buy here — fragments first, then special
     * items, matching the historical display/`buy <n>` numbering contract. Both generateShopDisplay and
     * handlePurchase resolve against THIS list, so the index a player sees always maps to the item they get
     * (filtering can never desync the numbering). Hides common items this player already bought; unique
     * items that have been bought are already physically gone from `inventory`, so no extra filter needed.
     * Each entry: [name, price, description, unique, kind:'fragment'|'special'].
     */
    private List<Map> visibleItemsFor(LambdaMerchant merchant, LambdaPlayer player) {
        // Re-read the live row, not the passed (possibly stale) object — a purchase earlier this turn may
        // have removed a unique item or recorded a common one. This keeps the display, the `buy <n>` index
        // resolution, and the just-committed stock state in agreement.
        def invJson = merchant.inventory
        def ppJson = merchant.playerPurchases
        LambdaMerchant.withTransaction {
            def m = LambdaMerchant.get(merchant.id)
            if (m != null) { invJson = m.inventory; ppJson = m.playerPurchases }
        }

        def inventory
        try {
            inventory = new JsonSlurper().parseText(invJson)
        } catch (Exception ignored) {
            return []
        }
        def fragments = (inventory?.fragments instanceof List) ? inventory.fragments : []
        def specialItems = (inventory?.specialItems instanceof List) ? inventory.specialItems : []

        def purchased = []
        try {
            def pp = new JsonSlurper().parseText(ppJson ?: '{}')
            purchased = (pp[player.id.toString()] ?: []) as List
        } catch (Exception ignored) { }

        def visible = []
        fragments.findAll { it?.name && !(it.name in purchased) }.each {
            visible << [name: it.name, price: it.price, description: it.description ?: '',
                        unique: (it.unique ?: false), kind: 'fragment']
        }
        specialItems.findAll { it?.name && !(it.name in purchased) }.each {
            visible << [name: it.name, price: it.price, description: it.description ?: '',
                        unique: (it.unique ?: false), kind: 'special']
        }
        return visible
    }

    private String generateShopDisplay(LambdaMerchant merchant, LambdaPlayer player) {
        // Telnet-thread read of the live bits — wrap in a transaction per the codebase rule.
        def currentBits = player.bits
        LambdaPlayer.withTransaction {
            def currentPlayer = LambdaPlayer.get(player.id)
            if (currentPlayer != null) currentBits = currentPlayer.bits
        }

        // The visible list already hides this player's common purchases and any globally-bought uniques,
        // and is ordered fragments-then-specials — so the number printed here is exactly the number
        // handlePurchase resolves against. Numbering is continuous across both sections.
        def visible = visibleItemsFor(merchant, player)

        def box = new BoxBuilder(48)  // Width matching the original box
            .addCenteredLine(merchant.merchantName)
            .addCenteredLine("FRAGMENT TRADER")
            .addSeparator()
            .addLine(" Your Bits: ${currentBits}")
            .addSeparator()
            .addCenteredLine("LOGIC FRAGMENTS")
            .addSeparator()

        // Unique items are marked with a compact '*' in the slot after the item number (a wordy
        // "(unique)" tag overflows the 48-col box and gets truncated). A legend line explains it.
        boolean anyFragment = false
        boolean anySpecial = false
        boolean anyUnique = false
        def itemLine = { int num, Map item ->
            if (item.unique) anyUnique = true
            def numMark = (num.toString() + (item.unique ? '*' : '')).padRight(3)  // '1*' hugs the digit
            " ${numMark} ${item.name.padRight(23)} ${item.price.toString().padLeft(6)} bits"
        }

        visible.eachWithIndex { item, index ->
            if (item.kind == 'fragment') { anyFragment = true; box.addLine(itemLine(index + 1, item)) }
        }
        if (!anyFragment) box.addLine(" (sold out)")

        box.addSeparator()
            .addCenteredLine("SPECIAL ITEMS")
            .addSeparator()

        visible.eachWithIndex { item, index ->
            if (item.kind == 'special') { anySpecial = true; box.addLine(itemLine(index + 1, item)) }
        }
        if (!anySpecial) box.addLine(" (sold out)")

        if (anyUnique) {
            box.addSeparator().addLine(" * unique - first buyer takes it for good")
        }

        def result = box.build()
        result += "Commands: buy <number> | sell <fragment_name> | exit\r\n"

        return result
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

        // Resolve against the SAME per-player visible list the player saw in `shop`, so `buy <n>` always
        // buys the n-th item they were shown — never a raw-inventory index that filtering would desync.
        def visible = visibleItemsFor(merchant, player)
        if (itemNumber < 1 || itemNumber > visible.size()) {
            return [success: false, output: "Item number out of range (1-${visible.size()})"]
        }

        def selectedItem = visible[itemNumber - 1]

        if (!selectedItem?.name || selectedItem?.price == null) {
            return [success: false, output: "Selected item is invalid. Please try a different item."]
        }

        // The whole purchase — claim, charge, grant, and stock bookkeeping — runs in ONE transaction so it
        // either fully commits or fully rolls back; a grant failure can never strand a claimed unique item.
        // The merchant row's optimistic-lock `version` is the race-resolution point: two buyers who both
        // read the same merchant and both write it (a unique removal, or a playerPurchases append) collide
        // on commit — one wins, the other throws OptimisticLockingFailureException. We RETRY the loser: on
        // retry it re-reads the now-current row, so a racer for the LAST unique sees it gone (→ sold out),
        // while two buyers of (different or common) items each re-read and both succeed. Retry, not a
        // pessimistic lock, because H2's row lock doesn't reliably block here and the merge-on-reread is
        // exactly what common-item contention needs (a plain lock-and-overwrite would drop the other write).
        def outcome = [status: 'ok']   // 'ok' | 'no_player' | 'broke' | 'sold_out'
        int attempt = 0
        while (true) {
            attempt++
            try {
                outcome = attemptPurchase(merchant, player, selectedItem)
                break
            } catch (org.springframework.dao.OptimisticLockingFailureException ignored) {
                // A racing buyer committed first; the loser's transaction rolled back (no charge, no grant).
                // Retry against the now-current row — see the contract in attemptPurchase. Give up after a
                // few rounds of contention and treat it as having lost the item.
                if (attempt >= 5) { outcome = [status: 'sold_out']; break }
            }
        }

        if (outcome.status == 'no_player') return [success: false, output: "Player not found in database."]
        if (outcome.status == 'broke') return [success: false, output: "Insufficient bits. Need ${selectedItem.price}, you have ${outcome.have}"]
        if (outcome.status == 'sold_out') return [success: false, output: "⚡ Someone just bought the last ${selectedItem.name}.\r\n", action: "purchase"]

        return [
                success: true,
                output : "✅ Purchased ${selectedItem.name} for ${selectedItem.price} bits!\r\n",
                action : "purchase"
        ]
    }

    /**
     * One atomic attempt at a resolved purchase: claim (unique) / record (common) the stock, charge the
     * player, and grant the item — ALL in one transaction so it fully commits or fully rolls back (a grant
     * failure can never strand a claimed unique item). The merchant row's optimistic-lock `version` is the
     * race-resolution point: two buyers who both read it and both write it (a unique removal, or a
     * playerPurchases append) collide on commit — one wins, the other throws and is retried by the caller
     * against the now-current row. Returns an outcome map; throws OptimisticLockingFailureException on a
     * lost commit race so the caller can retry.
     */
    private Map attemptPurchase(LambdaMerchant merchant, LambdaPlayer player, Map selectedItem) {
        def outcome = [status: 'ok']
        // REQUIRES_NEW: this service is @Transactional, so a plain withTransaction would JOIN the outer
        // transaction and defer the commit (and any optimistic-lock failure) to the service boundary —
        // past the caller's retry catch. A fresh, independent transaction commits HERE, so a version
        // conflict surfaces inside this call and the caller can retry it.
        // INVARIANT: the suspended outer transaction must hold NO write lock on this merchant or player row
        // when we get here (today it only did a read-only visibleItemsFor). If a caller ever writes either
        // row earlier in the same command, this inner tx would block on a lock the outer tx holds —
        // self-deadlock until LOCK_TIMEOUT. Keep merchant/player writes out of the pre-purchase path.
        LambdaPlayer.withTransaction([propagationBehavior: org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW]) {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (!managedPlayer) { outcome.status = 'no_player'; return }
            if (managedPlayer.bits < selectedItem.price) { outcome.status = 'broke'; outcome.have = managedPlayer.bits; return }

            def m = LambdaMerchant.get(merchant.id)
            if (m == null) { outcome.status = 'sold_out'; return }
            def inv
            try { inv = new JsonSlurper().parseText(m.inventory) } catch (Exception ignored) { outcome.status = 'sold_out'; return }
            def frags = (inv?.fragments instanceof List) ? inv.fragments : []
            def specs = (inv?.specialItems instanceof List) ? inv.specialItems : []

            if (selectedItem.unique) {
                // Re-check: if a racing buyer already took it, bail with nothing charged.
                if (!(frags.any { it?.name == selectedItem.name } || specs.any { it?.name == selectedItem.name })) {
                    outcome.status = 'sold_out'; return
                }
                inv.fragments = frags.findAll { it?.name != selectedItem.name }
                inv.specialItems = specs.findAll { it?.name != selectedItem.name }
                m.inventory = new JsonBuilder(inv).toString()
            } else {
                // COMMON: record against this player so it's hidden from THEM but stays buyable for others.
                def pp = [:]
                try { pp = new JsonSlurper().parseText(m.playerPurchases ?: '{}') } catch (Exception ignored) { pp = [:] }
                def key = player.id.toString()
                def list = (pp[key] ?: []) as List
                if (!(selectedItem.name in list)) list << selectedItem.name
                pp[key] = list
                m.playerPurchases = new JsonBuilder(pp).toString()
            }

            // Charge + grant.
            managedPlayer.bits -= selectedItem.price

            boolean isFragment = (selectedItem.kind == 'fragment')
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
            m.save(failOnError: true)
        }
        return outcome
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
        
        // Calculate sale price (50% of base value). Integer division — bits is an Integer column, so a
        // fractional price (e.g. 32.5) would truncate silently AND mis-print in the "Sold for N" message.
        def salePrice = (50 + (playerFragment.powerLevel * 15)).intdiv(2)
        
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

    // ===== MERCHANT COMMAND HANDLER (moved from TelnetServerService) =====

    String handleMerchantCommand(String command, LambdaPlayer player) {
        // Check if there's a merchant at current position
        def merchant = this.getMerchantAt(player.currentMatrixLevel, player.positionX, player.positionY)
        if (!merchant) {
            return "No Lambda merchant at current coordinates (${player.positionX},${player.positionY}). Use 'map' to find merchants. (M)\r\n"
        }
        
        def result = this.handleMerchantInteraction(merchant, command, player)
        
        // Convert \n to \r\n for proper telnet formatting
        def output = result.output?.replace('\n', '\r\n') ?: ""
        
        if (result.action == "browse") {
            return output
        } else if (result.action == "purchase") {
            return TerminalFormatter.formatText(output, 'bold', 'green')
        } else if (result.action == "sale") {
            return TerminalFormatter.formatText(output, 'bold', 'green')
        } else if (result.action == "help") {
            return TerminalFormatter.formatText(output, 'italic', 'yellow')
        } else {
            return TerminalFormatter.formatText(output, 'bold', 'red')
        }
    }
}