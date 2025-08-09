package ysap

class LambdaPlayer {
    String username
    String displayName
    String asciiFace
    String avatarSilhouette
    
    Integer currentMatrixLevel = 1
    Integer positionX = 0
    Integer positionY = 0
    
    Integer scanningLevel = 1
    Integer stealthLevel = 1
    Integer processingLevel = 1
    
    Integer bits = 100  // Starting currency
    Boolean isInMingle = false
    Boolean isOnline = false
    Boolean hasAcquiredPid = false  // For defrag bot encounters
    Date lastActivity
    Date createdDate = new Date()
    
    // Entropy System for Daily Addiction Hook
    Double entropy = 100.0  // Digital coherence (decays over time)
    Date lastEntropyRefresh = new Date()
    Date lastBitMining = new Date()
    Integer miningRate = 1  // Base bits per hour when offline
    Integer fusionAttempts = 0  // Daily fusion attempts
    Date lastFusionReset = new Date()
    
    // Ethnicity-Based Bonuses
    Double fragmentDetectionBonus = 0.0    // Classic Lambda: +20% scan range
    Double defragResistanceBonus = 0.0     // Circuit Pattern: +15% defrag resistance
    Integer movementRangeBonus = 0         // Geometric Entity: +2 movement range
    Double miningEfficiencyBonus = 0.0     // Flowing Current: +25% mining efficiency
    Double stealthBonus = 0.0              // Digital Ghost: +30% stealth bonus
    Double fusionSuccessBonus = 0.0        // Binary Form: +15% fusion success
    
    List logicFragments
    List skills
    List specialItems
    List puzzleLogicFragments  // Special executable logic fragments
    List collectedVariables    // Variables found in rooms
    List discoveredNonces      // Nonces for elemental symbol unlocking
    List executionHistory      // Record of puzzle executions
    
    // Elemental Symbols - collected as self attributes
    Boolean hasAirSymbol = false
    Boolean hasFireSymbol = false
    Boolean hasEarthSymbol = false
    Boolean hasWaterSymbol = false
    Date airSymbolAcquired
    Date fireSymbolAcquired
    Date earthSymbolAcquired
    Date waterSymbolAcquired
    
    static hasMany = [
        logicFragments: LogicFragment,
        skills: PlayerSkill,
        specialItems: SpecialItem,
        puzzleLogicFragments: PuzzleLogicFragment,
        collectedVariables: HiddenVariable,
        discoveredNonces: ElementalNonce,
        executionHistory: PuzzleExecution,
        commandHistory: CommandHistory
    ]
    
    static constraints = {
        username unique: true, blank: false, size: 3..20
        displayName blank: false, size: 3..30
        asciiFace maxSize: 500
        avatarSilhouette blank: false
        currentMatrixLevel min: 1, max: 10
        bits min: 0
        entropy min: 0.0d, max: 100.0d
        miningRate min: 1
        fusionAttempts min: 0
        lastActivity nullable: true
        lastEntropyRefresh nullable: true
        lastBitMining nullable: true
        lastFusionReset nullable: true
        fragmentDetectionBonus min: 0.0d, max: 1.0d
        defragResistanceBonus min: 0.0d, max: 1.0d
        movementRangeBonus min: 0, max: 5
        miningEfficiencyBonus min: 0.0d, max: 1.0d
        stealthBonus min: 0.0d, max: 1.0d
        fusionSuccessBonus min: 0.0d, max: 1.0d
        airSymbolAcquired nullable: true
        fireSymbolAcquired nullable: true
        earthSymbolAcquired nullable: true
        waterSymbolAcquired nullable: true
    }
    
    static mapping = {
        asciiFace type: 'text'
    }
    
}