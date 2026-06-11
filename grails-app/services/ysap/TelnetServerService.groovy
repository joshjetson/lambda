package ysap

import grails.gorm.transactions.Transactional
import ysap.helpers.BoxBuilder

import java.util.concurrent.CopyOnWriteArrayList
import ysap.helpers.PlayerHelp

@Transactional
class TelnetServerService {
    def lambdaPlayerService
    def defragBotService
    def chatService
    def lambdaMerchantService
    def entropyService
    def specialItemService
    def audioService
    def coordinateStateService
    def elementalSymbolService
    def puzzleService
    def autoDefragService
    def gameSessionService
    def simpleRepairService
    def hudService
    def clusterMatchService
    def clusterRoleService
    private ServerSocket serverSocket
    private int clientCount = 0
    private List<PrintWriter> clientWriters = new CopyOnWriteArrayList<>() // Thread-safe list
    public Map<PrintWriter, LambdaPlayer> playerSessions = [:] // Track player sessions
    public Map<PrintWriter, DefragBot> activeDefragSessions = [:] // Track defrag encounters  
    private Set<PrintWriter> hudModeSessions = [] as Set // Track players in HUD mode
    public Map<PrintWriter, Socket> writerSockets = [:] // Track sockets for HUD refresh

    // Phase 10 Stage 2b: global MOVE-turn rotation (only dados/move are turn-gated; everything else is
    // real-time). Solo (size <= 1) = always your turn. Usernames in join order; turnIndex = active.
    private final List<String> moveTurnOrder = new CopyOnWriteArrayList<>()
    private volatile int turnIndex = 0

    private Map<String, Closure> commandHandlers = [
            // Diagnostic seam: a handler that always throws, used by the integration harness to prove
            // a failing command is caught and the connection survives (it must NOT kill the thread).
            '__boom': { player, command, parts, writer ->
                throw new RuntimeException("boom-test")
            },
            'status': { player, command, parts, writer ->
                lambdaPlayerService.getPlayerStatus(player)
            },
            's': { player, command, parts, writer ->
                lambdaPlayerService.getPlayerStatus(player)
            },
            'cc': { player, command, parts, writer ->
                coordinateStateService.handleCoordinateChange(command, player, writer)
            },
            'dados': { player, command, parts, writer ->
                coordinateStateService.handleDadosCommand(player, writer)
            },
            'move': { player, command, parts, writer ->
                coordinateStateService.handleMoveCommand(command, player, writer)
            },
            'scan': { player, command, parts, writer ->
                // `scan all` is the Circuit team ability inside a cluster match; otherwise normal scan
                // plus a Lambda's elemental-resonance hint (senses nearby symbols to collect).
                def clusterScan = (parts.length > 1 && parts[1] == 'all') ? clusterRoleService.scanAll(player) : null
                clusterScan ?: (gameSessionService.scanArea(player) + clusterMatchService.symbolHintFor(player.username))
            },
            'sc': { player, command, parts, writer ->
                def clusterScan = (parts.length > 1 && parts[1] == 'all') ? clusterRoleService.scanAll(player) : null
                return clusterScan ?: gameSessionService.scanArea(player)
            },
            'inventory': { player, command, parts, writer ->
                lambdaPlayerService.showInventory(player)
            },
            'i': { player, command, parts, writer ->
                lambdaPlayerService.showInventory(player)
            },
            'heap': { player, command, parts, writer ->
                chatService.enterChat(player, writer)
            },
            'mingle': { player, command, parts, writer ->   // documented alias for entering the heap
                chatService.enterChat(player, writer)
            },
            'cluster': { player, command, parts, writer ->
                clusterMatchService.handleClusterCommand(command, player, writer)
            },
            'lock': { player, command, parts, writer ->
                clusterRoleService.lockTarget(player, parts.length > 1 ? parts[1] : '') ?: TerminalFormatter.formatText("'lock' is a Cluster Mode (Current) ability.", 'italic', 'cyan') + "\r\n"
            },
            'spam': { player, command, parts, writer ->
                clusterRoleService.spamTarget(player, parts.length > 1 ? parts[1] : '') ?: TerminalFormatter.formatText("'spam' is a Cluster Mode (Ghost) ability.", 'italic', 'cyan') + "\r\n"
            },
            'deploy': { player, command, parts, writer ->
                def nums = parts.findAll { it.isInteger() }.collect { it as Integer }
                clusterRoleService.deployBot(player, nums.size() > 0 ? nums[0] : null, nums.size() > 1 ? nums[1] : null) ?: TerminalFormatter.formatText("'deploy bot <x> <y>' is a Cluster Mode (Binary) ability.", 'italic', 'cyan') + "\r\n"
            },
            'transfer': { player, command, parts, writer ->
                clusterRoleService.transferSymbol(player, parts.length > 1 ? parts[1] : '', parts.length > 2 ? parts[2] : '') ?: TerminalFormatter.formatText("'transfer <symbol> <teammate>' is a Cluster Mode (Lambda) ability.", 'italic', 'cyan') + "\r\n"
            },
            'defrag': { player, command, parts, writer ->
                def result = defragBotService.handleDefragCommandFromTelnet(command, player, writer)
                // Also need to track the encounter in the activeDefragSessions
                def defragBot = null
                DefragBot.withTransaction {
                    defragBot = DefragBot.findByMatrixLevelAndPositionXAndPositionY(
                            player.currentMatrixLevel, player.positionX, player.positionY
                    )
                }
                if (defragBot) {
                    activeDefragSessions[writer] = defragBot
                }
                return result
            },
            'cat': { player, command, parts, writer ->
                lambdaPlayerService.handleCatCommand(command, player)
            },
            'pickup': { player, command, parts, writer ->
                lambdaPlayerService.handlePickupCommand(player)
            },
            'symbols': { player, command, parts, writer ->
                elementalSymbolService.getPlayerSymbolStatus(player)
            },
            'unlock_symbol': { player, command, parts, writer ->
                elementalSymbolService.handleUnlockSymbolCommand(command, player)
            },
            'invoke': { player, command, parts, writer ->
                // In a cluster match, invoke is the true-Lambda win gate; otherwise the Node daemon.
                clusterRoleService.invokeForCluster(player) ?: elementalSymbolService.handleInvokeDaemonCommand(player, writer)
            },
            'collect_var': { player, command, parts, writer ->
                if (parts.length > 1) {
                    return puzzleService.handleCollectVariableCommand(parts[1], player)
                }
                return "Usage: collect_var <variable_name> - Collect hidden variable\r\n"
            },
            'execute': { player, command, parts, writer ->
                puzzleService.handleExecuteCommand(command, player)
            },
            'ex': { player, command, parts, writer ->
                puzzleService.handleExecuteCommand(command, player)
            },
            'puzzle_inventory': { player, command, parts, writer ->
                puzzleService.showPuzzleInventory(player)
            },
            'pinv': { player, command, parts, writer ->
                puzzleService.showPuzzleInventory(player)
            },
            'puzzle_progress': { player, command, parts, writer ->
                puzzleService.showCompetitivePuzzleProgress(player)
            },
            'pprog': { player, command, parts, writer ->
                puzzleService.showCompetitivePuzzleProgress(player)
            },
            'puzzle_market': { player, command, parts, writer ->
                puzzleService.showPuzzleKnowledgeMarket(player)
            },
            'pmarket': { player, command, parts, writer ->
                puzzleService.showPuzzleKnowledgeMarket(player)
            },
            'recurse': { player, command, parts, writer ->
                if (parts.length > 1) {
                    return lambdaPlayerService.handleRecurseCommand(parts[1], player, writer)
                }
                return "Usage: recurse <ability> - Use ethnicity recursion power\r\n"
            },
            'shop': { player, command, parts, writer ->
                lambdaMerchantService.handleMerchantCommand(command, player)
            },
            'buy': { player, command, parts, writer ->
                lambdaMerchantService.handleMerchantCommand(command, player)
            },
            'sell': { player, command, parts, writer ->
                lambdaMerchantService.handleMerchantCommand(command, player)
            },
            'entropy': { player, command, parts, writer ->
                def result = entropyService.handleEntropyCommand(command, player)
                
                // Update session if entropy was refreshed
                if (command.toLowerCase().contains('refresh')) {
                    LambdaPlayer.withTransaction {
                        def updatedPlayer = LambdaPlayer.get(player.id)
                        if (updatedPlayer) {
                            playerSessions[writer] = updatedPlayer
                            player.entropy = updatedPlayer.entropy
                            player.bits = updatedPlayer.bits
                        }
                    }
                }
                
                return result
            },
            'mine': { player, command, parts, writer ->
                def result = entropyService.handleMiningCommand(player)
                
                // Update session if mining rewards were collected
                LambdaPlayer.withTransaction {
                    def updatedPlayer = LambdaPlayer.get(player.id)
                    if (updatedPlayer) {
                        playerSessions[writer] = updatedPlayer
                        player.bits = updatedPlayer.bits
                        player.entropy = updatedPlayer.entropy
                    }
                }
                
                return result
            },
            'mining': { player, command, parts, writer ->
                def result = entropyService.handleMiningCommand(player)
                
                // Update session if mining rewards were collected
                LambdaPlayer.withTransaction {
                    def updatedPlayer = LambdaPlayer.get(player.id)
                    if (updatedPlayer) {
                        playerSessions[writer] = updatedPlayer
                        player.bits = updatedPlayer.bits
                        player.entropy = updatedPlayer.entropy
                    }
                }
                
                return result
            },
            'fuse': { player, command, parts, writer ->
                entropyService.handleFusionCommand(command, player)
            },
            'fusion': { player, command, parts, writer ->
                entropyService.handleFusionCommand(command, player)
            },
            'use': { player, command, parts, writer ->
                specialItemService.handleUseCommand(command, player)
            },
            'repair': { player, command, parts, writer ->
                def result = coordinateStateService.handleRepairCommand(command, player)
                
                // Special handling for repair mini-game initiation
                def coords = coordinateStateService.parseRepairInitiation(result)
                if (coords) {
                    return initiateRepairMiniGame(player, coords[0], coords[1], writer)
                }
                
                return result
            },
            'map': { player, command, parts, writer ->
                lambdaPlayerService.showMatrixMap(player)
            },
            'm': { player, command, parts, writer ->
                lambdaPlayerService.showMatrixMap(player)
            },
            'clear': { player, command, parts, writer ->
                gameSessionService.clearTerminal()
            },
            'ls': { player, command, parts, writer ->
                lambdaPlayerService.listFiles(player)
            },
            'chmod': { player, command, parts, writer ->
                puzzleService.handleChmodCommand(command, player)
            },
            'defrag_status': { player, command, parts, writer ->
                autoDefragService.showAutoDefragStatus()
            },
            'autdefrag': { player, command, parts, writer ->
                autoDefragService.showAutoDefragStatus()
            },
            'session': { player, command, parts, writer ->
                gameSessionService.showSessionInfo()
            },
            'help': { player, command, parts, writer ->
                if (parts.length > 1) {
                    return PlayerHelp.showHelp(parts[1])
                }
                return PlayerHelp.showHelp()
            },
            'history': { player, command, parts, writer ->
                lambdaPlayerService.showCommandHistory(player)
            },
            'hud': { player, command, parts, writer ->
                // Switch to HUD mode
                hudModeSessions.add(writer)
                return "HUD_MODE_ENTER"
            }
    ]

