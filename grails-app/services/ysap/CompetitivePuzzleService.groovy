package ysap

import grails.gorm.transactions.Transactional
import java.util.Random

@Transactional
class CompetitivePuzzleService {
    
    def puzzleRandomizationService
    def chatService
    
    def initializePlayerPuzzleState(LambdaPlayer player, String gameSessionId, Integer mapNumber) {
        println "Initializing competitive puzzle state for player ${player.username} on map ${mapNumber}"
        
        def elements = ['AIR', 'FIRE', 'EARTH', 'WATER']
        def playerStates = []
        
        elements.each { elementType ->
            // Check if player already has puzzle state for this element/map
            def existingState = PlayerPuzzleState.findByPlayerIdAndGameSessionIdAndMapNumberAndElementType(
                player.id.toString(), gameSessionId, mapNumber, elementType
            )
            
            if (!existingState) {
                // Generate unique coordinates for this player
                def coordinates = generateUniqueCoordinatesForPlayer(player.id.toString(), gameSessionId, mapNumber, elementType)
                
                def puzzleState = new PlayerPuzzleState(
                    playerId: player.id.toString(),
                    gameSessionId: gameSessionId,
                    mapNumber: mapNumber,
                    elementType: elementType,
                    variableCoordinateX: coordinates.variableX,
                    variableCoordinateY: coordinates.variableY,
                    puzzleRoomCoordinateX: coordinates.roomX,
                    puzzleRoomCoordinateY: coordinates.roomY
                )
                
                puzzleState.recordCoordinateShift()
                puzzleState.save(failOnError: true)
                playerStates.add(puzzleState)
                
                println "Created puzzle state for ${player.username}: ${elementType} variable at (${coordinates.variableX},${coordinates.variableY}), room at (${coordinates.roomX},${coordinates.roomY})"
            } else {
                playerStates.add(existingState)
            }
        }
        
        return playerStates
    }
    
    private def generateUniqueCoordinatesForPlayer(String playerId, String gameSessionId, Integer mapNumber, String elementType) {
        // Use player ID + session + map + element to create deterministic but unique coordinates per player
        def seedString = "${playerId}_${gameSessionId}_${mapNumber}_${elementType}_${System.currentTimeMillis()}"
        def random = new Random(seedString.hashCode())
        
        def variableX = random.nextInt(10)
        def variableY = random.nextInt(10)
        
        // Ensure puzzle room is different from variable location
        def roomX, roomY
        do {
            roomX = random.nextInt(10)
            roomY = random.nextInt(10)
        } while (roomX == variableX && roomY == variableY)
        
        return [
            variableX: variableX,
            variableY: variableY,
            roomX: roomX,
            roomY: roomY
        ]
    }
    
    def getPlayerSpecificPuzzleElements(LambdaPlayer player, String gameSessionId, Integer mapNumber, Integer x, Integer y) {
        def results = []
        
        // Find player's puzzle states for this map
        def playerStates = PlayerPuzzleState.findAllByPlayerIdAndGameSessionIdAndMapNumber(
            player.id.toString(), gameSessionId, mapNumber
        )
        
        playerStates.each { state ->
            // Check if current coordinates match this player's variable location
            if (state.variableCoordinateX == x && state.variableCoordinateY == y && !state.hasCollectedVariable) {
                // Get the session's variable for this element (shared data but player-specific location)
                def sessionVariables = puzzleRandomizationService.getSessionVariables(gameSessionId, mapNumber)
                def elementVariable = sessionVariables.find { it.elementType == state.elementType }
                
                if (elementVariable) {
                    results.add([type: 'player_variable', data: elementVariable, puzzleState: state])
                }
            }
            
            // Check if current coordinates match this player's puzzle room location
            if (state.puzzleRoomCoordinateX == x && state.puzzleRoomCoordinateY == y && state.hasCalculatedCoords && !state.hasObtainedSymbol) {
                // Get the session's puzzle room for this element
                def sessionRooms = puzzleRandomizationService.getSessionPuzzleRooms(gameSessionId, mapNumber)
                def elementRoom = sessionRooms.find { it.elementType == state.elementType }
                
                if (elementRoom) {
                    results.add([type: 'player_puzzle_room', data: elementRoom, puzzleState: state])
                }
            }
        }
        
        return results
    }
    
    def collectPlayerVariable(LambdaPlayer player, String variableName, String gameSessionId, Integer mapNumber, Integer x, Integer y) {
        def result = [success: false, message: '']
        
        PlayerPuzzleState.withTransaction {
            // Find player's puzzle state that matches this variable collection
            def playerState = PlayerPuzzleState.findByPlayerIdAndGameSessionIdAndMapNumberAndVariableCoordinateXAndVariableCoordinateY(
                player.id.toString(), gameSessionId, mapNumber, x, y
            )
            
            if (!playerState) {
                result.message = "No variable available for you at this location."
                return result
            }
            
            if (playerState.hasCollectedVariable) {
                result.message = "You have already collected the variable for ${playerState.elementType}."
                return result
            }
            
            // Get the actual variable data from session
            def sessionVariables = puzzleRandomizationService.getSessionVariables(gameSessionId, mapNumber)
            def variable = sessionVariables.find { it.elementType == playerState.elementType && it.variableName == variableName }
            
            if (!variable) {
                result.message = "Variable '${variableName}' not found for your puzzle sequence."
                return result
            }
            
            // Mark as collected and add to player's collection
            playerState.recordVariableCollection()
            playerState.save(failOnError: true)
            
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                managedPlayer.addToCollectedVariables(variable)
                managedPlayer.save(failOnError: true)
            }
            
            result.success = true
            result.message = "📦 Variable collected: ${variable.variableName} for ${playerState.elementType} puzzle"
            result.variable = variable
            result.puzzleState = playerState
        }
        
