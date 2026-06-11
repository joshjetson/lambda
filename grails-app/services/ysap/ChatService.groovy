package ysap

import grails.gorm.transactions.Transactional
import ysap.helpers.PlayerHelp

import java.util.concurrent.ConcurrentHashMap

@Transactional
class ChatService {
    def telnetServerService
    def lambdaPlayerService
    def puzzleKnowledgeTradingService
    def hudService

    // Trade state (in-memory, mirrors SimpleRepairService.activeSessions):
    //  tradeTargets: sellerUsername -> the entity they ran `trade <entity>` against
    //  pendingOffers: buyerUsername -> a pending offer they can `accept`/`cancel` (consent step)
    private static final Map<String, Long> tradeTargets = new ConcurrentHashMap<>()
    private static final Map<String, Map> pendingOffers = new ConcurrentHashMap<>()

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
                    if (!telnetServerService.hudModeSessions.contains(writer)) {
                        // Normal mode users: ensure broadcast doesn't interfere with current prompt
                        // Move cursor to beginning of line, clear line, show message, then restore prompt
                        writer.print("\r\033[K" + message)  // \r moves to start, \033[K clears line
                        // Re-display the current prompt after the message
                        def prompt = telnetServerService.getPlayerPrompt(currentPlayer, writer)
                        writer.print(prompt)
                        writer.flush()
                    } else {
                        // HUD mode users: trigger immediate refresh of their heap chat display
                        def outputStream = telnetServerService.getOutputStreamForWriter(writer)
                        if (outputStream && hudService) {
                            hudService.refreshHudScreenForHeapChat(outputStream, currentPlayer)
                        }
                    }
                }
            }
        }
    }

    String handleChatCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def trimmedCommand = command.trim()

        // O(1) heap-command dispatch keyed by the leading token (mirrors TelnetServerService.commandHandlers).
        // Each closure preserves the original branch's bare-word/usage behavior internally.
        Map<String, Closure> heapHandlers = [
            'exit':      { c, p, w -> handleExitHeap(c, p, w) },
            'echo':      { c, p, w -> handleEchoHeap(c, p, w) },
            'i':         { c, p, w -> lambdaPlayerService.showInventory(p) },
            'inventory': { c, p, w -> lambdaPlayerService.showInventory(p) },
            'pay':       { c, p, w -> c.equalsIgnoreCase('pay') ? heapUsage('pay <entity_name> <bits>') : handlePayCommand(c, p, w) },
            'pm':        { c, p, w -> c.equalsIgnoreCase('pm') ? heapUsage('pm <entity_name> <message>') : handlePrivateMessageCommand(c, p, w) },
            'trade':     { c, p, w -> c.equalsIgnoreCase('trade') ? heapUsage('trade <entity_name>') : handleTradeCommand(c, p, w) },
            'offer':     { c, p, w -> handleOfferCommand(c, p, w) },
            'accept':    { c, p, w -> handleAcceptCommand(c, p, w) },
            'cancel':    { c, p, w -> handleCancelCommand(c, p, w) },
            'list':      { c, p, w -> listChatUsers(p) },
            'who':       { c, p, w -> listChatUsers(p) },
            'help':      { c, p, w -> PlayerHelp.chat('help') },
            'h':         { c, p, w -> PlayerHelp.chat('help') }
        ]

        def token = trimmedCommand.toLowerCase().split(/\s+/)[0]
        def handler = heapHandlers[token]
        if (handler) {
            return handler.call(trimmedCommand, player, writer)
        }
        if (trimmedCommand.isEmpty()) {
            return TerminalFormatter.formatText("Heap commands: echo <msg> | pay <entity> <bits> | pm <entity> <msg> | trade <entity> | offer | accept | list | help | exit\r\n", 'italic', 'cyan')
        }
        return TerminalFormatter.formatText("Unknown command '${trimmedCommand}'. Type 'help' for heap commands.\r\n", 'italic', 'yellow')
    }

    private String heapUsage(String u) {
        return TerminalFormatter.formatText("Usage: ${u}", 'bold', 'yellow')
    }

    private String handleExitHeap(String command, LambdaPlayer player, PrintWriter writer) {
        lambdaPlayerService.setMingleStatus(player, false)
        tradeTargets.remove(player.username); pendingOffers.remove(player.username)   // clear any trade context

        // Announce the departure to everyone still in the heap: persist for history + live broadcast
        // as one complete "[HH:mm] [SYSTEM] <name> popped from heap" line (no dangling empty header).
        def departure = "${player.displayName} popped from heap"
        this.sendSystemMessage(departure)
        def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
        this.broadcastToChatUsers("[${timeStr}] ${TerminalFormatter.formatText('[SYSTEM]', 'bold', 'red')} ${departure}\r\n")

        // Flavor line shown only to the leaving entity.
        return TerminalFormatter.formatText("Null pointer new memory address. Returned to working ram", 'bold', 'green') + "\r\n"
    }

    private String handleEchoHeap(String trimmedCommand, LambdaPlayer player, PrintWriter writer) {
        def output = new StringBuilder()
        def message = this.processEchoCommand(trimmedCommand)
        if (message) {
            def result = this.sendMessage(player, message)
            if (result.success) {
                def timeStr = new java.text.SimpleDateFormat('HH:mm').format(new Date())
                output.append("[${timeStr}] ${TerminalFormatter.formatText(player.displayName, 'bold', 'white')}: ${message} \r\n")
                output.append("\r\n")
                this.broadcastToChatUsers(output.toString())
                return
            }
            return TerminalFormatter.formatText("Error: ${result.error}", 'bold', 'red')
        }
        return TerminalFormatter.formatText("Invalid echo format. Use: echo <message>", 'bold', 'red')
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
            userList.append("Alone in heap..no sound..no life..just darkness..\r\n")
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
        this.broadcastToChatUsers("[${timeStr}] ${systemMsg}\r\n")

        return TerminalFormatter.formatText("Sent ${bitsAmount} bits to ${targetName}", 'bold', 'green') + "\r\n"
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

        // Remember who this entity is trading with, so `offer` knows the buyer.
        tradeTargets[player.username] = targetPlayer.id

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
                // sort by id so the F<index> shown here maps to the same fragment in `offer`
                playerFragments = managedPlayer.logicFragments.findAll { it != null }.sort { it.id }
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
            tradeableKnowledge.puzzleFragments.sort { it.id }.eachWithIndex { item, index ->
                tradeMenu.append("PF${index + 1}. ${item.name} (${item.elementHint}) - ${item.tradeValue} bits\r\n")
            }
        }

        // Variables
        if (tradeableKnowledge.variables.size() > 0) {
            tradeMenu.append("Collected Variables:\r\n")
            tradeableKnowledge.variables.sort { it.id }.eachWithIndex { item, index ->
                tradeMenu.append("V${index + 1}. ${item.name} (${item.elementHint}) - ${item.tradeValue} bits\r\n")
            }
        }

        // Nonces (only tradeable ones)
        def tradeableNonces = tradeableKnowledge.nonces.findAll { it.canTrade }.sort { it.id }
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
            tradeableKnowledge.completedSolutions.sort { it.id }.eachWithIndex { item, index ->
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

    // Maps an offer code prefix → how to fetch/sort its list and label items. Map-dispatch, no switch.
    private static final Map<String, Map> OFFER_SPECS = [
        PF: [type: 'puzzle_fragment',   pick: { k -> k.puzzleFragments.sort { it.id } },                label: { it.name }],
        V:  [type: 'variable',          pick: { k -> k.variables.sort { it.id } },                      label: { it.name }],
        N:  [type: 'nonce',             pick: { k -> k.nonces.findAll { it.canTrade }.sort { it.id } }, label: { it.name }],
        S:  [type: 'complete_solution', pick: { k -> k.completedSolutions.sort { it.id } },             label: { it.description }]
    ]

    private String heapWarn(String msg) {
        return TerminalFormatter.formatText(msg, 'bold', 'red') + "\r\n"
    }

    private void notifyPlayer(LambdaPlayer target, String message) {
        try {
            def w = findWriterForPlayer(target)
            if (w) { w.println("\r\n" + TerminalFormatter.formatText(message, 'bold', 'cyan')); w.flush() }
        } catch (ignored) { }
    }

    /** Resolve a menu code (F/PF/V/N/S + index) to [itemType, itemId, label], sorted by id to match the menu. */
    private Map resolveOfferItem(LambdaPlayer seller, String prefix, int idx) {
        if (idx < 1) return null
        if (prefix == 'F') {
            def mp = LambdaPlayer.get(seller.id)
            def frags = mp?.logicFragments?.findAll { it != null }?.sort { it.id } ?: []
            if (idx > frags.size()) return null
            def f = frags[idx - 1]
            return [itemType: 'F', itemId: f.id, label: f.name]
        }
        def spec = OFFER_SPECS[prefix]
        if (!spec) return null
        def k = puzzleKnowledgeTradingService.getTradeablePuzzleKnowledge(seller)
        def list = (spec.pick as Closure).call(k)
        if (idx > list.size()) return null
        def chosen = list[idx - 1]
        return [itemType: spec.type, itemId: chosen.id, label: (spec.label as Closure).call(chosen)]
    }

    // `offer <CODE><idx> [qty] <price>` — proposes a trade to the current `trade` target. The buyer
    // must `accept` before anything transfers (no force-charging another entity's wallet).
    private String handleOfferCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command.trim().split(/\s+/)
        if (parts.length < 3) return heapWarn("Usage: offer F<num> <qty> <price>  |  offer PF|V|N|S<num> <price>")

        def targetId = tradeTargets[player.username]
        if (!targetId) return heapWarn("Start a trade first: trade <entity>")
        def buyer = LambdaPlayer.get(targetId)
        if (!buyer) return heapWarn("Your trade target is no longer available.")
        if (buyer.id == player.id) return heapWarn("You cannot offer to yourself.")

        def m = (parts[1].toUpperCase() =~ /^(F|PF|V|N|S)(\d+)$/)
        if (!m.matches()) return heapWarn("Bad item code '${parts[1]}'. Use F/PF/V/N/S + a number, e.g. PF1.")
        def prefix = m.group(1); int idx = m.group(2).toInteger()

        int qty = 1; int price
        if (prefix == 'F') {
            if (parts.length < 4) return heapWarn("Standard fragments: offer F<num> <qty> <price>")
            if (!parts[2].isInteger() || !parts[3].isInteger()) return heapWarn("qty and price must be numbers")
            qty = parts[2].toInteger(); price = parts[3].toInteger()
        } else {
            if (!parts[2].isInteger()) return heapWarn("price must be a number")
            price = parts[2].toInteger()
        }
        if (qty < 1) return heapWarn("Quantity must be at least 1")
        if (price < 0) return heapWarn("Price must be 0 or more")

        def item = resolveOfferItem(player, prefix, idx)
        if (!item) return heapWarn("You have no ${prefix}${idx} to offer.")

        pendingOffers[buyer.username] = [sellerId: player.id, sellerName: player.displayName,
            kind: prefix, itemType: item.itemType, itemId: item.itemId, qty: qty, price: price, label: item.label]
        notifyPlayer(buyer, "💱 ${player.displayName} offers you ${item.label}${prefix == 'F' ? ' x' + qty : ''} for ${price} bits — type 'accept' to buy or 'cancel' to decline.")
        return TerminalFormatter.formatText("Offer sent to ${buyer.displayName}: ${item.label} for ${price} bits. Awaiting their 'accept'.", 'bold', 'green') + "\r\n"
    }

    // Buyer consents — only now do item + bits actually move (via the existing transfer methods).
    private String handleAcceptCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def offer = pendingOffers[player.username]
        if (!offer) return heapWarn("No pending offer to accept.")
        def seller = LambdaPlayer.get(offer.sellerId)
        if (!seller) { pendingOffers.remove(player.username); return heapWarn("The seller is no longer available.") }

        def res
        if (offer.kind == 'F') {
            res = lambdaPlayerService.transferFragment(seller, player, offer.itemId as Long, offer.qty as int, offer.price as int)
        } else {
            res = puzzleKnowledgeTradingService.executePuzzleKnowledgeTrade(seller, player, offer.itemType as String, offer.itemId.toString(), offer.price as int)
        }
        pendingOffers.remove(player.username)
        if (res?.success) {
            notifyPlayer(seller, "✅ ${player.displayName} accepted your offer: ${offer.label} for ${offer.price} bits.")
            return TerminalFormatter.formatText("✅ Trade complete: ${offer.label} for ${offer.price} bits.", 'bold', 'green') + "\r\n"
        }
        return heapWarn("Trade failed: ${res?.message ?: 'unknown error'}")
    }

    private String handleCancelCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def hadOffer = pendingOffers.remove(player.username) != null
        tradeTargets.remove(player.username)
        return hadOffer ? TerminalFormatter.formatText("Offer declined.", 'bold', 'yellow') + "\r\n"
                        : TerminalFormatter.formatText("Trade context cleared.", 'italic', 'cyan') + "\r\n"
    }

    private PrintWriter findWriterForPlayer(LambdaPlayer target) {
        // Delegate to the single live-writer-preferring resolver so pm/offer notifications never
        // resolve to a lingering ghost session (DRY: one resolver, used by username and by id).
        return telnetServerService.writerForPlayerId(target.id)
    }
}