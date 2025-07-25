package ysap

class GameSession {
    String sessionId              // Unique identifier for this game session
    Date sessionStart = new Date()
    Boolean isActive = true
    
    // Map-specific randomization seeds for consistent puzzle generation per map
    String mapSeed1, mapSeed2, mapSeed3, mapSeed4, mapSeed5
    String mapSeed6, mapSeed7, mapSeed8, mapSeed9, mapSeed10
    
    // Session-wide puzzle configuration
    Integer totalMaps = 10
    Date lastActivity = new Date()
    
    static constraints = {
        sessionId blank: false, maxSize: 50, unique: true
        mapSeed1 nullable: true, maxSize: 100
        mapSeed2 nullable: true, maxSize: 100
        mapSeed3 nullable: true, maxSize: 100
        mapSeed4 nullable: true, maxSize: 100
        mapSeed5 nullable: true, maxSize: 100
        mapSeed6 nullable: true, maxSize: 100
        mapSeed7 nullable: true, maxSize: 100
        mapSeed8 nullable: true, maxSize: 100
        mapSeed9 nullable: true, maxSize: 100
        mapSeed10 nullable: true, maxSize: 100
    }
    
    static mapping = {
        sessionId index: 'session_id_idx'
        lastActivity index: 'last_activity_idx'
    }
    
    String toString() {
        return "GameSession ${sessionId} (${isActive ? 'ACTIVE' : 'ENDED'})"
    }
    
    // Helper methods
    String getMapSeed(Integer mapNumber) {
        switch(mapNumber) {
            case 1: return mapSeed1
            case 2: return mapSeed2
            case 3: return mapSeed3
            case 4: return mapSeed4
            case 5: return mapSeed5
            case 6: return mapSeed6
            case 7: return mapSeed7
            case 8: return mapSeed8
            case 9: return mapSeed9
            case 10: return mapSeed10
            default: return null
        }
    }
    
    void setMapSeed(Integer mapNumber, String seed) {
        switch(mapNumber) {
            case 1: mapSeed1 = seed; break
            case 2: mapSeed2 = seed; break
            case 3: mapSeed3 = seed; break
            case 4: mapSeed4 = seed; break
            case 5: mapSeed5 = seed; break
            case 6: mapSeed6 = seed; break
            case 7: mapSeed7 = seed; break
            case 8: mapSeed8 = seed; break
            case 9: mapSeed9 = seed; break
            case 10: mapSeed10 = seed; break
        }
    }
    
    Boolean isSessionExpired() {
        def timeDiff = new Date().time - lastActivity.time
        return timeDiff > (24 * 60 * 60 * 1000) // 24 hours
    }
    
    void updateActivity() {
        lastActivity = new Date()
    }
}