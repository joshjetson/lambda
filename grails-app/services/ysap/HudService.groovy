package ysap

import grails.gorm.transactions.Transactional
import ysap.helpers.PlayerHelp
import ysap.TerminalFormatter
import java.io.StringWriter

@Transactional
class HudService {

    def lambdaPlayerService
    def gameSessionService
    def coordinateStateService
    def defragBotService
    def lambdaMerchantService
    def telnetServerService
    def puzzleService
    def chatService
    def simpleRepairService

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
    // Heap mode state tracking
    private Map<String, Boolean> playersInHeapMode = [:]
    private Map<String, List<String>> heapChatHistory = [:]
    // Repair mode state tracking
    private Map<String, Boolean> playersInRepairMode = [:]
    private Map<String, OutputStream> repairOutputStreams = [:] // Track outputStream for each player in repair mode

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
        String playerId = player.username
        
        // Check if player is in heap mode - show chat instead of map
        if (playersInHeapMode[playerId]) {
            renderHeapChatSection(screen, player)
            return
        }
        
        // Show the repair panel only while there's a LIVE repair session. If the flag is set but the
        // session has ended (completed / interrupted), self-heal: clear the flag and fall through to the
        // map, so the player can never get stuck on a dead "(repair complete)" panel.
        if (playersInRepairMode[playerId]) {
            if (simpleRepairService.isPlayerInRepairSession(playerId)) {
                renderRepairSection(screen, player)
                return
            }
            playersInRepairMode[playerId] = false
            repairOutputStreams.remove(playerId)
        }
        
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

    /**
     * Render heap chat section - SIMPLE AND CLEAN
     */
    private void renderHeapChatSection(String[][] screen, LambdaPlayer player) {
        String playerId = player.username
        List<String> chatHistory = heapChatHistory[playerId] ?: []
        
        // Clear the map area completely
        for (int row = 2; row < screen.length - 3; row++) {
            for (int col = MAP_START_COL; col < SCREEN_WIDTH - 1; col++) {
                screen[row][col] = " "
            }
        }
        
        // Simple header
        writeToScreen(screen, 2, MAP_START_COL, "HEAP CHAT")
        writeToScreen(screen, 3, MAP_START_COL, "---------")
        
        // Show messages simply - no wrapping, just truncate if too long
        int row = 5
        int maxWidth = 58 // Max width for map area
        int maxMessages = Math.min(chatHistory.size(), screen.length - 10)
        
        // Show most recent messages
        int startIndex = Math.max(0, chatHistory.size() - maxMessages)
        for (int i = startIndex; i < chatHistory.size() && row < screen.length - 4; i++) {
            String message = chatHistory[i]
            if (message) {
                // Truncate if too long instead of wrapping
                if (message.length() > maxWidth) {
                    message = message.substring(0, maxWidth - 3) + "..."
                }
                writeToScreen(screen, row, MAP_START_COL, message)
                row++
            }
        }
        
        // Simple exit instruction
        writeToScreen(screen, screen.length - 4, MAP_START_COL, "type 'exit' to return to map")
    }

    /**
     * Render repair section - SIMPLE AND CLEAN
     */
    private void renderRepairSection(String[][] screen, LambdaPlayer player) {
        // Clear the map area completely
        for (int row = 2; row < screen.length - 3; row++) {
            for (int col = MAP_START_COL; col < SCREEN_WIDTH - 1; col++) {
                screen[row][col] = " "
            }
        }

        // A FIXED in-place slot panel driven by live session state — the spinning digit (YOU row)
        // updates in place each render, no scrolling history. ASCII only (one glyph per grid cell).
        List<String> lines = simpleRepairService.repairPanelLines(player.username) ?: ["", "  (repair complete)"]
        int row = 2
        for (String line : lines) {
            if (row >= screen.length - 4) break
            if (line != null) {
                String shown = line.length() > 56 ? line.substring(0, 56) : line
                writeToScreen(screen, row, MAP_START_COL, shown)
            }
            row++
        }

        writeToScreen(screen, screen.length - 4, MAP_START_COL, "type 'exit' to return to map")
    }

