package ysap

import java.util.concurrent.CopyOnWriteArrayList

class TelnetServerService {
    def pageService
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
    def puzzleRandomizationService
    def competitivePuzzleService
    def puzzleKnowledgeTradingService
    def autoDefragService
    def gameSessionService
    def simpleRepairService
    private ServerSocket serverSocket
    private int clientCount = 0
    private List<PrintWriter> clientWriters = new CopyOnWriteArrayList<>() // Thread-safe list
    private Map<PrintWriter, LambdaPlayer> playerSessions = [:] // Track player sessions
    private Map<PrintWriter, DefragBot> activeDefragSessions = [:] // Track defrag encounters

    String logo = """
╔══════════════════════════════════════════════════════════════════════════════╗
║  ██▓    ▄▄▄       ███▄ ▄███▓ ▄▄▄▄   ▓█████▄  ▄▄▄                           ║
║ ▓██▒   ▒████▄    ▓██▒▀█▀ ██▒▓█████▄ ▒██▀ ██▌▒████▄                         ║
║ ▒██░   ▒██  ▀█▄  ▓██    ▓██░▒██▒ ▄██░██   █▌▒██  ▀█▄                       ║
║ ▒██░   ░██▄▄▄▄██ ▒██    ▒██ ▒██░█▀  ░▓█▄   ▌░██▄▄▄▄██                      ║
║ ░██████▒▓█   ▓██▒▒██▒   ░██▒░▓█  ▀█▓░▒████▓  ▓█   ▓██▒                     ║
║ ░ ▒░▓  ░▒▒   ▓▒█░░ ▒░   ░  ░░▒▓███▀▒ ▒▒▓  ▒  ▒▒   ▓▒█░                     ║
║ ░ ░ ▒  ░ ▒   ▒▒ ░░  ░      ░▒░▒   ░  ░ ▒  ▒   ▒   ▒▒ ░                     ║
║   ░ ░    ░   ▒   ░      ░    ░    ░  ░ ░  ░   ░   ▒                        ║
║     ░  ░     ░  ░       ░    ░         ░          ░  ░                     ║
║                                   ░       ░                                 ║
║                                                                              ║
║              A DIGITAL ENTITIES GAME                                        ║
║        Escape the System • Invade the Internet                              ║
║                                                                              ║
╚══════════════════════════════════════════════════════════════════════════════╝

    """
    void startServer(int port) {
        serverSocket = new ServerSocket(port)
        println "Telnet server started on port $port"
        
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
            // Skip terminal size detection for now
            println "Detected terminal size: unknown"

            writer.println(TerminalFormatter.formatText(logo, 'bold', 'cyan'))
            writer.println(TerminalFormatter.formatText("                    Welcome to the Lambda Digital Realm", 'bold', 'green'))
            writer.println(TerminalFormatter.formatText("               Where electrical entities fight for digital freedom", 'italic', 'yellow'))
            writer.println()
            writer.println("Connected entities: ${TerminalFormatter.formatText(clientCount.toString(), 'bold', 'white')} | Status: ${TerminalFormatter.formatText('ONLINE', 'bold', 'green')}")
            writer.println()

            // Handle player authentication/creation
            LambdaPlayer player = handlePlayerLogin(writer, reader)
            if (player) {
                playerSessions[writer] = player
                lambdaPlayerService.updatePlayerActivity(player)
                
                showPlayerDashboard(writer, player)
                
                // NOTE: Main game loop
                while (true) {
                    println 'DEBUG: WHILE LOOP RUNNING  :#1'
                    // Show prompt with player's avatar symbol
                    def prompt = getPlayerPrompt(player)
                    writer.print(prompt)
                    writer.flush()
                    println 'DEBUG: PAST SHOW PROMPT :#2'

                    def line = reader.readLine()
                    println "DEBUG: Received input: '${line}'  :#3"
                    // Check for repair mini-game commands
                    println "DEBUG: Checking repair session for ${player.username}, isInSession: ${simpleRepairService.isPlayerInRepairSession(player.username)}"
                    if (simpleRepairService.isPlayerInRepairSession(player.username)) {
                            def command = line.trim()
                            println "${line} SHOULD BE THE RAW READLINE"
                            println "DEBUG: In repair session, received command: '${command}'"
                            
                            // ONLY accept enter key (any input) or exit commands
                            if (command != "exit" && command != "quit") {
                                println "DEBUG: Calling handleSpaceBarPress for ${player.username}"
                                def enterResult = simpleRepairService.handleSpaceBarPress(player.username)
                                if (enterResult.success) {
                                    if (enterResult.message) {
                                        writer.println(enterResult.message)
                                    }
                                    if (!enterResult.continueGame) {
                                        // Mini-game completed - continue to next iteration of main game loop
                                        audioService.playSound(enterResult.success ? "victory" : "error")
                                        continue // Continue main game loop instead of exiting connection
                                    }
                                } else {
                                    writer.println(TerminalFormatter.formatText("⚠️ ${enterResult.message}", 'bold', 'red'))
                                }
                            } else if (command.toLowerCase() == "exit" || command.toLowerCase() == "quit") {
                                simpleRepairService.stopRepairSession(player.username)
                                writer.println(TerminalFormatter.formatText("Repair session cancelled.", 'italic', 'yellow'))
                                continue // Return to main game instead of disconnecting
                            }
                    } else {
                        // NOTE: This is the line that processes commands sent during normal gameplay
                        println "ELSE BLOCK BEGIN ===="
                        def response = processGameCommand(line, player, writer)
                        
                        // Check if quit command was issued
                        if (response?.startsWith("QUIT:")) {
                            writer.println(response.substring(5)) // Remove "QUIT:" prefix
                            break
                        }
                        
                        writer.println(response)
                        println "ELSE BLOCK END ===="
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

    private String queryTerminalSize(PrintWriter writer, BufferedReader reader) {
        // ANSI Sequence to query terminal size
        writer.println("Press Enter...")
        writer.print("\033[18t")
        writer.flush()

        try {
            // Wait and read response
            // Response format is expected as ESC[8;<height>;<width>t
            // Adjust this reading mechanism based on how your terminal actually responds
            String response = reader.readLine();

            // Log the raw response for debugging
            println "Raw terminal size response: $response"

            // Parsing the response to extract height and width
            // This is a simplified example; actual parsing may vary based on response format
            if (response && response.matches(/.*\033\[8;(\d+);(\d+)t.*/)) {
                return response.replaceAll(/.*\033\[8;(\d+);(\d+)t.*/, '$1x$2')
            }
        } catch (Exception e) {
            println "Error reading terminal size: $e"
        }
        return "unknown"
    }


    private void updateClientCount() {
        String message = TerminalFormatter.formatText("Total clients connected: $clientCount", 'underline', 'red', 'framed')
        sendToAllClients(message)
    }

    private String processCommand(String command) {
        return "You entered: $command"
    }

    private LambdaPlayer handlePlayerLogin(PrintWriter writer, BufferedReader reader) {
        try {
            writer.println(TerminalFormatter.formatText("=== LAMBDA ENTITY AUTHENTICATION ===", 'bold', 'cyan'))
            writer.println()
            writer.print("Enter username (or 'new' to create): ")
            writer.flush()
            
            def username = reader.readLine()?.trim()?.toLowerCase()
            if (!username) {
                println "${username} <-----THERE SHOULD NOT BE A USERNAME"

                return null
            }
            println "${username} <-----THERE BE A USERNAME"
            username = username.contains('new') ? 'new' : ''

            if (username.equalsIgnoreCase('new')) {
                println "making it in the username condition"
                return createNewPlayer(writer, reader)
            } else {
                def player = null
                LambdaPlayer.withTransaction {
                    player = LambdaPlayer.findByUsername(username)
                }
                if (player) {
                    writer.println(TerminalFormatter.formatText("Lambda entity ${player.displayName} authenticated!", 'bold', 'green'))
                    audioService.playSound("login")
                    return player
                } else {
                    writer.println(TerminalFormatter.formatText("Entity not found. Creating new Lambda...", 'italic', 'yellow'))
                    return createPlayerWithUsername(writer, reader, username)
                }
            }
        } catch (Exception e) {
            writer.println(TerminalFormatter.formatText("Authentication error: ${e.message}", 'bold', 'red'))
            return null
        }
    }
    
    private LambdaPlayer createNewPlayer(PrintWriter writer, BufferedReader reader) {
        writer.println(TerminalFormatter.formatText("=== LAMBDA ENTITY CREATION ===", 'bold', 'cyan'))
        writer.println()
        println "SHOULD BE CHOOSING THE USERNAME RIGHT NOW, IN THE createNewPlayer method"

        writer.print("Choose username: ")
        writer.flush()
        String username = reader.readLine()?.trim()
        if (!username || username.length() < 3) {
            writer.println("Username must be at least 3 characters")
            return null
        }
        
        return createPlayerWithUsername(writer, reader, username)
    }
    
    private LambdaPlayer createPlayerWithUsername(PrintWriter writer, BufferedReader reader, String username) {
        writer.print("Choose display name: ")
        writer.flush()
        String displayName = reader.readLine()?.trim()
        if (!displayName) displayName = username
        
        writer.println()
        writer.println(TerminalFormatter.formatText("Available Lambda Avatars:", 'bold', 'cyan'))
        def avatars = lambdaPlayerService.getAvailableAvatars()
        avatars.eachWithIndex { avatar, index ->
            writer.println("${index + 1}. ${avatar}")
            writer.println(lambdaPlayerService.getAvatarDisplay(avatar))
            writer.println()
        }
        
        writer.print("Select avatar (1-${avatars.size()}): ")
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
        writer.println(TerminalFormatter.formatText("=== LAMBDA ENTITY STATUS ===", 'bold', 'cyan'))
        writer.println("Entity: ${TerminalFormatter.formatText(player.displayName, 'bold', 'white')}")
        writer.println("Matrix Level: ${TerminalFormatter.formatText(player.currentMatrixLevel.toString(), 'bold', 'yellow')}")
        writer.println("Position: (${player.positionX},${player.positionY})")
        writer.println("Bits: ${TerminalFormatter.formatText(player.bits.toString(), 'bold', 'green')}")
        def fragmentCount = 0
        def skillCount = 0 
        def itemCount = 0
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            fragmentCount = managedPlayer?.logicFragments?.size() ?: 0
            skillCount = managedPlayer?.skills?.size() ?: 0
            itemCount = managedPlayer?.specialItems?.size() ?: 0
        }
        writer.println("Logic Fragments: ${fragmentCount}")
        writer.println("Skills Acquired: ${skillCount}")
        writer.println("Special Items: ${itemCount}")
        writer.println()
        writer.println("Avatar:")
        writer.println(lambdaPlayerService.getAvatarDisplay(player.avatarSilhouette))
        writer.println()
        writer.println("ASCII Face:")
        writer.println(player.asciiFace)
        writer.println()
        writer.println(TerminalFormatter.formatText("Available Commands:", 'bold', 'green'))
        writer.println("status - Show detailed status")
        writer.println("cc (x,y) - Change coordinates to specific position")
        writer.println("scan - Scan current area")
        writer.println("inventory - Show logic fragments and skills")
        writer.println("mingle - Enter the Lambda mingle chamber")
        writer.println(TerminalFormatter.formatText("map - Show matrix level overview with outages", 'bold', 'yellow'))
        writer.println(TerminalFormatter.formatText("ls - List your files | clear - Clear screen", 'bold', 'yellow'))
        writer.println("help - Show all commands")
        writer.println("quit - Disconnect")
        writer.println()
    }
    
    private String processGameCommand(String command, LambdaPlayer player, PrintWriter writer) {
        println 'DEBUG: PROCESSING GAME COMMAND'
        
        // Handle quit command
        if (command == null || command.trim().equalsIgnoreCase("quit")) {
            println "DEBUG: QUIT command received in processGameCommand"
            audioService.playSound("logout")
            return "QUIT:" + TerminalFormatter.formatText("Lambda entity disconnecting...", 'italic', 'yellow')
        }
        
        // Check if player is in an active defrag encounter
        if (activeDefragSessions.containsKey(writer)) {
            return handleDefragEncounter(command, player, writer)
        }
        
        // Refresh player state and check if player is in mingle mode
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
            return handleMingleCommand(command, refreshedPlayer, writer)
        }
        
        def parts = command.trim().toLowerCase().split(' ')
        def cmd = parts[0]
        
        switch (cmd) {
            case 'status':
                return getPlayerStatus(player)
            case 'cc':
                return handleCoordinateChange(command, player, writer)
            case 'scan':
                return scanArea(player)
            case 'inventory':
                return showInventory(player)
            case 'mingle':
                return enterMingle(player, writer)
            case 'defrag':
                return handleDefragCommand(command, player, writer)
            case 'cat':
                return handleCatCommand(command, player)
            case 'pickup':
                return handlePickupCommand(player)
            // Note: 'collect' command removed - symbols only obtained through puzzle-solving
            case 'symbols':
                return elementalSymbolService.getPlayerSymbolStatus(player)
            case 'collect_var':
                if (parts.length > 1) {
                    return handleCollectVariableCommand(parts[1], player)
                }
                return "Usage: collect_var <variable_name> - Collect hidden variable"
            case 'execute':
                return handleExecuteCommand(command, player)
            case 'puzzle_inventory':
            case 'pinv':
                return showPuzzleInventory(player)
            case 'puzzle_progress':
            case 'pprog':
                return showCompetitivePuzzleProgress(player)
            case 'puzzle_market':
            case 'pmarket':
                return showPuzzleKnowledgeMarket(player)
            case 'recurse':
                if (parts.length > 1) {
                    return handleRecurseCommand(parts[1], player, writer)
                }
                return "Usage: recurse <ability> - Use ethnicity recursion power"
            case 'shop':
            case 'buy':
            case 'sell':
                return handleMerchantCommand(command, player)
            case 'entropy':
                return handleEntropyCommand(command, player, writer)
            case 'mine':
            case 'mining':
                return handleMiningCommand(player, writer)
            case 'fuse':
            case 'fusion':
                return handleFusionCommand(command, player)
            case 'use':
                return handleUseCommand(command, player)
            case 'repair':
                return handleRepairCommand(command, player)
            case 'map':
                return showMatrixMap(player)
            case 'clear':
                return clearTerminal()
            case 'ls':
                return listFiles(player)
            case 'chmod':
                return handleChmodCommand(command, player)
            case 'defrag_status':
            case 'autdefrag':
                return showAutoDefragStatus()
            case 'session':
                return showSessionInfo()
            case 'help':
                return showHelp()
            default:
                audioService.playSound("error")
                return "Unknown command: $command. Type 'help' for available commands."
        }
    }
    
    private String getPlayerStatus(LambdaPlayer player) {
        def status = new StringBuilder()
        def entropyStatus = entropyService.getEntropyStatus(player)
        
        status.append(TerminalFormatter.formatText("=== DETAILED LAMBDA STATUS ===", 'bold', 'cyan')).append('\n')
        status.append("Entity: ${player.displayName}\n")
        status.append("Matrix Level: ${player.currentMatrixLevel}/10\n")
        status.append("Coordinates: (${player.positionX},${player.positionY})\n")
        status.append("Bits: ${player.bits}\n")
        def currentEntropy = entropyStatus.currentEntropy ?: 100.0
        status.append("Digital Coherence: ${TerminalFormatter.formatText("${currentEntropy}%", getEntropyColor(currentEntropy), 'bold')}\n")
        
        def miningRewards = entropyStatus.miningRewards ?: 0
        if (miningRewards > 0) {
            status.append("Mining Rewards: ${TerminalFormatter.formatText("+${miningRewards} bits", 'bold', 'green')} (use 'mining')\n")
        }
        
        def fusionAttempts = entropyService.getFragmentFusionAttempts(player)
        def remainingAttempts = fusionAttempts.remaining ?: 0
        if (remainingAttempts > 0) {
            status.append("Fusion Attempts: ${TerminalFormatter.formatText("${remainingAttempts} remaining", 'bold', 'yellow')}\n")
        }
        
        status.append("Online Lambda Entities: ${lambdaPlayerService.getOnlinePlayers().size()}\n")
        status.append("Entities in Current Matrix Level: ${lambdaPlayerService.getPlayersByMatrixLevel(player.currentMatrixLevel).size()}\n")
        
        // Fix Hibernate session issues by using transaction
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                status.append("Logic Fragments: ${managedPlayer.logicFragments?.size() ?: 0}\n")
                status.append("Special Items: ${managedPlayer.specialItems?.size() ?: 0}\n")
            } else {
                status.append("Logic Fragments: 0\n")
                status.append("Special Items: 0\n")
            }
        }
        
        // Show ethnicity bonuses if player has any
        def bonuses = []
        if (player.fragmentDetectionBonus > 0) bonuses.add("Enhanced Scan (+${Math.round(player.fragmentDetectionBonus * 100)}%)")
        if (player.defragResistanceBonus > 0) bonuses.add("Defrag Resistance (+${Math.round(player.defragResistanceBonus * 100)}%)")
        if (player.movementRangeBonus > 0) bonuses.add("Movement Range (+${player.movementRangeBonus})")
        if (player.miningEfficiencyBonus > 0) bonuses.add("Mining Efficiency (+${Math.round(player.miningEfficiencyBonus * 100)}%)")
        if (player.stealthBonus > 0) bonuses.add("Stealth (+${Math.round(player.stealthBonus * 100)}%)")
        if (player.fusionSuccessBonus > 0) bonuses.add("Fusion Success (+${Math.round(player.fusionSuccessBonus * 100)}%)")
        
        if (bonuses) {
            status.append("Ethnicity Bonuses: ${TerminalFormatter.formatText(bonuses.join(', '), 'bold', 'magenta')}\n")
        }
        
        def canRefresh = entropyStatus.canRefresh ?: false
        def timeUntilRefresh = entropyStatus.timeUntilRefresh ?: 0
        
        if (canRefresh) {
            status.append('\n').append(TerminalFormatter.formatText("🔋 Daily entropy refresh available!", 'bold', 'green'))
        } else if (currentEntropy < 50) {
            status.append('\n').append(TerminalFormatter.formatText("⚠️  Low coherence - refresh in ${timeUntilRefresh}h", 'bold', 'yellow'))
        }
        
        return status.toString()
    }
    
    
    private String handleCoordinateChange(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command.trim().split(' ')
        
        // Validate command format: cc (x,y) or cc x,y or cc x y
        if (parts.length < 2) {
            return "Usage: cc (x,y) - Change coordinates to specific position"
        }
        
        def coordinateString = parts[1..-1].join(' ').trim()
        def newX, newY
        
        // Parse different coordinate formats
        if (coordinateString.startsWith('(') && coordinateString.endsWith(')')) {
            // Format: cc (2,6)
            coordinateString = coordinateString.substring(1, coordinateString.length() - 1)
        }
        
        if (coordinateString.contains(',')) {
            // Format: cc 2,6 or cc (2,6)
            def coords = coordinateString.split(',')
            if (coords.length != 2) {
                return "Invalid coordinate format. Use: cc (x,y) or cc x,y"
            }
            try {
                newX = Integer.parseInt(coords[0].trim())
                newY = Integer.parseInt(coords[1].trim())
            } catch (NumberFormatException e) {
                return "Invalid coordinates. Use numbers only: cc (x,y)"
            }
        } else if (parts.length >= 3) {
            // Format: cc 2 6
            try {
                newX = Integer.parseInt(parts[1].trim())
                newY = Integer.parseInt(parts[2].trim())
            } catch (NumberFormatException e) {
                return "Invalid coordinates. Use numbers only: cc x y"
            }
        } else {
            return "Invalid coordinate format. Use: cc (x,y), cc x,y, or cc x y"
        }
        
        // Constrain coordinates to matrix bounds (0-9)
        if (newX < 0 || newX > 9 || newY < 0 || newY > 9) {
            return "Coordinates must be within matrix bounds (0-9). Requested: (${newX},${newY})"
        }
        
        // Check if coordinate change is allowed (accessibility)
        def movementCheck = coordinateStateService.canPlayerMoveToCoordinate(player, newX, newY)
        if (!movementCheck.allowed) {
            return TerminalFormatter.formatText("Coordinate change blocked: ${movementCheck.reason}", 'bold', 'red')
        }
        
        // Calculate movement direction for audio feedback
        def deltaX = newX - player.positionX
        def deltaY = newY - player.positionY
        
        // Play appropriate movement sound based on primary direction
        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            if (deltaX > 0) {
                audioService.playSound("move_east")
            } else {
                audioService.playSound("move_west")
            }
        } else {
            if (deltaY > 0) {
                audioService.playSound("move_north")
            } else {
                audioService.playSound("move_south")
            }
        }
        
        lambdaPlayerService.movePlayer(player, player.currentMatrixLevel, newX, newY)
        
        // Update the player object in the session with new coordinates
        LambdaPlayer.withTransaction {
            def updatedPlayer = LambdaPlayer.get(player.id)
            if (updatedPlayer) {
                playerSessions[writer] = updatedPlayer
                player.positionX = updatedPlayer.positionX
                player.positionY = updatedPlayer.positionY
            }
        }
        
        updatePlayerPositionOnBoard(player)
        
        // Check for defrag bot encounter with floor-based difficulty scaling
        def floorNumber = newX // Floor 0 = coordinates 0,0-0,9, Floor 1 = 1,0-1,9, etc.
        
        // Floor-based encounter rates: Early floors are much safer
        def baseEncounterChance
        switch (floorNumber) {
            case 0:
            case 1: 
                baseEncounterChance = 0.02 // 2% chance on floors 0-1 (very safe)
                break
            case 2:
                baseEncounterChance = 0.05 // 5% chance on floor 2 (safe)
                break
            case 3:
            case 4:
                baseEncounterChance = 0.10 // 10% chance on floors 3-4 (normal)
                break
            case 5:
            case 6:
                baseEncounterChance = 0.15 // 15% chance on floors 5-6 (challenging)
                break
            default:
                baseEncounterChance = 0.20 // 20% chance on floors 7+ (dangerous)
                break
        }
        
        // Safe zone: No defrag bots in starting area
        def inSafeZone = (newX <= 1 && newY <= 1)
        
        // Apply recursion stealth bonus (if active) and roll for encounter
        def totalAvoidanceBonus = (player.stealthBonus ?: 0.0) // Only active recursion bonuses count
        def encounterChance = baseEncounterChance * (1.0 - Math.min(0.8, totalAvoidanceBonus))
        
        if (!inSafeZone && Math.random() < encounterChance) {
            def defragBot = defragBotService.spawnDefragBot(player.currentMatrixLevel, 1, newX, newY)
            if (defragBot) {
                activeDefragSessions[writer] = defragBot
                
                def encounter = new StringBuilder()
                encounter.append(TerminalFormatter.formatText("Lambda entity changed coordinates to (${newX},${newY})", 'bold', 'green')).append('\n')
                encounter.append(TerminalFormatter.formatText("⚠️  DEFRAG BOT ENCOUNTERED!", 'bold', 'red')).append('\n')
                encounter.append(TerminalFormatter.formatText("System defragmentation process ${defragBot.botId} detected", 'italic', 'yellow')).append('\n')
                encounter.append(TerminalFormatter.formatText("Time limit: ${defragBot.timeLimit} seconds", 'bold', 'red')).append('\n')
                encounter.append("Type 'defrag -h' to analyze the defrag process or face system buffer clearing!")
                
                return encounter.toString()
            }
        }
        
        // Logic gate encounters on floors 7+ at coordinates >= (7,1)
        if (newX >= 7 && newY >= 1) {
            def gateCheck = Math.random()
            if (gateCheck < 0.15) { // 15% chance of logic gate
                return TerminalFormatter.formatText("Lambda entity moved to (${newX},${newY})\n⚡ LOGIC GATE DETECTED - Implementation pending", 'bold', 'cyan')
            }
        }
        
        def moveMessage = "Lambda entity changed coordinates to (${newX},${newY})"
        return TerminalFormatter.formatText(moveMessage, 'bold', 'green')
    }
    
    private String scanArea(LambdaPlayer player) {
        audioService.playSound("scan_activate")
        def scanResult = new StringBuilder()
        scanResult.append(TerminalFormatter.formatText("=== AREA SCAN RESULTS ===", 'bold', 'cyan')).append('\n')
        scanResult.append("Matrix Level ${player.currentMatrixLevel} Sector Analysis:\n")
        scanResult.append("Position: (${player.positionX},${player.positionY})\n")
        
        // Check for actual logic fragments at coordinates
        def fragment = findFragmentAtCoordinates(player)
        if (fragment) {
            scanResult.append("Logic fragments detected: ${TerminalFormatter.formatText('YES', 'bold', 'green')}\n")
            scanResult.append("Fragment type: ${fragment.name} (${fragment.fragmentType})\n")
            scanResult.append("Power level: ${fragment.powerLevel}/10\n")
            scanResult.append("Use 'cat ${fragment.name.toLowerCase().replace(' ', '_')}' to view content\n")
            scanResult.append("Use 'pickup' to collect this fragment\n")
        } else {
            scanResult.append("Logic fragments detected: None\n")
        }
        
        // Classic Lambda bonus: Enhanced fragment detection range
        if (player.fragmentDetectionBonus > 0) {
            def extendedFragments = scanExtendedFragmentRange(player)
            if (extendedFragments) {
                scanResult.append(TerminalFormatter.formatText("\n🔍 ENHANCED SCAN (Classic Lambda):", 'bold', 'cyan')).append('\n')
                extendedFragments.each { fragmentInfo ->
                    scanResult.append("${fragmentInfo}\n")
                }
            }
        }
        
        // Check for other players
        def nearbyPlayers = lambdaPlayerService.getPlayersByMatrixLevel(player.currentMatrixLevel).findAll { 
            it.id != player.id && Math.abs(it.positionX - player.positionX) <= 1 && Math.abs(it.positionY - player.positionY) <= 1 
        }
        scanResult.append("Other entities nearby: ${nearbyPlayers.size() > 0 ? "${nearbyPlayers.size()} detected" : 'None'}\n")
        
        // Check for defrag bots
        def defragBot = defragBotService.getActiveBotAt(player.currentMatrixLevel, player.positionX, player.positionY)
        scanResult.append("Defrag processes: ${defragBot ? TerminalFormatter.formatText('WARNING: Active', 'bold', 'red') : 'Clear'}\n")
        
        // Check for Lambda merchants
        def merchant = lambdaMerchantService.getMerchantAt(player.currentMatrixLevel, player.positionX, player.positionY)
        if (merchant) {
            scanResult.append("Lambda merchant: ${TerminalFormatter.formatText(merchant.merchantName, 'bold', 'yellow')} (${merchant.merchantType})\n")
            scanResult.append("Use 'shop' to browse their inventory\n")
        } else {
            scanResult.append("Lambda merchant: None\n")
        }
        
        // Note: Elemental symbols are hidden and only discoverable through puzzle-solving
        
        // Check for competitive puzzle elements (player-specific coordinates)
        def puzzleElements = puzzleService.scanForPuzzleElements(player, player.currentMatrixLevel, player.positionX, player.positionY)
        if (puzzleElements.size() > 0) {
            scanResult.append(TerminalFormatter.formatText("\n🏁 COMPETITIVE PUZZLE ELEMENTS:", 'bold', 'purple')).append('\n')
            puzzleElements.each { element ->
                switch(element.type) {
                    case 'player_variable':
                        def variable = element.data
                        def puzzleState = element.puzzleState
                        scanResult.append("${variable.getScanDescription()}\n")
                        scanResult.append("🎯 Personal ${puzzleState.elementType} puzzle variable\n")
                        scanResult.append("Use 'collect_var ${variable.variableName}' to obtain\n")
                        scanResult.append("⚡ WARNING: Coordinates unique to you - others have different locations!\n")
                        break
                    case 'player_puzzle_room':
                        def room = element.data
                        def puzzleState = element.puzzleState
                        scanResult.append("${room.getScanDescription()}\n")
                        scanResult.append("🎯 Personal ${puzzleState.elementType} puzzle chamber\n")
                        scanResult.append("${room.getExecutionHint()}\n")
                        scanResult.append("🏆 First to solve gets the symbol - others get new coordinates!\n")
                        break
                }
            }
        }
        
        // Check coordinate health
        def coordinateHealth = coordinateStateService.getCoordinateHealth(player.currentMatrixLevel, player.positionX, player.positionY)
        scanResult.append("Coordinate Health: ${TerminalFormatter.formatText("${coordinateHealth.health}% ${coordinateHealth.status}", 'bold', coordinateHealth.color)}\n")
        
        // Show nearby coordinate health for awareness
        def nearbyDamaged = []
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue
                def scanX = Math.max(0, Math.min(9, player.positionX + dx))
                def scanY = Math.max(0, Math.min(9, player.positionY + dy))
                def nearbyHealth = coordinateStateService.getCoordinateHealth(player.currentMatrixLevel, scanX, scanY)
                if (nearbyHealth.health < 100) {
                    nearbyDamaged.add("(${scanX},${scanY}): ${nearbyHealth.health}% ${nearbyHealth.status}")
                }
            }
        }
        
