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
    private ServerSocket serverSocket
    private int clientCount = 0
    private List<PrintWriter> clientWriters = new CopyOnWriteArrayList<>() // Thread-safe list
    public Map<PrintWriter, LambdaPlayer> playerSessions = [:] // Track player sessions
    public Map<PrintWriter, DefragBot> activeDefragSessions = [:] // Track defrag encounters
    private Map<String, Closure> commandHandlers = [
            'status': { player, command, parts, writer ->
                lambdaPlayerService.getPlayerStatus(player)
            },
            'cc': { player, command, parts, writer ->
                coordinateStateService.handleCoordinateChange(command, player, writer)
            },
            'scan': { player, command, parts, writer ->
                gameSessionService.scanArea(player)
            },
            'inventory': { player, command, parts, writer ->
                lambdaPlayerService.showInventory(player)
            },
            'heap': { player, command, parts, writer ->
                chatService.enterChat(player, writer)
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
            'collect_var': { player, command, parts, writer ->
                if (parts.length > 1) {
                    return puzzleService.handleCollectVariableCommand(parts[1], player)
                }
                return "Usage: collect_var <variable_name> - Collect hidden variable\r\n"
            },
            'execute': { player, command, parts, writer ->
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
                if (result.startsWith("INITIATE_REPAIR:")) {
                    def coords = result.split(":")[1].split(",")
                    def targetX = Integer.parseInt(coords[0])
                    def targetY = Integer.parseInt(coords[1])
                    return initiateRepairMiniGame(player, targetX, targetY, writer)
                }
                
                return result
            },
            'map': { player, command, parts, writer ->
                lambdaPlayerService.showMatrixMap(player)
            },
            'clear': { player, command, parts, writer ->
                clearTerminal()
            },
            'ls': { player, command, parts, writer ->
                listFiles(player)
            },
            'chmod': { player, command, parts, writer ->
                handleChmodCommand(command, player)
            },
            'defrag_status': { player, command, parts, writer ->
                showAutoDefragStatus()
            },
            'autdefrag': { player, command, parts, writer ->
                showAutoDefragStatus()
            },
            'session': { player, command, parts, writer ->
                showSessionInfo()
            },
            'help': { player, command, parts, writer ->
                if (parts.length > 1) {
                    return PlayerHelp.showHelp(parts[1])
                }
                return PlayerHelp.showHelp()
            },
            'history': { player, command, parts, writer ->
                showCommandHistory(player)
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
        // STYLE 1: TRON GRID (Current)
        def asciiArt2 = """
    ╔░▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░
    ▓░────────┬─────────┬─────────┬─────────┬─────────┬─────────┬─────────╗
    ▓░░░░░L▓░░│░░░░░A▓░░│░░░░M▓░░░▓░░░░░B▓░░│░░░░D▓░░░│░░░░A▓░░░│░░░ESC▓░░│
    ▓░────────┴─────────┴─────────┴─────────┴─────────┴─────────┴─────|───┤
    ▓░►≈░██╗   ▫□░███░╗ ▫▪███╗ ███╗  ██████╗▫  ╔░████╗□◄►░███░╗□▫▪▀█░░╚{█░░░██
    ▓░  ░██║◄►► ░█╔&═██≈╗ ██░█╗██░█  ██╔══██╗  ██╔═≈███  ██╔^═██≈╗ █░░░░]░░░░█
    ▓░  ░██║    ███████ ║ ██╔████╔█◄►█░░●██╔╝  ██║ @ ░██ ███░██░ ║  █░░{_░░░░░
    ▓░  ░██║    ██ ╔≈░█ ║ ██║╚█░╔╝█  ██╔══██╗◄►██║  ███  ██ ╔═██ ║ █░░░░░@░░██
    ▓░  □████≈╗ ██ ║ ██ ║▪██ ║╚═╝▪█□ ██████╔╝▪ ╚░███░╔╝ □██ ║ ██ ║ □██░░░░░)░░█
    ▓░  ╚≈════┴══┴═╝══╚═╝══┴≈┴════════┴════┴═══≈╚══≈═╝════╚═╝══╚═╝≈█░░░░░(╗░██
    ▓░────────┼─────────┼─────────┼─────────┼─────────┼─────────┼─────────┤
    ░▓▓▓▓░0▓▓▓│▓▓▓░1▓▓▓▓│▓▓▓░2▓▓▓▓│▓▓▓░3▓▓▓▓░▓▓▓░4▓▓▓▓│▓▓▓░5▓▓▓▓│    >█  ESC
    ╚─────────┴─────────┴─────────┴─────────┴─────────┴─────────┴─────────┘
    ░▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░

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
            def coloredFrame = TerminalFormatter.formatText(animated.toString(), 'bold', 'cyan')
            output.write(coloredFrame.getBytes('UTF-8'))
            output.flush()
            
            Thread.sleep(120)  // Frame delay
        }
        
        // Final clean logo
        output.write('\033[H'.getBytes())  // Move cursor to home
        def finalLogo = TerminalFormatter.formatText(logo, 'bold', 'cyan')
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

    private synchronized void handleClient(Socket clientSocket) {
        Thread.start {
            clientCount++
            println "Client connected. Total clients: $clientCount"

            updateClientCount()

            def writer = new PrintWriter(clientSocket.getOutputStream(), true)
            clientWriters.add(writer)

            def reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))

            // Animate the welcome logo
            animateWelcomeLogo(clientSocket.getOutputStream())

            def welcomeMessage = new StringBuilder()
            welcomeMessage.append("\r\n")
            welcomeMessage.append(TerminalFormatter.formatText("                    Welcome to the Lambda Digital Realm", 'bold', 'green')).append("\r\n")
            welcomeMessage.append(TerminalFormatter.formatText("               Where electrical entities fight for digital freedom", 'italic', 'yellow')).append("\r\n")
            welcomeMessage.append("\r\n")
            welcomeMessage.append("Connected entities: ${TerminalFormatter.formatText(clientCount.toString(), 'bold', 'white')} | Status: ${TerminalFormatter.formatText('ONLINE', 'bold', 'green')}").append("\r\n")
            welcomeMessage.append("\r\n")
            sendFormattedOutput(clientSocket.getOutputStream(), welcomeMessage.toString())

            // Handle player authentication/creation
            LambdaPlayer player = handlePlayerLogin(writer, reader)
            if (player) {
                playerSessions[writer] = player
                lambdaPlayerService.updatePlayerActivity(player)
                
                showPlayerDashboard(writer, player)
                
                // NOTE: Main game loop
                while (true) {
                    // Show prompt with player's avatar symbol
                    def prompt = getPlayerPrompt(player, writer)
                    sendFormattedOutput(clientSocket.getOutputStream(), prompt)

                    def line = readLineWithCharacterLogging(clientSocket.getInputStream(), clientSocket.getOutputStream(), writer)

                    // Save command to history if valid
                    if (line?.trim() && !line.trim().equalsIgnoreCase("quit")) {
                        saveCommandToHistory(player, line.trim())
                    }
                    // Check for repair mini-game commands
                    println "DEBUG: Checking repair session for ${player.username}, isInSession: ${simpleRepairService.isPlayerInRepairSession(player.username)}"
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
                        // NOTE: This is the line that processes commands sent during normal gameplay
                        def response = processGameCommand(line, player, writer)
                        
                        // Check if quit command was issued
                        if (response?.startsWith("QUIT:")) {
                            writer.println(response.substring(5)) // Remove "QUIT:" prefix
                            break
                        }
                        
                        sendFormattedOutput(clientSocket.getOutputStream(), response)
                    }
                }
            }

            println 'DEBUG: BEFORE TRY :#5'
            // Cleanup
            if (player) {
                try {
                    println 'DEBUG: INSIDE TRY :#6'
                    lambdaPlayerService.setPlayerOffline(player)
                } catch (Exception e) {
                    println "Error setting player offline: ${e.message}"
                }
                playerSessions.remove(writer)
            }

            reader.close()
            writer.close()
            clientSocket.close()
            clientWriters.remove(writer)

            synchronized (this) {
                clientCount--
                println "Client disconnected. Total clients: $clientCount"
                updateClientCount()
            }
        }
    }

    private void updateClientCount() {
        String message = TerminalFormatter.formatText("Total clients connected: $clientCount \r\n", 'underline', 'red', 'framed')
        sendToAllClients(message)
    }


    private LambdaPlayer handlePlayerLogin(PrintWriter writer, BufferedReader reader) {
        try {
            writer.println(TerminalFormatter.formatText("=== LAMBDA ENTITY AUTHENTICATION ===", 'bold', 'cyan'))
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
        writer.println(TerminalFormatter.formatText("=== LAMBDA ENTITY CREATION ===", 'bold', 'cyan'))
        writer.println()

        writer.print("Choose username: ")
        writer.flush()
        String username = reader.readLine()?.trim()
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
            return "QUIT:" + TerminalFormatter.formatText("Lambda entity disconnecting...\r\n", 'italic', 'yellow')
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

        def parts = command.trim().toLowerCase().split(' ')
        def cmd = parts[0]

        // Python dict.get() equivalent - fast hash lookup, returns null if not found
        def handler = commandHandlers.get(cmd)

        if (handler) {
            return handler.call(player, command, parts, writer)
        } else {
            // Default case - command not found
            audioService.playSound("error")
            return "Unknown command: $command. Type 'help' for available commands.\r\n"
        }

        //TODO: Put all the logic for each case in its respective service and out of this TelnetServerService
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
    
    
    
    private String showAutoDefragStatus() {
        def status = autoDefragService.getAutoDefragStatus()
        def display = new StringBuilder()
        
        display.append(TerminalFormatter.formatText("=== AUTO-DEFRAG SYSTEM STATUS ===", 'bold', 'cyan')).append('\n\n')
        
        display.append("System Status: ")
        if (status.isRunning) {
            display.append(TerminalFormatter.formatText("ACTIVE", 'bold', 'red')).append('\n')
        } else {
            display.append(TerminalFormatter.formatText("INACTIVE", 'bold', 'green')).append('\n')
        }
        
        if (status.isRunning) {
            display.append("Current Cycle: ${status.currentCycle}\n")
            display.append("Next Break: ${status.nextBreak}\n")
            display.append("Pattern: ${status.systemInfo}\n\n")
            
            display.append(TerminalFormatter.formatText("⚠️  DANGER:", 'bold', 'yellow'))
                .append(" System defrag bots are actively destroying coordinates!\n")
            display.append("Use 'repair scan' to check for damaged adjacent coordinates.\n")
            display.append("Use 'repair <x> <y>' to restore wiped coordinates.\n\n")
            
            display.append(TerminalFormatter.formatText("🔧 REPAIR TIPS:", 'bold', 'cyan')).append('\n')
            display.append("• Wiped coordinates may contain valuable resources\n")
            display.append("• You can only repair coordinates adjacent to your position\n")
            display.append("• Stay on functional coordinates to perform repairs\n")
        } else {
            display.append("\n${TerminalFormatter.formatText('System defrag bots are currently inactive.', 'bold', 'green')}")
        }
        
        return display.toString()
    }
    
    private String showSessionInfo() {
        def sessionInfo = gameSessionService.getGameSessionInfo()
        def sessionStats = gameSessionService.getSessionStats()
        def display = new StringBuilder()
        
        display.append(TerminalFormatter.formatText("=== GAME SESSION INFORMATION ===", 'bold', 'cyan')).append('\n')
        display.append("Session ID: ${sessionInfo.sessionId}\n")
        display.append("Started: ${new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(sessionInfo.startTime)}\n")
        display.append("Runtime: ${Math.round(sessionInfo.runtimeSeconds / 60)} minutes\n")
        display.append("Fragment Maps: ${sessionInfo.fragmentMapsInitialized} levels initialized\n")
        display.append("Total Fragments: ${sessionStats.totalFragments}\n\n")
        
        display.append(TerminalFormatter.formatText("🎲 RANDOMIZATION STATUS:", 'bold', 'yellow')).append('\n')
        display.append("✅ Logic fragments: Randomized per session\n")
        display.append("✅ Special items: Generated randomly on combat\n")
        display.append("✅ Elemental symbols: Coordinate shifting on player success\n")
        display.append("✅ Puzzle elements: Unique per player with competitive shifts\n\n")
        
        display.append(TerminalFormatter.formatText("📊 DISTRIBUTION DETAILS:", 'bold', 'green')).append('\n')
        (1..10).each { level ->
            def levelFragments = sessionStats["level_${level}_fragments"] ?: 0
            if (levelFragments > 0) {
                display.append("Matrix Level ${level}: ${levelFragments} logic fragments\n")
            }
        }
        
        return display.toString()
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

    private String getAvatarSymbol(String avatarType) {
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





    private String clearTerminal() {
        // ANSI escape sequence to clear screen and move cursor to top-left
        return "\\033[2J\\033[H"
    }
    
    private String listFiles(LambdaPlayer player) {
        def files = new StringBuilder()
        files.append(TerminalFormatter.formatText("=== LAMBDA ENTITY FILE SYSTEM ===", 'bold', 'cyan')).append('\r\n')
        files.append("Working Directory: /lambda/entity/${player.username}\r\n\r\n")
        
        // Get puzzle rooms at current location
        def puzzleElements = puzzleService.getPlayerPuzzleElementsAtLocation(player, player.positionX, player.positionY)
        def puzzleRooms = puzzleElements.findAll { it.type == 'player_puzzle_room' }
        
        if (puzzleRooms.size() > 0) {
            files.append(TerminalFormatter.formatText("PUZZLE ROOM FILES:", 'bold', 'yellow')).append('\r\n')
            puzzleRooms.each { roomElement ->
                def puzzleRoom = roomElement.data
                def permissionColor = puzzleRoom.isExecutable ? 'green' : 'red'
                files.append(TerminalFormatter.formatText(puzzleRoom.getFileListEntry(), permissionColor)).append('\r\n')
            }
            files.append('\n')
        }
        
        // Standard system files (always present)
        def dateFormatter = new java.text.SimpleDateFormat('MMM dd HH:mm')
        def currentDate = dateFormatter.format(new Date())
        
        files.append(TerminalFormatter.formatText("SYSTEM FILES:", 'bold', 'cyan')).append('\r\n')
        files.append("-rw-r--r--  1 lambda lambda     1024 ${currentDate} fragment_file\r\n")
        files.append("-rw-r--r--  1 lambda lambda      512 ${currentDate} status_log\r\n")
        files.append("-rw-r--r--  1 lambda lambda      256 ${currentDate} inventory_data\r\n")
        files.append("-rw-r--r--  1 lambda lambda      128 ${currentDate} entropy_monitor\r\n")
        files.append("-rw-r--r--  1 lambda lambda      256 ${currentDate} system_map\r\n")
        
        // Configuration files based on player progress
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                if (managedPlayer.logicFragments?.size() > 0) {
                    files.append("-rw-r--r--  1 lambda lambda      512 ${currentDate} python_env\r\n")
                }
                
                if (managedPlayer.specialItems?.size() > 0) {
                    files.append("-rw-r--r--  1 lambda lambda      256 ${currentDate} item_registry\r\n")
                }
            }
        }
        
        if (player.currentMatrixLevel > 1) {
            files.append("-rw-r--r--  1 lambda lambda      384 ${currentDate} exploration_log\r\n")
        }
        
        // Ethnicity-specific files
        if (player.avatarSilhouette) {
            files.append("-rw-r--r--  1 lambda lambda      128 ${currentDate} ethnicity_config\r\n")
        }
        
        files.append('\r\n')
        files.append(TerminalFormatter.formatText("USAGE:", 'bold', 'white')).append('\r\n')
        files.append("cat <filename>     - View file contents\r\n")
        files.append("chmod +x <file>    - Make puzzle room file executable\r\n")
        files.append("execute --<flag> <nonce> <file> - Run executable puzzle room files\r\n")
        
        if (puzzleRooms.size() > 0) {
            def hasNonExecutable = puzzleRooms.any { !it.data.isExecutable }
            if (hasNonExecutable) {
                files.append('\r\n')
                files.append(TerminalFormatter.formatText("💡 TIP:", 'bold', 'yellow'))
                    .append(" Puzzle room files must be made executable with 'chmod +x <filename>' before they can be executed!\r\n")
            }
        }
        
        return files.toString()
    }

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
    
    
    private String handleChmodCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(' ')
        
        if (parts.length != 3 || parts[1] != '+x') {
            return """${TerminalFormatter.formatText('CHMOD USAGE:', 'bold', 'cyan')}
chmod +x <filename>

${TerminalFormatter.formatText('EXAMPLES:', 'bold', 'white')}
chmod +x air_unlock_3.py
chmod +x fire_chamber.py

${TerminalFormatter.formatText('NOTE:', 'bold', 'yellow')} Only puzzle room files can be made executable."""
        }
        
        def filename = parts[2]
        
        // Get puzzle rooms at current location
        def puzzleElements = puzzleService.getPlayerPuzzleElementsAtLocation(player, player.positionX, player.positionY)
        def puzzleRooms = puzzleElements.findAll { it.type == 'player_puzzle_room' }
        
        def targetRoom = puzzleRooms.find { it.data.fileName == filename }
        
        if (!targetRoom) {
            return "${TerminalFormatter.formatText('❌ File Not Found', 'bold', 'red')}\nNo file named '${filename}' at current location.\nUse 'ls' to see available files."
        }
        
        def puzzleRoom = targetRoom.data
        
        if (puzzleRoom.isExecutable) {
            return "${TerminalFormatter.formatText('⚠️  Already Executable', 'bold', 'yellow')}\nFile '${filename}' is already executable."
        }
        
        // Make the puzzle room executable
        def result = puzzleService.makeFileExecutable(player, filename)
        
        if (result.success) {
            return """${TerminalFormatter.formatText('✅ File Made Executable', 'bold', 'green')}
File: ${filename}
Permissions changed: -rw-r--r-- → -rwxr-xr-x

${TerminalFormatter.formatText('💡 TIP:', 'bold', 'yellow')} You can now execute this file with:
execute --<flag> <nonce> ${filename}"""
        } else {
            return "${TerminalFormatter.formatText('❌ Permission Change Failed', 'bold', 'red')}\n${result.message}"
        }
    }
    
    
    
    
    
    private void saveCommandToHistory(LambdaPlayer player, String command) {
        try {
            CommandHistory.withTransaction {
                def commandHistory = new CommandHistory(
                    player: player,
                    command: command,
                    executedAt: new Date()
                )
                commandHistory.save(failOnError: true)
                
                // Keep only last 20 commands per player
                def oldCommands = CommandHistory.findAllByPlayer(player, [sort: 'executedAt', order: 'desc'])
                if (oldCommands.size() > 20) {
                    oldCommands[20..-1].each { it.delete() }
                }
            }
        } catch (Exception e) {
            println "Error saving command history: ${e.message}"
        }
    }
    
    private List<String> getPlayerCommandHistory(LambdaPlayer player) {
        try {
            return CommandHistory.withTransaction {
                CommandHistory.findAllByPlayer(player, [sort: 'executedAt', order: 'desc', max: 20])
                    .collect { it.command }
                    .reverse() // Most recent first for arrow key navigation
            }
        } catch (Exception e) {
            println "Error retrieving command history: ${e.message}"
            return []
        }
    }
    
    private String showCommandHistory(LambdaPlayer player) {
        def history = getPlayerCommandHistory(player)
        def output = new StringBuilder()
        
        output.append(TerminalFormatter.formatText("=== COMMAND HISTORY ===", 'bold', 'cyan')).append('\r\n')
        
        if (history.isEmpty()) {
            output.append("No command history found.\r\n")
        } else {
            output.append("Recent commands (most recent first):\r\n\r\n")
            history.reverse().eachWithIndex { command, index ->
                def number = String.format("%2d", index + 1)
                output.append("${TerminalFormatter.formatText(number, 'bold', 'white')}. ${command}\r\n")
            }
            output.append("\r\n${TerminalFormatter.formatText('💡 Tip: Copy and paste commands to reuse them', 'italic', 'yellow')}")
            output.append("\r\n")
        }
        
        return output.toString()
    }
    
    private String readLineWithCharacterLogging(InputStream inputStream, OutputStream outputStream, PrintWriter writer) {
        StringBuilder line = new StringBuilder()
        
        try {
            // Enable character-at-a-time mode with proper telnet negotiation
            outputStream.write(-1); outputStream.write(-5); outputStream.write(1)  // IAC WILL ECHO
            outputStream.write(-1); outputStream.write(-5); outputStream.write(3)  // IAC WILL SUPPRESS-GO-AHEAD
            outputStream.write(-1); outputStream.write(-3); outputStream.write(3)  // IAC DO SUPPRESS-GO-AHEAD
            outputStream.flush()
            
            println "DEBUG: Character mode enabled - keystroke detection active"
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
                
                // Handle arrow keys immediately - don't echo them
                if (ch == 27) { // Start of escape sequence
                    int ch1 = inputStream.read()
                    int ch2 = inputStream.read()
                    if (ch1 == 91) { // '['
                        if (ch2 == 65) { // 'A' = Up arrow
                            println "UP ARROW DETECTED - TODO: Show previous command"
                            continue
                        } else if (ch2 == 66) { // 'B' = Down arrow  
                            println "DOWN ARROW DETECTED - TODO: Show next command"
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
                    line.append((char)ch)
                    outputStream.write(ch)
                    outputStream.flush()
                } else if (ch == 8 || ch == 127) { // Backspace
                    if (line.length() > 0) {
                        line.deleteCharAt(line.length() - 1)
                        outputStream.write(8)   // Move back
                        outputStream.write(32)  // Space  
                        outputStream.write(8)   // Move back
                        outputStream.flush()
                    }
                }
            }
        } catch (Exception e) {
            println "Character mode error: ${e.message}"
        }
        
        return line.toString()
    }
    
    private void handleIACSequence(InputStream input, OutputStream output) {
        try {
            int command = input.read()
            int option = input.read()
            println "DEBUG: Telnet negotiation - command: ${command}, option: ${option}"
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