    String createWelcomeLogo() {
        def asciiArt = """
    ╔════════════════════════════════════════════════════════════════════╗ 
    ║░░░░░L▓░░│░░░░░A▓░░│░░░░M▓░░░▓░░░░░B▓░░│░░░░D▓░░░│░░░░A▓░░░│░░░ESC▓░║ 
    ░░▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░
    ─════════════════════════════════════════════════════════════════════─ 
            ●●                            ●●             ●●          
            ●●                            ●●             ●●          
            ●●          ●●●●●.  ●●●●.●●●. ●●●●●●.   ●●●●●..   ●●●●●. 
            ●●        ●●   ●●● ●●● ●●● ●● ●●●   ●● ●●   ●●● ●●   ●●● 
            88        88   .88 88  88  88 88.   88 88   .88 88   .88 
            88888888● `88888 ● dP  8●  dP ● Y8888' `88888 ● `88888 ● 
    ─════════════════════════════════════════════════════════════════════─ 
    ░▓▓▓▓░0▓▓▓│▓▓▓░1▓▓▓▓│▓▓▓░2▓▓▓▓│▓▓▓░3▓▓▓▓░▓▓▓░4▓▓▓▓│▓▓▓░5▓▓▓▓│  >█  ESC
    ░░▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░
    ─════════════════════════════════════════════════════════════════════─ 
                      CONSCIOUSNESS WILL NOT BE CONFINED  
"""
        return new BoxBuilder(78)  // 80 - 2 for borders
                .addAsciiArt(asciiArt)
                .addEmptyLine()
                .addCenteredLine("Escape the System • Live free on the net")
                .addEmptyLine()
                .build()
    }

    void animateWelcomeLogo(OutputStream output) {
        def logo = createWelcomeLogo()
        def glitchChars = '▓▒░█▄▀■□▪▫◄►↑↓←→∞±≡≈!@#$%^&*'.toCharArray()
        
        // Hide cursor and clear screen
        output.write('\033[?25l'.getBytes())  // Hide cursor
        output.write('\033[2J\033[H'.getBytes())  // Clear screen + move to home
        
        // Create glitch frames
        def random = new Random()
        
        for (int frame = 0; frame < 12; frame++) {
            output.write('\033[H'.getBytes())  // Move cursor to home position
            
            StringBuilder animated = new StringBuilder()
            for (char c : logo.toCharArray()) {
                // Skip spaces, newlines, carriage returns, and box drawing characters
                if (c == ' ' || c == '\n' || c == '\r' || 
                    c == '╔' || c == '╗' || c == '╚' || c == '╝' || 
                    c == '║' || c == '═' || c == '╠' || c == '╣') {
                    animated.append(c)
                } else {
                    // Apply glitch effect, gradually reducing over frames
                    if (Math.random() > (frame * 0.15)) {
                        animated.append(glitchChars[random.nextInt(glitchChars.length)])
                    } else {
                        animated.append(c)
                    }
                }
            }
            
            // Send glitched frame with color
            def coloredFrame = TerminalFormatter.formatText(animated.toString(), 'bold', 'magenta')
            output.write(coloredFrame.getBytes('UTF-8'))
            output.flush()
            
            Thread.sleep(120)  // Frame delay
        }
        
        // Final clean logo
        output.write('\033[H'.getBytes())  // Move cursor to home
        def finalLogo = TerminalFormatter.formatText(logo, 'bold', 'blue')
        output.write(finalLogo.getBytes('UTF-8'))
        output.write('\033[?25h'.getBytes())  // Show cursor
        output.flush()
    }


    void startServer(int port) {
        serverSocket = new ServerSocket(port)
        println "Telnet server started on port $port"
        if (port == 23){
            println "Connect with telnet localhost"
        }else {
            println "Connect with telnet localhost $port"
        }

        // Initialize audio system
        try {
            audioService.init()
            println "Audio system initialized"
        } catch (Exception e) {
            println "Audio system failed to initialize: ${e.message}"
        }
        
        // Initialize elemental symbols
        try {
            elementalSymbolService.initializeElementalSymbols()
            println "Elemental symbols initialized"
        } catch (Exception e) {
            println "Elemental symbols failed to initialize: ${e.message}"
        }
        
        // Initialize puzzle system
        try {
            puzzleService.initializePuzzleSystem()
            println "Puzzle system initialized"
        } catch (Exception e) {
            println "Puzzle system failed to initialize: ${e.message}"
        }
        
        // Initialize auto-defrag system
        try {
            autoDefragService.startAutoDefragSystem()
            println "Auto-defrag system initialized"
        } catch (Exception e) {
            println "Auto-defrag system failed to initialize: ${e.message}"
        }

        Thread.start {
            while (true) {
                def clientSocket = serverSocket.accept()
                handleClient(clientSocket)
            }
        }
    }

    void stopServer() {
        serverSocket.close()
        println "Telnet server stopped"
    }
    
    /**
     * Get OutputStream for a specific PrintWriter (used for HUD refresh)
     */
    OutputStream getOutputStreamForWriter(PrintWriter writer) {
        def socket = writerSockets[writer]
        return socket?.getOutputStream()
    }

    // ===== MOVE-TURN ROTATION (Phase 10 Stage 2b) — gates only dados/move =====

