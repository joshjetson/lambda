package ysap

import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic

@Transactional
class PuzzleService {
    
    def puzzleRandomizationService
    def competitivePuzzleService
    def audioService
    
    def initializePuzzleSystem() {
        // Initialize base puzzle logic fragments (these are reusable across all sessions)
        initializePuzzleLogicFragments()
        
        // Initialize randomized game session with dynamic puzzle elements
        puzzleRandomizationService.initializeDefaultGameSession()
        
        println "✅ Puzzle system initialized with randomized elements and competitive mechanics"
    }
    
    private def initializePuzzleLogicFragments() {
        // Create special executable logic fragments for each element type
        if (PuzzleLogicFragment.count() == 0) {
            
            // AIR CALCULATOR - Processes atmospheric data to find coordinates
            def airCalculator = new PuzzleLogicFragment(
                name: "Atmospheric Processor",
                description: "Analyzes atmospheric pressure readings to calculate elemental coordinates",
                fragmentType: "CALCULATOR",
                powerLevel: 7,
                requiredVariable: "pressure_data",
                functionCode: '''
                def processAtmosphericData(pressure_data) {
                    def readings = pressure_data.split(',').collect { Double.parseDouble(it) }
                    def x = Math.round(readings.sum() / readings.size()).intValue() % 10
                    def y = Math.round(readings.max() - readings.min()).intValue() % 10
                    return "coordinates:${x},${y}"
                }
                return processAtmosphericData(pressure_data)
                ''',
                expectedOutput: "coordinates pattern"
            )
            airCalculator.save(failOnError: true)
            
            // FIRE DECODER - Decodes thermal signatures
            def fireDecoder = new PuzzleLogicFragment(
                name: "Thermal Signature Decoder",
                description: "Decodes thermal patterns to reveal hidden fire element locations",
                fragmentType: "DECODER", 
                powerLevel: 8,
                requiredVariable: "thermal_hex",
                functionCode: '''
                def decodeThermalHex(thermal_hex) {
                    def decoded = new String(thermal_hex.decodeBase64())
                    def coords = decoded.split(':')
                    if (coords.size() >= 2) {
                        def x = Integer.parseInt(coords[0]) % 10
                        def y = Integer.parseInt(coords[1]) % 10
                        return "fire_coordinates:${x},${y}"
                    }
                    return "decoding_failed"
                }
                return decodeThermalHex(thermal_hex)
                ''',
                expectedOutput: "fire_coordinates pattern"
            )
            fireDecoder.save(failOnError: true)
            
            // EARTH LOCATOR - Uses geological data
            def earthLocator = new PuzzleLogicFragment(
                name: "Geological Survey Tool",
                description: "Processes mineral density readings to locate earth element sources",
                fragmentType: "LOCATOR",
                powerLevel: 6,
                requiredVariable: "mineral_density",
                functionCode: '''
                def locateEarthElement(mineral_density) {
                    def density = Double.parseDouble(mineral_density)
                    def x = Math.round(density * 3.7).intValue() % 10
                    def y = Math.round(density * 2.3).intValue() % 10
                    return "earth_location:${x},${y}:confidence_high"
                }
                return locateEarthElement(mineral_density)
                ''',
                expectedOutput: "earth_location pattern"
            )
            earthLocator.save(failOnError: true)
            
            // WATER VALIDATOR - Validates H2O signatures
            def waterValidator = new PuzzleLogicFragment(
                name: "Hydro-Chemical Validator",
                description: "Validates molecular signatures to confirm water element presence",
                fragmentType: "VALIDATOR",
                powerLevel: 9,
                requiredVariable: "h2o_signature",
                functionCode: '''
                def validateWaterSignature(h2o_signature) {
                    def parts = h2o_signature.split('_')
                    if (parts.size() >= 3) {
                        def x = (parts[0].length() + parts[1].length()) % 10
                        def y = (parts[2].hashCode().abs()) % 10
                        return "water_validated:${x},${y}:purity_confirmed"
                    }
                    return "validation_failed:invalid_signature"
                }
                return validateWaterSignature(h2o_signature)
                ''',
                expectedOutput: "water_validated pattern"
            )
            waterValidator.save(failOnError: true)
            
            println "✅ Initialized ${PuzzleLogicFragment.count()} puzzle logic fragments"
        }
    }
    
