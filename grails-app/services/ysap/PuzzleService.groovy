package ysap

import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic

@Transactional
class PuzzleService {
    
    def puzzleRandomizationService
    def competitivePuzzleService
    def audioService
    def puzzleKnowledgeTradingService
    
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

    // ===== EXECUTE COMMAND HANDLER (moved from TelnetServerService) =====

    String handleExecuteCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(' ')
        
        // Check if this is a puzzle room execution: execute --flag nonce filename
        if (parts.length == 4 && parts[1].startsWith('--')) {
            def flag = parts[1]
            def nonce = parts[2]
            def filename = parts[3]
            
            // Check if file exists and is executable at current location
            def puzzleElements = this.getPlayerPuzzleElementsAtLocation(player, player.positionX, player.positionY)
            def puzzleRooms = puzzleElements.findAll { it.type == 'player_puzzle_room' }
            def targetRoom = puzzleRooms.find { it.data.fileName == filename }
            
            if (!targetRoom) {
                audioService.playSound("error_sound")
                return "${TerminalFormatter.formatText('❌ File Not Found', 'bold', 'red')}\r\nNo file named '${filename}' at current location.\r\nUse 'ls' to see available files."
            }
            
            if (!targetRoom.data.isExecutable) {
                audioService.playSound("error_sound")
                return """${TerminalFormatter.formatText('❌ Permission Denied', 'bold', 'red')}
File '${filename}' is not executable.\r\n\r\n${TerminalFormatter.formatText('💡 SOLUTION:', 'bold', 'yellow')} Make the file executable first:\r\nchmod +x ${filename}\r\n\r\nThen retry your execute command."""
            }
            
            def result = this.executePuzzleRoom(player, flag, nonce, filename)
            if (result.success) {
                audioService.playSound("victory_chord")
                def response = new StringBuilder()
                response.append("${TerminalFormatter.formatText('🏆 PUZZLE SOLVED!', 'bold', 'green')}\r\n")
                response.append("${result.message}\r\n")
                
                if (result.triggerShift) {
                    response.append("\r\n${TerminalFormatter.formatText('⚡ COORDINATE SHIFT TRIGGERED!', 'bold', 'yellow')}\r\n")
                    response.append("${TerminalFormatter.formatText('Other players\' coordinates have been randomized!', 'bold', 'cyan')}\r\n")
                    response.append("${TerminalFormatter.formatText('Competition intensifies! 🔥', 'italic', 'red')}\r\n")
                }
                
                return response.toString()
            } else {
                audioService.playSound("error_sound")
                return "${TerminalFormatter.formatText('❌ Execution Failed', 'bold', 'red')}\r\n${result.message}"
            }
        }
        
        // Check if this is a logic fragment execution: execute <fragment_name> [variable_value]
        if (parts.length >= 2) {
            def fragmentName = parts[1]
            def inputVariable = parts.length > 2 ? parts[2] : null
            
            def result = this.executePuzzleFragment(player, fragmentName, inputVariable)
            if (result.success) {
                audioService.playSound("fragment_pickup")
                def output = new StringBuilder()
                output.append("${TerminalFormatter.formatText('🧩 FRAGMENT EXECUTED!', 'bold', 'cyan')}\r\n")
                output.append("${result.message}\r\n")
                output.append("${TerminalFormatter.formatText('OUTPUT:', 'bold', 'white')} ${result.output}\r\n")
                
                // Check if output contains coordinates
                if (result.output?.contains(':')) {
                    output.append("${TerminalFormatter.formatText('💡 TIP:', 'bold', 'yellow')} Output may contain coordinate information!\r\n")
                }
                
                return output.toString()
            } else {
                return "${TerminalFormatter.formatText('❌ Execution Failed', 'bold', 'red')}\r\n${result.message}"
            }
        }
        