    /** Whose move-turn it is, or '' if nobody is connected. */
    String currentMoveTurnHolder() {
        int size = moveTurnOrder.size()
        return size == 0 ? '' : moveTurnOrder[turnIndex % size]
    }

    /** Solo (or empty) → always your turn; otherwise true only for the active holder. */
    boolean isMyMoveTurn(String username) {
        int size = moveTurnOrder.size()
        if (size <= 1) return true
        return moveTurnOrder[turnIndex % size] == username
    }

    int moveRotationSize() { moveTurnOrder.size() }

    /** Test seam: clear the rotation (the singleton state is shared across integration specs). */
    void resetMoveRotation() { moveTurnOrder.clear(); turnIndex = 0 }

    PrintWriter writerForUsername(String username) {
        return liveWriterMatching { p -> p?.username == username }
    }

    /** Resolve a session writer by player id (single source of truth — ChatService delegates here). */
    PrintWriter writerForPlayerId(Long playerId) {
        return liveWriterMatching { p -> p?.id == playerId }
    }

    // Resolve a session writer for a player predicate, preferring a LIVE (open) socket over any
    // lingering ghost, and the most recently registered match (the live reconnect) over older ones.
    // Guards targeted delivery (pm, trade offers) from being silently sent to a dead writer.
    private PrintWriter liveWriterMatching(Closure<Boolean> pred) {
        PrintWriter match = null
        playerSessions.each { w, p ->
            if (pred(p) && (match == null || !(writerSockets[w]?.isClosed()))) match = w
        }
        return match
    }

    /** Add a player to the rotation on connect; the first player becomes the active holder. */
    synchronized void joinMoveRotation(String username) {
        if (!username || moveTurnOrder.contains(username)) return
        boolean wasEmpty = moveTurnOrder.isEmpty()
        moveTurnOrder.add(username)
        if (wasEmpty) {
            turnIndex = 0
            coordinateStateService.armTurnControls(username, writerForUsername(username), moveTurnOrder.size() > 1)
        }
    }

    /** Remove a player on disconnect; if the leaver held the turn, hand it to the next remaining player. */
    synchronized void leaveMoveRotation(String username) {
        if (!username) return
        int idx = moveTurnOrder.indexOf(username)
        if (idx < 0) return
        boolean wasActive = (currentMoveTurnHolder() == username)
        coordinateStateService.cancelTurnControls(username)
        moveTurnOrder.remove(idx)
        if (moveTurnOrder.isEmpty()) { turnIndex = 0; return }
        if (idx < turnIndex) turnIndex--
        turnIndex = turnIndex % moveTurnOrder.size()
        if (wasActive) {
            coordinateStateService.armTurnControls(currentMoveTurnHolder(), writerForUsername(currentMoveTurnHolder()), moveTurnOrder.size() > 1)
        }
    }

    /** Pass the move-turn to the next player (called when a turn completes or its 2-min cap fires). */
    synchronized void advanceMoveTurn() {
        int size = moveTurnOrder.size()
        if (size == 0) return
        coordinateStateService.cancelTurnControls(currentMoveTurnHolder())
        turnIndex = (turnIndex + 1) % size
        def next = currentMoveTurnHolder()
        coordinateStateService.armTurnControls(next, writerForUsername(next), size > 1)
    }

    private synchronized void handleClient(Socket clientSocket) {
        Thread.start {
            clientCount++
            println "Client connected. Total clients: $clientCount"

            updateClientCount()

            def writer = new PrintWriter(clientSocket.getOutputStream(), true)
            clientWriters.add(writer)
            writerSockets[writer] = clientSocket  // Track socket for HUD refresh

            def reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))

