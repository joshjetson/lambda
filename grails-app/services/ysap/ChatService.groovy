package ysap

import grails.gorm.transactions.Transactional
import ysap.helpers.PlayerHelp

@Transactional
class ChatService {
    def telnetServerService
    def lambdaPlayerService
    def puzzleKnowledgeTradingService

    def sendMessage(LambdaPlayer player, String messageText, String messageType = 'CHAT') {
        // Check if player has print capability (always true for basic print)
        if (!hasEchoCapability(player)) {
            return [success: false, error: "No echo capability - requires print logic fragment"]
        }
        
        def message = new ChatMessage(
            senderName: player.displayName,
            message: messageText,
            messageType: messageType,
            matrixLevel: 0  // Global mingle for now
        )
        
        message.save(failOnError: true)
        return [success: true, message: message]
    }
    
    def getRecentMessages(Integer limit = 20, Integer matrixLevel = 0) {
        return ChatMessage.withTransaction {
            ChatMessage.createCriteria().list(max: limit) {
                eq 'matrixLevel', matrixLevel
                order 'timestamp', 'desc'
            }.reverse() // Show oldest first in chat display
        }
    }
    
    def sendTradeOffer(LambdaPlayer seller, String itemDescription, Integer price) {
        def tradeMessage = "🔄 TRADE: ${itemDescription} for ${price} bits. Whisper '${seller.username}' to negotiate."
        
        def message = new ChatMessage(
            senderName: seller.displayName,
            message: tradeMessage,
            messageType: 'TRADE',
            matrixLevel: 0
        )
        
        message.save(failOnError: true)
        return message
    }
    
    def sendSystemMessage(String messageText, Integer matrixLevel = 0) {
        def message = new ChatMessage(
            senderName: "SYSTEM",
            message: messageText,
            messageType: 'SYSTEM',
            matrixLevel: matrixLevel
        )
        
        message.save(failOnError: true)
        return message
    }
    
    def processEchoCommand(String fullCommand) {
        // Extract message from "echo <message>" command
        if (fullCommand.toLowerCase().startsWith('echo ')) {
            return fullCommand.substring(5).trim()
        }
        return null
    }
    
    def getMingleUsers() {
        return LambdaPlayer.withTransaction {
            LambdaPlayer.findAllByIsInMingleAndIsOnline(true, true)
        }
    }
    
    def formatChatDisplay(List<ChatMessage> messages) {
        def display = new StringBuilder()
        display.append(TerminalFormatter.formatText("=== HEAP SPACE ===", 'bold', 'cyan')).append('\n')
        display.append(TerminalFormatter.formatText("Digital entities exchange bits, barter items, and share knowledge", 'italic', 'yellow')).append('\n')
        display.append(TerminalFormatter.formatText("@*STAY TOO LONG AND GET CACHED*@", 'italic', 'yellow')).append('\n')
        display.append(TerminalFormatter.formatText("Type 'echo <message>' to communicate | 'exit' to leave heap", 'italic', 'green')).append('\n')
        display.append("─" * 80).append('\n')
        
        messages.each { msg ->
            def formattedMsg = formatMessageByType(msg)
            display.append(formattedMsg).append('\n')
        }
        
        display.append("─" * 80).append('\n')
        display.append("🔊 Echo: ")
        
        return display.toString()
    }
    
    private String formatMessageByType(ChatMessage msg) {
        // Handle both java.util.Date and java.sql.Timestamp
        def timeStr
        if (msg.timestamp instanceof java.sql.Timestamp) {
            timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date(msg.timestamp.getTime()))
        } else {
            timeStr = new java.text.SimpleDateFormat('HH:mm').format(msg.timestamp)
        }
        