    /**
     * Wrap text to fit within specified width
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = []
        if (!text || maxWidth <= 0) return lines
        
        // Strip ANSI codes for length calculation but preserve them in output
        String cleanText = stripAnsiCodes(text)
        
        if (cleanText.length() <= maxWidth) {
            lines.add(text)
            return lines
        }
        
        // Simple word wrapping
        String[] words = text.split(' ')
        StringBuilder currentLine = new StringBuilder()
        
        for (String word : words) {
            String cleanWord = stripAnsiCodes(word)
            if (stripAnsiCodes(currentLine.toString()).length() + cleanWord.length() + 1 <= maxWidth) {
                if (currentLine.length() > 0) {
                    currentLine.append(' ')
                }
                currentLine.append(word)
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString())
                    currentLine = new StringBuilder(word)
                } else {
                    // Word is too long, break it
                    lines.add(word.substring(0, Math.min(word.length(), maxWidth)))
                    if (word.length() > maxWidth) {
                        currentLine = new StringBuilder(word.substring(maxWidth))
                    }
                }
            }
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString())
        }
        
        return lines
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
            for (int i = 0; i < text.length(); i++) {
                if (col + i >= 0 && col + i < SCREEN_WIDTH) {
                    // Allow writing to both command area and map area
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
        
        // Check if player is in heap mode first
        if (playersInHeapMode[playerId] && command.toLowerCase() == 'exit') {
            // Exit heap mode, return to map view
            playersInHeapMode[playerId] = false
            // Exit chat mode in the chat service too
            try {
                player.isInMingle = false
                player.save(flush: true, failOnError: true)
            } catch (Exception e) {
                // Continue even if save fails
            }
            
            // Automatically trigger help command to refresh display and position cursor properly
            String helpOutput = processGameCommandForHud('help', player)
            storeCommandOutput(playerId, 'help', helpOutput)
            renderFullScreenWithCommand(outputStream, player, 'help', helpOutput)
            outputStream.write("\033[?25h".getBytes()) // Show cursor
            outputStream.flush()
            
            return "CONTINUE_HUD_MODE" // Continue in HUD mode with refreshed display
        }
        
        // Check for active repair mini-game session (highest priority)
        if (simpleRepairService.isPlayerInRepairSession(playerId)) {
            // Player is in active repair mini-game - route input to repair service
            return handleActiveRepairSession(command, player, outputStream)
        }
        
        // Check if player is in repair mode
        if (playersInRepairMode[playerId] && command.toLowerCase() == 'exit') {
            // Exit repair mode, return to map view
            playersInRepairMode[playerId] = false
            repairOutputStreams.remove(playerId) // Clean up outputStream
            
            // Automatically trigger help command to refresh display and position cursor properly
            String helpOutput = processGameCommandForHud('help', player)
            storeCommandOutput(playerId, 'help', helpOutput)
            renderFullScreenWithCommand(outputStream, player, 'help', helpOutput)
            outputStream.write("\033[?25h".getBytes()) // Show cursor
            outputStream.flush()
            
            return "CONTINUE_HUD_MODE" // Continue in HUD mode with refreshed display
        }
        
        // Check for HUD mode exit
        if (command.toLowerCase() in ['exit', 'normal', 'quit']) {
            exitHudMode(outputStream, player)
            return "EXIT_HUD_MODE"
        }
        
        // If in heap mode, handle chat commands differently
        if (playersInHeapMode[playerId]) {
            // Always refresh chat history first to get latest messages from database
            refreshChatHistory(playerId)
            
            boolean messageWasSent = processChatCommand(command, player)
            
            // Always re-render screen to show updated messages
            renderFullScreen(outputStream, player)
            return "CONTINUE_HUD_MODE"
        }
        
        // Check for repair command BEFORE processing other commands
        if (command.trim().toLowerCase().startsWith('repair ')) {
            // Handle repair command here where we have access to outputStream
            def result = coordinateStateService.handleRepairCommand(command, player)
            
            // Check if this should initiate a mini-game
            def repairCoords = coordinateStateService.parseRepairInitiation(result)
            if (repairCoords) {
                def targetX = repairCoords[0]
                def targetY = repairCoords[1]

                // Stop any existing repair session
                simpleRepairService.stopRepairSession(player.username)
                
                // Store outputStream FIRST for cycling updates
                repairOutputStreams[player.username] = outputStream
                
                // Custom PrintWriter: the DigitCycler pings it each ~300ms tick. We don't care about the
                // string — it's a pure "session state changed, re-render the panel" trigger.
                def hudPrintWriter = new PrintWriter(new StringWriter()) {
                    @Override
                    void print(String s) {
                        def os = repairOutputStreams[player.username]
                        if (os) refreshHudScreenForRepairCycling(os, player)
                    }
                    @Override
                    void println(String s) {
                        def os = repairOutputStreams[player.username]
                        if (os) refreshHudScreenForRepairCycling(os, player)
                    }
                }

                // Initiate the new repair mini-game with HUD-compatible PrintWriter
                def repairResult = simpleRepairService.initiateRepair(player, targetX, targetY, hudPrintWriter)

                if (repairResult.success) {
                    // Enter repair display mode — the panel renders from live session state (no history).
                    playersInRepairMode[player.username] = true
                    renderFullScreen(outputStream, player)
                    return "CONTINUE_HUD_MODE"
                } else {
                    // Show error and continue
                    storeCommandOutput(playerId, command, repairResult.message)
                    renderFullScreenWithCommand(outputStream, player, command, repairResult.message)
                    outputStream.write("\033[?25h".getBytes())
                    outputStream.flush()
                    return "CONTINUE_HUD_MODE"
                }
            } else {
                // Show validation error and continue
                storeCommandOutput(playerId, command, result)
                renderFullScreenWithCommand(outputStream, player, command, result)
                outputStream.write("\033[?25h".getBytes())
                outputStream.flush()
                return "CONTINUE_HUD_MODE"
            }
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
    // HUD-specific command overrides: commands whose HUD rendering/behavior differs from classic.
    // Everything NOT here delegates to the shared TelnetServerService.commandHandlers map (Phase 9),
    // so the ~37 previously-"not implemented" text commands now work in HUD. Map-dispatch, no switch.
    private final Map<String, Closure> hudOverrides = [
        'map':    { String c, LambdaPlayer p -> "Map is always visible in HUD mode (right side)" },
        'm':      { String c, LambdaPlayer p -> "Map is always visible in HUD mode (right side)" },
        'clear':  { String c, LambdaPlayer p -> "" },
        'ls':     { String c, LambdaPlayer p -> hudLs(p) },
        'help':   { String c, LambdaPlayer p -> hudHelp(c) },
        'heap':   { String c, LambdaPlayer p -> hudHeap(p) },
        'mingle': { String c, LambdaPlayer p -> hudHeap(p) }
        // defrag is intentionally NOT overridden — it flows through dispatchCommand like any command, and
        // the encounter's cat/grep/kill turns route via routeActiveDefrag (shared, keyed by the stable
        // HUD writer), so defrag combat works in HUD with zero HUD-specific code.
    ]

    private String processGameCommandForHud(String command, LambdaPlayer player) {
        if (!command || command.trim().isEmpty()) {
            return "Enter a command or type 'help' for available commands"
        }

        String cmd = command.trim().toLowerCase().split(' ')[0]
        def override = hudOverrides[cmd]
        if (override) {
            return override.call(command, player)
        }

        // Reuse the authoritative classic dispatch (status/scan/cc/inventory/cat + entropy/recurse/
        // symbols/shop/use/pickup/... all now work in HUD). Thread the HUD session writer through so
        // writer-dependent handlers (e.g. cc) behave correctly.
        return telnetServerService.dispatchCommand(command, player, hudSessionWriters[player.username])
    }

    private String hudLs(LambdaPlayer player) {
        try { return getCompactFileList(player) }
        catch (Exception e) { return "File listing not available: ${e.message}" }
    }

    private String hudHelp(String command) {
        String[] parts = command.trim().toLowerCase().split(' ')
        return parts.length > 1 ? PlayerHelp.showHelp(parts[1], 45) : PlayerHelp.showHelp(null, 45)
    }

    private String hudHeap(LambdaPlayer player) {
        String playerId = player.username
        if (!playersInHeapMode[playerId]) {
            playersInHeapMode[playerId] = true
            try {
                chatService.enterChat(player, hudSessionWriters[playerId])
                refreshChatHistory(playerId)
            } catch (Exception e) {
                heapChatHistory[playerId] = ["Error connecting to heap space"]
            }
            return "Entered heap space. Chat appears on RIGHT side. Type 'exit' to return to map."
        }
        return "Already in heap space. Type 'exit' to return to map view."
    }
    

    /**
     * Get compact file listing optimized for HUD mode width constraints
     */
    private String getCompactFileList(LambdaPlayer player) {
        def files = new StringBuilder()
        files.append(TerminalFormatter.formatText("FILES", 'bold', 'cyan')).append('\r\n')
        files.append("Dir: /lambda/${player.username}\r\n\r\n")
        
        // Get puzzle rooms at current location (shortened format)
        def puzzleElements = puzzleService.getPlayerPuzzleElementsAtLocation(player, player.positionX, player.positionY)
        def puzzleRooms = puzzleElements.findAll { it.type == 'player_puzzle_room' }
        
        if (puzzleRooms.size() > 0) {
            files.append(TerminalFormatter.formatText("PUZZLES:", 'bold', 'yellow')).append('\r\n')
            puzzleRooms.each { roomElement ->
                def puzzleRoom = roomElement.data
                def permissionColor = puzzleRoom.isExecutable ? 'green' : 'red'
                def perms = puzzleRoom.isExecutable ? 'rwx' : 'r--'
                files.append(TerminalFormatter.formatText("${perms} ${puzzleRoom.filename}", permissionColor)).append('\r\n')
            }
            files.append('\r\n')
        }
        
        // System files (compact)
        files.append(TerminalFormatter.formatText("SYSTEM:", 'bold', 'blue')).append('\r\n')
        files.append("rw- fragment_file\r\n")
        files.append("rw- status_log\r\n")
        files.append("rw- inventory_data\r\n")
        files.append("rw- entropy_monitor\r\n")
        files.append("rw- system_map\r\n")
        files.append("rw- python_env\r\n")
        files.append("rw- ethnicity_config\r\n")
        files.append("r-- puzzle_vars\r\n")
        
        // Logic fragments (if any)
        if (player.logicFragments && player.logicFragments.size() > 0) {
            files.append("\r\n")
            files.append(TerminalFormatter.formatText("FRAGMENTS:", 'bold', 'magenta')).append('\r\n')
            player.logicFragments.findAll { it != null }.each { fragment ->
                if (fragment) {
                    def quantity = fragment.quantity > 1 ? " x${fragment.quantity}" : ""
                    files.append("r-- ${fragment.name}${quantity}\r\n")
                }
            }
        }
        
        return files.toString()
    }