    // NOTE: Elemental nonces, variables, and puzzle rooms are now generated dynamically
    // by PuzzleRandomizationService per game session and map. No static initialization needed.
    
    // Scan for puzzle elements at coordinates (now competitive and player-specific)
    def scanForPuzzleElements(LambdaPlayer player, Integer matrixLevel, Integer x, Integer y) {
        def session = puzzleRandomizationService.getCurrentGameSession()
        if (!session) {
            return []
        }
        
        // Initialize player's competitive puzzle state if needed
        competitivePuzzleService.initializePlayerPuzzleState(player, session.sessionId, matrixLevel)
        
        // Get player-specific puzzle elements (different coordinates per player)
        return competitivePuzzleService.getPlayerSpecificPuzzleElements(
            player, session.sessionId, matrixLevel, x, y
        )
    }
    
    // Collect hidden variable (now competitive and player-specific)
    def collectVariable(LambdaPlayer player, String variableName, Integer matrixLevel, Integer x, Integer y) {
        def session = puzzleRandomizationService.getCurrentGameSession()
        
        if (!session) {
            return [success: false, message: "No active game session found."]
        }
        
        // Use competitive service for player-specific variable collection
        return competitivePuzzleService.collectPlayerVariable(
            player, variableName, session.sessionId, matrixLevel, x, y
        )
    }
    
    // Execute puzzle logic fragment
    def executePuzzleFragment(LambdaPlayer player, String fragmentName, String inputVariable = null) {
        def result = [success: false, message: '', output: '']
        
        PuzzleLogicFragment.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            def fragment = managedPlayer?.puzzleLogicFragments?.find { it.name == fragmentName }
            
            if (!fragment) {
                result.message = "Puzzle fragment '${fragmentName}' not found in your collection."
                return result
            }
            
            // Check if fragment requires a variable
            if (fragment.requiresVariable() && !inputVariable) {
                result.message = "This fragment requires variable: ${fragment.requiredVariable}"
                return result
            }
            
            // Find the variable in player's collection if needed
            def variableValue = inputVariable
            if (fragment.requiresVariable()) {
                def playerVariable = managedPlayer.collectedVariables?.find { 
                    it.variableName == fragment.requiredVariable 
                }
                if (!playerVariable) {
                    result.message = "You don't have the required variable: ${fragment.requiredVariable}"
                    return result
                }
                variableValue = playerVariable.variableValue
            }
            
            try {
                // Execute the fragment's code
                def binding = new Binding()
                if (variableValue) {
                    binding.setVariable(fragment.requiredVariable, variableValue)
                }
                
                def shell = new GroovyShell(binding)
                def output = shell.evaluate(fragment.functionCode)
                
                // Record execution
                def execution = new PuzzleExecution(
                    executionId: "EXE${System.currentTimeMillis()}",
                    executedBy: managedPlayer.username,
                    fragmentName: fragmentName,
                    inputVariable: variableValue,
                    outputResult: output.toString(),
                    wasSuccessful: true,
                    matrixLevel: managedPlayer.currentMatrixLevel,
                    positionX: managedPlayer.positionX,
                    positionY: managedPlayer.positionY,
                    player: managedPlayer,
                    fragment: fragment
                )
                execution.save(failOnError: true)
                
                managedPlayer.addToExecutionHistory(execution)
                managedPlayer.save(failOnError: true)
                
                result.success = true
                result.message = "✅ Fragment executed successfully!"
                result.output = output.toString()
                result.execution = execution
                
                // Check if this output contains coordinates and record for competitive system
                if (output.toString().contains(':')) {
                    def session = puzzleRandomizationService.getCurrentGameSession()
                    if (session) {
                        // Try to determine element type from fragment name
                        def elementType = determineElementTypeFromFragment(fragmentName)
                        if (elementType) {
                            competitivePuzzleService.recordPlayerCoordinateCalculation(
                                managedPlayer, session.sessionId, managedPlayer.currentMatrixLevel, 
                                elementType, output.toString()
                            )
                        }
                    }
                }
                
            } catch (Exception e) {
                // Record failed execution
                def execution = new PuzzleExecution(
                    executionId: "EXE${System.currentTimeMillis()}",
                    executedBy: managedPlayer.username,
                    fragmentName: fragmentName,
                    inputVariable: variableValue,
                    wasSuccessful: false,
                    errorMessage: e.message,
                    matrixLevel: managedPlayer.currentMatrixLevel,
                    positionX: managedPlayer.positionX,
                    positionY: managedPlayer.positionY,
                    player: managedPlayer,
                    fragment: fragment
                )
                execution.save(failOnError: true)
                
                result.message = "❌ Execution failed: ${e.message}"
            }
        }
        
