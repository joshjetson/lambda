package ysap

import grails.gorm.transactions.Transactional
import ysap.helpers.PlayerHelp
import ysap.TerminalFormatter

@Transactional
class HudService {

    def lambdaPlayerService
    def gameSessionService
    def coordinateStateService
    def defragBotService
    def lambdaMerchantService
    def telnetServerService

    // HUD mode uses completely different rendering architecture
    private static final int SCREEN_WIDTH = 120
    private static final int SCREEN_HEIGHT = 24
    private static final int MAP_START_COL = 60
    private static final int COMMAND_AREA_WIDTH = 58
    
    // Screen buffer for HUD mode - completely different from telnet character-by-character
    private Map<String, String[][]> screenBuffers = [:]
    private Map<String, Integer> commandScrollPositions = [:]
    // HUD mode session writers for service compatibility
    private Map<String, PrintWriter> hudSessionWriters = [:]

    /**
     * Enter HUD mode for a player - COMPLETELY DIFFERENT ARCHITECTURE
     */
    def enterHudMode(OutputStream outputStream, LambdaPlayer player) {
        try {
            String playerId = player.username
            
            // Create dedicated screen buffer for this player
            screenBuffers[playerId] = createEmptyScreenBuffer()
            commandScrollPositions[playerId] = 0
            
            // Create a session writer for HUD mode to maintain service compatibility
            if (!hudSessionWriters[playerId]) {
                // Create a dummy PrintWriter that writes to a StringWriter
                // This serves as a session identifier for service methods
                hudSessionWriters[playerId] = new PrintWriter(new StringWriter())
            }
            
            // Switch to alternative screen buffer
            outputStream.write("\033[?1049h".getBytes()) // Enter alt buffer
            outputStream.write("\033[2J\033[H".getBytes()) // Clear and home
            outputStream.write("\033[?25l".getBytes()) // Hide cursor during rendering
            
            // Initial render of complete screen
            renderFullScreen(outputStream, player)
            
            // Clear prompt line and show initial prompt at column 1
            outputStream.write("\033[24;1H\033[2K".getBytes()) // Go to line 24, col 1, clear entire line
            String initialPrompt = getHudPrompt(player)
            outputStream.write(initialPrompt.getBytes("UTF-8"))
            outputStream.write("\033[?25h".getBytes()) // Show cursor
            
            return "HUD_MODE_ACTIVE"
        } catch (Exception e) {
            println "Error entering HUD mode: ${e.message}"
            return "Error entering HUD mode\r\n"
        }
    }

    /**
     * Create empty screen buffer - 2D array representing terminal screen
     */
    private String[][] createEmptyScreenBuffer() {
        String[][] buffer = new String[SCREEN_HEIGHT][SCREEN_WIDTH]
        for (int row = 0; row < SCREEN_HEIGHT; row++) {
            for (int col = 0; col < SCREEN_WIDTH; col++) {
                buffer[row][col] = " "
            }
        }
        return buffer
    }

    /**
     * COMPLETELY NEW RENDERING SYSTEM - builds entire screen then sends at once
     */
    private void renderFullScreen(OutputStream outputStream, LambdaPlayer player) {
        String playerId = player.username
        String[][] screen = screenBuffers[playerId]
        
        // Clear screen buffer
        clearScreenBuffer(screen)
        
        // Render map section (right side) - FIXED POSITION
        renderMapSection(screen, player)
        
        // Render command section (left side) - SCROLLABLE
        renderCommandSection(screen, player)
        
        // Render border between sections
        renderBorder(screen)
        
        // DON'T render prompt in screen buffer - handle it separately
        
        // Send entire screen as one atomic operation
        sendScreenBuffer(outputStream, screen)
    }

    private void clearScreenBuffer(String[][] screen) {
        for (int row = 0; row < SCREEN_HEIGHT; row++) {
            for (int col = 0; col < SCREEN_WIDTH; col++) {
                screen[row][col] = " "
            }
        }
    }

    /**
     * Render map section - PERSISTENT, only changes when player moves
     */
    private void renderMapSection(String[][] screen, LambdaPlayer player) {
        // Get map content
        String mapContent = lambdaPlayerService.showMatrixMap(player)
        String[] mapLines = mapContent.split("\r\n")
        
        // Place map in right section starting at MAP_START_COL with colors
        int row = 1
        for (String line : mapLines) {
            if (row < SCREEN_HEIGHT - 2) { // Leave room for prompt
                String cleanLine = stripAnsiCodes(line)
                for (int i = 0; i < Math.min(cleanLine.length(), SCREEN_WIDTH - MAP_START_COL); i++) {
                    if (MAP_START_COL + i < SCREEN_WIDTH) {
                        char symbol = cleanLine.charAt(i)
                        String coloredSymbol = applyHudMapColor(symbol, player, row, i)
                        screen[row][MAP_START_COL + i] = coloredSymbol
                    }
                }
                row++
            }
        }
    }