        return result
    }
    
    def recordPlayerCoordinateCalculation(LambdaPlayer player, String gameSessionId, Integer mapNumber, String elementType, String calculatedCoords) {
        PlayerPuzzleState.withTransaction {
            def playerState = PlayerPuzzleState.findByPlayerIdAndGameSessionIdAndMapNumberAndElementType(
                player.id.toString(), gameSessionId, mapNumber, elementType
            )
            
            if (playerState) {
                playerState.recordCoordinateCalculation(calculatedCoords)
                playerState.save(failOnError: true)
                
                println "Player ${player.username} calculated coordinates for ${elementType}: ${calculatedCoords}"
                return playerState
            }
        }
        return null
    }
    
    def executePlayerPuzzleRoom(LambdaPlayer player, String gameSessionId, Integer mapNumber, String flag, String nonce, String filename, Integer x, Integer y) {
        def result = [success: false, message: '']
        
        PuzzleRoom.withTransaction {
            // Find player's puzzle state for this location
            def playerState = PlayerPuzzleState.findByPlayerIdAndGameSessionIdAndMapNumberAndPuzzleRoomCoordinateXAndPuzzleRoomCoordinateY(
                player.id.toString(), gameSessionId, mapNumber, x, y
            )
            
            if (!playerState) {
                result.message = "No puzzle room available for you at this location."
                return result
            }
            
            if (playerState.hasObtainedSymbol) {
                result.message = "You have already obtained the ${playerState.elementType} symbol."
                return result
            }
            
            if (!playerState.hasCalculatedCoords) {
                result.message = "You must calculate coordinates before accessing the puzzle room."
                return result
            }
            
            // Get the session's puzzle room data
            def sessionRooms = puzzleRandomizationService.getSessionPuzzleRooms(gameSessionId, mapNumber)
            def puzzleRoom = sessionRooms.find { it.elementType == playerState.elementType }
            
            if (!puzzleRoom) {
                result.message = "Puzzle room data not found."
                return result
            }
            
            if (puzzleRoom.fileName != filename) {
                result.message = "File '${filename}' not found. Available: ${puzzleRoom.fileName}"
                return result
            }
            
            // Validate execution parameters
            if (puzzleRoom.validateExecution(flag, nonce)) {
                // SUCCESS! Player obtained the symbol
                playerState.recordSymbolObtained()
                playerState.save(failOnError: true)
                
                // Award symbol to player
                def managedPlayer = LambdaPlayer.get(player.id)
                if (managedPlayer) {
                    switch(playerState.elementType) {
                        case 'AIR':
                            managedPlayer.hasAirSymbol = true
                            managedPlayer.airSymbolAcquired = new Date()
                            break
                        case 'FIRE':
                            managedPlayer.hasFireSymbol = true
                            managedPlayer.fireSymbolAcquired = new Date()
                            break
                        case 'EARTH':
                            managedPlayer.hasEarthSymbol = true
                            managedPlayer.earthSymbolAcquired = new Date()
                            break
                        case 'WATER':
                            managedPlayer.hasWaterSymbol = true
                            managedPlayer.waterSymbolAcquired = new Date()
                            break
                    }
                    managedPlayer.save(failOnError: true)
                }
                
                // TRIGGER COORDINATE SHIFT FOR ALL OTHER PLAYERS
                triggerCompetitiveCoordinateShift(gameSessionId, mapNumber, playerState.elementType, player.username)
                
                result.success = true
                result.message = puzzleRoom.getSuccessResponse()
                result.elementType = playerState.elementType
                result.triggerShift = true
                
            } else {
                result.message = puzzleRoom.getFailureResponse()
            }
        }
        
        return result
    }
    
    private def triggerCompetitiveCoordinateShift(String gameSessionId, Integer mapNumber, String elementType, String winnerUsername) {
        println "🏆 ${winnerUsername} obtained ${elementType} symbol! Triggering coordinate shift for other players..."
        
        // Find all other players who haven't obtained this symbol yet
        def otherPlayerStates = PlayerPuzzleState.findAllByGameSessionIdAndMapNumberAndElementTypeAndHasObtainedSymbol(
            gameSessionId, mapNumber, elementType, false
        )
        
        def shiftedPlayers = []
        
        otherPlayerStates.each { state ->
            if (state.playerId != winnerUsername) {
                // Generate new coordinates for this player
                def newCoordinates = generateUniqueCoordinatesForPlayer(
                    state.playerId, gameSessionId, mapNumber, elementType
                )
                
                // Update their puzzle state with new coordinates
                state.variableCoordinateX = newCoordinates.variableX
                state.variableCoordinateY = newCoordinates.variableY
                state.puzzleRoomCoordinateX = newCoordinates.roomX
                state.puzzleRoomCoordinateY = newCoordinates.roomY
                
                // Reset their progress for this element
                state.hasCollectedVariable = false
                state.hasCalculatedCoords = false
                state.calculatedCoordinates = null
                state.coordinatesCalculatedAt = null
                
                state.recordCoordinateShift()
                state.save(failOnError: true)
                
                shiftedPlayers.add(state.playerId)
                
                println "Shifted ${elementType} coordinates for player ${state.playerId}: var(${newCoordinates.variableX},${newCoordinates.variableY}), room(${newCoordinates.roomX},${newCoordinates.roomY})"
            }
        }
        
        // Broadcast coordinate shift to all affected players
        if (shiftedPlayers.size() > 0) {
            def shiftMessage = "⚡ COORDINATE SHIFT! ${winnerUsername} obtained the ${elementType} symbol. Your ${elementType} puzzle coordinates have been randomized. Competition intensifies!"
            
            // Send to mingle chamber if available
            try {
                chatService?.broadcastSystemMessage(shiftMessage)
            } catch (Exception e) {
                println "Could not broadcast shift message: ${e.message}"
            }
            
            println "Coordinate shift complete: ${shiftedPlayers.size()} players affected"
        }
    }
    
    def getPlayerPuzzleProgress(LambdaPlayer player, String gameSessionId, Integer mapNumber) {
        def progress = [:]
        
        def playerStates = PlayerPuzzleState.findAllByPlayerIdAndGameSessionIdAndMapNumber(
            player.id.toString(), gameSessionId, mapNumber
        )
        
        playerStates.each { state ->
            progress[state.elementType] = [
                status: state.getCompetitionStatus(),
                variableCoords: "(${state.variableCoordinateX},${state.variableCoordinateY})",
                roomCoords: "(${state.puzzleRoomCoordinateX},${state.puzzleRoomCoordinateY})",
                hasVariable: state.hasCollectedVariable,
                hasCalculated: state.hasCalculatedCoords,
                hasSymbol: state.hasObtainedSymbol,
                shiftCount: state.shiftCount,
                shiftReason: state.getShiftReason()
            ]
        }
        
        return progress
    }
    
    def formatPlayerProgressDisplay(LambdaPlayer player, String gameSessionId, Integer mapNumber) {
        def progress = getPlayerPuzzleProgress(player, gameSessionId, mapNumber)
        def output = new StringBuilder()
        
        output.append("=== 🏁 COMPETITIVE PUZZLE PROGRESS ===\n")
        output.append("Map ${mapNumber} - Competition Status\n\n")
        
        ['AIR', 'FIRE', 'EARTH', 'WATER'].each { elementType ->
            def elementProgress = progress[elementType]
            if (elementProgress) {
                def icon = getElementIcon(elementType)
                output.append("${icon} ${elementType}:\n")
                output.append("  Status: ${elementProgress.status}\n")
                output.append("  Variable Location: ${elementProgress.variableCoords}\n")
                output.append("  Puzzle Room Location: ${elementProgress.roomCoords}\n")
                output.append("  Coordinate Shifts: ${elementProgress.shiftCount}\n")
                if (elementProgress.shiftCount > 0) {
                    output.append("  Last Shift: ${elementProgress.shiftReason}\n")
                }
                output.append("\n")
            }
        }
        
        def completedCount = progress.values().count { it.hasSymbol }
        output.append("Progress: ${completedCount}/4 symbols obtained\n")
        
        if (completedCount == 4) {
            output.append("🌟 ALL SYMBOLS COLLECTED! Ready for Logic Daemon encounter! 🌟\n")
        } else {
            output.append("⚡ WARNING: Other players are competing for the same symbols!\n")
            output.append("💡 First to solve gets the symbol - others get new coordinates!\n")
        }
        
        return output.toString()
    }
    
    private String getElementIcon(String elementType) {
        switch(elementType) {
            case 'AIR': return '🜁'
            case 'FIRE': return '🜂'
            case 'EARTH': return '🜃'
            case 'WATER': return '🜄'
            default: return '❓'
        }
    }
    
    def cleanupCompletedMapStates(String gameSessionId, Integer mapNumber) {
        // Clean up puzzle states for players who have completed all symbols on this map
        def completedStates = PlayerPuzzleState.createCriteria().list {
            eq 'gameSessionId', gameSessionId
            eq 'mapNumber', mapNumber
            eq 'hasObtainedSymbol', true
        }
        
        // Group by player and check if they have all 4 symbols
        def playerCompletionMap = completedStates.groupBy { it.playerId }
        
        playerCompletionMap.each { playerId, states ->
            if (states.size() == 4) {
                // Player has all 4 symbols, can clean up their states for this map
                states.each { state ->
                    state.delete()
                }
                println "Cleaned up completed puzzle states for player ${playerId} on map ${mapNumber}"
            }
        }
    }
}