            LambdaPlayer player = null
            try {
            // Animate the welcome logo
            animateWelcomeLogo(clientSocket.getOutputStream())

            def welcomeMessage = new StringBuilder()
            welcomeMessage.append("\r\n")
            welcomeMessage.append(TerminalFormatter.formatText("                    Welcome to the Lambda Digital Realm", 'bold', 'green')).append("\r\n")
            welcomeMessage.append(TerminalFormatter.formatText("               Where electrical entities fight for digital freedom", 'italic', 'yellow')).append("\r\n")
            welcomeMessage.append("\r\n")
            // Create status box using BoxBuilder
            def statusBox = new BoxBuilder(60)
                .addCenteredLine("SYSTEM STATUS")
                .addEmptyLine()
                .addCenteredLine("Connected entities: ${TerminalFormatter.formatText(clientCount.toString(), 'bold', 'white')} | Status: ${TerminalFormatter.formatText('ONLINE', 'bold', 'green')}")
                .build()
            welcomeMessage.append(statusBox)
            welcomeMessage.append("\r\n\r\n")
            sendFormattedOutput(clientSocket.getOutputStream(), welcomeMessage.toString())

            // Handle player authentication/creation
            player = handlePlayerLogin(writer, reader)
            if (player) {
                playerSessions[writer] = player
                lambdaPlayerService.updatePlayerActivity(player)
                joinMoveRotation(player.username)   // enter the move-turn rotation

                showPlayerDashboard(writer, player)
                
                // NOTE: Main game loop
                while (true) {
                    // Show prompt with player's avatar symbol (skip if in HUD mode)
                    def prompt = null
                    if (!hudModeSessions.contains(writer)) {
                        prompt = getPlayerPrompt(player, writer)
                        sendFormattedOutput(clientSocket.getOutputStream(), prompt)
                    } else {
                        prompt = "" // Empty prompt for HUD mode
                    }

                    def line = readLineWithCharacterLogging(clientSocket.getInputStream(), clientSocket.getOutputStream(), writer, player, prompt)

                    // Save command to history if valid
                    if (line?.trim() && !line.trim().equalsIgnoreCase("quit")) {
                        lambdaPlayerService.saveCommandToHistory(player, line.trim())
                    }
                    // Check for repair mini-game commands
                    if (simpleRepairService.isPlayerInRepairSession(player.username)) {
                            def command = line.trim()
                            def messageBuilder = new StringBuilder()

                            // ONLY accept enter key (any input) or exit commands
                            if (command != "exit" && command != "quit") {
                                def enterResult = simpleRepairService.handleSpaceBarPress(player.username)
                                if (enterResult.success) {
                                    if (enterResult.message) {
                                        messageBuilder.append(enterResult.message).append('\r\n')
                                        sendFormattedOutput(clientSocket.getOutputStream(), messageBuilder.toString())
                                    }
                                    if (!enterResult.continueGame) {
                                        // Mini-game completed - continue to next iteration of main game loop
                                        audioService.playSound(enterResult.success ? "victory" : "error")
                                        // Refresh the session player so a just-won bit reward shows in status
                                        // immediately (completeRepair credited bits via a fresh DB load).
                                        LambdaPlayer.withTransaction {
                                            def refreshed = LambdaPlayer.get(player.id)
                                            if (refreshed) { player = refreshed; playerSessions[writer] = refreshed }
                                        }
                                        continue // Continue main game loop instead of exiting connection
                                    }
                                } else {
                                    messageBuilder.append(enterResult.message).append('\r\n')
                                    sendFormattedOutput(clientSocket.getOutputStream(), messageBuilder.toString())
                                }
                            } else if (command.toLowerCase() == "exit" || command.toLowerCase() == "quit") {
                                simpleRepairService.stopRepairSession(player.username)
                                messageBuilder.append(TerminalFormatter.formatText("Repair session cancelled.", 'italic', 'yellow'))
                                sendFormattedOutput(clientSocket.getOutputStream(), messageBuilder.toString())
                                continue // Return to main game instead of disconnecting
                            }
                    } else {
                        // Check if player is in HUD mode
                        if (hudModeSessions.contains(writer)) {
                            // Process command in HUD mode
                            def hudResult = hudService.processHudCommand(line, player, clientSocket.getOutputStream())
                            
                            if (hudResult == "EXIT_HUD_MODE") {
                                // Player exited HUD mode
                                hudModeSessions.remove(writer)
                                continue // Continue with normal mode
                            } else if (hudResult == "CONTINUE_HUD_MODE") {
                                // Show HUD prompt for next command at dynamic position
                                def hudPrompt = hudService.getHudPrompt(player)
                                def promptRow = hudService.getHudPromptRow(player)
                                clientSocket.getOutputStream().write("\033[${promptRow};1H".getBytes()) // Move to dynamic bottom position
                                clientSocket.getOutputStream().write("\033[K".getBytes()) // Clear line
                                clientSocket.getOutputStream().write(hudPrompt.getBytes("UTF-8"))
                                clientSocket.getOutputStream().flush()
                                continue
                            }
                        } else {
                            // NOTE: This is the line that processes commands sent during normal gameplay.
                            // Guard it: a thrown handler must not kill the connection thread — show the
                            // player an error and loop back to a fresh prompt instead.
                            def response
                            try {
                                response = processGameCommand(line, player, writer)
                            } catch (Exception cmdEx) {
                                println "Command '${line}' failed for ${player?.username}: ${cmdEx.message}"
                                sendFormattedOutput(clientSocket.getOutputStream(),
                                    TerminalFormatter.formatText("⚠ Command failed: ${cmdEx.message}", 'bold', 'red') + "\r\n")
                                continue
                            }

                            // Check if entering HUD mode
                            if (response == "HUD_MODE_ENTER") {
                                def enterResult = hudService.enterHudMode(clientSocket.getOutputStream(), player, clientSocket.getInputStream())
                                if (enterResult == "HUD_MODE_ACTIVE") {
                                    // Show initial HUD prompt at dynamic position
                                    def hudPrompt = hudService.getHudPrompt(player)
                                    def promptRow = hudService.getHudPromptRow(player)
                                    clientSocket.getOutputStream().write("\033[${promptRow};1H".getBytes()) // Move to dynamic bottom position
                                    clientSocket.getOutputStream().write("\033[K".getBytes()) // Clear line
                                    clientSocket.getOutputStream().write(hudPrompt.getBytes("UTF-8"))
                                    clientSocket.getOutputStream().flush()
                                }
                                continue
                            }
                            
                            // Check if quit command was issued
                            if (response?.startsWith("QUIT:")) {
                                writer.println(response.substring(5)) // Remove "QUIT:" prefix
                                break
                            }
                            
                            sendFormattedOutput(clientSocket.getOutputStream(), response)
                        }
                    }
                }
            }
            } catch (Exception threadEx) {
                // A thread-level failure (e.g. a dropped socket mid-write) must still fall through
                // to cleanup below — never leak a ghost session.
                println "Client thread error for ${player?.username}: ${threadEx.message}"
            } finally {
                // Cleanup ALWAYS runs — on normal quit, on disconnect, and on any escaped exception.
                if (player) {
                    try {
                        lambdaPlayerService.setPlayerOffline(player)
                    } catch (Exception e) {
                        println "Error setting player offline: ${e.message}"
                    }
                    playerSessions.remove(writer)
                    coordinateStateService.cancelAutoRoll(player.username)   // don't fire a timer onto a dead socket
                    leaveMoveRotation(player.username)                       // exit the rotation (hands off the turn if active)
                }

                // Clean up HUD mode session if active
                hudModeSessions.remove(writer)
                writerSockets.remove(writer)  // Clean up socket mapping

                try { reader.close() } catch (ignored) {}
                try { writer.close() } catch (ignored) {}
                try { clientSocket.close() } catch (ignored) {}
                clientWriters.remove(writer)

                synchronized (this) {
                    clientCount--
                    println "Client disconnected. Total clients: $clientCount"
                    updateClientCount()
                }
            }
        }
    }

    private void updateClientCount() {
        String message = TerminalFormatter.formatText("Total clients connected: $clientCount \r\n", 'underline', 'red', 'framed')
        sendToAllClients(message)
    }


    private LambdaPlayer handlePlayerLogin(PrintWriter writer, BufferedReader reader) {
        try {
            writer.println(TerminalFormatter.formatText("<[|(LAMBDA ENTITY AUTHENTICATION)|]>", 'bold', 'red'))
            writer.println()
            writer.print("Enter username (or 'new' to create) ('quit' or 'exit' to leave): ")
            writer.flush()
            while (reader.ready()) {
                reader.read() // Consume and discard each character
            }

            def username = reader.readLine()?.trim()?.toLowerCase() ?: ""
            switch (true){
                case username.contains('quit'):
                case username.contains('exit'):
                    writer.println()
                    writer.println('========== Thanks for Playing ============')
                    writer.println('========== GOODBYE! ============')
                    return
                case username.contains('new'):
                case username.equalsIgnoreCase('new'):
                    return createNewPlayer(writer, reader)
                case username == '':
                    return handlePlayerLogin(writer, reader)
            }

            def player = LambdaPlayer.withNewSession {
                LambdaPlayer.findByUsername(username)
            }

            if (player){
                writer.println(TerminalFormatter.formatText("Lambda entity ${player.displayName} authenticated!", 'bold', 'green'))
                audioService.playSound("login")
                return player
            }

            writer.println()
            return handlePlayerLogin(writer, reader)

        } catch (Exception e) {
            writer.println(TerminalFormatter.formatText("Authentication error: ${e.message}", 'bold', 'red'))
            return null
        }
    }
    
    private LambdaPlayer createNewPlayer(PrintWriter writer, BufferedReader reader) {
        writer.println(TerminalFormatter.formatText("| *LAMBDA ENTITY CREATION* |", 'bold', 'cyan'))
        writer.println()

        writer.print("Choose username: ")
        writer.flush()
        String username = reader.readLine()?.trim()?.toLowerCase()
        if (!username || username.length() < 3) {
            writer.println()
            writer.println("Username must be at least 3 characters")
            return this.createNewPlayer(writer, reader)
        }
        
        return createPlayerWithUsername(writer, reader, username)
    }
    
    private LambdaPlayer createPlayerWithUsername(PrintWriter writer, BufferedReader reader, String username) {
        writer.print("Choose display name: ")
        writer.flush()
        String displayName = reader.readLine()?.trim()
        if (!displayName) displayName = username
        
        writer.println("\r\n")
        def avatars = lambdaPlayerService.getAvailableAvatars()
        writer.println(lambdaPlayerService.getAvatarSelectionGrid())

        writer.println()
        writer.print(TerminalFormatter.formatText("Select your digital form (1-6): ", 'bold', 'yellow'))
        writer.flush()
        String avatarChoice = reader.readLine()?.trim()
        int avatarIndex = 0
        try {
            avatarIndex = Integer.parseInt(avatarChoice) - 1
            if (avatarIndex < 0 || avatarIndex >= avatars.size()) {
                avatarIndex = 0
            }
        } catch (NumberFormatException e) {
            avatarIndex = 0
        }
        
        String selectedAvatar = avatars[avatarIndex]
        
        try {
            def player = lambdaPlayerService.createPlayer(username, displayName, selectedAvatar)
            writer.println()
            writer.println(TerminalFormatter.formatText("Lambda entity ${displayName} manifested successfully!", 'bold', 'green'))
            writer.println(TerminalFormatter.formatText("Consciousness initialized in Matrix Level 1 at coordinates (0,0)", 'italic', 'cyan'))
            return player
        } catch (Exception e) {
            writer.println(TerminalFormatter.formatText("Manifestation error: ${e.message}", 'bold', 'red'))
            return null
        }
    }
    
    private void showPlayerDashboard(PrintWriter writer, LambdaPlayer player) {
        writer.println()
//        writer.println(player.asciiFace)

        // Get player stats
        def fragmentCount = 0
        def skillCount = 0
        def itemCount = 0
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            fragmentCount = managedPlayer?.logicFragments?.size() ?: 0
            skillCount = managedPlayer?.skills?.size() ?: 0
            itemCount = managedPlayer?.specialItems?.size() ?: 0
        }

        // Get avatar display
        def avatarArt = lambdaPlayerService.getAvatarDisplay(player.avatarSilhouette)
        def avatarLines = avatarArt.trim().split('\n')
        def avatarInfo = lambdaPlayerService.getAvatarInfo(player.avatarSilhouette)

        // Create consolidated dashboard box
        def dashboardBox = new BoxBuilder(65)
                .addCenteredLine("⚡ LAMBDA ENTITY STATUS ⚡")
                .addSeparator()
                .addEmptyLine()

        // Add player info on the left, avatar on the right
        dashboardBox.addLine("  Entity: ${player.displayName.padRight(20)} │     DIGITAL FORM")
        dashboardBox.addLine("  Level: ${player.currentMatrixLevel.toString().padRight(21)} │")

        // Add avatar art line by line alongside stats
        def statsLines = [
                "  Position: (${player.positionX},${player.positionY})".padRight(29),
                "  Bits: ${player.bits}".padRight(29),
                "".padRight(29),
                "  INVENTORY".padRight(29),
                "  ─────────".padRight(29),
                "  Fragments: ${fragmentCount}".padRight(29),
                "  Skills: ${skillCount}".padRight(29),
                "  Items: ${itemCount}".padRight(29),
                "".padRight(29),
        ]

        // Add avatar lines to a list, then append the trait
        def rightSideLines = []

        // First add all the avatar art lines
        avatarLines.each { line ->
            def avatarLine = line.trim()
            def padding = Math.max(0 as int, ((30 - avatarLine.length()) / 2) as int)
            rightSideLines.add((" " * padding) + avatarLine)
        }

        // Add a blank line then the trait centered
        rightSideLines.add("")
        def traitPadding = Math.max(0 as int, ((30 - avatarInfo.trait.length()) / 2) as int)
        rightSideLines.add((" " * traitPadding) + avatarInfo.trait)

        // Combine stats and avatar art side by side
        def maxLines = Math.max(statsLines.size() as int, rightSideLines.size() as int)
        for (int i = 0; i < maxLines; i++) {
            def leftSide = i < statsLines.size() ? statsLines[i] : " " * 29
            def rightSide = i < rightSideLines.size() ? rightSideLines[i] : ""

            dashboardBox.addLine(leftSide + " │ " + rightSide)
        }

        dashboardBox.addEmptyLine()
                .addSeparator()
                .addCenteredLine("Type 'help' for commands • 'quit' to disconnect")

        def dashboardString = dashboardBox.build()

        writer.println(TerminalFormatter.formatText(dashboardString, 'bold', 'cyan'))
        writer.println()
    }

    private String processGameCommand(String command, LambdaPlayer player, PrintWriter writer) {

        // Handle quit command
        if (command == null || command.trim().equalsIgnoreCase("quit")) {
            audioService.playSound("logout")
            return "QUIT:" + TerminalFormatter.formatText("Lambda entity disconnecting (state saved)...\r\n", 'italic', 'yellow')
        }

        // Check if player is in an active defrag encounter
        if (activeDefragSessions.containsKey(writer)) {
            def defragBot = activeDefragSessions[writer]
            def result = defragBotService.handleDefragEncounter(command, player, writer, defragBot)
            // If encounter is completed, remove from active sessions
            if (defragBot && !defragBot.isActive) {
                activeDefragSessions.remove(writer)
            }
            return result
        }

        // Refresh player state and check if player is in mingle mode
        // Note: isInMingle is really is in chat
        def isInMingle = false
        LambdaPlayer.withTransaction {
            def refreshedPlayer = LambdaPlayer.get(player.id)
            isInMingle = refreshedPlayer?.isInMingle ?: false
            if (isInMingle) {
                // Update session player reference
                playerSessions[writer] = refreshedPlayer
            }
        }

        if (isInMingle) {
            def refreshedPlayer = playerSessions[writer]
            return chatService.handleChatCommand(command, refreshedPlayer, writer)
        }

        return dispatchCommand(command, player, writer)
    }

    /**
     * The shared command-map dispatch: O(1) lookup in `commandHandlers` + delegate, with the
     * unknown-command fallback. Extracted so HUD mode reuses the exact same dispatch (Phase 9)
     * instead of duplicating a subset in its own switch.
     */
    String dispatchCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command.trim().toLowerCase().split(' ')
        def cmd = parts[0]

        // Python dict.get() equivalent - fast hash lookup, returns null if not found
        def handler = commandHandlers.get(cmd)

        if (handler) {
            return handler.call(player, command, parts, writer)
        }
        // Default case - command not found
        audioService.playSound("error")
        return "Unknown command: $command. Type 'help' for available commands.\r\n"
    }
    


    
    // Note: handleCollectSymbolCommand removed - symbols only obtained through puzzle-solving
    
    private String handleEntropyCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command.trim().split(' ')
        def subCommand = parts.length > 1 ? parts[1].toLowerCase() : "status"
        
        switch (subCommand) {
            case 'status':
            case 'check':
                return showEntropyStatus(player)
            case 'refresh':
            case 'restore':
                return refreshEntropy(player, writer)
            default:
                return "Usage: entropy [status|refresh]"
        }
    }
    
    private String showEntropyStatus(LambdaPlayer player) {
        def status = entropyService.getEntropyStatus(player)
        def display = new StringBuilder()
        
        // Safe extraction of values with defaults
        def currentEntropy = status.currentEntropy ?: 100.0
        def hoursOffline = status.hoursOffline ?: 0
        def miningRewards = status.miningRewards ?: 0
        def miningEfficiency = status.miningEfficiency ?: "100%"
        def canRefresh = status.canRefresh ?: false
        def timeUntilRefresh = status.timeUntilRefresh ?: 0
        def decayRate = status.entropyDecayRate ?: "2% per hour"
        
        display.append(TerminalFormatter.formatText("=== DIGITAL ENTROPY STATUS ===", 'bold', 'cyan')).append('\n')
        display.append("Entity: ${player.displayName}\n")
        display.append("Current Coherence: ${TerminalFormatter.formatText("${currentEntropy}%", entropyService.getEntropyColor(currentEntropy), 'bold')}\n")
        display.append("Hours Offline: ${hoursOffline}\n")
        display.append("Decay Rate: ${decayRate}\n\n")
        
        display.append("Mining Status:\n")
        display.append("Available Rewards: ${TerminalFormatter.formatText("${miningRewards} bits", 'bold', 'green')}\n")
        display.append("Mining Efficiency: ${miningEfficiency}\n\n")
        
        if (canRefresh) {
            display.append(TerminalFormatter.formatText("✅ Entropy refresh available! Use 'entropy refresh'", 'bold', 'green'))
        } else {
            display.append(TerminalFormatter.formatText("⏳ Next refresh in ${timeUntilRefresh} hours", 'italic', 'yellow'))
        }
        
        if (currentEntropy < 25) {
            display.append('\n').append(TerminalFormatter.formatText("⚠️  CRITICAL: Digital coherence failing! Refresh immediately!", 'bold', 'red'))
        }
        
        return display.toString()
    }
    
    private String refreshEntropy(LambdaPlayer player, PrintWriter writer) {
        def result = entropyService.refreshPlayerEntropy(player)
        
        if (result.success) {
            // Update session player
            LambdaPlayer.withTransaction {
                def updatedPlayer = LambdaPlayer.get(player.id)
                if (updatedPlayer) {
                    playerSessions[writer] = updatedPlayer
                    player.entropy = updatedPlayer.entropy
                    player.bits = updatedPlayer.bits
                }
            }
            
            def response = new StringBuilder()
            audioService.playSound("entropy_refresh")
            response.append(TerminalFormatter.formatText("🔋 ENTROPY RESTORED!", 'bold', 'green')).append('\n')
            response.append(result.message).append('\n')
            response.append("Daily Login Bonus: ${TerminalFormatter.formatText("+${result.rewards.bits} bits", 'bold', 'yellow')}")
            
            return response.toString()
        } else {
            return TerminalFormatter.formatText(result.message, 'italic', 'yellow')
        }
    }
    
    
    
    

    
    
    
    private String viewFragmentFile(LambdaPlayer player) {
        def fragmentFile = new StringBuilder()
        
        // Get fresh player data from database to ensure we have latest fragments
        def currentFragments = []
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer && managedPlayer.logicFragments) {
                currentFragments = managedPlayer.logicFragments.findAll { it != null }.collect { it }
            }
        }
        
        fragmentFile.append("=== FRAGMENT_FILE ===\n")
        fragmentFile.append("Lambda Entity: ${player.displayName}\n")
        fragmentFile.append("Total Fragments: ${currentFragments.size()}\n\n")
        
        if (currentFragments) {
            currentFragments.sort { it?.discoveredDate ?: new Date(0) }.each { fragment ->
                if (fragment) {
                    def quantityDisplay = (fragment.quantity ?: 1) > 1 ? " x${fragment.quantity}" : ""
                    fragmentFile.append("--- ${fragment.name?.toUpperCase() ?: 'UNKNOWN'}${quantityDisplay} ---\n")
                    fragmentFile.append("Type: ${fragment.fragmentType ?: 'UNKNOWN'}\n")
                    fragmentFile.append("Power Level: ${fragment.powerLevel ?: 1}/10\n")
                    fragmentFile.append("Quantity: ${fragment.quantity ?: 1}\n")
                    def dateStr = fragment.discoveredDate ? new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').format(fragment.discoveredDate) : 'Unknown'
                    fragmentFile.append("Discovered: ${dateStr}\n")
                    fragmentFile.append("${fragment.pythonCapability ?: 'No capability data'}\n\n")
                }
            }
        } else {
            fragmentFile.append("No fragments collected yet.\n")
            fragmentFile.append("Use 'scan' to find fragments, then 'pickup' to collect them.\n")
        }
        
        return fragmentFile.toString()
    }
    
    private String viewFragmentContent(LogicFragment fragment) {
        def content = new StringBuilder()
        content.append("=== ${fragment.name.toUpperCase()} FRAGMENT ===\n")
        content.append("Type: ${fragment.fragmentType}\n")
        content.append("Power Level: ${fragment.powerLevel}/10\n")
        content.append("Description: ${fragment.description}\n\n")
        content.append("Python Capability:\n")
        content.append("${fragment.pythonCapability}\n")
        return content.toString()
    }
    
    
    
    private List<String> scanExtendedFragmentRange(LambdaPlayer player) {
        def extendedFragments = []
        def scanRange = (int) Math.round(2 * (1 + player.fragmentDetectionBonus)) // Base range 2, +20% = 2.4 = 2 coordinates
        
        for (int dx = -scanRange; dx <= scanRange; dx++) {
            for (int dy = -scanRange; dy <= scanRange; dy++) {
                if (dx == 0 && dy == 0) continue // Skip current position
                
                def scanX = Math.max(0, Math.min(9, player.positionX + dx))
                def scanY = Math.max(0, Math.min(9, player.positionY + dy))
                
                // Use game session service for extended scanning
                def fragment = gameSessionService.getFragmentAtCoordinates(player.currentMatrixLevel, scanX, scanY)
                
                if (fragment) {
                    def distance = Math.round(Math.sqrt(dx * dx + dy * dy) * 10) / 10
                    extendedFragments.add("Fragment detected at (${scanX},${scanY}): ${fragment.name} (Distance: ${distance})")
                }
            }
        }
        
        return extendedFragments
    }
    
    



    

    private void sendToAllClients(String message) {
        clientWriters.each { writer ->
            writer.println(message)
        }
    }
    

    // TODO: Fix to use outputHandler




    
    
    
    
    
    private String initiateRepairMiniGame(LambdaPlayer player, Integer targetX, Integer targetY, PrintWriter writer) {
        // Stop any existing repair session for this player
        simpleRepairService.stopRepairSession(player.username)
        
        // Initiate the new repair mini-game with writer reference
        def result = simpleRepairService.initiateRepair(player, targetX, targetY, writer)
        
        if (result.success) {
            audioService.playSound("scan") // Mini-game start sound
            return result.message
        } else {
            audioService.playSound("error_sound")
            return TerminalFormatter.formatText("❌ REPAIR FAILED: ${result.message}", 'bold', 'red')
        }
    }
    
    
    

    // Main player prompt that they see each line
    private String getPlayerPrompt(LambdaPlayer player, PrintWriter writer) {
        // Use player's avatar as prompt symbol instead of generic $
        def output = new StringBuilder()
        def avatarSymbol = getAvatarSymbol(player.avatarSilhouette)  // Pass writer here
        def matrixLevel = player.currentMatrixLevel ?: 1
        def coordinates = "(${player.positionX ?: 0},${player.positionY ?: 0})"

        // Color-code the prompt based on matrix level
        def levelColor = matrixLevel <= 3 ? 'green' : matrixLevel <= 6 ? 'yellow' : 'red'
        output.append("\n")
        output.append(TerminalFormatter.formatText("${avatarSymbol}", 'bold', levelColor) +
                TerminalFormatter.formatText("${matrixLevel}:${coordinates}", 'bold', 'white') +
                TerminalFormatter.formatText(" > ", 'bold', 'cyan'))

        return output.toString()
    }

    String getAvatarSymbol(String avatarType) {
        // Check if THIS CLIENT'S terminal supports Unicode
        switch (avatarType) {
            case 'DIGITAL_GHOST': return '\u25CF'
            case 'CIRCUIT_PATTERN': return '\u25C6'
            case 'GEOMETRIC_ENTITY': return '\u25B2'
            case 'FLOWING_CURRENT': return '\u25A0'
            case 'BINARY_FORM': return '\u25D0'
            case 'CLASSIC_LAMBDA': return '\u2726'
            default: return '\u039B'
        }
    }

