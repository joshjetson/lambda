package ysap

class ChatMessage {
    String senderName
    String message
    String messageType = 'CHAT'  // CHAT, TRADE, SYSTEM
    Integer matrixLevel = 0  // 0 = global mingle
    Date timestamp = new Date()
    
    static constraints = {
        senderName blank: false, size: 3..30
        message blank: false, maxSize: 500
        messageType inList: ['CHAT', 'TRADE', 'SYSTEM', 'WHISPER']
        matrixLevel min: 0, max: 10
    }
    
    static mapping = {
        message type: 'text'
        sort timestamp: 'desc'
    }
    
    String toString() {
        return "${senderName}: ${message}"
    }
    
    String getFormattedMessage() {
        def timeStr = new java.text.SimpleDateFormat('HH:mm:ss').format(timestamp)
        switch (messageType) {
            case 'TRADE':
                return "[${timeStr}] [TRADE] ${senderName}: ${message}"
            case 'SYSTEM':
                return "[${timeStr}] [SYSTEM] ${message}"
            case 'WHISPER':
                return "[${timeStr}] [WHISPER] ${senderName}: ${message}"
            default:
                return "[${timeStr}] ${senderName}: ${message}"
        }
    }
}