    /**
     * Process chat commands when in heap mode - return true if message was sent
     */
    private boolean processChatCommand(String command, LambdaPlayer player) {
        String playerId = player.username
        boolean messageSent = false

        try {
            String cmd = command.trim()
            // Route EVERY heap command through the chat service — not just echo. This is what gives HUD
            // full heap parity: pay / pm / trade / offer / accept / list / help all work now. echo
            // broadcasts (it shows via the DB refresh below); the others return a direct response we
            // surface in the panel so the action is visible.
            String response = chatService.handleChatCommand(cmd, player, hudSessionWriters[playerId])
            messageSent = cmd.toLowerCase().startsWith('echo ')

            // Pull broadcast messages (echo, trade/payment notices) from the DB.
            refreshChatHistory(playerId)

            // Non-broadcast responses (list / pay / pm / trade / help) aren't DB chat — append them to the
            // panel so they're visible this turn. The next command's refresh rebuilds from the DB.
            if (response?.trim() && !messageSent) {
                if (heapChatHistory[playerId] == null) heapChatHistory[playerId] = []
                stripAnsiCodes(response).split('\r\n').each { line ->
                    if (line?.trim()) heapChatHistory[playerId] << ("» " + line.trim())
                }
            }
        } catch (Exception e) {
            if (heapChatHistory[playerId] == null) heapChatHistory[playerId] = []
            heapChatHistory[playerId] << ("» error: " + e.message)
        }

        return messageSent
    }

