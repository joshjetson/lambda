package ysap

import grails.gorm.transactions.Transactional

@Transactional
class LambdaPlayerService {

    def createPlayer(String username, String displayName, String avatarSilhouette) {
        def player = new LambdaPlayer(
            username: username,
            displayName: displayName,
            avatarSilhouette: avatarSilhouette,
            asciiFace: generateDefaultAsciiFace(),
            currentMatrixLevel: 1,
            positionX: 0,
            positionY: 0,
            bits: 100,
            isOnline: true,
            lastActivity: new Date()
        )
        
        initializeStartingSkills(player)
        initializeStartingLogic(player)
        applyEthnicityBonuses(player, avatarSilhouette)
        
        player.save(failOnError: true)
        return player
    }
    
    def updatePlayerActivity(LambdaPlayer player) {
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                managedPlayer.lastActivity = new Date()
                managedPlayer.isOnline = true
                managedPlayer.save(failOnError: true)
            }
        }
    }
    
    def setPlayerOffline(LambdaPlayer player) {
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                managedPlayer.isOnline = false
                managedPlayer.save(failOnError: true)
            }
        }
    }
    
    def updateAsciiFace(LambdaPlayer player, String newFace) {
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                managedPlayer.asciiFace = newFace
                managedPlayer.save(failOnError: true)
            }
        }
    }
    
    def movePlayer(LambdaPlayer player, Integer newMatrixLevel, Integer newX, Integer newY) {
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                managedPlayer.currentMatrixLevel = newMatrixLevel
                managedPlayer.positionX = newX
                managedPlayer.positionY = newY
                managedPlayer.lastActivity = new Date()
                managedPlayer.isOnline = true
                managedPlayer.save(failOnError: true)
            }
        }
    }
    
    def getOnlinePlayers() {
        return LambdaPlayer.findAllByIsOnline(true)
    }
    
    def getPlayersByMatrixLevel(Integer matrixLevel) {
        return LambdaPlayer.findAllByCurrentMatrixLevel(matrixLevel)
    }
    
    def addBits(LambdaPlayer player, Integer amount) {
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                managedPlayer.bits += amount
                managedPlayer.save(failOnError: true)
            }
        }
    }
    
    def deductBits(LambdaPlayer player, Integer amount) {
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer && managedPlayer.bits >= amount) {
                managedPlayer.bits -= amount
                managedPlayer.save(failOnError: true)
                return true
            }
            return false
        }
    }
    
    def setMingleStatus(LambdaPlayer player, Boolean inMingle) {
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                managedPlayer.isInMingle = inMingle
                managedPlayer.save(failOnError: true)
            }
        }
    }
    
    private void initializeStartingSkills(LambdaPlayer player) {
        def startingSkills = ['SCANNING', 'STEALTH', 'PROCESSING']
        
        startingSkills.each { skillName ->
            def skill = new PlayerSkill(
                skillName: skillName,
                level: 1,
                experience: 0,
                player: player
            )
            player.addToSkills(skill)
        }
    }
    
    private void initializeStartingLogic(LambdaPlayer player) {
        def basicLogic = new LogicFragment(
            name: 'Basic Print',
            description: 'Fundamental output capability',
            fragmentType: 'FUNCTION',
            powerLevel: 1,
            pythonCapability: 'print()',
            owner: player
        )
        player.addToLogicFragments(basicLogic)
    }
    
    private String generateDefaultAsciiFace() {
        return '''
   ╭───╮
  │ ◉ ◉ │
  │  ▽  │
   ╰───╯
        '''.trim()
    }
    
    List<String> getAvailableAvatars() {
        return [
            'CLASSIC_LAMBDA',
            'CIRCUIT_PATTERN', 
            'GEOMETRIC_ENTITY',
            'FLOWING_CURRENT',
            'DIGITAL_GHOST',
            'BINARY_FORM'
        ]
    }
    
    String getAvatarDisplay(String avatarType) {
        def avatars = [
            'CLASSIC_LAMBDA': '''
    λλλλλ
   λ     λ
  λ   λ   λ
 λ   λ λ   λ
λλλλλ λλλλλ
            ''',
            'CIRCUIT_PATTERN': '''
┌─┬─┬─┬─┬─┐
├─┼─┼─┼─┼─┤
├─┼─●─┼─┼─┤
├─┼─┼─┼─┼─┤
└─┴─┴─┴─┴─┘
            ''',
            'GEOMETRIC_ENTITY': '''
    ◇
   ◇◇◇
  ◇◇◇◇◇
   ◇◇◇
    ◇
            ''',
            'FLOWING_CURRENT': '''
~~~⚡~~~
 ~~⚡~~
  ~⚡~
 ~~⚡~~
~~~⚡~~~
            ''',
            'DIGITAL_GHOST': '''
 ░▒▓▒░ 
░▒▓█▓▒░
▒▓███▓▒
▓█████▓
▒▓███▓▒
            ''',
            'BINARY_FORM': '''
10110101
01001010
11010101
01010110
10101011
            '''
        ]
        return avatars[avatarType] ?: avatars['CLASSIC_LAMBDA']
    }
    
    private void applyEthnicityBonuses(LambdaPlayer player, String avatarSilhouette) {
        // PASSIVE BONUSES DISABLED - All ethnicity advantages now require 'recurse' command
        switch (avatarSilhouette) {
            case 'CLASSIC_LAMBDA':
                // The Pioneers: Recursion ability - enhanced fusion success
                // player.fragmentDetectionBonus = 0.20  // DISABLED - use 'recurse fusion'
                break
                
            case 'CIRCUIT_PATTERN':
                // The Engineers: Recursion ability - temporary defrag resistance
                // player.defragResistanceBonus = 0.15  // DISABLED - use 'recurse defend'
                break
                
            case 'GEOMETRIC_ENTITY':
                // The Architects: Recursion ability - enhanced movement
                // player.movementRangeBonus = 2  // DISABLED - use 'recurse movement'
                break
                
            case 'FLOWING_CURRENT':
                // The Networkers: Recursion ability - mining efficiency boost
                // player.miningEfficiencyBonus = 0.25  // DISABLED - use 'recurse mine'
                break
                
            case 'DIGITAL_GHOST':
                // The Mystics: Recursion ability - enhanced stealth
                // player.stealthBonus = 0.30  // DISABLED - use 'recurse stealth'
                break
                
            case 'BINARY_FORM':
                // The Purists: Recursion ability - processing acceleration
                // player.fusionSuccessBonus = 0.15  // DISABLED - use 'recurse process'
                break
                
            default:
                // No bonuses for unknown avatars
                break
        }
    }
}