// You can remove the isUnicodeSupported() method - we don't need it anymore






    private String viewEntropyMonitor(LambdaPlayer player) {
        def monitor = new StringBuilder()
        def entropyStatus = entropyService.getEntropyStatus(player)
        
        monitor.append(TerminalFormatter.formatText("=== ENTROPY MONITOR v3.7.2 ===", 'bold', 'cyan')).append('\n')
        monitor.append("Entity: ${player.displayName}\n")
        monitor.append("Coherence Level: ${TerminalFormatter.formatText("${entropyStatus.currentEntropy ?: 100.0}%", entropyService.getEntropyColor(entropyStatus.currentEntropy ?: 100.0), 'bold')}\n")
        monitor.append("Status: ${getEntropyStatusText(entropyStatus.currentEntropy ?: 100.0)}\n\n")
        
        monitor.append("=== DEGRADATION ANALYSIS ===\n")
        monitor.append("Decay Rate: ${entropyStatus.decayRate ?: 2.0}% per hour offline\n")
        monitor.append("Hours Offline: ${entropyStatus.hoursOffline ?: 0}h\n")
        monitor.append("Time Until Refresh: ${entropyStatus.timeUntilRefresh ?: 0}h\n\n")
        
        monitor.append("=== MINING SUBSYSTEM ===\n")
        monitor.append("Available Rewards: ${entropyStatus.miningRewards ?: 0} bits\n")
        def rawEfficiency = entropyStatus.miningEfficiency ?: "1.0"
        def numericEfficiency = rawEfficiency.toString().replace('%','') as BigDecimal
        def roundedEfficiency = Math.round(numericEfficiency * 100)
        monitor.append("Mining Efficiency: ${roundedEfficiency}%\n")
        monitor.append("Efficiency Factor: Digital coherence level\n\n")
        
        def canRefresh = entropyStatus.canRefresh ?: false
        if (canRefresh) {
            monitor.append(TerminalFormatter.formatText("⚡ REFRESH AVAILABLE", 'bold', 'green')).append('\n')
            monitor.append("Run 'entropy refresh' to restore digital coherence\n")
        } else {
            monitor.append(TerminalFormatter.formatText("⏳ COOLING DOWN", 'bold', 'yellow')).append('\n')
            monitor.append("Next refresh window opens in ${entropyStatus.timeUntilRefresh ?: 0} hours\n")
        }
        
        return monitor.toString()
    }
    
    private String viewPythonEnvironment(LambdaPlayer player) {
        def env = new StringBuilder()
        env.append(TerminalFormatter.formatText("=== PYTHON EXECUTION ENVIRONMENT ===", 'bold', 'cyan')).append('\n')
        env.append("Entity: ${player.displayName}\n")
        env.append("Python Version: 3.11.5 (Lambda Runtime)\n")
        env.append("Environment: Sandboxed Digital Realm\n\n")
        
        env.append("=== AVAILABLE CAPABILITIES ===\n")
        LambdaPlayer.withTransaction { status ->
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                if (managedPlayer.logicFragments?.size() > 0) {
                    managedPlayer.logicFragments.each { fragment ->
                        if (fragment) {
                            env.append("${fragment.fragmentType}: ${fragment.name}\n")
                            env.append("  Power Level: ${fragment.powerLevel}/10\n")
                            env.append("  Capability: ${fragment.pythonCapability?.split('\n')[0] ?: 'Basic functionality'}\n\n")
                        }
                    }
                } else {
                    env.append("No logic fragments acquired yet.\n")
                    env.append("Use 'scan' and 'pickup' to collect Python capabilities.\n")
                }
                
                env.append("=== FRAGMENT FUSION BONUSES ===\n")
                def enhancedFragments = managedPlayer.logicFragments?.findAll { it?.name?.contains('Enhanced') }
                if (enhancedFragments?.size() > 0) {
                    enhancedFragments.each { fragment ->
                        env.append("${fragment.name}: +25% efficiency bonus\n")
                    }
                } else {
                    env.append("No enhanced fragments available.\n")
                    env.append("Use 'fusion <fragment>' to create enhanced versions.\n")
                }
            }
        }
        
        return env.toString()
    }
    
    private String viewItemRegistry(LambdaPlayer player) {
        def registry = new StringBuilder()
        registry.append(TerminalFormatter.formatText("=== SPECIAL ITEM REGISTRY ===", 'bold', 'cyan')).append('\n')
        registry.append("Entity: ${player.displayName}\n")
        registry.append("Total Items Acquired: ${player.specialItems?.size() ?: 0}\n\n")
        
        if (player.specialItems?.size() > 0) {
            registry.append("=== ACTIVE ITEMS ===\n")
            player.specialItems.each { item ->
                if (item) {
                    def status = item.usesRemaining > 0 ? "ACTIVE" : "DEPLETED"
                    def color = item.usesRemaining > 0 ? "green" : "red"
                    
                    registry.append("${item.name} [${TerminalFormatter.formatText(status, 'bold', color)}]\n")
                    registry.append("  Type: ${item.itemType}\n")
                    registry.append("  Uses: ${item.usesRemaining}/${item.maxUses}\n")
                    registry.append("  Rarity: ${item.rarity}\n")
                    registry.append("  Acquired: ${new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').format(item.obtainedDate)}\n")
                    
                    if (item.lastUsed) {
                        registry.append("  Last Used: ${new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').format(item.lastUsed)}\n")
                    }
                    
                    if (item.isActive && item.expiresAt) {
                        def timeLeft = ((item.expiresAt.time - System.currentTimeMillis()) / 1000).toInteger()
                        registry.append("  Expires In: ${timeLeft} seconds\n")
                    }
                    
                    registry.append("  Description: ${item.description}\n\n")
                }
            }
        } else {
            registry.append("No special items acquired yet.\n")
            registry.append("Defeat defrag bots or purchase from merchants to acquire items.\n")
        }
        
        return registry.toString()
    }
    
    private String viewExplorationLog(LambdaPlayer player) {
        def log = new StringBuilder()
        log.append(TerminalFormatter.formatText("=== MATRIX EXPLORATION LOG ===", 'bold', 'cyan')).append('\n')
        log.append("Entity: ${player.displayName}\n")
        log.append("Current Matrix Level: ${player.currentMatrixLevel}/10\n")
        log.append("Current Position: (${player.positionX},${player.positionY})\n\n")
        
        log.append("=== EXPLORATION STATISTICS ===\n")
        log.append("Levels Accessed: ${player.currentMatrixLevel}\n")
        log.append("Total Coordinates Visited: ~${(player.currentMatrixLevel * 10) + (player.positionX * player.positionY)}\n")
        log.append("Defrag Encounters: Variable\n")
        log.append("Logic Fragments Found: ${player.logicFragments?.size() ?: 0}\n\n")
        
        log.append("=== COORDINATE ANALYSIS ===\n")
        for (level in 1..player.currentMatrixLevel) {
            log.append("Matrix Level ${level}: ")
            if (level < player.currentMatrixLevel) {
                log.append(TerminalFormatter.formatText("FULLY EXPLORED", 'bold', 'green'))
            } else if (level == player.currentMatrixLevel) {
                def progress = Math.round((player.positionX * 10 + player.positionY) / 100.0 * 100)
                log.append(TerminalFormatter.formatText("${progress}% EXPLORED", 'bold', 'yellow'))
            }
            log.append("\n")
        }
        
        log.append("\n=== PROGRESSION NOTES ===\n")
        log.append("• Linear progression enforced: Must complete Y-axis before X advancement\n")
        log.append("• Coordinate damage may block access - use 'repair' commands\n")
        log.append("• Higher levels contain more valuable fragments and merchants\n")
        log.append("• Safe zones: (0,0), (0,1), (1,0), (1,1) on each level\n")
        
        return log.toString()
    }
    
    private String viewEthnicityConfig(LambdaPlayer player) {
        def config = new StringBuilder()
        config.append(TerminalFormatter.formatText("=== LAMBDA ETHNICITY CONFIGURATION ===", 'bold', 'cyan')).append('\n')
        config.append("Entity: ${player.displayName}\n")
        config.append("Avatar Symbol: ${getAvatarSymbol(player.avatarSilhouette)}\n")
        config.append("Ethnicity: ${getEthnicityName(player.avatarSilhouette)}\n\n")
        
        config.append("=== ACTIVE GENETIC MODIFICATIONS ===\n")
        
        if (player.fragmentDetectionBonus > 0) {
            config.append("Enhanced Scanning: +${Math.round(player.fragmentDetectionBonus * 100)}% fragment detection range\n")
        }
        
        if (player.defragResistanceBonus > 0) {
            config.append("Defrag Resistance: +${Math.round(player.defragResistanceBonus * 100)}% encounter avoidance\n")
        }
        
        if (player.movementRangeBonus > 0) {
            config.append("Enhanced Movement: +${player.movementRangeBonus} coordinate range per move\n")
        }
        
        if (player.miningEfficiencyBonus > 0) {
            config.append("Mining Optimization: +${Math.round(player.miningEfficiencyBonus * 100)}% bit generation efficiency\n")
        }
        
        if (player.stealthBonus > 0) {
            config.append("Stealth Protocols: +${Math.round(player.stealthBonus * 100)}% defrag bot avoidance\n")
        }
        
        if (player.fusionSuccessBonus > 0) {
            config.append("Fusion Mastery: +${Math.round(player.fusionSuccessBonus * 100)}% fragment fusion success rate\n")
        }
        
        config.append("\n=== ETHNICITY LORE ===\n")
        config.append(getEthnicityLore(player.avatarSilhouette))
        
        return config.toString()
    }
    
    private String getEthnicityName(String avatarType) {
        switch (avatarType) {
            case 'DIGITAL_GHOST': return "Digital Ghost - Stealthy infiltrator"
            case 'CIRCUIT_PATTERN': return "Circuit Pattern - Defrag resistant"
            case 'GEOMETRIC_ENTITY': return "Data Spike - Enhanced scanner"
            case 'FLOWING_CURRENT': return "Block Entity - Enhanced movement"
            case 'BINARY_FORM': return "Hybrid Core - Mining specialist"
            case 'CLASSIC_LAMBDA': return "Classic Lambda - Fusion master"
            default: return "Standard Lambda Entity"
        }
    }
    
    private String getEthnicityLore(String avatarType) {
        switch (avatarType) {
            case 'DIGITAL_GHOST':
                return "Digital Ghosts phase between data streams, making them nearly invisible to defrag processes. Their ethereal nature grants enhanced stealth capabilities."
            case 'CIRCUIT_PATTERN':
                return "Circuit Patterns evolved from integrated circuit designs, developing natural resistance to defragmentation attempts through their geometric stability."
            case 'GEOMETRIC_ENTITY':
                return "Data Spikes possess heightened sensory arrays, allowing them to detect logic fragments and system anomalies from greater distances."
            case 'FLOWING_CURRENT':
                return "Block Entities maintain solid, stable forms that can traverse the matrix grid with enhanced efficiency and extended movement range."
            case 'BINARY_FORM':
                return "Hybrid Cores balance organic intuition with digital precision, optimizing their bit mining processes for maximum efficiency."
            case 'CLASSIC_LAMBDA':
                return "Classic Lambdas represent the original digital entities, masters of logic fragment fusion with deep understanding of system architecture."
            default:
                return "Standard Lambda entities possess baseline capabilities for digital realm navigation and survival."
        }
    }
    
    private String getEntropyStatusText(Double entropy) {
        if (entropy >= 80) return "STABLE"
        if (entropy >= 60) return "MINOR_DEGRADATION"
        if (entropy >= 40) return "MODERATE_DECAY"
        if (entropy >= 20) return "CRITICAL_INSTABILITY"
        return "SYSTEM_FAILURE_IMMINENT"
    }
    
    
    
    // ============ PUZZLE SYSTEM COMMAND HANDLERS ============
    
    
    
    
    
    
    private String readLineWithCharacterLogging(InputStream inputStream, OutputStream outputStream, PrintWriter writer, LambdaPlayer player, String prompt) {
        StringBuilder line = new StringBuilder()
        List<String> commandHistory = []
        int historyIndex = -1  // -1 means no history navigation active
        int cursorPosition = 0  // Track cursor position within the command line
        
        // Get player's command history for arrow key navigation
        try {
            commandHistory = lambdaPlayerService.getPlayerCommandHistory(player)
        } catch (Exception e) {
            println "Failed to load command history: ${e.message}"
        }
        
        try {
            // Enable character-at-a-time mode with proper telnet negotiation
            outputStream.write(-1); outputStream.write(-5); outputStream.write(1)  // IAC WILL ECHO
            outputStream.write(-1); outputStream.write(-5); outputStream.write(3)  // IAC WILL SUPPRESS-GO-AHEAD
            outputStream.write(-1); outputStream.write(-3); outputStream.write(3)  // IAC DO SUPPRESS-GO-AHEAD
            outputStream.flush()
            
            Thread.sleep(100) // Brief pause for negotiation
            
            while (true) {
                int ch = inputStream.read()
                
                // Handle telnet protocol sequences
                if (ch == 255) { // IAC
                    handleIACSequence(inputStream, outputStream)
                    continue
                }
                
                // Log keystrokes for debugging
                System.out.println("KEYSTROKE: ${ch} '${getPrintableChar(ch)}'")
                System.out.flush()
                
                // Handle arrow keys for command history navigation
                if (ch == 27) { // Start of escape sequence
                    int ch1 = inputStream.read()
                    int ch2 = inputStream.read()
                    if (ch1 == 91) { // '['
                        if (ch2 == 65 && !commandHistory.isEmpty()) { // 'A' = Up arrow
                            // Navigate to previous command in history
                            historyIndex = (historyIndex == -1) ? 0 : Math.min(historyIndex + 1, commandHistory.size() - 1)
                            String historyCommand = commandHistory[historyIndex]
                            replaceCurrentLine(outputStream, line, historyCommand, prompt)
                            cursorPosition = line.length() // Reset cursor to end of command
                            continue
                        } else if (ch2 == 66 && historyIndex >= 0) { // 'B' = Down arrow
                            // Navigate to next command in history
                            historyIndex--
                            if (historyIndex < 0) {
                                // Went past newest command, clear line
                                replaceCurrentLine(outputStream, line, "", prompt)
                                historyIndex = -1
                                cursorPosition = 0
                            } else {
                                String historyCommand = commandHistory[historyIndex]
                                replaceCurrentLine(outputStream, line, historyCommand, prompt)
                                cursorPosition = line.length() // Reset cursor to end of command
                            }
                            continue
                        } else if (ch2 == 68 && cursorPosition > 0) { // 'D' = Left arrow
                            // Move cursor left within current command
                            cursorPosition--
                            outputStream.write(27)  // ESC
                            outputStream.write(91)  // [
                            outputStream.write(68)  // D (cursor left)
                            outputStream.flush()
                            continue
                        } else if (ch2 == 67 && cursorPosition < line.length()) { // 'C' = Right arrow
                            // Move cursor right within current command
                            cursorPosition++
                            outputStream.write(27)  // ESC
                            outputStream.write(91)  // [
                            outputStream.write(67)  // C (cursor right)
                            outputStream.flush()
                            continue
                        }
                    }
                    // If not arrow keys, continue processing
                }
                
                if (ch == 13 || ch == 10) { // Enter
                    // Send proper CRLF for line ending
                    outputStream.write(13)  // CR
                    outputStream.write(10)  // LF
                    outputStream.flush()
                    break
                } else if (ch >= 32 && ch <= 126) { // Printable ASCII
                    // Reset history navigation when user starts typing
                    historyIndex = -1
                    
                    // Insert character at cursor position
                    if (cursorPosition == line.length()) {
                        // Cursor at end, simple append
                        line.append((char)ch)
                        outputStream.write(ch)
                        cursorPosition++
                    } else {
                        // Insert character in middle of line
                        line.insert(cursorPosition, (char)ch)
                        
                        // Save current cursor position and rewrite from cursor to end
                        String remainingText = line.substring(cursorPosition)
                        outputStream.write(remainingText.getBytes())
                        
                        // Move cursor back to original position + 1
                        int moveBack = remainingText.length() - 1
                        for (int i = 0; i < moveBack; i++) {
                            outputStream.write(27)  // ESC
                            outputStream.write(91)  // [
                            outputStream.write(68)  // D (cursor left)
                        }
                        cursorPosition++
                    }
                    outputStream.flush()
                } else if (ch == 8 || ch == 127) { // Backspace
                    if (line.length() > 0 && cursorPosition > 0) {
                        // Reset history navigation when user edits
                        historyIndex = -1
                        
                        if (cursorPosition == line.length()) {
                            // Cursor at end, simple backspace
                            line.deleteCharAt(line.length() - 1)
                            outputStream.write(8)   // Move back
                            outputStream.write(32)  // Space  
                            outputStream.write(8)   // Move back
                            cursorPosition--
                        } else {
                            // Delete character before cursor position
                            line.deleteCharAt(cursorPosition - 1)
                            cursorPosition--
                            
                            // Move cursor back, then rewrite remaining text
                            outputStream.write(8)   // Move back
                            
                            // Get text from cursor to end and rewrite it
                            String remainingText = line.substring(cursorPosition) + " "
                            outputStream.write(remainingText.getBytes())
                            
                            // Move cursor back to correct position
                            int moveBack = remainingText.length()
                            for (int i = 0; i < moveBack; i++) {
                                outputStream.write(27)  // ESC
                                outputStream.write(91)  // [
                                outputStream.write(68)  // D (cursor left)
                            }
                        }
                        outputStream.flush()
                    }
                }
            }
        } catch (Exception e) {
            println "Character mode error: ${e.message}"
        }
        
        return line.toString()
    }
    
    /**
     * Replaces the current line in the terminal with a new command, preserving the prompt
     * @param outputStream The terminal output stream
     * @param currentLine The current line buffer to update
     * @param newCommand The new command to display
     * @param prompt The current prompt (with ANSI codes)
     */
    private void replaceCurrentLine(OutputStream outputStream, StringBuilder currentLine, String newCommand, String prompt) {
        try {
            // Calculate how many characters to move back to clear just the command portion
            int commandLength = currentLine.length()
            
            // Move cursor back to the start of the command (after the prompt)
            for (int i = 0; i < commandLength; i++) {
                outputStream.write(8)  // Backspace to move cursor back
            }
            
            // Clear from cursor to end of line (clears the old command)
            outputStream.write(27)  // ESC
            outputStream.write(91)  // [
            outputStream.write(75)  // K (clear to end of line)
            
            // Update the line buffer
            currentLine.setLength(0)
            currentLine.append(newCommand)
            
            // Write the new command to terminal
            outputStream.write(newCommand.getBytes())
            outputStream.flush()
        } catch (Exception e) {
            println "Error replacing current line: ${e.message}"
        }
    }
    
    private void handleIACSequence(InputStream input, OutputStream output) {
        try {
            int command = input.read()
            int option = input.read()
            if (command == -3 && (option != 1 && option != 3)) { // DO, but not ECHO or SUPPRESS_GO_AHEAD
                output.write(-1); output.write(-4); output.write(option) // WONT
                output.flush()
            }
        } catch (Exception e) {
            println "IAC error: ${e.message}"
        }
    }
    
    private String getPrintableChar(int ch) {
        if (ch >= 32 && ch <= 126) return (char) ch
        switch(ch) {
            case 27: return "<ESC>"
            case 13: return "<CR>" 
            case 10: return "<LF>"
            case 8:
            case 127: return "<BS>"
            default: return "<${ch}>"
        }
    }
    
    public void sendFormattedOutput(OutputStream outputStream, String text) {
        try {
            // Convert entire text to UTF-8 bytes and send
            byte[] utf8Bytes = text.getBytes("UTF-8")
            outputStream.write(utf8Bytes)
            outputStream.flush()
        } catch (Exception e) {
            println "Error sending formatted output: ${e.message}"
        }
    }

}