    /**
     * Refresh chat history from database for display - SIMPLE VERSION
     */
    private void refreshChatHistory(String playerId) {
        try {
            // Get recent chat messages from database
            def recentMessages = chatService.getRecentMessages(15)
            
            // Create super simple message format
            List<String> simpleMessages = []
            recentMessages.each { msg ->
                if (msg && msg.messageType == 'CHAT') {
                    // Only show actual chat messages, skip system messages
                    String simpleMsg = "${msg.senderName}: ${msg.message}"
                    simpleMessages.add(simpleMsg)
                }
            }
            
            // Update local chat history
            heapChatHistory[playerId] = simpleMessages
            
        } catch (Exception e) {
            // Simple fallback
            heapChatHistory[playerId] = ["Chat unavailable"]
        }
    }

    /**
     * Handle active repair mini-game session (like TelnetServerService does)
     */
    private String handleActiveRepairSession(String command, LambdaPlayer player, OutputStream outputStream) {
        String playerId = player.username
        try {
            // Abort: stop the session and show the outcome on the left command area.
            if (command.toLowerCase() in ['exit', 'quit']) {
                simpleRepairService.stopRepairSession(playerId)
                exitRepairTo(outputStream, player, 'repair', "Repair aborted.")
                return "CONTINUE_HUD_MODE"
            }

            // Any other input = lock the spinning digit.
            def enterResult = simpleRepairService.handleSpaceBarPress(playerId)

            // Game over (all slots locked) → leave repair mode and show a PLAIN outcome line. (The
            // service's BoxBuilder result box uses ╔═╗ box-drawing + emoji, which corrupt the HUD grid;
            // a plain string renders cleanly in the left command area.)
            if (!enterResult.continueGame || !simpleRepairService.isPlayerInRepairSession(playerId)) {
                String msg = (enterResult.message ?: '').toString().toUpperCase()
                String outcome
                if (enterResult.gameWon) {
                    outcome = "REPAIR SUCCESSFUL - coordinate restored, bits awarded."
                } else if (msg.contains('PRE-EMPTED')) {
                    outcome = "Repair pre-empted - another entity finished it first. No prize."
                } else {
                    outcome = "REPAIR FAILED - sequence mismatch. Type 'repair <x> <y>' to retry."
                }
                exitRepairTo(outputStream, player, 'repair', outcome)
                return "CONTINUE_HUD_MODE"
            }

            // Still cycling → re-render the in-place panel (reads fresh session state).
            renderFullScreen(outputStream, player)
            outputStream.write("\033[?25h".getBytes())
            outputStream.flush()
            return "CONTINUE_HUD_MODE"

        } catch (Exception e) {
            println "Error in HUD repair session: ${e.message}"
            renderFullScreen(outputStream, player)
            return "CONTINUE_HUD_MODE"
        }
    }

