package ysap

import grails.gorm.transactions.Transactional

@Transactional
class PuzzleKnowledgeTradingService {
    
    def competitivePuzzleService
    def puzzleRandomizationService
    
    def getTradeablePuzzleKnowledge(LambdaPlayer player) {
        def tradeableItems = [
            puzzleFragments: [],
            variables: [],
            nonces: [],
            completedSolutions: []
        ]
        
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (!managedPlayer) return tradeableItems
            
            def session = puzzleRandomizationService.getCurrentGameSession()
            if (!session) return tradeableItems
            
            // Get player's competitive puzzle state
            def puzzleStates = competitivePuzzleService.getPlayerPuzzleProgress(
                managedPlayer, session.sessionId, managedPlayer.currentMatrixLevel
            )
            
            // PUZZLE FRAGMENTS - Always tradeable (other players might need different ones)
            managedPlayer.puzzleLogicFragments?.each { fragment ->
                if (fragment) {
                    tradeableItems.puzzleFragments.add([
                        id: fragment.id,
                        name: fragment.name,
                        type: fragment.fragmentType,
                        powerLevel: fragment.powerLevel,
                        description: fragment.description,
                        elementHint: getElementHintFromFragment(fragment.name),
                        tradeValue: calculateFragmentTradeValue(fragment),
                        canTrade: true
                    ])
                }
            }
            
            // VARIABLES - Tradeable if player has used them (they're still valuable to others)
            managedPlayer.collectedVariables?.each { variable ->
                if (variable) {
                    tradeableItems.variables.add([
                        id: variable.id,
                        name: variable.variableName,
                        value: variable.variableValue,
                        type: variable.variableType,
                        description: variable.description,
                        elementHint: getElementHintFromVariable(variable),
                        tradeValue: calculateVariableTradeValue(variable),
                        canTrade: true
                    ])
                }
            }
            
            // NONCES - Tradeable if player has obtained the symbol (no longer needs them)
            managedPlayer.discoveredNonces?.each { nonce ->
                if (nonce) {
                    def hasSymbol = playerHasElementSymbol(managedPlayer, nonce.elementType)
                    tradeableItems.nonces.add([
                        id: nonce.id,
                        name: nonce.nonceName,
                        elementType: nonce.elementType,
                        chemicalClue: nonce.chemicalClue,
                        commandFlag: nonce.commandFlag,
                        description: nonce.description,
                        tradeValue: calculateNonceTradeValue(nonce, hasSymbol),
                        canTrade: hasSymbol, // Only tradeable if player no longer needs it
                        reason: hasSymbol ? "You already have the ${nonce.elementType} symbol" : "You still need this nonce"
                    ])
                }
            }
            
            // COMPLETE SOLUTIONS - If player has solved an element, they can sell the whole solution
            ['AIR', 'FIRE', 'EARTH', 'WATER'].each { elementType ->
                if (playerHasElementSymbol(managedPlayer, elementType)) {
                    def progress = puzzleStates[elementType]
                    if (progress?.hasSymbol) {
                        tradeableItems.completedSolutions.add([
                            elementType: elementType,
                            description: "Complete ${elementType.toLowerCase()} puzzle solution",
                            includes: "Variable location, execution process, nonce, and flag",
                            tradeValue: calculateCompleteSolutionValue(elementType),
                            canTrade: true
                        ])
                    }
                }
            }
        }
        