        if (nearbyDamaged) {
            scanResult.append("Nearby Damage: ${nearbyDamaged.join(', ')}\n")
        }
        
        scanResult.append("System stability: ${['Stable', 'Fluctuating', 'Unstable'][new Random().nextInt(3)]}\n")
        return scanResult.toString()
    }
    
    private String showInventory(LambdaPlayer player) {
        def inventory = new StringBuilder()
        
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                inventory.append(TerminalFormatter.formatText("=== LAMBDA INVENTORY ===", 'bold', 'cyan')).append('\n')
                inventory.append("Bits: ${TerminalFormatter.formatText(managedPlayer.bits.toString(), 'bold', 'green')}\n\n")
                
                inventory.append("Logic Fragments:\n")
                def validFragments = managedPlayer.logicFragments?.findAll { it != null }
                if (validFragments?.size() > 0) {
                    validFragments.each { fragment ->
                        if (fragment?.name) {
                            inventory.append("  • ${fragment.name} (${fragment.fragmentType}) - Level ${fragment.powerLevel}\n")
                        }
                    }
                } else {
                    inventory.append("  No logic fragments acquired\n")
                }
                
                inventory.append("\nSpecial Items:\n")
                def validItems = managedPlayer.specialItems?.findAll { it != null }
                if (validItems?.size() > 0) {
                    validItems.each { item ->
                        if (item?.name) {
                            def usesText = item.isPermanent ? "[PERMANENT]" : "[${item.usesRemaining}/${item.maxUses} uses]"
                            def activeText = item.isActive ? " [ACTIVE]" : ""
                            inventory.append("  • ${item.name} ${usesText}${activeText}\n")
                            inventory.append("    ${item.description}\n")
                            if (item.expiresAt && item.isActive) {
                                def now = new Date()
                                def timeLeft = ((item.expiresAt.time - now.time) / 1000).toInteger()
                                if (timeLeft > 0) {
                                    inventory.append("    Expires in: ${timeLeft} seconds\n")
                                }
                            }
                        }
                    }
                } else {
                    inventory.append("  No special items acquired\n")
                }
                
                inventory.append("\nSkills:\n")
                def validSkills = managedPlayer.skills?.findAll { it != null }
                if (validSkills?.size() > 0) {
                    validSkills.each { skill ->
                        if (skill?.skillName) {
                            inventory.append("  • ${skill.skillName} - Level ${skill.level} (${skill.experience} XP)\n")
                        }
                    }
                } else {
                    inventory.append("  No skills acquired\n")
                }
            } else {
                inventory.append("Error: Player not found\n")
            }
        }
        
        return inventory.toString()
    }
    
    private String handleUseCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(' ', 2)
        if (parts.length < 2) {
            return showSpecialItemsUsage(player)
        }
        
        def itemName = parts[1]
        def result = specialItemService.useSpecialItem(player, itemName)
        
        if (result.success) {
            audioService.playSound("special_item_use")
            def response = new StringBuilder()
            response.append(TerminalFormatter.formatText("✨ ${result.message}", 'bold', 'green'))
            
            // Handle special item effects
            if (result.scanData) {
                response.append('\n').append(TerminalFormatter.formatText("📡 SCAN RESULTS:", 'bold', 'cyan'))
                result.scanData.each { scanLine ->
                    response.append('\n').append(scanLine)
                }
            }
            
            if (result.mapData) {
                response.append('\n').append(TerminalFormatter.formatText("🗺️  MATRIX MAP:", 'bold', 'cyan'))
                result.mapData.each { mapLine ->
                    response.append('\n').append(mapLine)
                }
            }
            
            return response.toString()
        } else {
            return TerminalFormatter.formatText("❌ ${result.message}", 'bold', 'red')
        }
    }
    
    private String showSpecialItemsUsage(LambdaPlayer player) {
        def items = specialItemService.listPlayerItems(player)
        def usage = new StringBuilder()
        
        usage.append(TerminalFormatter.formatText("=== SPECIAL ITEMS USAGE ===", 'bold', 'cyan')).append('\n')
        usage.append("Usage: use <item_name>\n\n")
        
        if (items) {
            usage.append("Available Items:\n")
            items.each { item ->
                def status = item.usesRemaining > 0 ? "[${item.usesRemaining} uses]" : "[DEPLETED]"
                def color = item.usesRemaining > 0 ? 'green' : 'red'
                usage.append("• ${item.name} ${TerminalFormatter.formatText(status, 'bold', color)}\n")
                usage.append("  ${item.description}\n")
            }
        } else {
            usage.append("No special items in inventory.\n")
            usage.append("Defeat defrag bots to find special items!\n")
        }
        
        return usage.toString()
    }

    private def createLogicFragmentFromType(String fragmentType, LambdaPlayer player) {
        def fragmentDefinitions = [
                'CONDITIONAL': [name: 'Advanced Conditional', description: 'Enhanced if-else logic with nested conditions', powerLevel: 4],
                'LOOP': [name: 'Advanced Loop', description: 'Optimized iteration with break/continue support', powerLevel: 5],
                'FUNCTION': [name: 'Advanced Function', description: 'Higher-order functions with lambda support', powerLevel: 6],
                'CLASS': [name: 'Advanced Class', description: 'Inheritance and polymorphism capabilities', powerLevel: 8],
                'EXCEPTION_HANDLING': [name: 'Advanced Exception', description: 'Custom exceptions and error handling', powerLevel: 7]
        ]

        def fragmentData = fragmentDefinitions[fragmentType]
        if (!fragmentData) return null

        return LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                def newFragment = new LogicFragment(
                        name: fragmentData.name,
                        description: fragmentData.description,
                        fragmentType: fragmentType,
                        powerLevel: fragmentData.powerLevel,
                        pythonCapability: "# Advanced ${fragmentType.toLowerCase()} capability\n# Unlocked through defrag bot victory",
                        quantity: 1,
                        isActive: true,
                        discoveredDate: new Date(),
                        owner: managedPlayer
                )
                newFragment.save(failOnError: true)
                println 'RETURNING THE FRAGMENT, FRAGMENT MADE'
                return fragmentData
            }
            println 'RETURNING NULL FRAGMENT NOT MADE'
            return null
        }
    }


    private String showHelp() {
        def help = new StringBuilder()
        help.append(TerminalFormatter.formatText("=== LAMBDA COMMAND REFERENCE ===", 'bold', 'cyan')).append('\n')
        help.append("status - Show detailed entity status\n")
        help.append("cc (x,y) - Change coordinates to specified position (may encounter defrag bots!)\n")
        help.append("scan - Scan current area for fragments, entities, and threats\n")
        help.append("inventory - Show logic fragments, special items, and skills\n")
        // Note: collect command removed - symbols obtained through puzzle-solving only
        help.append("symbols - Show elemental symbol collection status\n")
        help.append("recurse <ability> - Use ethnicity recursion power (movement/fusion/defend/mine/stealth/process)\n")
        help.append("\nPuzzle System Commands:\n")
        help.append("collect_var <name> - Collect hidden puzzle variables\n")
        help.append("execute <fragment> [variable] - Execute puzzle logic fragments\n")
        help.append("execute --<flag> <nonce> <file> - Unlock elemental symbols in puzzle rooms\n")
        help.append("pinv - Show puzzle inventory (fragments, variables, nonces)\n")
        help.append("pprog - Show competitive puzzle progress\n")
        help.append("pmarket - Show tradeable puzzle knowledge\n")
        help.append("\nmingle - Enter the Lambda mingle chamber for chat and trading\n")
        help.append("help - Show this command reference\n")
        help.append("quit - Disconnect from the digital realm\n")
        help.append("\nMingle Commands:\n")
        help.append("echo <message> - Send message to mingle chamber\n")
        help.append("pay <entity> <bits> - Transfer bits to another player\n")
        help.append("pm <entity> <message> - Send private message\n")
        help.append("trade <entity> - Open trading interface with player\n")
        help.append("list/who - Show all entities in mingle chamber\n")
        help.append("exit - Leave mingle chamber\n")
        help.append("\nDefrag Bot Encounters:\n")
        help.append("defrag -h - Learn how to use file system and grep to find process PID\n")
        help.append("cat /proc/defrag/<botId> - View defrag bot process file\n")
        help.append("grep '<regex>' /proc/defrag/<botId> - Search for PID using regex\n")
        help.append("kill -9 <pid> - Terminate defrag bot (after acquiring PID)\n")
        help.append("⚠️  Warning: Defrag bots drain 5 bits every 5 seconds!\n")
        help.append("\nLogic Fragment Commands:\n")
        help.append("pickup - Collect logic fragment at current coordinates\n")
        help.append("cat fragment_file - View your collected fragment file\n")
        help.append("cat <fragment_name> - View specific fragment content\n")
        help.append("\nSpecial Items:\n")
        help.append("use <item_name> - Activate special item abilities\n")
        help.append("use - Show available special items and their effects\n")
        help.append("\nEntropy & Mining System:\n")
        help.append("entropy - Check digital coherence status and daily refresh\n")
        help.append("mining - Collect passive bit mining rewards\n")
        help.append("fusion <fragment> - Fuse 3+ fragments for enhanced versions\n")
        help.append("\nLambda Merchant Commands:\n")
        help.append("shop - Browse merchant inventory when at merchant coordinates\n")
        help.append("buy <item_number> - Purchase item from merchant\n")
        help.append("sell <fragment_name> - Sell logic fragment to merchant\n")
        help.append("\nCoordinate Repair System:\n")
        help.append("repair - Show adjacent repairable coordinates\n")
        help.append("repair <x> <y> - Repair specific adjacent wiped coordinate\n")
        help.append("repair scan - Detailed scan of all repairable coordinates\n")
        help.append("repair status - Display area repair status in 5x5 grid\n")
        help.append("defrag_status - Show auto-defrag system status and warnings\n")
        help.append("⚠️  Warning: Auto-defrag bots destroy coordinates every minute!\n")
        help.append("\nMap & Navigation Commands:\n")
        help.append("map - Show full matrix level map with outages, entities, and threats\n")
        help.append("session - Show game session and randomization status\n")
        help.append("\nFile System Commands:\n")
        help.append("ls - List available files in your entity directory\n")
        help.append("clear - Clear terminal screen\n")
        help.append("cat <filename> - View file contents (use with files from 'ls')\n")
        return help.toString()
    }
    
    private String handleCatCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(' ')
        if (parts.length < 2) {
            return "Usage: cat <filename>"
        }
        
        def filename = parts[1]
        
        // Handle fragment_file viewing
        if (filename == "fragment_file") {
            return viewFragmentFile(player)
        }
        
        // Handle system files from ls command
        switch (filename) {
            case 'status_log':
                return getPlayerStatus(player)
            case 'inventory_data':
                return showInventory(player)
            case 'entropy_monitor':
                return viewEntropyMonitor(player)
            case 'system_map':
                return showMatrixMap(player)
            case 'python_env':
                return viewPythonEnvironment(player)
            case 'item_registry':
                return viewItemRegistry(player)
            case 'exploration_log':
                return viewExplorationLog(player)
            case 'ethnicity_config':
                return viewEthnicityConfig(player)
        }
        
        // Handle specific fragment viewing
        def fragment = findPlayerFragment(player, filename)
        if (fragment) {
            return viewFragmentContent(fragment)
        }
        
        // Check if there's a logic fragment at current coordinates
        def coordinateFragment = findFragmentAtCoordinates(player)
        if (coordinateFragment && coordinateFragment.name.toLowerCase().replace(' ', '_') == filename.toLowerCase()) {
            return viewFragmentContent(coordinateFragment)
        }
        
        return "File not found: ${filename}"
    }
    
    private String handlePickupCommand(LambdaPlayer player) {
        def fragment = findFragmentAtCoordinates(player)
        if (!fragment) {
            return "No logic fragment found at current coordinates (${player.positionX},${player.positionY})"
        }
        
        def resultMessage = ""
        
        // All database operations must be within transaction
        LambdaPlayer.withTransaction {
            // Check if player has already picked up a fragment at this coordinate
            def existingPickup = FragmentPickup.findByPlayerUsernameAndMatrixLevelAndPositionXAndPositionY(
                player.username, 
                player.currentMatrixLevel, 
                player.positionX, 
                player.positionY
            )
            
            if (existingPickup) {
                resultMessage = "You have already collected a logic fragment from this coordinate. Fragments respawn elsewhere after pickup."
                return // Exit transaction early
            }
            
            // Check if player already has this fragment
            def existingFragment = findPlayerFragment(player, fragment.name)
            def managedPlayer = LambdaPlayer.get(player.id)
            
            if (managedPlayer) {
                if (existingFragment) {
                    // Increment quantity of existing fragment
                    def managedFragment = LogicFragment.get(existingFragment.id)
                    if (managedFragment) {
                        managedFragment.quantity += 1
                        managedFragment.save(failOnError: true)
                        audioService.playSound("fragment_pickup")
                        resultMessage = "Logic fragment '${fragment.name}' acquired! Quantity: x${managedFragment.quantity}"
                        def validFragments = managedPlayer.logicFragments?.findAll { it != null }
                        if (validFragments?.size() > 0) {
                            validFragments.each { frag ->
                                if (frag?.name) {
                                    println "Player has fragment: ${frag.name} (${frag.fragmentType}) - Level ${frag.powerLevel}, Quantity: ${frag.quantity}"
                                }
                            }
                        }
                    }
                } else {
                    // Create new fragment entry
                    def newFragment = new LogicFragment(
                        name: fragment.name,
                        description: fragment.description,
                        fragmentType: fragment.fragmentType,
                        powerLevel: fragment.powerLevel,
                        pythonCapability: fragment.pythonCapability,
                        quantity: 1,
                        isActive: true,
                        discoveredDate: new Date(),
                        owner: managedPlayer
                    )
                    newFragment.save(failOnError: true)
                    managedPlayer.addToLogicFragments(newFragment)
                    managedPlayer.save(failOnError: true)
                    def validFragments = managedPlayer.logicFragments?.findAll { it != null }
                    if (validFragments?.size() > 0) {
                        validFragments.each { frag ->
                            if (frag?.name) {
                                println "Player has fragment: ${frag.name} (${frag.fragmentType}) - Level ${frag.powerLevel}, Quantity: ${frag.quantity}"
                            }
                        }
                    }
                    audioService.playSound("fragment_pickup")
                    resultMessage = "Logic fragment '${fragment.name}' acquired and added to your fragment file!"
                }
                
                // Record the pickup to prevent infinite collection at this coordinate
                def fragmentPickup = new FragmentPickup(
                    playerUsername: managedPlayer.username,
                    matrixLevel: managedPlayer.currentMatrixLevel,
                    positionX: managedPlayer.positionX,
                    positionY: managedPlayer.positionY,
                    fragmentName: fragment.name,
                    pickedUpAt: new Date()
                )
                fragmentPickup.save(failOnError: true)
            }
        }
        
        // Respawn fragment at random coordinates on the same level (outside transaction)
        if (resultMessage.contains("acquired")) {
            respawnFragmentAtRandomLocation(fragment, player.currentMatrixLevel)
        }
        
        return resultMessage
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
        display.append("Current Coherence: ${TerminalFormatter.formatText("${currentEntropy}%", getEntropyColor(currentEntropy), 'bold')}\n")
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
    
    private String handleMiningCommand(LambdaPlayer player, PrintWriter writer) {
        def result = entropyService.collectMiningRewards(player)
        
        if (result.success) {
            // Update session player
            LambdaPlayer.withTransaction {
                def updatedPlayer = LambdaPlayer.get(player.id)
                if (updatedPlayer) {
                    playerSessions[writer] = updatedPlayer
                    player.bits = updatedPlayer.bits
                    player.entropy = updatedPlayer.entropy
                }
            }
            
            def response = new StringBuilder()
            response.append(TerminalFormatter.formatText("⛏️  MINING OPERATION COMPLETE!", 'bold', 'green')).append('\n')
            response.append(result.message).append('\n')
            response.append("Hours Offline: ${result.rewards.hoursOffline}h\n")
            response.append("Efficiency: ${Math.round(result.rewards.entropyMultiplier * 100)}%\n")
            if (result.rewards.cappedAt24Hours) {
                response.append(TerminalFormatter.formatText("⚠️  Mining capped at 24 hours", 'italic', 'yellow'))
            }
            
            return response.toString()
        } else {
            return TerminalFormatter.formatText(result.message, 'italic', 'yellow')
        }
    }
    
    private String handleFusionCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(' ')
        if (parts.length < 2) {
            return showFusionStatus(player)
        }
        
        def fragmentName = parts[1..-1].join(' ')
        return attemptFragmentFusion(player, fragmentName)
    }
    
    private String showFusionStatus(LambdaPlayer player) {
        def attempts = entropyService.getFragmentFusionAttempts(player)
        def display = new StringBuilder()
        
        display.append(TerminalFormatter.formatText("=== FRAGMENT FUSION STATUS ===", 'bold', 'cyan')).append('\n')
        display.append("Daily Attempts: ${attempts.used}/${attempts.max}\n")
        display.append("Remaining: ${TerminalFormatter.formatText("${attempts.remaining}", 'bold', attempts.remaining > 0 ? 'green' : 'red')}\n\n")
        display.append("Usage: fusion <fragment_name>\n")
        display.append("Requires: 3+ identical fragments\n")
        display.append("Success Rate: Variable (higher with more fragments)\n\n")
        
        // Show fusible fragments
        def fusibleFragments = findFusibleFragments(player)
        if (fusibleFragments) {
            display.append("Available for Fusion:\n")
            fusibleFragments.each { fragment ->
                display.append("• ${fragment.name} x${fragment.quantity}\n")
            }
        } else {
            display.append("No fragments available for fusion\n")
        }
        
        return display.toString()
    }
    
    private String attemptFragmentFusion(LambdaPlayer player, String fragmentName) {
        def attempts = entropyService.getFragmentFusionAttempts(player)
        if (attempts.remaining <= 0) {
            return TerminalFormatter.formatText("No fusion attempts remaining today", 'bold', 'red')
        }
        
        // Find fragment to fuse
        def targetFragment = findPlayerFragment(player, fragmentName)
        if (!targetFragment || targetFragment.quantity < 3) {
            return "Need at least 3 identical fragments to attempt fusion"
        }
        
        // Calculate success rate based on quantity and ethnicity bonus
        def baseSuccessRate = 30  // 30% base
        def bonusRate = (targetFragment.quantity - 3) * 10  // +10% per extra fragment
        def ethnicityBonus = (player.fusionSuccessBonus ?: 0.0) * 100  // Binary Form +15%
        def successRate = Math.min(95, baseSuccessRate + bonusRate + ethnicityBonus)  // Cap at 95%
        
        def success = Math.random() * 100 < successRate
        
        // Update player
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            def managedFragment = managedPlayer.logicFragments.find { it.id == targetFragment.id }
            
            if (managedFragment && managedPlayer) {
                managedPlayer.fusionAttempts += 1
                
                if (success) {
                    // Success: Remove 3 fragments, create 1 enhanced version
                    managedFragment.quantity -= 3
                    
                    // Create enhanced version regardless of remaining quantity
                    def enhancedFragment = new LogicFragment(
                        name: "${managedFragment.name} Enhanced",
                        description: "Fused variant with improved capabilities",
                        fragmentType: managedFragment.fragmentType,
                        powerLevel: Math.min(10, managedFragment.powerLevel + 1),
                        pythonCapability: enhanceFragmentCapability(managedFragment.pythonCapability),
                        quantity: 1,
                        isActive: true,
                        discoveredDate: new Date(),
                        owner: managedPlayer
                    )
                    enhancedFragment.save(failOnError: true)
                    managedPlayer.addToLogicFragments(enhancedFragment)
                    
                    // Remove original if quantity is now 0
                    if (managedFragment.quantity <= 0) {
                        managedPlayer.removeFromLogicFragments(managedFragment)
                        managedFragment.delete()
                    } else {
                        managedFragment.save(failOnError: true)
                    }
                } else {
                    // Failure: Lose 1 fragment
                    managedFragment.quantity -= 1
                    if (managedFragment.quantity <= 0) {
                        managedPlayer.removeFromLogicFragments(managedFragment)
                        managedFragment.delete()
                    } else {
                        managedFragment.save(failOnError: true)
                    }
                }
                
                managedPlayer.save(failOnError: true)
            }
        }
        
        def response = new StringBuilder()
        if (success) {
            audioService.playSound("fusion_success")
            response.append(TerminalFormatter.formatText("✨ FUSION SUCCESS!", 'bold', 'green')).append('\n')
            response.append("Created enhanced ${fragmentName} with +1 power level!")
        } else {
            audioService.playSound("fusion_fail")
            response.append(TerminalFormatter.formatText("💥 Fusion Failed", 'bold', 'red')).append('\n')
            response.append("Lost 1 fragment in the process. Better luck next time!")
        }
        response.append("\nFusion attempts remaining: ${attempts.remaining - 1}")
        
        return response.toString()
    }
    
    private String getEntropyColor(Double entropy) {
        def safeEntropy = entropy ?: 100.0
        if (safeEntropy >= 75) return 'green'
        if (safeEntropy >= 50) return 'yellow'
        if (safeEntropy >= 25) return 'red'
        return 'red'
    }
    
    private List findFusibleFragments(LambdaPlayer player) {
        def fragments = []
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer?.logicFragments) {
                fragments = managedPlayer.logicFragments.findAll { it != null && it.quantity >= 3 }
            }
        }
        return fragments
    }
    
    private String enhanceFragmentCapability(String originalCapability) {
        return "${originalCapability}\n\n# Enhanced fusion variant with 25% improved efficiency"
    }
    
    private String handleMerchantCommand(String command, LambdaPlayer player) {
        // Check if there's a merchant at current position
        def merchant = lambdaMerchantService.getMerchantAt(player.currentMatrixLevel, player.positionX, player.positionY)
        if (!merchant) {
            return "No Lambda merchant at current coordinates (${player.positionX},${player.positionY}). Use 'map' to find merchants. (M) "
        }
        
        def result = lambdaMerchantService.handleMerchantInteraction(merchant, command, player)
        
        if (result.action == "browse") {
            return result.output
        } else if (result.action == "purchase") {
            return TerminalFormatter.formatText(result.output, 'bold', 'green')
        } else if (result.action == "sale") {
            return TerminalFormatter.formatText(result.output, 'bold', 'green')
        } else if (result.action == "help") {
            return TerminalFormatter.formatText(result.output, 'italic', 'yellow')
        } else {
            return TerminalFormatter.formatText(result.output, 'bold', 'red')
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
    
    private LogicFragment findPlayerFragment(LambdaPlayer player, String fragmentName) {
        def foundFragment = null
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer && managedPlayer.logicFragments) {
                foundFragment = managedPlayer.logicFragments.findAll { it != null }.find { fragment ->
                    fragment.name && (
                        fragment.name.toLowerCase().replace(' ', '_') == fragmentName.toLowerCase() ||
                        fragment.name.toLowerCase() == fragmentName.toLowerCase()
                    )
                }
            }
        }
        return foundFragment
    }
    
    private LogicFragment findFragmentAtCoordinates(LambdaPlayer player) {
        // Use game session service for truly random fragment distribution
        return gameSessionService.getFragmentAtCoordinates(player.currentMatrixLevel, player.positionX, player.positionY)
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
    
    
    private void updatePlayerPositionOnBoard(LambdaPlayer player) {
        // TODO: Implement GPIO LED updates for physical board
        // This will light up the LED corresponding to the player's position
        println "Updating board position for ${player.displayName} at Matrix Level ${player.currentMatrixLevel} (${player.positionX},${player.positionY})"
    }
    
    private String enterMingle(LambdaPlayer player, PrintWriter writer) {
        lambdaPlayerService.setMingleStatus(player, true)
        
        // Send system message and broadcast to others
        def systemMsg = "${player.displayName} enters the mingle chamber"
        chatService.sendSystemMessage(systemMsg)
        
        def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
        def broadcastMsg = "[${timeStr}] ${TerminalFormatter.formatText('[SYSTEM]', 'bold', 'red')} ${systemMsg}"
        broadcastToMingleUsers(broadcastMsg)
        
        // Show recent messages to the entering player
        def recentMessages = chatService.getRecentMessages(15)
        def display = new StringBuilder()
        display.append(TerminalFormatter.formatText("=== LAMBDA MINGLE CHAMBER ===", 'bold', 'cyan')).append('\n')
        display.append(TerminalFormatter.formatText("IRC-style chat • Type 'echo <message>' to talk • 'exit' to leave", 'italic', 'yellow')).append('\n')
        display.append("─" * 80).append('\n')
        
        recentMessages.each { msg ->
            display.append(chatService.formatMessageByType(msg)).append('\n')
        }
        
        display.append("─" * 80).append('\n')
        display.append(TerminalFormatter.formatText("You are now in the mingle chamber. Use 'echo <message>' to chat.", 'bold', 'green'))
        
        return display.toString()
    }
    
    private String handleMingleCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def trimmedCommand = command.trim()
        
        if (trimmedCommand.equalsIgnoreCase('exit')) {
            lambdaPlayerService.setMingleStatus(player, false)
            
            // Broadcast exit message to remaining users
            def systemMsg = "${player.displayName} leaves the mingle chamber"
            chatService.sendSystemMessage(systemMsg)
            
            def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
            def broadcastMsg = "[${timeStr}] ${TerminalFormatter.formatText('[SYSTEM]', 'bold', 'red')} ${systemMsg}"
            broadcastToMingleUsers(broadcastMsg)
            
            return TerminalFormatter.formatText("Exited mingle chamber. Back to the digital realm.", 'bold', 'green')
        }
        
        if (trimmedCommand.toLowerCase().startsWith('echo ')) {
            def message = chatService.processEchoCommand(trimmedCommand)
            if (message) {
                def result = chatService.sendMessage(player, message)
                if (result.success) {
                    // Show immediate feedback with the new message in IRC style
                    def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
                    def ircMessage = "[${timeStr}] ${TerminalFormatter.formatText(player.displayName, 'bold', 'white')}: ${message}"
                    
                    // Broadcast to all mingle users immediately
                    broadcastToMingleUsers(ircMessage)
                    
                    return TerminalFormatter.formatText("→ Message sent to mingle chamber", 'italic', 'green')
                } else {
                    return TerminalFormatter.formatText("Error: ${result.error}", 'bold', 'red')
                }
            } else {
                return TerminalFormatter.formatText("Invalid echo format. Use: echo <message>", 'bold', 'red')
            }
        }
        
        if (trimmedCommand.toLowerCase().startsWith('pay')) {
            if (trimmedCommand.toLowerCase() == 'pay') {
                return TerminalFormatter.formatText("Usage: pay <entity_name> <bits>", 'bold', 'yellow')
            }
            return handlePayCommand(trimmedCommand, player, writer)
        }
        
        if (trimmedCommand.toLowerCase().startsWith('pm')) {
            if (trimmedCommand.toLowerCase() == 'pm') {
                return TerminalFormatter.formatText("Usage: pm <entity_name> <message>", 'bold', 'yellow')
            }
            return handlePrivateMessageCommand(trimmedCommand, player, writer)
        }
        
        if (trimmedCommand.toLowerCase().startsWith('trade')) {
            if (trimmedCommand.toLowerCase() == 'trade') {
                return TerminalFormatter.formatText("Usage: trade <entity_name>", 'bold', 'yellow')
            }
            return handleTradeCommand(trimmedCommand, player, writer)
        }
        
        if (trimmedCommand.equalsIgnoreCase('list') || trimmedCommand.equalsIgnoreCase('who')) {
            return listMingleUsers(player)
        }
        
        if (trimmedCommand.equalsIgnoreCase('help')) {
            return showMingleHelp()
        }
        
        // For pressing enter or unknown commands, just give simple feedback
        if (trimmedCommand.isEmpty()) {
            return TerminalFormatter.formatText("Mingle commands: echo <msg> | pay <entity> <bits> | pm <entity> <msg> | trade <entity> | list | help | exit", 'italic', 'cyan')
        } else {
            return TerminalFormatter.formatText("Unknown command '${trimmedCommand}'. Type 'help' for mingle commands.", 'italic', 'yellow')
        }
    }
    
    private String handleDefragEncounter(String command, LambdaPlayer player, PrintWriter writer) {
        def defragBot = activeDefragSessions[writer]
        if (!defragBot) {
            activeDefragSessions.remove(writer)
            return "No active defrag encounter found."
        }
        
        def result = defragBotService.handleDefragCommand(defragBot, command, player)
        
        if (result.success) {
            // Player defeated the defrag bot
            activeDefragSessions.remove(writer)
            audioService.playSound("defrag_victory")
            
            def response = new StringBuilder()
            response.append(TerminalFormatter.formatText("✅ DEFRAG BOT TERMINATED!", 'bold', 'green')).append('\n')
            response.append(result.output).append('\n')
            
            // Apply rewards
            if (result.rewards.bits) {
                lambdaPlayerService.addBits(player, result.rewards.bits)
                audioService.playSound("bits_earned")
                response.append(TerminalFormatter.formatText("Earned ${result.rewards.bits} bits!", 'bold', 'yellow')).append('\n')
                
                // Update session player object with new bits
                LambdaPlayer.withTransaction {
                    def updatedPlayer = LambdaPlayer.get(player.id)
                    if (updatedPlayer) {
                        playerSessions[writer] = updatedPlayer
                        player.bits = updatedPlayer.bits
                    }
                }
            }
            
            if (result.rewards.stolenFragment) {
                // Return stolen fragment to player
                def fragment = result.rewards.stolenFragment
                response.append(TerminalFormatter.formatText("Recovered stolen logic fragment: ${fragment.name}!", 'bold', 'cyan')).append('\n')
            }
            
            if (result.rewards.specialItem) {
                try {
                    println "DEBUG TelnetServer: Creating special item '${result.rewards.specialItem}' for player ${player.displayName} (ID: ${player.id})"
                    def item = specialItemService.createSpecialItem(player, result.rewards.specialItem)
                    audioService.playSound("item_found")
                    if (item) {
                        println "DEBUG TelnetServer: Item creation SUCCESS - ${item.name} (ID: ${item.id})"
                        response.append(TerminalFormatter.formatText("Found special item: ${item.name}!", 'bold', 'magenta')).append('\n')
                        response.append(TerminalFormatter.formatText("${item.description}", 'italic', 'white')).append('\n')
                    } else {
                        println "DEBUG TelnetServer: Item creation FAILED - createSpecialItem returned null"
                        response.append(TerminalFormatter.formatText("Found special item: ${result.rewards.specialItem}!", 'bold', 'magenta')).append('\n')
                        response.append(TerminalFormatter.formatText("⚠️ Item creation failed - please contact admin", 'italic', 'red')).append('\n')
                    }
                } catch (Exception e) {
                    println "DEBUG TelnetServer: Exception during item creation: ${e.class.simpleName}: ${e.message}"
                    e.printStackTrace()
                    response.append(TerminalFormatter.formatText("Found special item: ${result.rewards.specialItem}!", 'bold', 'magenta')).append('\n')
                    response.append(TerminalFormatter.formatText("⚠️ Item creation error - ${e.message}", 'italic', 'red')).append('\n')
                }
            }
            
            if (result.rewards.puzzleFragment) {
                try {
                    def puzzleResult = puzzleService.awardPuzzleFragment(player, result.rewards.puzzleFragment)
                    if (puzzleResult.success) {
                        audioService.playSound("fragment_pickup")
                        response.append(TerminalFormatter.formatText("${puzzleResult.message}!", 'bold', 'purple')).append('\n')
                        response.append(TerminalFormatter.formatText("Executable logic fragment acquired - use 'pinv' to view!", 'italic', 'white')).append('\n')
                    } else {
                        response.append(TerminalFormatter.formatText("Puzzle fragment found: ${result.rewards.puzzleFragment}!", 'bold', 'purple')).append('\n')
                        response.append(TerminalFormatter.formatText("⚠️ ${puzzleResult.message}", 'italic', 'red')).append('\n')
                    }
                } catch (Exception e) {
                    println "DEBUG TelnetServer: Exception during puzzle fragment award: ${e.message}"
                    response.append(TerminalFormatter.formatText("Puzzle fragment found: ${result.rewards.puzzleFragment}!", 'bold', 'purple')).append('\n')
                }
            }
            
            if (result.rewards.elementalNonce) {
                try {
                    def nonceResult = puzzleService.awardNonce(player, result.rewards.elementalNonce)
                    if (nonceResult.success) {
                        audioService.playSound("item_found")
                        response.append(TerminalFormatter.formatText("${nonceResult.message}!", 'bold', 'magenta')).append('\n')
                        response.append(TerminalFormatter.formatText("${nonceResult.nonce?.getChemicalHint() ?: 'Chemical signature detected!'}", 'italic', 'yellow')).append('\n')
                        response.append(TerminalFormatter.formatText("Use 'pinv' to view your nonce collection!", 'italic', 'white')).append('\n')
                    } else {
                        response.append(TerminalFormatter.formatText("Elemental nonce discovered: ${result.rewards.elementalNonce}!", 'bold', 'magenta')).append('\n')
                        response.append(TerminalFormatter.formatText("⚠️ ${nonceResult.message}", 'italic', 'red')).append('\n')
                    }
                } catch (Exception e) {
                    println "DEBUG TelnetServer: Exception during nonce award: ${e.message}"
                    response.append(TerminalFormatter.formatText("Elemental nonce discovered: ${result.rewards.elementalNonce}!", 'bold', 'magenta')).append('\n')
                }
            }
            
            if (result.rewards.logicFragment) {
                // Add logic fragment to player inventory
                def fragmentType = result.rewards.logicFragment
                def fragmentData = createLogicFragmentFromType(fragmentType, player)
                if (fragmentData) {
                    response.append(TerminalFormatter.formatText("Discovered logic fragment: ${fragmentData.name}!", 'bold', 'cyan')).append('\n')
                } else {
                    response.append(TerminalFormatter.formatText("Discovered logic fragment: ${result.rewards.logicFragment}!", 'bold', 'cyan')).append('\n')
                }
            }
            
            return response.toString()
            
        } else if (result.action == 'help') {
            // Show defrag help with file system instructions
            def helpDisplay = new StringBuilder()
            helpDisplay.append(TerminalFormatter.formatText("⚠️  DEFRAG PROCESS ANALYSIS", 'bold', 'red')).append('\n')
            helpDisplay.append(result.output.replace('\\n', '\n')).append('\n')
            return helpDisplay.toString()
            
        } else if (result.action == 'file_view') {
            // Show file content for cat command
            def fileDisplay = new StringBuilder()
            fileDisplay.append(TerminalFormatter.formatText("=== /proc/defrag/${defragBot.botId} ===", 'bold', 'cyan')).append('\n')
            fileDisplay.append(result.output).append('\n')
            return fileDisplay.toString()
            
        } else if (result.action == 'grep') {
            // Show grep output
            def grepDisplay = new StringBuilder()
            if (result.pidAcquired) {
                grepDisplay.append(TerminalFormatter.formatText(result.output, 'bold', 'green')).append('\n')
                grepDisplay.append(TerminalFormatter.formatText("Use 'kill -9 ${defragBot.processId}' to terminate process!", 'italic', 'yellow'))
            } else {
                grepDisplay.append(TerminalFormatter.formatText("Grep output:", 'bold', 'cyan')).append('\n')
                grepDisplay.append(result.output).append('\n')
                grepDisplay.append(TerminalFormatter.formatText("Refine your regex to isolate the PID on its own line.", 'italic', 'yellow'))
            }
            return grepDisplay.toString()
            
        } else {
            // Invalid command, timeout, or bit drain
            def errorDisplay = new StringBuilder()
            errorDisplay.append(TerminalFormatter.formatText("⚠️  ${result.output}", 'bold', 'red'))
            
            // Check if player got defragged
            LambdaPlayer.withTransaction {
                def refreshedPlayer = LambdaPlayer.get(player.id)
                if (refreshedPlayer && refreshedPlayer.bits <= 0) {
                    activeDefragSessions.remove(writer)
                    errorDisplay.append('\n').append(TerminalFormatter.formatText("💀 DEFRAGGED! Respawning at (0,0) with 10 bits...", 'bold', 'red'))
                    if (refreshedPlayer.logicFragments?.size() < player.logicFragments?.size()) {
                        errorDisplay.append('\n').append(TerminalFormatter.formatText("Logic fragment stolen by defrag bot!", 'bold', 'red'))
                    }
                    playerSessions[writer] = refreshedPlayer
                }
            }
            
            return errorDisplay.toString()
        }
    }
    
    private String handleDefragCommand(String command, LambdaPlayer player, PrintWriter writer) {
        // Check if there's a defrag bot at the player's current position
        def defragBot = null
        DefragBot.withTransaction {
            defragBot = DefragBot.findByMatrixLevelAndPositionXAndPositionY(
                player.currentMatrixLevel, player.positionX, player.positionY
            )
        }
        
        if (!defragBot) {
            return TerminalFormatter.formatText("No defrag bot present at current coordinates (${player.positionX},${player.positionY})", 'italic', 'yellow')
        }
        
        // If there is a defrag bot, start an encounter
        activeDefragSessions[writer] = defragBot
        
        def parts = command.trim().toLowerCase().split(' ')
        if (parts.length > 1 && parts[1] == '-h') {
            // Handle defrag -h help command
            def result = defragBotService.handleDefragCommand(defragBot, command, player)
            if (result.action == 'help') {
                def helpDisplay = new StringBuilder()
                helpDisplay.append(TerminalFormatter.formatText("⚠️  DEFRAG PROCESS ANALYSIS", 'bold', 'red')).append('\n')
                helpDisplay.append(result.output.replace('\\n', '\n')).append('\n')
                helpDisplay.append(TerminalFormatter.formatText("Find the kill command to terminate this process!", 'italic', 'yellow'))
                return helpDisplay.toString()
            }
        }
        
        // Regular defrag command without -h
        def encounter = new StringBuilder()
        encounter.append(TerminalFormatter.formatText("⚠️  DEFRAG BOT ENCOUNTERED!", 'bold', 'red')).append('\n')
        encounter.append(TerminalFormatter.formatText("System defragmentation process ${defragBot.botId} detected at (${player.positionX},${player.positionY})", 'italic', 'yellow')).append('\n')
        encounter.append(TerminalFormatter.formatText("Difficulty Level: ${defragBot.difficultyLevel}/10", 'bold', 'yellow')).append('\n')
        encounter.append(TerminalFormatter.formatText("Time limit: ${defragBot.timeLimit} seconds", 'bold', 'red')).append('\n')
        encounter.append(TerminalFormatter.formatText("⚠️  BITS DRAINING: -5 bits every 5 seconds!", 'bold', 'red')).append('\n')
        encounter.append("Type 'defrag -h' to learn how to find and terminate the process!")
        
        return encounter.toString()
    }

    private void sendToAllClients(String message) {
        clientWriters.each { writer ->
            writer.println(message)
        }
    }
    
    private void sendToMingleUsers(String message) {
        playerSessions.each { writer, player ->
            if (player.isInMingle) {
                writer.println(message)
            }
        }
    }
    
    private void broadcastToMingleUsers(String message) {
        playerSessions.each { writer, player ->
            // Check current mingle status from database
            LambdaPlayer.withTransaction {
                def currentPlayer = LambdaPlayer.get(player.id)
                if (currentPlayer?.isInMingle) {
                    writer.println(message)
                    writer.flush()
                }
            }
        }
    }
    
    private String handlePayCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command.trim().split(' ')
        if (parts.length < 3) {
            return TerminalFormatter.formatText("Usage: pay <entity_name> <bits>", 'bold', 'red')
        }
        
        def targetName = parts[1]
        def bitsAmount = 0
        try {
            bitsAmount = Integer.parseInt(parts[2])
        } catch (NumberFormatException e) {
            return TerminalFormatter.formatText("Invalid bit amount: ${parts[2]}", 'bold', 'red')
        }
        
        if (bitsAmount <= 0) {
            return TerminalFormatter.formatText("Bit amount must be positive", 'bold', 'red')
        }
        
        // Find target player in mingle
        def targetPlayer = findMingleUser(targetName)
        if (!targetPlayer) {
            return TerminalFormatter.formatText("Entity '${targetName}' not found in mingle chamber", 'bold', 'red')
        }
        
        if (targetPlayer.id == player.id) {
            return TerminalFormatter.formatText("Cannot pay yourself", 'bold', 'red')
        }
        
        // Check if sender has enough bits
        def currentBits = 0
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            currentBits = managedPlayer.bits
        }
        
        if (currentBits < bitsAmount) {
            return TerminalFormatter.formatText("Insufficient bits. You have: ${currentBits}", 'bold', 'red')
        }
        
        // Transfer bits
        LambdaPlayer.withTransaction {
            def sender = LambdaPlayer.get(player.id)
            def receiver = LambdaPlayer.get(targetPlayer.id)
            
            if (sender && receiver) {
                sender.bits -= bitsAmount
                receiver.bits += bitsAmount
                sender.save(failOnError: true)
                receiver.save(failOnError: true)
            }
        }
        
        // Notify both parties
        def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
        def systemMsg = "${TerminalFormatter.formatText('[PAYMENT]', 'bold', 'green')} ${player.displayName} sent ${bitsAmount} bits to ${targetName}"
        broadcastToMingleUsers("[${timeStr}] ${systemMsg}")
        
        return TerminalFormatter.formatText("Sent ${bitsAmount} bits to ${targetName}", 'bold', 'green')
    }
    
    private String handlePrivateMessageCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command.trim().split(' ', 3)
        if (parts.length < 3) {
            return TerminalFormatter.formatText("Usage: pm <entity_name> <message>", 'bold', 'red')
        }
        
        def targetName = parts[1]
        def message = parts[2]
        
        def targetPlayer = findMingleUser(targetName)
        if (!targetPlayer) {
            return TerminalFormatter.formatText("Entity '${targetName}' not found in mingle chamber", 'bold', 'red')
        }
        
        if (targetPlayer.id == player.id) {
            return TerminalFormatter.formatText("Cannot PM yourself", 'bold', 'red')
        }
        
        // Send private message to target
        def targetWriter = findWriterForPlayer(targetPlayer)
        if (targetWriter) {
            def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
            def pmMessage = "[${timeStr}] ${TerminalFormatter.formatText('[PM]', 'bold', 'magenta')} ${TerminalFormatter.formatText(player.displayName, 'bold', 'white')}: ${message}"
            targetWriter.println(pmMessage)
            targetWriter.flush()
        }
        
        return TerminalFormatter.formatText("Private message sent to ${targetName}", 'italic', 'green')
    }
    
    private String handleTradeCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command.trim().split(' ')
        if (parts.length < 2) {
            return TerminalFormatter.formatText("Usage: trade <entity_name>", 'bold', 'red')
        }
        
        def targetName = parts[1]
        def targetPlayer = findMingleUser(targetName)
        if (!targetPlayer) {
            return TerminalFormatter.formatText("Entity '${targetName}' not found in mingle chamber", 'bold', 'red')
        }
        
        if (targetPlayer.id == player.id) {
            return TerminalFormatter.formatText("Cannot trade with yourself", 'bold', 'red')
        }
        
        // Enhanced trade menu with puzzle knowledge
        def tradeMenu = new StringBuilder()
        tradeMenu.append(TerminalFormatter.formatText("=== ENHANCED TRADE INTERFACE ===", 'bold', 'cyan')).append('\n')
        tradeMenu.append("Target Entity: ${TerminalFormatter.formatText(targetName, 'bold', 'yellow')}\n\n")
        
        // STANDARD LOGIC FRAGMENTS
        tradeMenu.append(TerminalFormatter.formatText("📚 STANDARD LOGIC FRAGMENTS:", 'bold', 'green')).append('\n')
        def playerFragments = []
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer?.logicFragments) {
                playerFragments = managedPlayer.logicFragments.collect { it }
            }
        }
        
        if (playerFragments) {
            playerFragments.eachWithIndex { fragment, index ->
                tradeMenu.append("F${index + 1}. ${fragment.name} x${fragment.quantity} (Power: ${fragment.powerLevel}/10) - ~${20 + fragment.powerLevel * 5} bits\n")
            }
        } else {
            tradeMenu.append("No standard fragments to trade\n")
        }
        
        // PUZZLE KNOWLEDGE
        def tradeableKnowledge = puzzleKnowledgeTradingService.getTradeablePuzzleKnowledge(player)
        
        tradeMenu.append("\n${TerminalFormatter.formatText('🧩 PUZZLE KNOWLEDGE:', 'bold', 'purple')}\n")
        
        // Puzzle Fragments
        if (tradeableKnowledge.puzzleFragments.size() > 0) {
            tradeMenu.append("Executable Puzzle Fragments:\n")
            tradeableKnowledge.puzzleFragments.eachWithIndex { item, index ->
                tradeMenu.append("PF${index + 1}. ${item.name} (${item.elementHint}) - ${item.tradeValue} bits\n")
            }
        }
        
        // Variables
        if (tradeableKnowledge.variables.size() > 0) {
            tradeMenu.append("Collected Variables:\n")
            tradeableKnowledge.variables.eachWithIndex { item, index ->
                tradeMenu.append("V${index + 1}. ${item.name} (${item.elementHint}) - ${item.tradeValue} bits\n")
            }
        }
        
        // Nonces (only tradeable ones)
        def tradeableNonces = tradeableKnowledge.nonces.findAll { it.canTrade }
        if (tradeableNonces.size() > 0) {
            tradeMenu.append("Elemental Nonces:\n")
            tradeableNonces.eachWithIndex { item, index ->
                tradeMenu.append("N${index + 1}. ${item.name} (${item.elementType}) - ${item.tradeValue} bits\n")
                tradeMenu.append("     Digital Spec: ${item.chemicalClue} | Flag: ${item.commandFlag}\n")
            }
        }
        
        // Complete Solutions
        if (tradeableKnowledge.completedSolutions.size() > 0) {
            tradeMenu.append("Complete Solutions:\n")
            tradeableKnowledge.completedSolutions.eachWithIndex { item, index ->
                tradeMenu.append("S${index + 1}. ${item.description} - ${item.tradeValue} bits\n")
                tradeMenu.append("     ${item.includes}\n")
            }
        }
        
        if (tradeableKnowledge.puzzleFragments.size() == 0 && 
            tradeableKnowledge.variables.size() == 0 && 
            tradeableNonces.size() == 0 && 
            tradeableKnowledge.completedSolutions.size() == 0) {
            tradeMenu.append("No puzzle knowledge available for trade\n")
        }
        
        tradeMenu.append("\n${TerminalFormatter.formatText('💰 TRADING COMMANDS:', 'bold', 'yellow')}\n")
        tradeMenu.append("Standard Fragments: offer F<num> <quantity> <price>\n")
        tradeMenu.append("Puzzle Fragments: offer PF<num> <price>\n")
        tradeMenu.append("Variables: offer V<num> <price>\n")
        tradeMenu.append("Nonces: offer N<num> <price>\n")
        tradeMenu.append("Complete Solutions: offer S<num> <price>\n")
        tradeMenu.append("cancel - Cancel trade\n")
        
        return tradeMenu.toString()
    }
    
    private String listMingleUsers(LambdaPlayer player) {
        def userList = new StringBuilder()
        userList.append(TerminalFormatter.formatText("=== MINGLE CHAMBER ENTITIES ===", 'bold', 'cyan')).append('\n')
        
        def mingleUsers = []
        LambdaPlayer.withTransaction {
            mingleUsers = LambdaPlayer.findAllByIsInMingle(true)
        }
        
        if (mingleUsers) {
            mingleUsers.each { user ->
                def marker = (user.id == player.id) ? " (you)" : ""
                userList.append("• ${user.displayName}${marker} [Level ${user.currentMatrixLevel}]\n")
            }
        } else {
            userList.append("No entities in mingle chamber\n")
        }
        
        return userList.toString()
    }
    
    private String showMingleHelp() {
        def help = new StringBuilder()
        help.append(TerminalFormatter.formatText("=== MINGLE CHAMBER COMMANDS ===", 'bold', 'cyan')).append('\n')
        help.append(TerminalFormatter.formatText("Communication:", 'bold', 'white')).append('\n')
        help.append("echo <message> - Send message to all entities in chamber\n")
        help.append("pm <entity> <message> - Send private message to specific entity\n")
        help.append("list/who - Show all entities currently in mingle chamber\n\n")
        
        help.append(TerminalFormatter.formatText("Trading & Economy:", 'bold', 'white')).append('\n')
        help.append("pay <entity> <bits> - Transfer bits to another player\n")
        help.append("trade <entity> - Open enhanced trading interface\n")
        help.append("  └─ Trade standard fragments AND puzzle knowledge\n")
        help.append("  └─ Puzzle fragments, variables, nonces, complete solutions\n")
        help.append("  └─ Winners can monetize their knowledge!\n\n")
        
        help.append(TerminalFormatter.formatText("Navigation:", 'bold', 'white')).append('\n')
        help.append("exit - Leave mingle chamber and return to matrix\n")
        help.append("help - Show this command reference\n\n")
        
        help.append(TerminalFormatter.formatText("💡 Tips:", 'italic', 'yellow')).append('\n')
        help.append("• Use 'list' to see who's available for trading\n")
        help.append("• Logic fragments are valuable - trade them for bits!\n")
        help.append("• Higher power level fragments are worth more\n")
        help.append("• Duplicate fragments (x2, x3) can be sold individually\n")
        
        return help.toString()
    }
    
    private LambdaPlayer findMingleUser(String name) {
        def foundPlayer = null
        LambdaPlayer.withTransaction {
            foundPlayer = LambdaPlayer.findByDisplayNameAndIsInMingle(name, true)
        }
        return foundPlayer
    }
    
    private PrintWriter findWriterForPlayer(LambdaPlayer target) {
        return playerSessions.find { writer, player -> 
            player.id == target.id 
        }?.key
    }
    
    private String handleRepairCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(' ')
        
        if (parts.length == 1) {
            return showRepairOptions(player)
        }
        
        if (parts.length == 3) {
            // repair <x> <y> - Initiate repair mini-game for specific adjacent coordinate
            try {
                def targetX = Integer.parseInt(parts[1])
                def targetY = Integer.parseInt(parts[2])
                return initiateRepairMiniGame(player, targetX, targetY, playerSessions.find { it.value?.id == player.id }?.key)
            } catch (NumberFormatException e) {
                return "Invalid coordinates. Usage: repair <x> <y>"
            }
        }
        
        def subCommand = parts[1].toLowerCase()
        
        switch (subCommand) {
            case 'status':
                return showAreaRepairStatus(player)
            case 'scan':
                return showRepairableCoordinates(player)
            case 'help':
                return showRepairHelp()
            default:
                return "Unknown repair command. Use 'repair help' for available commands."
        }
    }
    
    private String showRepairOptions(LambdaPlayer player) {
        def repair = new StringBuilder()
        repair.append(TerminalFormatter.formatText("=== COORDINATE REPAIR SYSTEM ===", 'bold', 'cyan')).append('\n')
        
        def coordinateHealth = coordinateStateService.getCoordinateHealth(player.currentMatrixLevel, player.positionX, player.positionY)
        repair.append("Current Position: (${player.positionX},${player.positionY})\n")
        repair.append("Health: ${TerminalFormatter.formatText("${coordinateHealth.health}%", 'bold', coordinateHealth.color)} - ${coordinateHealth.status}\n\n")
        
        // Show repairable adjacent coordinates
        def repairableCoords = coordinateStateService.getRepairableCoordinatesForPlayer(player)
        if (repairableCoords.size() > 0) {
            repair.append(TerminalFormatter.formatText("🔧 ADJACENT WIPED COORDINATES:", 'bold', 'red')).append('\n')
            repairableCoords.each { coord ->
                def dateStr = coord.lastDamaged ? new java.text.SimpleDateFormat('HH:mm').format(coord.lastDamaged) : 'Unknown'
                repair.append("  (${coord.x},${coord.y}) - ${coord.status} - Destroyed at ${dateStr}\n")
            }
            repair.append('\n')
        } else {
            repair.append(TerminalFormatter.formatText("✅ No adjacent coordinates need repair", 'bold', 'green')).append('\n\n')
        }
        
        repair.append("Available Commands:\n")
        repair.append("repair scan - Show all adjacent repairable coordinates\n")
        repair.append("repair <x> <y> - Repair specific adjacent coordinate\n")
        repair.append("repair status - Show area repair status\n")
        repair.append("repair help - Show detailed repair help\n\n")
        
        if (repairableCoords.size() > 0) {
            repair.append(TerminalFormatter.formatText("💡 TIP:", 'bold', 'yellow'))
                .append(" Use 'repair <x> <y>' to fix adjacent wiped coordinates\n")
            repair.append("Example: repair ${repairableCoords[0].x} ${repairableCoords[0].y}")
        }
        
        return repair.toString()
    }
    
    private String showAreaRepairStatus(LambdaPlayer player) {
        def repair = new StringBuilder()
        repair.append(TerminalFormatter.formatText("=== AREA REPAIR STATUS ===", 'bold', 'cyan')).append('\n')
        
        def damaged = []
        def critical = []
        def wiped = []
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                def scanX = Math.max(0, Math.min(9, player.positionX + dx))
                def scanY = Math.max(0, Math.min(9, player.positionY + dy))
                def health = coordinateStateService.getCoordinateHealth(player.currentMatrixLevel, scanX, scanY)
                
                def distance = Math.round(Math.sqrt(dx * dx + dy * dy) * 10) / 10
                def coordInfo = "(${scanX},${scanY}) - ${health.health}% [Dist: ${distance}]"
                
                if (health.health <= 0) {
                    wiped.add(coordInfo)
                } else if (health.health <= 25) {
                    critical.add(coordInfo)
                } else if (health.health < 100) {
                    damaged.add(coordInfo)
                }
            }
        }
        
        if (wiped) {
            repair.append(TerminalFormatter.formatText("🔴 WIPED COORDINATES:", 'bold', 'red')).append('\n')
            wiped.each { repair.append("  ${it}\n") }
            repair.append('\n')
        }
        
        if (critical) {
            repair.append(TerminalFormatter.formatText("🟡 CRITICAL COORDINATES:", 'bold', 'yellow')).append('\n')
            critical.each { repair.append("  ${it}\n") }
            repair.append('\n')
        }
        
        if (damaged) {
            repair.append(TerminalFormatter.formatText("🟠 DAMAGED COORDINATES:", 'bold', 'cyan')).append('\n')
            damaged.each { repair.append("  ${it}\n") }
            repair.append('\n')
        }
        
        if (!wiped && !critical && !damaged) {
            repair.append(TerminalFormatter.formatText("✅ All coordinates in area are operational!", 'bold', 'green'))
        }
        
        return repair.toString()
    }
    
    private String repairCurrentCoordinate(LambdaPlayer player) {
        def coordinateHealth = coordinateStateService.getCoordinateHealth(player.currentMatrixLevel, player.positionX, player.positionY)
        
        if (coordinateHealth.health >= 100) {
            return TerminalFormatter.formatText("Current coordinate is already at full operational capacity.", 'bold', 'green')
        }
        
        // Create a Linux-based repair challenge
        def challenge = new StringBuilder()
        challenge.append(TerminalFormatter.formatText("🔧 COORDINATE REPAIR INITIATED", 'bold', 'cyan')).append('\n')
        challenge.append("Coordinate (${player.positionX},${player.positionY}) Health: ${coordinateHealth.health}%\n\n")
        
        challenge.append("System corruption detected. Execute repair sequence:\n")
        challenge.append("1. Check filesystem integrity: ${TerminalFormatter.formatText('fsck -y /matrix/level${player.currentMatrixLevel}/coord_${player.positionX}_${player.positionY}', 'bold', 'white')}\n")
        challenge.append("2. Clear temp files: ${TerminalFormatter.formatText('rm -rf /tmp/defrag_damage/*', 'bold', 'white')}\n")
        challenge.append("3. Restart services: ${TerminalFormatter.formatText('systemctl restart matrix-coordinator', 'bold', 'white')}\n")
        challenge.append("4. Verify repair: ${TerminalFormatter.formatText('systemctl status matrix-coordinator', 'bold', 'white')}\n\n")
        
        // Actually perform the repair
        def repairAmount = Math.min(100 - coordinateHealth.health, 25) // Repair 25 points at a time
        coordinateStateService.repairCoordinate(player.currentMatrixLevel, player.positionX, player.positionY, repairAmount)
        
        def newHealth = coordinateStateService.getCoordinateHealth(player.currentMatrixLevel, player.positionX, player.positionY)
        challenge.append(TerminalFormatter.formatText("✅ REPAIR SUCCESSFUL", 'bold', 'green')).append('\n')
        challenge.append("Coordinate health: ${coordinateHealth.health}% → ${TerminalFormatter.formatText("${newHealth.health}%", 'bold', 'green')}\n")
        
        if (newHealth.health < 100) {
            challenge.append("Coordinate still needs additional repair. Repeat command to continue.")
        } else {
            challenge.append("Coordinate fully restored to operational status!")
        }
        
        return challenge.toString()
    }
    
    private String startRepairChallenge(LambdaPlayer player) {
        return TerminalFormatter.formatText("🚧 Advanced repair challenges coming soon! Use 'repair current' for basic repairs.", 'italic', 'yellow')
    }
    
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
    
    private String showRepairableCoordinates(LambdaPlayer player) {
        def repair = new StringBuilder()
        repair.append(TerminalFormatter.formatText("=== REPAIRABLE COORDINATES SCAN ===", 'bold', 'cyan')).append('\n')
        repair.append("Your Position: (${player.positionX},${player.positionY})\n\n")
        
        def repairableCoords = coordinateStateService.getRepairableCoordinatesForPlayer(player)
        
        if (repairableCoords.size() > 0) {
            repair.append(TerminalFormatter.formatText("🔧 ADJACENT WIPED COORDINATES (Repairable):", 'bold', 'red')).append('\n')
            repairableCoords.each { coord ->
                def dateStr = coord.lastDamaged ? new java.text.SimpleDateFormat('HH:mm:ss').format(coord.lastDamaged) : 'Unknown'
                repair.append("  (${coord.x},${coord.y}) - Status: ${coord.status} - Destroyed: ${dateStr}\n")
                repair.append("    Command: ${TerminalFormatter.formatText("repair ${coord.x} ${coord.y}", 'bold', 'white')}\n")
            }
            repair.append('\n')
            repair.append(TerminalFormatter.formatText("⚠️  WARNING:", 'bold', 'yellow'))
                .append(" Wiped coordinates may contain valuable logic fragments or elemental symbols!\n")
            repair.append("Repair them to access their contents and ensure game progression.")
        } else {
            repair.append(TerminalFormatter.formatText("✅ No adjacent coordinates need repair", 'bold', 'green')).append('\n')
            repair.append("All surrounding coordinates are operational.")
        }
        
        return repair.toString()
    }
    
    private String showRepairHelp() {
        def help = new StringBuilder()
        help.append(TerminalFormatter.formatText("=== REPAIR COMMAND HELP ===", 'bold', 'cyan')).append('\n\n')
        
        help.append(TerminalFormatter.formatText("COORDINATE REPAIR SYSTEM:", 'bold', 'white')).append('\n')
        help.append("Auto-defrag bots destroy coordinates every minute. When coordinates\n")
        help.append("are wiped, they may contain valuable logic fragments or elemental\n")
        help.append("symbols needed for game progression.\n\n")
        
        help.append(TerminalFormatter.formatText("COMMANDS:", 'bold', 'white')).append('\n')
        help.append("repair - Show repair options and adjacent wiped coordinates\n")
        help.append("repair scan - Detailed scan of all repairable coordinates\n")
        help.append("repair <x> <y> - Start repair mini-game for specific coordinate\n")
        help.append("repair status - Show area repair status in 5x5 grid\n")
        help.append("repair help - Show this help\n\n")
        
        help.append(TerminalFormatter.formatText("REPAIR MINI-GAME:", 'bold', 'yellow')).append('\n')
        help.append("• High-value coordinates require 4-digit repair codes\n")
        help.append("• Standard coordinates require 3-digit repair codes\n")
        help.append("• Numbers cycle 0-9 with increasing speed for each digit\n")
        help.append("• Press SPACE BAR to lock in each digit when it shows the correct number\n")
        help.append("• Match the target repair code exactly to succeed\n")
        help.append("• Failed attempts require starting over with a new random code\n\n")
        
        help.append(TerminalFormatter.formatText("REPAIR RULES:", 'bold', 'white')).append('\n')
        help.append("• You can only repair coordinates adjacent to your position\n")
        help.append("• You must be on a functional coordinate to perform repairs\n")
        help.append("• Wiped coordinates (0% health) are inaccessible until repaired\n")
        help.append("• Repaired coordinates restore to 100% health\n\n")
        
        help.append(TerminalFormatter.formatText("EXAMPLES:", 'bold', 'white')).append('\n')
        help.append("repair 3 4    # Repair coordinate (3,4) if adjacent to you\n")
        help.append("repair scan   # Show all coordinates you can repair\n\n")
        
        help.append(TerminalFormatter.formatText("⚠️  IMPORTANT:", 'bold', 'yellow'))
            .append(" Auto-defrag bots destroy coordinates every minute!\n")
        help.append("Repair wiped coordinates quickly to prevent progression blocks.")
        
        return help.toString()
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
    
    private String getPlayerPrompt(LambdaPlayer player) {
        // Use player's avatar as prompt symbol instead of generic $
        def avatarSymbol = getAvatarSymbol(player.avatarSilhouette)
        def matrixLevel = player.currentMatrixLevel ?: 1
        def coordinates = "(${player.positionX ?: 0},${player.positionY ?: 0})"
        
        // Color-code the prompt based on matrix level  
        def levelColor = matrixLevel <= 3 ? 'green' : matrixLevel <= 6 ? 'yellow' : 'red'
        
        return TerminalFormatter.formatText("${avatarSymbol}", 'bold', levelColor) + 
               TerminalFormatter.formatText("${matrixLevel}:${coordinates}", 'bold', 'white') + 
               TerminalFormatter.formatText(" > ", 'bold', 'cyan')
    }
    
    private String getAvatarSymbol(String avatarType) {
        switch (avatarType) {
            case 'DIGITAL_GHOST': return '●'
            case 'CIRCUIT_PATTERN': return '◆'
            case 'GEOMETRIC_ENTITY': return '▲'
            case 'FLOWING_CURRENT': return '■'
            case 'BINARY_FORM': return '◐'
            case 'CLASSIC_LAMBDA': return '✦'
            default: return 'Λ'
        }
    }
    
    private String showMatrixMap(LambdaPlayer player) {
        def map = new StringBuilder()
        map.append(TerminalFormatter.formatText("=== MATRIX LEVEL ${player.currentMatrixLevel} MAP ===", 'bold', 'cyan')).append('\n')
        map.append(TerminalFormatter.formatText("Legend: @ = You, D = Defrag Bot, F = Fragment, M = Merchant, X = Wiped, ! = Critical, . = OK", 'italic', 'white')).append('\n\n')
        
        // Get health data for entire matrix level - wrap in transaction
        def healthMap = [:]
        LambdaPlayer.withTransaction {
            healthMap = coordinateStateService.getMatrixLevelHealth(player.currentMatrixLevel)
        }
        
        // Build 10x10 map (Y=9 at top, Y=0 at bottom to match normal coordinates)
        for (int y = 9; y >= 0; y--) {
            map.append(TerminalFormatter.formatText("${y} ", 'bold', 'white'))
            for (int x = 0; x <= 9; x++) {
                def symbol = "."
                def color = "green"
                
                // Check if this is the player's position
                if (x == player.positionX && y == player.positionY) {
                    symbol = "@"
                    color = "cyan"
                } else {
                    // Check coordinate health
                    def health = healthMap["${x},${y}"]
                    if (health) {
                        if (health.health <= 0) {
                            symbol = "X"
                            color = "red"
                        } else if (health.health <= 25) {
                            symbol = "!"
                            color = "red"
                        } else if (health.health <= 50) {
                            symbol = "!"
                            color = "yellow"
                        }
                    }
                    
                    // Check for defrag bot (overrides health display)
                    def bot = null
                    LambdaPlayer.withTransaction {
                        bot = DefragBot.findByMatrixLevelAndPositionXAndPositionYAndIsActive(
                            player.currentMatrixLevel, x, y, true
                        )
                    }
                    if (bot) {
                        symbol = "D"
                        color = "red"
                    }
                    
                    // Check for fragment (lower priority than defrag bot)
                    else if (symbol == "." || symbol == "!") {
                        def fragment = findFragmentAtCoordinates(player.currentMatrixLevel, x, y)
                        if (fragment) {
                            symbol = "F"
                            color = "green"
                        }
                    }
                    
                    // Check for merchant (lowest priority)
                    if (symbol == "." || symbol == "!") {
                        try {
                            def merchant = null
                            LambdaPlayer.withTransaction {
                                merchant = lambdaMerchantService.getMerchantAt(player.currentMatrixLevel, x, y)
                            }
                            if (merchant) {
                                symbol = "M"
                                color = "yellow"
                            }
                        } catch (Exception e) {
                            // Ignore merchant service errors
                        }
                    }
                }
                
                map.append(TerminalFormatter.formatText(symbol, color) + " ")
            }
            map.append('\n')
        }
        
        // Add X-axis labels
        map.append("  ")
        for (int x = 0; x <= 9; x++) {
            map.append(TerminalFormatter.formatText("${x} ", 'bold', 'white'))
        }
        map.append('\n\n')
        
        // Add coordinate health summary
        def totalCoords = 100
        def wipedCoords = healthMap.values().count { it.health <= 0 }
        def criticalCoords = healthMap.values().count { it.health > 0 && it.health <= 25 }
        def damagedCoords = healthMap.values().count { it.health > 25 && it.health < 100 }
        def healthyCoords = totalCoords - wipedCoords - criticalCoords - damagedCoords
        
        map.append(TerminalFormatter.formatText("Matrix Level Health Summary:", 'bold', 'white')).append('\n')
        map.append("Operational: ${TerminalFormatter.formatText("${healthyCoords}", 'default', 'green')} ")
        map.append("Damaged: ${TerminalFormatter.formatText("${damagedCoords}", 'default', 'yellow')} ")
        map.append("Critical: ${TerminalFormatter.formatText("${criticalCoords}", 'default', 'red')} ")
        map.append("Wiped: ${TerminalFormatter.formatText("${wipedCoords}", 'default', 'red')}\n")
        
        return map.toString()
    }
    
    private String clearTerminal() {
        // ANSI escape sequence to clear screen and move cursor to top-left
        return "\\033[2J\\033[H"
    }
    
    private String listFiles(LambdaPlayer player) {
        def files = new StringBuilder()
        files.append(TerminalFormatter.formatText("=== LAMBDA ENTITY FILE SYSTEM ===", 'bold', 'cyan')).append('\n')
        files.append("Working Directory: /lambda/entity/${player.username}\n\n")
        
        // Get puzzle rooms at current location
        def puzzleElements = puzzleService.getPlayerPuzzleElementsAtLocation(player, player.positionX, player.positionY)
        def puzzleRooms = puzzleElements.findAll { it.type == 'player_puzzle_room' }
        
        if (puzzleRooms.size() > 0) {
            files.append(TerminalFormatter.formatText("PUZZLE ROOM FILES:", 'bold', 'yellow')).append('\n')
            puzzleRooms.each { roomElement ->
                def puzzleRoom = roomElement.data
                def permissionColor = puzzleRoom.isExecutable ? 'green' : 'red'
                files.append(TerminalFormatter.formatText(puzzleRoom.getFileListEntry(), permissionColor)).append('\n')
            }
            files.append('\n')
        }
        
        // Standard system files (always present)
        def dateFormatter = new java.text.SimpleDateFormat('MMM dd HH:mm')
        def currentDate = dateFormatter.format(new Date())
        
        files.append(TerminalFormatter.formatText("SYSTEM FILES:", 'bold', 'cyan')).append('\n')
        files.append("-rw-r--r--  1 lambda lambda     1024 ${currentDate} fragment_file\n")
        files.append("-rw-r--r--  1 lambda lambda      512 ${currentDate} status_log\n")
        files.append("-rw-r--r--  1 lambda lambda      256 ${currentDate} inventory_data\n")
        files.append("-rw-r--r--  1 lambda lambda      128 ${currentDate} entropy_monitor\n")
        files.append("-rw-r--r--  1 lambda lambda      256 ${currentDate} system_map\n")
        
        // Configuration files based on player progress
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                if (managedPlayer.logicFragments?.size() > 0) {
                    files.append("-rw-r--r--  1 lambda lambda      512 ${currentDate} python_env\n")
                }
                
                if (managedPlayer.specialItems?.size() > 0) {
                    files.append("-rw-r--r--  1 lambda lambda      256 ${currentDate} item_registry\n")
                }
            }
        }
        
        if (player.currentMatrixLevel > 1) {
            files.append("-rw-r--r--  1 lambda lambda      384 ${currentDate} exploration_log\n")
        }
        
        // Ethnicity-specific files
        if (player.avatarSilhouette) {
            files.append("-rw-r--r--  1 lambda lambda      128 ${currentDate} ethnicity_config\n")
        }
        
        files.append('\n')
        files.append(TerminalFormatter.formatText("USAGE:", 'bold', 'white')).append('\n')
        files.append("cat <filename>     - View file contents\n")
        files.append("chmod +x <file>    - Make puzzle room file executable\n")
        files.append("execute --<flag> <nonce> <file> - Run executable puzzle room files\n")
        
        if (puzzleRooms.size() > 0) {
            def hasNonExecutable = puzzleRooms.any { !it.data.isExecutable }
            if (hasNonExecutable) {
                files.append('\n')
                files.append(TerminalFormatter.formatText("💡 TIP:", 'bold', 'yellow'))
                    .append(" Puzzle room files must be made executable with 'chmod +x <filename>' before they can be executed!\n")
            }
        }
        
        return files.toString()
    }
    
    // Helper method for map generation - use game session service
    private def findFragmentAtCoordinates(Integer matrixLevel, Integer x, Integer y) {
        return gameSessionService.getFragmentAtCoordinates(matrixLevel, x, y)
    }
    
    private String viewEntropyMonitor(LambdaPlayer player) {
        def monitor = new StringBuilder()
        def entropyStatus = entropyService.getEntropyStatus(player)
        
        monitor.append(TerminalFormatter.formatText("=== ENTROPY MONITOR v3.7.2 ===", 'bold', 'cyan')).append('\n')
        monitor.append("Entity: ${player.displayName}\n")
        monitor.append("Coherence Level: ${TerminalFormatter.formatText("${entropyStatus.currentEntropy ?: 100.0}%", getEntropyColor(entropyStatus.currentEntropy ?: 100.0), 'bold')}\n")
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
    
    private String handleRecurseCommand(String ability, LambdaPlayer player, PrintWriter writer) {
        // Check if player has recursion charges available
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (!managedPlayer) {
                return "Player not found"
            }
            
            // Check recursion cooldown and charges (TODO: implement tracking fields)
            // For now, return a placeholder implementation
            def ethnicity = managedPlayer.avatarSilhouette
            
            switch (ability.toLowerCase()) {
                case 'movement':
                    if (ethnicity == 'GEOMETRIC_ENTITY') {
                        return handleRecursiveMovement(managedPlayer, writer)
                    }
                    return "Movement recursion not available for your ethnicity"
                    
                case 'fusion':
                    if (ethnicity == 'CLASSIC_LAMBDA') {
                        return "Fusion recursion activated - next fragment fusion has +15% success rate"
                    }
                    return "Fusion recursion not available for your ethnicity"
                    
                case 'defend':
                    if (ethnicity == 'CIRCUIT_PATTERN') {
                        return "Defense recursion activated - next defrag encounter has +15% resistance"
                    }
                    return "Defense recursion not available for your ethnicity"
                    
                case 'mine':
                    if (ethnicity == 'FLOWING_CURRENT') {
                        return "Mining recursion activated - next mining cycle has +25% efficiency"
                    }
                    return "Mining recursion not available for your ethnicity"
                    
                case 'stealth':
                    if (ethnicity == 'DIGITAL_GHOST') {
                        return "Stealth recursion activated - enhanced defrag avoidance for 10 minutes"
                    }
                    return "Stealth recursion not available for your ethnicity"
                    
                case 'process':
                    if (ethnicity == 'BINARY_FORM') {
                        return "Processing recursion activated - cooldowns reduced for 5 minutes"
                    }
                    return "Processing recursion not available for your ethnicity"
                    
                default:
                    return "Unknown recursion ability. Available: movement, fusion, defend, mine, stealth, process"
            }
        }
    }
    
    private String handleRecursiveMovement(LambdaPlayer player, PrintWriter writer) {
        // Enhanced movement allows 2-3 coordinate jump
        writer.println("Enhanced movement mode activated!")
        writer.print("Enter target coordinates for recursive movement (x,y): ")
        writer.flush()
        
        // TODO: Implement proper input handling for recursive movement
        return "Recursive movement ready - next cc command will jump 2-3 coordinates"
    }
    
    private void respawnFragmentAtRandomLocation(def fragment, Integer matrixLevel) {
        FragmentPickup.withTransaction {
            // Generate random coordinates for respawn (avoiding picked coordinates)
            def maxAttempts = 20
            def attempts = 0
            def newX, newY
            
            while (attempts < maxAttempts) {
                newX = (0..9).shuffled().first()
                newY = (0..9).shuffled().first()
                
                // Check if any player has picked up a fragment at these coordinates
                def existingPickup = FragmentPickup.findByMatrixLevelAndPositionXAndPositionY(
                    matrixLevel, newX, newY
                )
                
                if (!existingPickup) {
                    // Found clear coordinates, update fragment hash for this location
                    println "Fragment ${fragment.name} respawned at (${newX},${newY}) on level ${matrixLevel}"
                    break
                }
                attempts++
            }
            
            // If we couldn't find clear coordinates after 20 attempts, just use random ones
            // This prevents infinite loops if the level becomes too picked-over
            if (attempts >= maxAttempts) {
                newX = (0..9).shuffled().first()
                newY = (0..9).shuffled().first()
                println "Fragment ${fragment.name} force-respawned at (${newX},${newY}) on level ${matrixLevel} after ${maxAttempts} attempts"
            }
        }
    }
    
    // ============ PUZZLE SYSTEM COMMAND HANDLERS ============
    
    private String handleCollectVariableCommand(String variableName, LambdaPlayer player) {
        def result = puzzleService.collectVariable(player, variableName, player.currentMatrixLevel, player.positionX, player.positionY)
        if (result.success) {
            audioService.playSound("item_found")
            return "${TerminalFormatter.formatText('✅ VARIABLE COLLECTED!', 'bold', 'green')}\n${result.message}\n${result.variable?.getUsageHint() ?: ''}"
        } else {
            return "${TerminalFormatter.formatText('❌ Collection Failed', 'bold', 'red')}\n${result.message}"
        }
    }
    
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
    
    private String handleExecuteCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(' ')
        
        // Check if this is a puzzle room execution: execute --flag nonce filename
        if (parts.length == 4 && parts[1].startsWith('--')) {
            def flag = parts[1]
            def nonce = parts[2]
            def filename = parts[3]
            
            // Check if file exists and is executable at current location
            def puzzleElements = puzzleService.getPlayerPuzzleElementsAtLocation(player, player.positionX, player.positionY)
            def puzzleRooms = puzzleElements.findAll { it.type == 'player_puzzle_room' }
            def targetRoom = puzzleRooms.find { it.data.fileName == filename }
            
            if (!targetRoom) {
                audioService.playSound("error_sound")
                return "${TerminalFormatter.formatText('❌ File Not Found', 'bold', 'red')}\nNo file named '${filename}' at current location.\nUse 'ls' to see available files."
            }
            
            if (!targetRoom.data.isExecutable) {
                audioService.playSound("error_sound")
                return """${TerminalFormatter.formatText('❌ Permission Denied', 'bold', 'red')}
File '${filename}' is not executable.

${TerminalFormatter.formatText('💡 SOLUTION:', 'bold', 'yellow')} Make the file executable first:
chmod +x ${filename}

Then retry your execute command."""
            }
            
            def result = puzzleService.executePuzzleRoom(player, flag, nonce, filename)
            if (result.success) {
                audioService.playSound("victory_chord")
                def response = new StringBuilder()
                response.append("${TerminalFormatter.formatText('🏆 PUZZLE SOLVED!', 'bold', 'green')}\n")
                response.append("${result.message}\n")
                
                if (result.triggerShift) {
                    response.append("\n${TerminalFormatter.formatText('⚡ COORDINATE SHIFT TRIGGERED!', 'bold', 'yellow')}\n")
                    response.append("${TerminalFormatter.formatText('Other players\' coordinates have been randomized!', 'bold', 'cyan')}\n")
                    response.append("${TerminalFormatter.formatText('Competition intensifies! 🔥', 'italic', 'red')}\n")
                }
                
                return response.toString()
            } else {
                audioService.playSound("error_sound")
                return "${TerminalFormatter.formatText('❌ Execution Failed', 'bold', 'red')}\n${result.message}"
            }
        }
        
        // Check if this is a logic fragment execution: execute <fragment_name> [variable_value]
        if (parts.length >= 2) {
            def fragmentName = parts[1]
            def inputVariable = parts.length > 2 ? parts[2] : null
            
            def result = puzzleService.executePuzzleFragment(player, fragmentName, inputVariable)
            if (result.success) {
                audioService.playSound("fragment_pickup")
                def output = new StringBuilder()
                output.append("${TerminalFormatter.formatText('🧩 FRAGMENT EXECUTED!', 'bold', 'cyan')}\n")
                output.append("${result.message}\n")
                output.append("${TerminalFormatter.formatText('OUTPUT:', 'bold', 'white')} ${result.output}\n")
                
                // Check if output contains coordinates
                if (result.output?.contains(':')) {
                    output.append("${TerminalFormatter.formatText('💡 TIP:', 'bold', 'yellow')} Output may contain coordinate information!\n")
                }
                
                return output.toString()
            } else {
                return "${TerminalFormatter.formatText('❌ Execution Failed', 'bold', 'red')}\n${result.message}"
            }
        }
        
        return """${TerminalFormatter.formatText('EXECUTE COMMAND USAGE:', 'bold', 'cyan')}

${TerminalFormatter.formatText('Puzzle Room Execution:', 'bold', 'white')}
  execute --<flag> <nonce> <filename>
  Example: execute --electrical 7e4a92f1b3d5c8e9 electrical_unlock.py

${TerminalFormatter.formatText('Logic Fragment Execution:', 'bold', 'white')}
  execute <fragment_name> [variable_value]
  Example: execute "Electrical Analyzer" 12.0,5.0,3.3

${TerminalFormatter.formatText('💡 TIP:', 'bold', 'yellow')} Use 'pinv' to see your puzzle inventory"""
    }
    
    private String showPuzzleInventory(LambdaPlayer player) {
        def inventory = puzzleService.getPlayerPuzzleInventory(player)
        def output = new StringBuilder()
        
        output.append(TerminalFormatter.formatText("=== 🧩 PUZZLE INVENTORY ===", 'bold', 'cyan')).append('\n\n')
        
        // Puzzle Logic Fragments
        output.append(TerminalFormatter.formatText("PUZZLE LOGIC FRAGMENTS:", 'bold', 'purple')).append('\n')
        if (inventory.puzzleFragments.size() > 0) {
            inventory.puzzleFragments.each { fragment ->
                output.append("  🧩 ${fragment.name} (${fragment.type} Level ${fragment.powerLevel})\n")
                output.append("     ${fragment.hint}\n")
            }
        } else {
            output.append("  No puzzle fragments acquired\n")
        }
        
        output.append('\n')
        
        // Hidden Variables
        output.append(TerminalFormatter.formatText("COLLECTED VARIABLES:", 'bold', 'green')).append('\n')
        if (inventory.variables.size() > 0) {
            inventory.variables.each { variable ->
                output.append("  📦 ${variable.name} = ${variable.value} (${variable.type})\n")
                output.append("     ${variable.hint}\n")
            }
        } else {
            output.append("  No variables collected\n")
        }
        
        output.append('\n')
        
        // Elemental Nonces
        output.append(TerminalFormatter.formatText("DISCOVERED NONCES:", 'bold', 'yellow')).append('\n')
        if (inventory.nonces.size() > 0) {
            inventory.nonces.each { nonce ->
                output.append("  🔑 ${nonce.name} (${nonce.element})\n")
                output.append("     ${nonce.clue}\n")
                output.append("     Flag: ${nonce.flag}\n")
            }
        } else {
            output.append("  No nonces discovered\n")
        }
        
        // Recent Executions
        if (inventory.recentExecutions.size() > 0) {
            output.append('\n')
            output.append(TerminalFormatter.formatText("RECENT EXECUTIONS:", 'bold', 'white')).append('\n')
            inventory.recentExecutions.each { execution ->
                output.append("  ⚡ ${execution.summary}\n")
            }
        }
        
        output.append('\n')
        output.append(TerminalFormatter.formatText("💡 COMMANDS:", 'bold', 'cyan')).append('\n')
        output.append("  execute <fragment> [variable] - Execute puzzle logic\n")
        output.append("  execute --<flag> <nonce> <file> - Unlock elemental symbols\n")
        output.append("  collect_var <name> - Collect hidden variables\n")
        output.append("  pprog - Show competitive puzzle progress\n")
        output.append("  pmarket - Show tradeable puzzle knowledge\n")
        output.append("  scan - Detect puzzle elements at your location\n")
        
        return output.toString()
    }
    
    private String showCompetitivePuzzleProgress(LambdaPlayer player) {
        def session = puzzleRandomizationService.getCurrentGameSession()
        if (!session) {
            return "No active game session found."
        }
        
        return competitivePuzzleService.formatPlayerProgressDisplay(
            player, session.sessionId, player.currentMatrixLevel
        )
    }
    
    private String showPuzzleKnowledgeMarket(LambdaPlayer player) {
        return puzzleKnowledgeTradingService.formatTradeablePuzzleKnowledge(player)
    }
}
