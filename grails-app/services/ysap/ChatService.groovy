package ysap

import grails.gorm.transactions.Transactional

@Transactional
class ChatService {

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
}