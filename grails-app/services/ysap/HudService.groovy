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
    private static final int DEFAULT_SCREEN_HEIGHT = 24
    private static final int MAP_START_COL = 60
    private static final int COMMAND_AREA_WIDTH = 58
    
    // Dynamic screen dimensions per session
    private Map<String, Integer> sessionScreenHeights = [:]

    /**
     * Detect terminal size using ANSI cursor position technique
     */
    private int detectTerminalHeight(OutputStream outputStream, InputStream inputStream) {
        try {
            // Move cursor to extreme bottom right (999,999)
            outputStream.write("\033[999;999H".getBytes())
            // Query cursor position - terminal will clamp to actual size
            outputStream.write("\033[6n".getBytes())
            outputStream.flush()
            
            // Read response: ESC[{row};{col}R
            StringBuilder response = new StringBuilder()
            long timeout = System.currentTimeMillis() + 1000 // 1 second timeout
            
            while (System.currentTimeMillis() < timeout) {
                if (inputStream.available() > 0) {
                    int ch = inputStream.read()
                    response.append((char) ch)
                    if (ch == 'R') break // End of response
                }
                Thread.sleep(10)
            }
            
            String responseStr = response.toString()
            // Parse ESC[{row};{col}R
            if (responseStr.matches(/.*\033\[(\d+);(\d+)R.*/)) {
                String rowStr = responseStr.replaceAll(/.*\033\[(\d+);(\d+)R.*/, '$1')
                int detectedHeight = Integer.parseInt(rowStr)
                
                // Sanity check - reasonable terminal height
                if (detectedHeight >= 24 && detectedHeight <= 100) {
                    return detectedHeight
                }
            }
        } catch (Exception e) {
            // Fallback silently to default
        }
        
        return DEFAULT_SCREEN_HEIGHT
    }
    
    // Screen buffer for HUD mode - completely different from telnet character-by-character
    private Map<String, String[][]> screenBuffers = [:]
    private Map<String, Integer> commandScrollPositions = [:]
    // HUD mode session writers for service compatibility
    private Map<String, PrintWriter> hudSessionWriters = [:]

    /**
     * Get screen height for a session (cached after first detection)
     */
    private int getScreenHeight(String playerId) {
        return sessionScreenHeights[playerId] ?: DEFAULT_SCREEN_HEIGHT
    }

    /**
     * Get the row position where the HUD prompt should appear (dynamic based on screen height)
     */
    def getHudPromptRow(LambdaPlayer player) {
        String playerId = player.username
        int screenHeight = getScreenHeight(playerId)
        // Position prompt 2 rows above the bottom (accounting for 1-based ANSI positioning)
        return screenHeight - 1
    }

    /**
     * Enter HUD mode for a player - COMPLETELY DIFFERENT ARCHITECTURE
     */
    def enterHudMode(OutputStream outputStream, LambdaPlayer player, InputStream inputStream = null) {
        try {
            String playerId = player.username
            
            // Detect terminal height if we have access to input stream and haven't detected yet
            if (inputStream && !sessionScreenHeights[playerId]) {
                int detectedHeight = detectTerminalHeight(outputStream, inputStream)
                sessionScreenHeights[playerId] = detectedHeight
            }
            
            // Create dedicated screen buffer for this player using dynamic height
            screenBuffers[playerId] = createEmptyScreenBuffer(playerId)
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
            
            // Initial render of complete screen (includes prompt in buffer)
            renderFullScreen(outputStream, player)
            
            // Position cursor at the end of the prompt for input (prompt is already in buffer)
//            String prompt = getHudPrompt(player)
//            outputStream.write(("\033[23;" + (prompt.length() + 3) + "H").getBytes()) // Position after prompt at row 23
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
    private String[][] createEmptyScreenBuffer(String playerId) {
        int screenHeight = getScreenHeight(playerId)
        String[][] buffer = new String[screenHeight][SCREEN_WIDTH]
        for (int row = 0; row < screenHeight; row++) {
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
        
        // Render outer box around entire HUD
        renderOuterBox(screen)
        
        // Render map section (right side) - FIXED POSITION
        renderMapSection(screen, player)
        
        // Render command section (left side) - SCROLLABLE
        renderCommandSection(screen, player)
        
        // Render border between sections
        renderBorder(screen)
        
        // Render prompt INSIDE the screen buffer to maintain box borders
        renderPromptInBuffer(screen, player)
        
        // Send entire screen as one atomic operation
        sendScreenBuffer(outputStream, screen)
    }

    private void clearScreenBuffer(String[][] screen) {
        int screenHeight = screen.length
        for (int row = 0; row < screenHeight; row++) {
            for (int col = 0; col < SCREEN_WIDTH; col++) {
                screen[row][col] = " "
            }
        }
    }

    /**
     * Render outer box around entire HUD display
     */
    private void renderOuterBox(String[][] screen) {
        int screenHeight = screen.length
        
        // Top border
        for (int col = 0; col < SCREEN_WIDTH; col++) {
            screen[0][col] = (col == 0) ? "╔" : (col == SCREEN_WIDTH - 1) ? "╗" : "═"
        }
        
        // Bottom border  
        for (int col = 0; col < SCREEN_WIDTH; col++) {
            screen[screenHeight - 1][col] = (col == 0) ? "╚" : (col == SCREEN_WIDTH - 1) ? "╝" : "═"
        }
        
        // Left and right borders
        for (int row = 1; row < screenHeight - 1; row++) {
            screen[row][0] = "║"
            screen[row][SCREEN_WIDTH - 1] = "║"
        }
    }

    /**
     * Render map section - PERSISTENT, only changes when player moves
     */
    private void renderMapSection(String[][] screen, LambdaPlayer player) {
        // Get map content
        String mapContent = lambdaPlayerService.showMatrixMap(player)
        String[] mapLines = mapContent.split("\r\n")
        
        // Place map in right section starting at MAP_START_COL with colors (inside outer box)
        int row = 2 // Start inside the outer box
        for (String line : mapLines) {
            if (row < screen.length - 3) { // Leave room for outer box and prompt
                String cleanLine = stripAnsiCodes(line)
                for (int i = 0; i < Math.min(cleanLine.length(), SCREEN_WIDTH - MAP_START_COL - 1); i++) {
                    if (MAP_START_COL + i < SCREEN_WIDTH - 1) { // Leave room for right border
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
        // Header (inside outer box)
        String header = "=== HUD MODE ACTIVE ==="
        writeToScreen(screen, 2, 3, header) // Start inside outer box
        
        writeToScreen(screen, 3, 3, "Commands work normally, 'exit' to leave")
        writeToScreen(screen, 4, 3, "Map updates automatically as you move")
        
        // Add any recent command output here (stored in player session)
        // For now, just show instructions
        writeToScreen(screen, 6, 3, "Type commands below:")
    }

    /**
     * Render prompt inside the screen buffer to maintain box borders
     */
    private void renderPromptInBuffer(String[][] screen, LambdaPlayer player) {
//        String prompt = getHudPrompt(player)
        // Place prompt at bottom inside the outer box (inside left border)
        writeToScreen(screen, screen.length - 2, 2, '')
    }

    /**
     * Render vertical border between sections (inside outer box)
     */
    private void renderBorder(String[][] screen) {
        for (int row = 1; row < screen.length - 1; row++) {
            screen[row][MAP_START_COL - 1] = "║"
        }
    }

    /**
     * Render prompt at bottom
     */
    private void renderPrompt(String[][] screen, LambdaPlayer player) {
        String prompt = "HUD▲${player.currentMatrixLevel}:(${player.positionX},${player.positionY}) > "
        writeToScreen(screen, screen.length - 1, 0, prompt)
    }

    /**
     * Write text to specific position in screen buffer
     */
    private void writeToScreen(String[][] screen, int row, int col, String text) {
        if (row >= 0 && row < screen.length) {
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
        for (int row = 0; row < screen.length; row++) {
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
        
        // Re-render entire screen with new output (includes prompt in buffer)
        renderFullScreenWithCommand(outputStream, player, command, commandOutput)
        
        // Position cursor at the end of the prompt for next input  
//        String prompt = getHudPrompt(player)
//        outputStream.write(("\033[" + (SCREEN_HEIGHT - 1) + ";" + (prompt.length() + 3) + "H").getBytes()) // Position after prompt
        outputStream.write("\033[?25h".getBytes()) // Show cursor
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
        
        // Render outer box around entire HUD
        renderOuterBox(screen)
        
        // Render map section (right side) - FIXED POSITION
        renderMapSection(screen, player)
        
        // Render command section with output (left side)
        renderCommandSectionWithOutput(screen, player, lastCommand, lastOutput)
        
        // Render border between sections
        renderBorder(screen)
        
        // Render prompt INSIDE the screen buffer to maintain box borders
        // renderPromptInBuffer(screen, player)  // DISABLED - was causing double prompt
        
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
                    return PlayerHelp.showHelp(parts[1], 45) // HUD mode width (reduced for better fit)
                }
                return PlayerHelp.showHelp(null, 45) // HUD mode width (reduced for better fit)
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
        // Header (inside outer box)
        writeToScreen(screen, 2, 3, "◄HUD▪MODE►")
        writeToScreen(screen, 3, 3, "Commands: status, scan, cc, inventory, exit")
        
        // Draw separator (inside outer box)
        for (int col = 2; col < COMMAND_AREA_WIDTH - 1; col++) {
            screen[4][col] = "≈"
        }
        
        // Show last command and output if available
        if (lastCommand && lastOutput) {
            writeToScreen(screen, 6, 3, "> ${lastCommand}")
            
            // Display output, word-wrapping if needed
            String[] outputLines = lastOutput.split("\r\n")
            int outputRow = 7
            int maxOutputRows = screen.length - 6 // Leave room for outer box, prompt, and truncation message
            boolean truncated = false
            
            for (int i = 0; i < outputLines.length; i++) {
                if (outputRow >= maxOutputRows) {
                    // Check if there are more lines to show
                    if (i < outputLines.length - 1) {
                        truncated = true
                    }
                    break
                }
                
                String line = outputLines[i]
                String cleanLine = stripAnsiCodes(line)
                if (cleanLine.length() <= COMMAND_AREA_WIDTH - 6) { // Account for outer box
                    writeToScreen(screen, outputRow, 3, cleanLine)
                } else {
                    // Word wrap long lines
                    String wrappedLine = cleanLine.substring(0, COMMAND_AREA_WIDTH - 6)
                    writeToScreen(screen, outputRow, 3, wrappedLine)
                }
                outputRow++
            }
            
            // Show truncation message if content was cut off
            if (truncated && outputRow < screen.length - 4) {
                writeToScreen(screen, outputRow, 3, "... (output truncated for HUD display)")
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