package ysap

class BootStrap {

    TelnetServerService telnetServerService
    BootstrapService bootstrapService
    LambdaMerchantService lambdaMerchantService
    
    def init = { servletContext ->
        // Initialize game board data
        initializeBoardPositions()
        
        // Initialize test coordinates for repair mini-game
        initializeTestCoordinates()
        
        // Spawn Lambda merchants across all matrix levels
        lambdaMerchantService.spawnMerchantsForAllLevels()
        
        // Start telnet server for Lambda game
        telnetServerService.startServer(23)
        
        // Initialize legacy page system
//        bootstrapService.createFirstPage()
        
        println "Lambda: A Digital Entities Game initialized"
        println "Telnet server running on port 8181"
        println "Connect via: telnet localhost 8181"
    }
    
    def destroy = {
        try {
            telnetServerService.stopServer()
        } catch (Exception e) {
            println "Error stopping telnet server: ${e.message}"
        }
    }
    
    private void initializeBoardPositions() {
        if (BoardPosition.count() == 0) {
            def ringThemes = [
                1: "Kernel Space - The Core",
                2: "Memory Management - RAM Districts", 
                3: "Process Control - The Scheduler",
                4: "File System - Data Sectors",
                5: "Network Stack - Protocol Layers",
                6: "User Space - Application Territory",
                7: "Security Perimeter - Firewall Zone",
                8: "Internet Gateway - The Border",
                9: "Wild Web - Uncharted Networks",
                10: "Digital Freedom - The Open Net"
            ]
            
            ringThemes.each { matrixLevel, theme ->
                (1..12).each { sector ->
                    def position = new BoardPosition(
                        matrixLevel: matrixLevel,
                        sector: sector,
                        positionX: matrixLevel * 10 + sector,
                        positionY: matrixLevel * 10 + sector,
                        ringTheme: theme,
                        description: "Sector ${sector} of ${theme}",
                        hasLogicFragment: Math.random() > 0.6,
                        requiresCooperation: Math.random() > 0.8,
                        difficultyLevel: matrixLevel
                    )
                    position.save(failOnError: true)
                }
            }
            println "Initialized ${BoardPosition.count()} board positions across 10 matrix levels"
        }
    }
    
    private void initializeTestCoordinates() {
        // Create a wiped coordinate at (0,1) for repair testing
        if (!CoordinateState.findByMatrixLevelAndCoordinateXAndCoordinateY(1, 0, 1)) {
            def wipedCoordinate = new CoordinateState(
                matrixLevel: 1,
                coordinateX: 0,
                coordinateY: 1,
                health: 0,
                isAccessible: false,
                lastDamaged: new Date(),
                degradationRate: 1
            )
            wipedCoordinate.save(failOnError: true)
            println "Created wiped test coordinate at (0,1) for repair mini-game testing"
        }
    }
}