    // HUD Map Color Handlers - O(1) lookup for map symbol coloring
    private final Map<String, Closure<String>> mapColorHandlers = [
        '@': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "cyan") },
        'D': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "red") },
        'F': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "green") },
        'M': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "yellow") },
        'X': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "red") },
        '!': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "yellow") },
        '.': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "default", "green") },
        '0': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "white") },
        '1': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "white") },
        '2': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "white") },
        '3': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "white") },
        '4': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "white") },
        '5': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "white") },
        '6': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "white") },
        '7': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "white") },
        '8': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "white") },
        '9': { char symbol -> TerminalFormatter.formatText(symbol.toString(), "bold", "white") }
    ]

    /**
     * Apply colors to map symbols for HUD mode using O(1) map lookup
     */
    private String applyHudMapColor(char symbol, LambdaPlayer player, int mapRow, int mapCol) {
        String symbolKey = symbol.toString()
        def colorHandler = mapColorHandlers[symbolKey]
        return colorHandler ? colorHandler(symbol) : symbolKey
    }

    /**
     * Render command section - SCROLLABLE left side
     */
    private void renderCommandSection(String[][] screen, LambdaPlayer player) {
        // Header
        String header = "=== HUD MODE ACTIVE ==="
        writeToScreen(screen, 1, 2, header)
        
        writeToScreen(screen, 2, 2, "Commands work normally, 'exit' to leave")
        writeToScreen(screen, 3, 2, "Map updates automatically as you move")
        
        // Add any recent command output here (stored in player session)
        // For now, just show instructions
        writeToScreen(screen, 5, 2, "Type commands below:")
    }

    /**
     * Render vertical border between sections
     */
    private void renderBorder(String[][] screen) {
        for (int row = 0; row < SCREEN_HEIGHT - 1; row++) {
            screen[row][MAP_START_COL - 1] = "│"
        }
    }

    /**
     * Render prompt at bottom
     */
    private void renderPrompt(String[][] screen, LambdaPlayer player) {
        String prompt = "HUD▲${player.currentMatrixLevel}:(${player.positionX},${player.positionY}) > "
        writeToScreen(screen, SCREEN_HEIGHT - 1, 0, prompt)
    }

    /**
     * Write text to specific position in screen buffer
     */
    private void writeToScreen(String[][] screen, int row, int col, String text) {
        if (row >= 0 && row < SCREEN_HEIGHT) {
            for (int i = 0; i < text.length() && (col + i) < COMMAND_AREA_WIDTH; i++) {
                if (col + i >= 0 && col + i < SCREEN_WIDTH) {
                    screen[row][col + i] = text.charAt(i).toString()
                }
            }
        }
    }

    /**
     * Send entire screen buffer as atomic operation
     */
    private void sendScreenBuffer(OutputStream outputStream, String[][] screen) {
        StringBuilder fullScreen = new StringBuilder()
        
        // Build entire screen content
        for (int row = 0; row < SCREEN_HEIGHT; row++) {
            fullScreen.append("\033[${row + 1};1H") // Move to start of line
            for (int col = 0; col < SCREEN_WIDTH; col++) {
                fullScreen.append(screen[row][col])
            }
        }
        
        // Send everything at once
        outputStream.write(fullScreen.toString().getBytes("UTF-8"))
        outputStream.flush()
    }

    /**
     * Strip ANSI codes from text
     */
    private String stripAnsiCodes(String text) {

        return text.replaceAll(/\033\[[0-9;]*[mK]/, "")
    }

    /**
     * Exit HUD mode and return to normal terminal - CLEAN BUFFER CLEANUP
     */
    def exitHudMode(OutputStream outputStream, LambdaPlayer player) {
        try {
            String playerId = player.username
            
            // Clean up screen buffer for this player
            screenBuffers.remove(playerId)
            commandScrollPositions.remove(playerId)
            hudSessionWriters.remove(playerId)
            
            // Return to main screen buffer
            outputStream.write("\033[?25l".getBytes()) // Hide cursor
            outputStream.write("\033[?1049l".getBytes()) // Exit alt buffer  
            outputStream.write("\033[?25h".getBytes()) // Show cursor
            outputStream.flush()
            
            return "Exited HUD mode\r\n"
        } catch (Exception e) {
            println "Error exiting HUD mode: ${e.message}"
            return "Error exiting HUD mode\r\n"
        }
    }

    /**
     * Process command in HUD mode - BUFFERED RENDERING APPROACH
     */
    def processHudCommand(String command, LambdaPlayer player, OutputStream outputStream) {
        String playerId = player.username
        
        // Check for HUD mode exit
        if (command.toLowerCase() in ['exit', 'normal', 'quit']) {
            exitHudMode(outputStream, player)
            return "EXIT_HUD_MODE"
        }
        
        // Store command output for display in command area
        String commandOutput = processGameCommandForHud(command, player)
        
        // Store the output for this player (enhanced storage)
        storeCommandOutput(playerId, command, commandOutput)
        
        // Re-render entire screen with new output
        renderFullScreenWithCommand(outputStream, player, command, commandOutput)
        
        // Clear command line completely and redraw fresh prompt
        outputStream.write("\033[24;1H\033[2K".getBytes()) // Go to line 24, col 1, clear entire line
        String prompt = getHudPrompt(player)
        outputStream.write(prompt.getBytes("UTF-8"))
        outputStream.write("\033[?25h".getBytes()) // Show cursor at end of prompt
        outputStream.flush()
        
        return "CONTINUE_HUD_MODE"
    }

    /**
     * Enhanced full screen render that shows command output
     */
    private void renderFullScreenWithCommand(OutputStream outputStream, LambdaPlayer player, String lastCommand, String lastOutput) {
        String playerId = player.username
        String[][] screen = screenBuffers[playerId]
        
        // Clear screen buffer
        clearScreenBuffer(screen)
        
        // Render map section (right side) - FIXED POSITION
        renderMapSection(screen, player)
        
        // Render command section with output (left side)
        renderCommandSectionWithOutput(screen, player, lastCommand, lastOutput)
        
        // Render border between sections
        renderBorder(screen)
        
        // DON'T render prompt here - it will be rendered separately
        
        // Send entire screen as one atomic operation
        sendScreenBuffer(outputStream, screen)
    }

    /**
     * Process game commands specifically for HUD mode
     */
    private String processGameCommandForHud(String command, LambdaPlayer player) {
        if (!command || command.trim().isEmpty()) {
            return "Enter a command or type 'help' for available commands"
        }
        
        String[] parts = command.trim().toLowerCase().split(' ')
        String cmd = parts[0]
        
        switch (cmd) {
            case 'status':
            case 's':
                try {
                    return lambdaPlayerService.getPlayerStatus(player)
                } catch (Exception e) {
                    return "Status command not implemented in HUD mode yet"
                }
            case 'scan':
            case 'sc':
                try {
                    return gameSessionService.scanArea(player)
                } catch (Exception e) {
                    return "Scan command not implemented in HUD mode yet"
                }
            case 'cc':
                String playerId = player.username
                PrintWriter sessionWriter = hudSessionWriters[playerId]
                return coordinateStateService.handleCoordinateChange(command, player, sessionWriter)

            case 'help':
                if (parts.length > 1) {
                    return PlayerHelp.showHelp(parts[1])
                }
                return PlayerHelp.showHelp()
            case 'inventory':
            case 'i':
                try {
                    return lambdaPlayerService.showInventory(player)
                } catch (Exception e) {
                    return "Inventory command not implemented in HUD mode yet"
                }
                
            case 'map':
                return "Map is always visible in HUD mode (right side)"
            case 'clear':
                return
            case '':
                return "Enter a command or type 'help'"
                
            default:
                return "Command '${cmd}' not implemented in HUD mode yet.\nAvailable: ${getAvailableCommands()}"
        }
    }
    

    /**
     * Get list of available commands
     */
    private String getAvailableCommands() {
        return "status, scan, cc, inventory, map, clear, help, exit"
    }


    /**
     * Store command output for display in command area
     */
    private void storeCommandOutput(String playerId, String command, String output) {
        // For now, just store in a simple way - could be enhanced to maintain history
        // This is where we'd store command results to display in the left panel
    }

    /**
     * Enhanced command section rendering with stored output
     */
    private void renderCommandSectionWithOutput(String[][] screen, LambdaPlayer player, String lastCommand, String lastOutput) {
        // Header
        writeToScreen(screen, 1, 2, "=== HUD MODE ACTIVE ===")
        writeToScreen(screen, 2, 2, "Commands: status, scan, cc, inventory, exit")
        
        // Draw separator
        for (int col = 1; col < COMMAND_AREA_WIDTH - 1; col++) {
            screen[3][col] = "─"
        }
        
        // Show last command and output if available
        if (lastCommand && lastOutput) {
            writeToScreen(screen, 5, 2, "> ${lastCommand}")
            
            // Display output, word-wrapping if needed
            String[] outputLines = lastOutput.split("\r\n")
            int outputRow = 6
            for (String line : outputLines) {
                if (outputRow < SCREEN_HEIGHT - 3) { // Leave room for prompt
                    String cleanLine = stripAnsiCodes(line)
                    if (cleanLine.length() <= COMMAND_AREA_WIDTH - 4) {
                        writeToScreen(screen, outputRow, 2, cleanLine)
                    } else {
                        // Word wrap long lines
                        String wrappedLine = cleanLine.substring(0, COMMAND_AREA_WIDTH - 4)
                        writeToScreen(screen, outputRow, 2, wrappedLine)
                    }
                    outputRow++
                }
            }
        }
    }

    /**
     * Show HUD prompt
     */
    def getHudPrompt(LambdaPlayer player) {
        def avatarSymbol = telnetServerService.getAvatarSymbol(player.avatarSilhouette)  // Pass writer here
        return "HUD${avatarSymbol} ${player.currentMatrixLevel}:(${player.positionX},${player.positionY})>"
    }
}