        return """${TerminalFormatter.formatText('EXECUTE COMMAND USAGE:', 'bold', 'cyan')}\r\n\r\n${TerminalFormatter.formatText('Puzzle Room Execution:', 'bold', 'white')}\r\n  execute --<flag> <nonce> <filename>\r\n  Example: execute --electrical 7e4a92f1b3d5c8e9 electrical_unlock.py\r\n\r\n${TerminalFormatter.formatText('Logic Fragment Execution:', 'bold', 'white')}\r\n  execute <fragment_name> [variable_value]\r\n  Example: execute "Electrical Analyzer" 12.0,5.0,3.3\r\n\r\n${TerminalFormatter.formatText('💡 TIP:', 'bold', 'yellow')} Use 'pinv' to see your puzzle inventory"""
    }

    // ===== PUZZLE INVENTORY COMMAND HANDLER (moved from TelnetServerService) =====

    String showPuzzleInventory(LambdaPlayer player) {
        def inventory = this.getPlayerPuzzleInventory(player)
        def output = new StringBuilder()
        
        output.append(TerminalFormatter.formatText("=== 🧩 PUZZLE INVENTORY ===", 'bold', 'cyan')).append('\r\n\r\n')
        
        // Puzzle Logic Fragments
        output.append(TerminalFormatter.formatText("PUZZLE LOGIC FRAGMENTS:", 'bold', 'purple')).append('\r\n')
        if (inventory.puzzleFragments.size() > 0) {
            inventory.puzzleFragments.each { fragment ->
                output.append("  🧩 ${fragment.name} (${fragment.type} Level ${fragment.powerLevel})\r\n")
                output.append("     ${fragment.hint}\r\n")
            }
        } else {
            output.append("  No puzzle fragments acquired\r\n")
        }
        
        output.append('\r\n')
        
        // Hidden Variables
        output.append(TerminalFormatter.formatText("COLLECTED VARIABLES:", 'bold', 'green')).append('\r\n')
        if (inventory.variables.size() > 0) {
            inventory.variables.each { variable ->
                output.append("  📦 ${variable.name} = ${variable.value} (${variable.type})\r\n")
                output.append("     ${variable.hint}\r\n")
            }
        } else {
            output.append("  No variables collected\r\n")
        }
        
        output.append('\r\n')
        
        // Elemental Nonces
        output.append(TerminalFormatter.formatText("DISCOVERED NONCES:", 'bold', 'yellow')).append('\r\n')
        if (inventory.nonces.size() > 0) {
            inventory.nonces.each { nonce ->
                output.append("  🔑 ${nonce.name} (${nonce.element})\r\n")
                output.append("     ${nonce.clue}\r\n")
                output.append("     Flag: ${nonce.flag}\r\n")
            }
        } else {
            output.append("  No nonces discovered\r\n")
        }
        
        // Recent Executions
        if (inventory.recentExecutions.size() > 0) {
            output.append('\r\n')
            output.append(TerminalFormatter.formatText("RECENT EXECUTIONS:", 'bold', 'white')).append('\r\n')
            inventory.recentExecutions.each { execution ->
                output.append("  ⚡ ${execution.summary}\r\n")
            }
        }
        
        output.append('\r\n')
        output.append(TerminalFormatter.formatText("💡 COMMANDS:", 'bold', 'cyan')).append('\r\n')
        output.append("  execute <fragment> [variable] - Execute puzzle logic\r\n")
        output.append("  execute --<flag> <nonce> <file> - Unlock elemental symbols\r\n")
        output.append("  collect_var <name> - Collect hidden variables\r\n")
        output.append("  pprog - Show competitive puzzle progress\r\n")
        output.append("  pmarket - Show tradeable puzzle knowledge\r\n")
        output.append("  scan - Detect puzzle elements at your location\r\n")
        
        return output.toString()
    }

    // ===== PUZZLE PROGRESS COMMAND HANDLER (moved from TelnetServerService) =====

    String showCompetitivePuzzleProgress(LambdaPlayer player) {
        def session = puzzleRandomizationService.getCurrentGameSession()
        if (!session) {
            return "No active game session found.\r\n"
        }
        
        return competitivePuzzleService.formatPlayerProgressDisplay(
            player, session.sessionId, player.currentMatrixLevel
        )
    }

    // ===== PUZZLE MARKET COMMAND HANDLER (moved from TelnetServerService) =====

    String showPuzzleKnowledgeMarket(LambdaPlayer player) {
        return puzzleKnowledgeTradingService.formatTradeablePuzzleKnowledge(player)
    }
    
    /**
     * Handles chmod command to make puzzle room files executable
     * @param command The full chmod command (e.g., "chmod +x filename.py")
     * @param player The lambda player
     * @return Formatted response with success/error message and proper telnet line endings
     */
    String handleChmodCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(' ')
        
        if (parts.length != 3 || parts[1] != '+x') {
            return """${TerminalFormatter.formatText('CHMOD USAGE:', 'bold', 'cyan')}\r\n""" +
                   """chmod +x <filename>\r\n""" +
                   """${TerminalFormatter.formatText('EXAMPLES:', 'bold', 'white')}\r\n""" +
                   """chmod +x air_unlock_3.py\r\n""" +
                   """chmod +x fire_chamber.py\r\n""" +
                   """${TerminalFormatter.formatText('NOTE:', 'bold', 'yellow')} Only puzzle room files can be made executable.\r\n"""
        }
        
        def filename = parts[2]
        
        // Get puzzle rooms at current location
        def puzzleElements = getPlayerPuzzleElementsAtLocation(player, player.positionX, player.positionY)
        def puzzleRooms = puzzleElements.findAll { it.type == 'player_puzzle_room' }
        
        def targetRoom = puzzleRooms.find { it.data.fileName == filename }
        
        if (!targetRoom) {
            return """${TerminalFormatter.formatText('❌ File Not Found', 'bold', 'red')}\r\n""" +
                   """No file named '${filename}' at current location.\r\n""" +
                   """Use 'ls' to see available files.\r\n"""
        }
        
        def puzzleRoom = targetRoom.data
        
        if (puzzleRoom.isExecutable) {
            return """${TerminalFormatter.formatText('⚠️  Already Executable', 'bold', 'yellow')}\r\n""" +
                   """File '${filename}' is already executable.\r\n"""
        }
        
        // Make the puzzle room executable
        def result = makeFileExecutable(player, filename)
        
        if (result.success) {
            return """${TerminalFormatter.formatText('✅ File Made Executable', 'bold', 'green')}\r\n""" +
                   """File: ${filename}\r\n""" +
                   """Permissions changed: -rw-r--r-- → -rwxr-xr-x\r\n""" +
                   """${TerminalFormatter.formatText('💡 TIP:', 'bold', 'yellow')} You can now execute this file with:\r\n""" +
                   """execute --<flag> <nonce> ${filename}\r\n"""
        } else {
            return """${TerminalFormatter.formatText('❌ Permission Change Failed', 'bold', 'red')}\r\n""" +
                   """${result.message}\r\n"""
        }
    }
}