        return tradeableItems
    }
    
    def getDesiredPuzzleKnowledge(LambdaPlayer player) {
        def desiredItems = [
            missingFragments: [],
            neededVariables: [],
            neededNonces: [],
            incompleteSolutions: []
        ]
        
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (!managedPlayer) return desiredItems
            
            def session = puzzleRandomizationService.getCurrentGameSession()
            if (!session) return desiredItems
            
            // Get what the player still needs
            def puzzleStates = competitivePuzzleService.getPlayerPuzzleProgress(
                managedPlayer, session.sessionId, managedPlayer.currentMatrixLevel
            )
            
            // MISSING FRAGMENTS - Player might want fragments for elements they haven't solved
            def allFragmentTypes = ['CALCULATOR', 'DECODER', 'LOCATOR', 'VALIDATOR']
            def playerFragmentTypes = managedPlayer.puzzleLogicFragments?.collect { it.fragmentType } ?: []
            
            allFragmentTypes.each { type ->
                if (!playerFragmentTypes.contains(type)) {
                    desiredItems.missingFragments.add([
                        type: type,
                        description: "Any ${type.toLowerCase()} puzzle fragment",
                        maxWillPay: 50 + (managedPlayer.bits * 0.1).intValue()
                    ])
                }
            }
            
            // NEEDED VARIABLES - For elements they haven't completed
            ['AIR', 'FIRE', 'EARTH', 'WATER'].each { elementType ->
                def progress = puzzleStates[elementType]
                if (progress && !progress.hasSymbol && !progress.hasVariable) {
                    desiredItems.neededVariables.add([
                        elementType: elementType,
                        description: "Variables for ${elementType.toLowerCase()} puzzle",
                        maxWillPay: 75 + (managedPlayer.bits * 0.15).intValue()
                    ])
                }
            }
            
            // NEEDED NONCES - For elements they haven't completed
            ['AIR', 'FIRE', 'EARTH', 'WATER'].each { elementType ->
                if (!playerHasElementSymbol(managedPlayer, elementType)) {
                    desiredItems.neededNonces.add([
                        elementType: elementType,
                        description: "Nonce and flag for ${elementType.toLowerCase()} puzzle room",
                        maxWillPay: 100 + (managedPlayer.bits * 0.2).intValue()
                    ])
                }
            }
            
            // INCOMPLETE SOLUTIONS - Complete solutions for elements they haven't solved
            ['AIR', 'FIRE', 'EARTH', 'WATER'].each { elementType ->
                if (!playerHasElementSymbol(managedPlayer, elementType)) {
                    desiredItems.incompleteSolutions.add([
                        elementType: elementType,
                        description: "Complete ${elementType.toLowerCase()} puzzle solution",
                        maxWillPay: 200 + (managedPlayer.bits * 0.3).intValue()
                    ])
                }
            }
        }
        
        return desiredItems
    }
    
    def executePuzzleKnowledgeTrade(LambdaPlayer seller, LambdaPlayer buyer, String itemType, String itemId, Integer price) {
        def result = [success: false, message: '']
        
        // Validate trade
        if (buyer.bits < price) {
            result.message = "Buyer doesn't have enough bits (${buyer.bits} < ${price})"
            return result
        }
        
        LambdaPlayer.withTransaction {
            def managedSeller = LambdaPlayer.get(seller.id)
            def managedBuyer = LambdaPlayer.get(buyer.id)
            
            if (!managedSeller || !managedBuyer) {
                result.message = "Player not found"
                return result
            }
            
            def tradeableItems = getTradeablePuzzleKnowledge(managedSeller)
            def transferredItem = null
            
            switch (itemType) {
                case 'puzzle_fragment':
                    transferredItem = executePuzzleFragmentTrade(managedSeller, managedBuyer, itemId, price, tradeableItems)
                    break
                case 'variable':
                    transferredItem = executeVariableTrade(managedSeller, managedBuyer, itemId, price, tradeableItems)
                    break
                case 'nonce':
                    transferredItem = executeNonceTrade(managedSeller, managedBuyer, itemId, price, tradeableItems)
                    break
                case 'complete_solution':
                    transferredItem = executeCompleteSolutionTrade(managedSeller, managedBuyer, itemId, price, tradeableItems)
                    break
                default:
                    result.message = "Invalid item type: ${itemType}"
                    return result
            }
            
            if (transferredItem) {
                // Transfer bits
                managedSeller.bits += price
                managedBuyer.bits -= price
                
                managedSeller.save(failOnError: true)
                managedBuyer.save(failOnError: true)
                
                result.success = true
                result.message = "Trade completed: ${transferredItem.name} for ${price} bits"
                result.transferredItem = transferredItem
            } else {
                result.message = "Trade failed - item not available or not tradeable"
            }
        }
        
        return result
    }
    
    private def executePuzzleFragmentTrade(LambdaPlayer seller, LambdaPlayer buyer, String itemId, Integer price, def tradeableItems) {
        def fragmentItem = tradeableItems.puzzleFragments.find { it.id.toString() == itemId }
        if (!fragmentItem || !fragmentItem.canTrade) return null
        
        def originalFragment = PuzzleLogicFragment.get(fragmentItem.id)
        if (!originalFragment) return null
        
        // Create copy for buyer
        def newFragment = new PuzzleLogicFragment(
            name: originalFragment.name,
            description: originalFragment.description,
            fragmentType: originalFragment.fragmentType,
            powerLevel: originalFragment.powerLevel,
            functionCode: originalFragment.functionCode,
            requiredVariable: originalFragment.requiredVariable,
            expectedOutput: originalFragment.expectedOutput,
            owner: buyer
        )
        newFragment.save(failOnError: true)
        
        buyer.addToPuzzleLogicFragments(newFragment)
        buyer.save(failOnError: true)
        
        return [name: originalFragment.name, type: 'Puzzle Fragment']
    }
    
    private def executeVariableTrade(LambdaPlayer seller, LambdaPlayer buyer, String itemId, Integer price, def tradeableItems) {
        def variableItem = tradeableItems.variables.find { it.id.toString() == itemId }
        if (!variableItem || !variableItem.canTrade) return null
        
        def originalVariable = HiddenVariable.get(variableItem.id)
        if (!originalVariable) return null
        
        // Create copy for buyer (they can use the same variable data)
        buyer.addToCollectedVariables(originalVariable)
        buyer.save(failOnError: true)
        
        return [name: originalVariable.variableName, type: 'Variable']
    }
    
    private def executeNonceTrade(LambdaPlayer seller, LambdaPlayer buyer, String itemId, Integer price, def tradeableItems) {
        def nonceItem = tradeableItems.nonces.find { it.id.toString() == itemId }
        if (!nonceItem || !nonceItem.canTrade) return null
        
        def originalNonce = ElementalNonce.get(nonceItem.id)
        if (!originalNonce) return null
        
        // Add nonce to buyer's collection
        buyer.addToDiscoveredNonces(originalNonce)
        buyer.save(failOnError: true)
        
        return [name: originalNonce.nonceName, type: 'Elemental Nonce']
    }
    
    private def executeCompleteSolutionTrade(LambdaPlayer seller, LambdaPlayer buyer, String elementType, Integer price, def tradeableItems) {
        def solutionItem = tradeableItems.completedSolutions.find { it.elementType == elementType }
        if (!solutionItem || !solutionItem.canTrade) return null
        
        def session = puzzleRandomizationService.getCurrentGameSession()
        if (!session) return null
        
        // Give buyer everything they need for this element
        def sessionVariables = puzzleRandomizationService.getSessionVariables(session.sessionId, buyer.currentMatrixLevel)
        def sessionNonces = puzzleRandomizationService.getSessionNonces(session.sessionId, buyer.currentMatrixLevel)
        
        // Add element-specific variable and nonce
        def elementVariable = sessionVariables.find { it.elementType == elementType }
        def elementNonce = sessionNonces.find { it.elementType == elementType }
        
        if (elementVariable) {
            buyer.addToCollectedVariables(elementVariable)
        }
        
        if (elementNonce) {
            buyer.addToDiscoveredNonces(elementNonce)
        }
        
        // Also give them a puzzle fragment that works with this element
        def sellerFragments = seller.puzzleLogicFragments?.findAll { 
            getElementHintFromFragment(it.name) == elementType 
        }
        
        if (sellerFragments) {
            def fragmentToCopy = sellerFragments.first()
            def newFragment = new PuzzleLogicFragment(
                name: fragmentToCopy.name,
                description: fragmentToCopy.description,
                fragmentType: fragmentToCopy.fragmentType,
                powerLevel: fragmentToCopy.powerLevel,
                functionCode: fragmentToCopy.functionCode,
                requiredVariable: fragmentToCopy.requiredVariable,
                expectedOutput: fragmentToCopy.expectedOutput,
                owner: buyer
            )
            newFragment.save(failOnError: true)
            buyer.addToPuzzleLogicFragments(newFragment)
        }
        
        buyer.save(failOnError: true)
        
        return [name: "Complete ${elementType} Solution", type: 'Complete Solution']
    }
    
    private Boolean playerHasElementSymbol(LambdaPlayer player, String elementType) {
        switch (elementType) {
            case 'AIR': return player.hasAirSymbol
            case 'FIRE': return player.hasFireSymbol
            case 'EARTH': return player.hasEarthSymbol
            case 'WATER': return player.hasWaterSymbol
            default: return false
        }
    }
    
    private String getElementHintFromFragment(String fragmentName) {
        if (fragmentName.toLowerCase().contains('atmospheric') || fragmentName.toLowerCase().contains('air')) {
            return 'AIR'
        } else if (fragmentName.toLowerCase().contains('thermal') || fragmentName.toLowerCase().contains('fire')) {
            return 'FIRE'
        } else if (fragmentName.toLowerCase().contains('geological') || fragmentName.toLowerCase().contains('earth')) {
            return 'EARTH'
        } else if (fragmentName.toLowerCase().contains('hydro') || fragmentName.toLowerCase().contains('water')) {
            return 'WATER'
        }
        return 'UNKNOWN'
    }
    
    private String getElementHintFromVariable(HiddenVariable variable) {
        def varName = variable.variableName.toLowerCase()
        if (varName.contains('pressure') || varName.contains('atmospheric') || varName.contains('air')) {
            return 'AIR'
        } else if (varName.contains('thermal') || varName.contains('heat') || varName.contains('fire')) {
            return 'FIRE'
        } else if (varName.contains('mineral') || varName.contains('geological') || varName.contains('earth')) {
            return 'EARTH'
        } else if (varName.contains('h2o') || varName.contains('molecular') || varName.contains('water')) {
            return 'WATER'
        }
        return 'UNKNOWN'
    }
    
    private Integer calculateFragmentTradeValue(PuzzleLogicFragment fragment) {
        return 25 + (fragment.powerLevel * 10) // Base 25 + 10 per power level
    }
    
    private Integer calculateVariableTradeValue(HiddenVariable variable) {
        switch (variable.variableType) {
            case 'ENCODED': return 75 // Encoded variables are more valuable
            case 'NUMERIC': return 50
            case 'STRING': return 40
            default: return 30
        }
    }
    
    private Integer calculateNonceTradeValue(ElementalNonce nonce, Boolean sellerHasSymbol) {
        def baseValue = 100
        if (sellerHasSymbol) {
            return baseValue // Full value since seller doesn't need it
        } else {
            return baseValue * 2 // Premium if seller still needs it (unlikely to trade)
        }
    }
    
    private Integer calculateCompleteSolutionValue(String elementType) {
        return 250 // High value for complete solutions
    }
    
    def formatTradeablePuzzleKnowledge(LambdaPlayer player) {
        def tradeableItems = getTradeablePuzzleKnowledge(player)
        def output = new StringBuilder()
        
        output.append("=== 🧩 TRADEABLE PUZZLE KNOWLEDGE ===\n\n")
        
        // Puzzle Fragments
        if (tradeableItems.puzzleFragments.size() > 0) {
            output.append("📚 PUZZLE FRAGMENTS:\n")
            tradeableItems.puzzleFragments.eachWithIndex { item, index ->
                output.append("${index + 1}. ${item.name} (${item.type} L${item.powerLevel})\n")
                output.append("   Element: ${item.elementHint} | Value: ${item.tradeValue} bits\n")
                output.append("   ${item.description}\n\n")
            }
        }
        
        // Variables
        if (tradeableItems.variables.size() > 0) {
            output.append("📦 COLLECTED VARIABLES:\n")
            tradeableItems.variables.eachWithIndex { item, index ->
                output.append("${index + 1}. ${item.name} (${item.type})\n")
                output.append("   Element: ${item.elementHint} | Value: ${item.tradeValue} bits\n")
                output.append("   ${item.description}\n\n")
            }
        }
        
        // Nonces
        if (tradeableItems.nonces.size() > 0) {
            output.append("🔑 ELEMENTAL NONCES:\n")
            tradeableItems.nonces.eachWithIndex { item, index ->
                def status = item.canTrade ? "TRADEABLE" : "NEEDED"
                output.append("${index + 1}. ${item.name} (${item.elementType}) - ${status}\n")
                output.append("   Chemical Clue: ${item.chemicalClue}\n")
                output.append("   Flag: ${item.commandFlag} | Value: ${item.tradeValue} bits\n")
                output.append("   ${item.reason}\n\n")
            }
        }
        
        // Complete Solutions
        if (tradeableItems.completedSolutions.size() > 0) {
            output.append("🏆 COMPLETE SOLUTIONS:\n")
            tradeableItems.completedSolutions.eachWithIndex { item, index ->
                output.append("${index + 1}. ${item.description}\n")
                output.append("   Includes: ${item.includes}\n")
                output.append("   Value: ${item.tradeValue} bits\n\n")
            }
        }
        
        if (tradeableItems.puzzleFragments.size() == 0 && 
            tradeableItems.variables.size() == 0 && 
            tradeableItems.nonces.size() == 0 && 
            tradeableItems.completedSolutions.size() == 0) {
            output.append("No puzzle knowledge available for trade.\n")
            output.append("Complete puzzles to unlock tradeable knowledge!\n")
        }
        
        output.append("\n💡 Use 'trade <player>' in mingle chamber to negotiate trades!")
        
        return output.toString()
    }
}