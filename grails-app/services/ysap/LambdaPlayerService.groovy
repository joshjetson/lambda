package ysap

import grails.gorm.transactions.Transactional
import ysap.helpers.BoxBuilder

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

        def box = new BoxBuilder(36)
                .addSeparator()
        def art = '''
   ╭───╮
  │ ◉ ◉ │
  │  ▽  │
   ╰───╯
        '''.trim()

        // Add the ASCII art with proper centering
        def centeredArt = centerAsciiArt(art, 34)
        centeredArt.split('\n').each { line ->
            box.addLine(line)
        }
        return box.build()
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
    String getAvatarSelectionGrid() {
        def grid = new StringBuilder()

        // Header
        def headerBox = new BoxBuilder(78)
                .addCenteredLine("🎭 CHOOSE YOUR DIGITAL FORM 🎭")
                .addSeparator()
                .addCenteredLine("Each ethnicity has unique recursion powers")
                .build()

        grid.append(TerminalFormatter.formatText(headerBox, 'bold', 'cyan'))
        grid.append("\r\n\r\n")

        // Get avatar data
        def avatars = getAvailableAvatars()
        def avatarBoxes = []

        // Create all avatar boxes first
        avatars.eachWithIndex { avatar, index ->
            avatarBoxes.add(formatAvatarCompact(index + 1, avatar))
        }

        // Display in 2x3 grid
        for (int i = 0; i < avatarBoxes.size(); i += 2) {
            def leftBox = avatarBoxes[i]
            def rightBox = (i + 1 < avatarBoxes.size()) ? avatarBoxes[i + 1] : null

            // Split into lines - handle both \n and \r\n
            def leftLines = leftBox.split(/\r?\n/)
            def rightLines = rightBox ? rightBox.split(/\r?\n/) : []

            // Combine side by side
            def maxLines = Math.max(leftLines.size() as int, rightLines.size() as int)
            for (int j = 0; j < maxLines; j++) {
                // Left side
                if (j < leftLines.length) {
                    grid.append(leftLines[j])
                } else {
                    grid.append(" " * 38)  // Empty space matching box width
                }

                grid.append("  ")  // Gap between boxes

                // Right side
                if (rightBox && j < rightLines.length) {
                    grid.append(rightLines[j])
                }

                grid.append("\r\n")
            }

            // Add space between rows
            if (i + 2 < avatarBoxes.size()) {
                grid.append("\r\n")
            }
        }

        return grid.toString()
    }

    private String formatAvatarCompact(int number, String avatarType) {
        def info = getAvatarInfo(avatarType)

        // Create a compact box for the avatar
        def box = new BoxBuilder(36)
                .addLine(" ${number}. ${info.name}")
                .addSeparator()

        // Add the ASCII art with proper centering
        def centeredArt = centerAsciiArt(info.art, 34)
        centeredArt.split('\n').each { line ->
            box.addLine(line)
        }

        box.addSeparator()
                .addCenteredLine(info.trait)

        return box.build()  // Added .build() here!
    }

    private String centerAsciiArt(String art, int width) {
        def lines = []
        art.split('\n').each { line ->
            def trimmedLine = line.trim()
            if (trimmedLine.length() > 0) {
                def totalPadding = width - trimmedLine.length()
                def leftPad = totalPadding / 2
                def rightPad = totalPadding - leftPad
                lines.add((" " * leftPad) + trimmedLine + (" " * rightPad))
            } else {
                lines.add(" " * width)
            }
        }

        // Ensure we have exactly 5 lines for consistency
        while (lines.size() < 5) {
            lines.add(" " * width)
        }

        return lines.join('\n')
    }

    private Map getAvatarInfo(String avatarType) {
        def avatarData = [
                'CLASSIC_LAMBDA': [
                        name: 'CLASSIC LAMBDA',
                        art: '''λλλλλ
λ     λ
λ   λ   λ
λ   λ λ   λ
λλλλλ λλλλλ''',
                        trait: 'The Pioneers',
                        power: 'Enhanced Fusion'
                ],
                'CIRCUIT_PATTERN': [
                        name: 'CIRCUIT PATTERN',
                        art: '''┌─┬─┬─┬─┬─┐
├─┼─┼─┼─┼─┤
├─┼─●─┼─┼─┤
├─┼─┼─┼─┼─┤
└─┴─┴─┴─┴─┘''',
                        trait: 'The Engineers',
                        power: 'Defrag Defense'
                ],
                'GEOMETRIC_ENTITY': [
                        name: 'GEOMETRIC ENTITY',
                        art: '''◇
◇◇◇
◇◇◇◇◇
◇◇◇
◇''',
                        trait: 'The Architects',
                        power: 'Enhanced Movement'
                ],
                'FLOWING_CURRENT': [
                        name: 'FLOWING CURRENT',
                        art: '''~~~⚡~~~
~~⚡~~
~⚡~
~~⚡~~
~~~⚡~~~''',
                        trait: 'The Networkers',
                        power: 'Mining Boost'
                ],
                'DIGITAL_GHOST': [
                        name: 'DIGITAL GHOST',
                        art: '''░▒▓▒░
░▒▓█▓▒░
▒▓███▓▒
▓█████▓
▒▓███▓▒''',
                        trait: 'The Mystics',
                        power: 'Stealth Mode'
                ],
                'BINARY_FORM': [
                        name: 'BINARY FORM',
                        art: '''10110101
01001010
11010101
01010110
10101011''',
                        trait: 'The Purists',
                        power: 'Fast Processing'
                ]
        ]

        return avatarData[avatarType] ?: avatarData['CLASSIC_LAMBDA']
    }
}