    /** Leave repair mode and render the given outcome text in the left command area. */
    private void exitRepairTo(OutputStream outputStream, LambdaPlayer player, String label, String outcome) {
        String playerId = player.username
        playersInRepairMode[playerId] = false
        repairOutputStreams.remove(playerId)
        storeCommandOutput(playerId, label, outcome)
        renderFullScreenWithCommand(outputStream, player, label, outcome)
        outputStream.write("\033[?25h".getBytes())
        outputStream.flush()
    }

    /**
     * Format message timestamp simply
     */
    private String formatMessageTime(def timestamp) {
        try {
            if (timestamp instanceof java.sql.Timestamp) {
                return new java.text.SimpleDateFormat('HH:mm').format(new Date(timestamp.getTime()))
            } else {
                return new java.text.SimpleDateFormat('HH:mm').format(timestamp)
            }
        } catch (Exception e) {
            return ""
        }
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
            
            boolean stop = false
            for (int i = 0; i < outputLines.length && !stop; i++) {
                String cleanLine = stripAnsiCodes(outputLines[i])
                // WRAP long lines onto extra rows instead of truncating, so nothing important (e.g. a
                // symbol's MISSING/ACQUIRED status at the end of a long line) is lost. Char-count wrapping
                // is emoji-safe: a wide glyph can only make a line wrap slightly early, never overflow.
                List<String> pieces = (cleanLine.length() <= COMMAND_AREA_WIDTH - 6) ? [cleanLine] : wrapText(cleanLine, COMMAND_AREA_WIDTH - 6)
                for (String piece : pieces) {
                    if (outputRow >= maxOutputRows) { truncated = true; stop = true; break }
                    writeToScreen(screen, outputRow, 3, piece)
                    outputRow++
                }
            }
            
            // Show truncation message if content was cut off
            if (truncated && outputRow < screen.length - 4) {
                writeToScreen(screen, outputRow, 3, "... (output truncated for HUD display)")
            }
        }
    }

    /**
     * Refresh HUD screen when new heap chat messages arrive from other users
     */
    def refreshHudScreenForHeapChat(OutputStream outputStream, LambdaPlayer player) {
        String playerId = player.username
        
        // Only refresh if player is in HUD mode AND in heap mode
        if (playersInHeapMode[playerId]) {
            try {
                // Refresh chat history to get latest messages
                refreshChatHistory(playerId)
                
                // Re-render full screen to show new messages
                renderFullScreen(outputStream, player)
                
                // Properly position cursor at the HUD prompt location (same as TelnetServerService does)
                def hudPrompt = getHudPrompt(player)
                def promptRow = getHudPromptRow(player)
                outputStream.write("\033[${promptRow};1H".getBytes()) // Move to prompt position
                outputStream.write("\033[K".getBytes()) // Clear line
                outputStream.write(hudPrompt.getBytes("UTF-8")) // Write prompt
                outputStream.write("\033[?25h".getBytes()) // Show cursor
                outputStream.flush()
            } catch (Exception e) {
                // If refresh fails, just continue silently
            }
        }
    }

    /**
     * Refresh HUD screen when repair cycling digits update - SAME PATTERN AS HEAP CHAT
     */
    // Pure "session state changed, re-render the panel" ping from the DigitCycler tick — the spinning
    // digit reads fresh from session state inside renderRepairSection, so no string/history is needed.
    def refreshHudScreenForRepairCycling(OutputStream outputStream, LambdaPlayer player) {
        String playerId = player.username
        if (playersInRepairMode[playerId] && simpleRepairService.isPlayerInRepairSession(playerId)) {
            try {
                renderFullScreen(outputStream, player)
                def hudPrompt = getHudPrompt(player)
                def promptRow = getHudPromptRow(player)
                outputStream.write("\033[${promptRow};1H".getBytes())
                outputStream.write("\033[K".getBytes())
                outputStream.write(hudPrompt.getBytes("UTF-8"))
                outputStream.write("\033[?25h".getBytes())
                outputStream.flush()
            } catch (Exception e) {
                println "Error refreshing HUD repair panel: ${e.message}"
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