        return result
    }
    
    private String determineElementTypeFromFragment(String fragmentName) {
        if (fragmentName.toLowerCase().contains('atmospheric') || fragmentName.toLowerCase().contains('air')) {
            return 'AIR'
        } else if (fragmentName.toLowerCase().contains('thermal') || fragmentName.toLowerCase().contains('fire')) {
            return 'FIRE'
        } else if (fragmentName.toLowerCase().contains('geological') || fragmentName.toLowerCase().contains('earth')) {
            return 'EARTH'
        } else if (fragmentName.toLowerCase().contains('hydro') || fragmentName.toLowerCase().contains('water')) {
            return 'WATER'
        }
        return null
    }
    
    // Execute puzzle room file (now competitive with coordinate shifting)
    def executePuzzleRoom(LambdaPlayer player, String flag, String nonce, String filename) {
        def session = puzzleRandomizationService.getCurrentGameSession()
        
        if (!session) {
            return [success: false, message: "No active game session found."]
        }
        
        // Use competitive service which handles coordinate shifting
        return competitivePuzzleService.executePlayerPuzzleRoom(
            player, session.sessionId, player.currentMatrixLevel, flag, nonce, filename,
            player.positionX, player.positionY
        )
    }
    
    // Get player's puzzle inventory
    def getPlayerPuzzleInventory(LambdaPlayer player) {
        def inventory = [
            puzzleFragments: [],
            variables: [],
            nonces: [],
            recentExecutions: []
        ]
        
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            
            // Puzzle fragments
            managedPlayer.puzzleLogicFragments?.each { fragment ->
                inventory.puzzleFragments.add([
                    name: fragment.name,
                    type: fragment.fragmentType,
                    powerLevel: fragment.powerLevel,
                    requiresVariable: fragment.requiresVariable(),
                    hint: fragment.getExecutionHint()
                ])
            }
            
            // Variables
            managedPlayer.collectedVariables?.each { variable ->
                inventory.variables.add([
                    name: variable.variableName,
                    value: variable.getDisplayValue(),
                    type: variable.variableType,
                    hint: variable.getUsageHint()
                ])
            }
            
            // Nonces
            managedPlayer.discoveredNonces?.each { nonce ->
                inventory.nonces.add([
                    name: nonce.nonceName,
                    element: nonce.elementType,
                    clue: nonce.getChemicalHint(),
                    flag: nonce.commandFlag
                ])
            }
            
            // Recent executions
            def recentExecutions = managedPlayer.executionHistory?.findAll { 
                it.isRecentExecution() 
            }?.sort { -it.executionDate.time }?.take(5)
            
            recentExecutions?.each { execution ->
                inventory.recentExecutions.add([
                    summary: execution.getExecutionSummary(),
                    details: execution.getDetailedResult()
                ])
            }
        }
        
        return inventory
    }
    
    // Award puzzle logic fragment to player
    def awardPuzzleFragment(LambdaPlayer player, String fragmentName) {
        def result = [success: false, message: '']
        
        PuzzleLogicFragment.withTransaction {
            def fragment = PuzzleLogicFragment.findByName(fragmentName)
            if (!fragment) {
                result.message = "Puzzle fragment '${fragmentName}' not found."
                return result
            }
            
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                // Check if player already has this fragment
                def hasFragment = managedPlayer.puzzleLogicFragments?.any { it.name == fragmentName }
                if (hasFragment) {
                    result.message = "You already possess the '${fragmentName}' fragment."
                    return result
                }
                
                // Create a copy for the player
                def playerFragment = new PuzzleLogicFragment(
                    name: fragment.name,
                    description: fragment.description,
                    fragmentType: fragment.fragmentType,
                    powerLevel: fragment.powerLevel,
                    functionCode: fragment.functionCode,
                    requiredVariable: fragment.requiredVariable,
                    expectedOutput: fragment.expectedOutput,
                    owner: managedPlayer
                )
                playerFragment.save(failOnError: true)
                
                managedPlayer.addToPuzzleLogicFragments(playerFragment)
                managedPlayer.save(failOnError: true)
                
                result.success = true
                result.message = "🧩 Puzzle fragment acquired: ${fragment.name}"
            }
        }
        
        return result
    }
    
    // Award nonce to player
    def awardNonce(LambdaPlayer player, String nonceName) {
        def result = [success: false, message: '']
        
        ElementalNonce.withTransaction {
            def nonce = ElementalNonce.findByNonceName(nonceName)
            if (!nonce) {
                result.message = "Nonce '${nonceName}' not found."
                return result
            }
            
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                // Check if player already has this nonce
                def hasNonce = managedPlayer.discoveredNonces?.any { it.nonceName == nonceName }
                if (hasNonce) {
                    result.message = "You already possess the '${nonceName}' nonce."
                    return result
                }
                
                // Mark nonce as discovered and add to player
                nonce.isDiscovered = true
                nonce.discoveredDate = new Date()
                nonce.save(failOnError: true)
                
                managedPlayer.addToDiscoveredNonces(nonce)
                managedPlayer.save(failOnError: true)
                
                result.success = true
                result.message = "🔑 Elemental nonce discovered: ${nonce.nonceName}"
                result.nonce = nonce
            }
        }
        
        return result
    }
    
    // Get puzzle elements at player's current location
    def getPlayerPuzzleElementsAtLocation(LambdaPlayer player, Integer x, Integer y) {
        def gameSessionId = "default_session" // Use default session for now
        def mapNumber = player.currentMatrixLevel
        
        return competitivePuzzleService.getPlayerSpecificPuzzleElements(player, gameSessionId, mapNumber, x, y)
    }
    
    // Make a puzzle room file executable
    def makeFileExecutable(LambdaPlayer player, String filename) {
        def result = [success: false, message: '']
        
        PuzzleRoom.withTransaction {
            // Get puzzle rooms at current location
            def puzzleElements = getPlayerPuzzleElementsAtLocation(player, player.positionX, player.positionY)
            def puzzleRooms = puzzleElements.findAll { it.type == 'player_puzzle_room' }
            
            def targetRoom = puzzleRooms.find { it.data.fileName == filename }
            
            if (!targetRoom) {
                result.message = "No file named '${filename}' found at current location."
                return result
            }
            
            def puzzleRoom = targetRoom.data
            
            if (puzzleRoom.isExecutable) {
                result.message = "File '${filename}' is already executable."
                return result
            }
            
            // Make the file executable
            puzzleRoom.makeExecutable()
            puzzleRoom.save(failOnError: true)
            
            result.success = true
            result.message = "File '${filename}' made executable."
        }
        
        return result
    }

    // ===== COLLECT VARIABLE COMMAND (moved from TelnetServerService) =====

    String handleCollectVariableCommand(String variableName, LambdaPlayer player) {
        
        def result = this.collectVariable(player, variableName, player.currentMatrixLevel, player.positionX, player.positionY)
        if (result.success) {
            audioService.playSound("item_found")
            return "${TerminalFormatter.formatText('✅ VARIABLE COLLECTED!', 'bold', 'green')}\r\n${result.message}\r\n${result.variable?.getUsageHint() ?: ''}"
        } else {
            return "${TerminalFormatter.formatText('❌ Collection Failed', 'bold', 'red')}\r\n${result.message}"
        }
    }
}