        switch (msg.messageType) {
            case 'TRADE':
                return TerminalFormatter.formatText("[${timeStr}] [TRADE] ${msg.senderName}: ${msg.message}", 'bold', 'magenta')
            case 'SYSTEM':
                return TerminalFormatter.formatText("[${timeStr}] [SYSTEM] ${msg.message}", 'bold', 'red')
            case 'WHISPER':
                return TerminalFormatter.formatText("[${timeStr}] [WHISPER] ${msg.senderName}: ${msg.message}", 'italic', 'cyan')
            default:
                return "[${timeStr}] ${TerminalFormatter.formatText(msg.senderName, 'bold', 'white')}: ${msg.message}"
        }
    }
    
    private Boolean hasEchoCapability(LambdaPlayer player) {
        // Player always has basic print/echo capability as a fundamental requirement
        return LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            return managedPlayer?.logicFragments?.any { it.fragmentType == 'FUNCTION' && it.pythonCapability?.contains('print') } ?: true
        }
    }
    
    def cleanupOldMessages() {
        def cutoffDate = new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000)) // 24 hours
        def deletedCount = ChatMessage.executeUpdate("delete ChatMessage c where c.timestamp < :cutoff", [cutoff: cutoffDate])
        return deletedCount
    }

    String enterChat(LambdaPlayer player, PrintWriter writer) {
        lambdaPlayerService.setMingleStatus(player, true)

        // Send system message and broadcast to others
        def systemMsg = "${player.displayName} enters the heap \r\n"
        this.sendSystemMessage(systemMsg)

        def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
        def broadcastMsg = "[${timeStr}] ${TerminalFormatter.formatText('[SYSTEM]', 'bold', 'red')} ${systemMsg} \r\n"
        this.broadcastToChatUsers(broadcastMsg)

        // Show recent messages to the entering player
        def recentMessages = this.getRecentMessages(15)
        def display = new StringBuilder()
        display.append(TerminalFormatter.formatText("=== HEAP SPACE ===", 'bold', 'cyan')).append('\r\n')
        display.append(TerminalFormatter.formatText("IRC-style chat • Type 'echo <message>' to talk • 'exit' to leave", 'italic', 'yellow')).append('\r\n')
        display.append("─" * 80).append('\r\n')

        recentMessages.each { msg ->
            display.append(this.formatMessageByType(msg)).append('\r\n')
        }

        display.append("─" * 80).append('\r\n')
        display.append(TerminalFormatter.formatText("You are now in heap space. Use 'echo <message>' to chat.", 'bold', 'green'))
        display.append("\r\n")

        return display.toString()
    }
    void broadcastToChatUsers(String message) {
        telnetServerService.playerSessions.each { writer, player ->
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

    String handleChatCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def trimmedCommand = command.trim()
        def output = new StringBuilder()

        if (trimmedCommand.equalsIgnoreCase('exit')) {
            lambdaPlayerService.setMingleStatus(player, false)

            // Broadcast exit message to remaining users
            output.append("${player.displayName} popped from heap\r\n")
            this.sendSystemMessage(output.toString())

            def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
            output.append("[${timeStr}] ${TerminalFormatter.formatText('[SYSTEM]', 'bold', 'red')}")
            this.broadcastToChatUsers(output.toString())
            output.append("\r\n")
            output.append(TerminalFormatter.formatText("Null pointer new memory address. Returned to working ram", 'bold', 'green'))
            output.append("\r\n")

            return  output.toString()
        }

        if (trimmedCommand.toLowerCase().startsWith('echo ')) {
            def message = this.processEchoCommand(trimmedCommand)
            if (message) {
                def result = this.sendMessage(player, message)
                if (result.success) {
                    // Show immediate feedback with the new message in IRC style
                    def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
                    output.append("[${timeStr}] ${TerminalFormatter.formatText(player.displayName, 'bold', 'white')}: ${message}")
                    output.append("\r\n")

                    // Broadcast to all mingle users immediately
                    this.broadcastToChatUsers(output.toString())

                    return
                } else {
                    output.append(TerminalFormatter.formatText("Error: ${result.error}", 'bold', 'red'))
                    return output.toString()
                }
            } else {
                output.append(TerminalFormatter.formatText("Invalid echo format. Use: echo <message>", 'bold', 'red'))
                return output.toString()
            }
        }
        if (trimmedCommand.toLowerCase().startsWith('pay')) {
            if (trimmedCommand.toLowerCase() == 'pay') {
                return TerminalFormatter.formatText("Usage: pay <entity_name> <bits>", 'bold', 'yellow')
            }
            return this.handlePayCommand(trimmedCommand, player, writer)
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
            return this.listChatUsers(player)
        }

        if (trimmedCommand.equalsIgnoreCase('help')) {
            return PlayerHelp.chat('help')
        }

        // For pressing enter or unknown commands, just give simple feedback
        if (trimmedCommand.isEmpty()) {
            return TerminalFormatter.formatText("Heap commands: echo <msg> | pay <entity> <bits> | pm <entity> <msg> | trade <entity> | list | help | exit\r\n", 'italic', 'cyan')
        } else {
            return TerminalFormatter.formatText("Unknown command '${trimmedCommand}'. Type 'help' for heap commands.\r\n", 'italic', 'yellow')
        }
    }
    private String listChatUsers(LambdaPlayer player) {
        def userList = new StringBuilder()
        userList.append(TerminalFormatter.formatText("=== HEAP LIST ===", 'bold', 'cyan')).append('\r\n')

        def mingleUsers = []
        LambdaPlayer.withTransaction {
            mingleUsers = LambdaPlayer.findAllByIsInMingle(true)
        }

        if (mingleUsers) {
            mingleUsers.each { user ->
                def marker = (user.id == player.id) ? " (you)" : ""
                userList.append("• ${user.displayName}${marker} [Level ${user.currentMatrixLevel}]\r\n")
            }
        } else {
            userList.append("Alone in heap..\r\n")
        }

        return userList.toString()
    }

    private LambdaPlayer findChatUser(String name) {
        def foundPlayer = null
        LambdaPlayer.withTransaction {
            foundPlayer = LambdaPlayer.findByDisplayNameAndIsInMingle(name, true)
        }
        return foundPlayer
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
        def targetPlayer = findChatUser(targetName)
        if (!targetPlayer) {
            return TerminalFormatter.formatText("Entity '${targetName}' not found in heap", 'bold', 'red')
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
        this.broadcastToChatUsers("[${timeStr}] ${systemMsg}")

        return TerminalFormatter.formatText("Sent ${bitsAmount} bits to ${targetName}", 'bold', 'green')
    }

    private String handlePrivateMessageCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command.trim().split(' ', 3)
        if (parts.length < 3) {
            return TerminalFormatter.formatText("Usage: pm <entity_name> <message>", 'bold', 'red')
        }

        def targetName = parts[1]
        def message = parts[2]

        def targetPlayer = findChatUser(targetName)
        if (!targetPlayer) {
            return TerminalFormatter.formatText("Entity '${targetName}' not found in heap", 'bold', 'red')
        }

        if (targetPlayer.id == player.id) {
            return TerminalFormatter.formatText("Cannot PM yourself", 'bold', 'red')
        }

        // Send private message to target
        def targetWriter = findWriterForPlayer(targetPlayer)
        if (targetWriter) {
            def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
            def pmMessage = "[${timeStr}] ${TerminalFormatter.formatText('[PM]', 'bold', 'magenta')} ${TerminalFormatter.formatText(player.displayName, 'bold', 'white')}: ${message}\r\n"
            targetWriter.println(pmMessage)
            targetWriter.flush()
        }

        return TerminalFormatter.formatText("Private message sent to ${targetName}\r\n", 'italic', 'green')
    }

    private String handleTradeCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command.trim().split(' ')
        if (parts.length < 2) {
            return TerminalFormatter.formatText("Usage: trade <entity_name>", 'bold', 'red')
        }

        def targetName = parts[1]
        def targetPlayer = findChatUser(targetName)
        if (!targetPlayer) {
            return TerminalFormatter.formatText("Entity '${targetName}' not found in heap", 'bold', 'red')
        }

        if (targetPlayer.id == player.id) {
            return TerminalFormatter.formatText("Cannot trade with yourself", 'bold', 'red')
        }

        // Enhanced trade menu with puzzle knowledge
        def tradeMenu = new StringBuilder()
        tradeMenu.append(TerminalFormatter.formatText("=== ENHANCED TRADE INTERFACE ===", 'bold', 'cyan')).append('\r\n')
        tradeMenu.append("Target Entity: ${TerminalFormatter.formatText(targetName, 'bold', 'yellow')}\r\n\r\n")

        // STANDARD LOGIC FRAGMENTS
        tradeMenu.append(TerminalFormatter.formatText("📚 STANDARD LOGIC FRAGMENTS:", 'bold', 'green')).append('\r\n')
        def playerFragments = []
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer?.logicFragments) {
                playerFragments = managedPlayer.logicFragments.collect { it }
            }
        }

        if (playerFragments) {
            playerFragments.eachWithIndex { fragment, index ->
                tradeMenu.append("F${index + 1}. ${fragment.name} x${fragment.quantity} (Power: ${fragment.powerLevel}/10) - ~${20 + fragment.powerLevel * 5} bits\r\n")
            }
        } else {
            tradeMenu.append("No standard fragments to trade\r\n")
        }

        // PUZZLE KNOWLEDGE
        def tradeableKnowledge = puzzleKnowledgeTradingService.getTradeablePuzzleKnowledge(player)

        tradeMenu.append("\r\n${TerminalFormatter.formatText('🧩 PUZZLE KNOWLEDGE:', 'bold', 'purple')}\r\n")

        // Puzzle Fragments
        if (tradeableKnowledge.puzzleFragments.size() > 0) {
            tradeMenu.append("Executable Puzzle Fragments:\r\n")
            tradeableKnowledge.puzzleFragments.eachWithIndex { item, index ->
                tradeMenu.append("PF${index + 1}. ${item.name} (${item.elementHint}) - ${item.tradeValue} bits\r\n")
            }
        }

        // Variables
        if (tradeableKnowledge.variables.size() > 0) {
            tradeMenu.append("Collected Variables:\r\n")
            tradeableKnowledge.variables.eachWithIndex { item, index ->
                tradeMenu.append("V${index + 1}. ${item.name} (${item.elementHint}) - ${item.tradeValue} bits\r\n")
            }
        }

        // Nonces (only tradeable ones)
        def tradeableNonces = tradeableKnowledge.nonces.findAll { it.canTrade }
        if (tradeableNonces.size() > 0) {
            tradeMenu.append("Elemental Nonces:\r\n")
            tradeableNonces.eachWithIndex { item, index ->
                tradeMenu.append("N${index + 1}. ${item.name} (${item.elementType}) - ${item.tradeValue} bits\r\n")
                tradeMenu.append("     Digital Spec: ${item.chemicalClue} | Flag: ${item.commandFlag}\r\n")
            }
        }

        // Complete Solutions
        if (tradeableKnowledge.completedSolutions.size() > 0) {
            tradeMenu.append("Complete Solutions:\r\n")
            tradeableKnowledge.completedSolutions.eachWithIndex { item, index ->
                tradeMenu.append("S${index + 1}. ${item.description} - ${item.tradeValue} bits\r\n")
                tradeMenu.append("     ${item.includes}\r\n")
            }
        }

        if (tradeableKnowledge.puzzleFragments.size() == 0 &&
                tradeableKnowledge.variables.size() == 0 &&
                tradeableNonces.size() == 0 &&
                tradeableKnowledge.completedSolutions.size() == 0) {
            tradeMenu.append("No puzzle knowledge available for trade\r\n")
        }

        tradeMenu.append("\r\n${TerminalFormatter.formatText('💰 TRADING COMMANDS:', 'bold', 'yellow')}\r\n")
        tradeMenu.append("Standard Fragments: offer F<num> <quantity> <price>\r\n")
        tradeMenu.append("Puzzle Fragments: offer PF<num> <price>\r\n")
        tradeMenu.append("Variables: offer V<num> <price>\r\n")
        tradeMenu.append("Nonces: offer N<num> <price>\r\n")
        tradeMenu.append("Complete Solutions: offer S<num> <price>\r\n")
        tradeMenu.append("cancel - Cancel trade\r\n")

        return tradeMenu.toString()
    }

    private PrintWriter findWriterForPlayer(LambdaPlayer target) {
        return telnetServerService.playerSessions.find { writer, player ->
            player.id == target.id
        }?.key
    }
}