package ysap

import grails.gorm.transactions.Transactional
import ysap.helpers.BoxBuilder

@Transactional
class LambdaPlayerService {
    def entropyService
    def gameSessionService
    def audioService
    def coordinateStateService
    def lambdaMerchantService
    def puzzleService

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

    String getPlayerStatus(LambdaPlayer player) {
        def status = new StringBuilder()
        def entropyStatus = entropyService.getEntropyStatus(player)

        status.append(TerminalFormatter.formatText("=== DETAILED LAMBDA STATUS ===", 'bold', 'cyan')).append('\r\n')
        status.append("Entity: ${player.displayName}\r\n")
        status.append("Matrix Level: ${player.currentMatrixLevel}/10\r\n")
        status.append("Coordinates: (${player.positionX},${player.positionY})\r\n")
        status.append("Bits: ${player.bits}\r\n")
        def currentEntropy = entropyStatus.currentEntropy ?: 100.0
        status.append("Digital Coherence: ${TerminalFormatter.formatText("${currentEntropy}%", entropyService.getEntropyColor(currentEntropy), 'bold')}\r\n")

        def miningRewards = entropyStatus.miningRewards ?: 0
        if (miningRewards > 0) {
            status.append("Mining Rewards: ${TerminalFormatter.formatText("+${miningRewards} bits", 'bold', 'green')} (use 'mining')\r\n")
        }

        def fusionAttempts = entropyService.getFragmentFusionAttempts(player)
        def remainingAttempts = fusionAttempts.remaining ?: 0
        if (remainingAttempts > 0) {
            status.append("Fusion Attempts: ${TerminalFormatter.formatText("${remainingAttempts} remaining", 'bold', 'yellow')}\r\n")
        }

        status.append("Online Lambda Entities: ${this.getOnlinePlayers().size()}\r\n")
        status.append("Entities in Current Matrix Level: ${this.getPlayersByMatrixLevel(player.currentMatrixLevel).size()}\r\n")

        // Fix Hibernate session issues by using transaction
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                status.append("Logic Fragments: ${managedPlayer.logicFragments?.size() ?: 0}\r\n")
                status.append("Special Items: ${managedPlayer.specialItems?.size() ?: 0}\r\n")
            } else {
                status.append("Logic Fragments: 0\r\n")
                status.append("Special Items: 0\r\n")
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
            status.append("Ethnicity Bonuses: ${TerminalFormatter.formatText(bonuses.join(', '), 'bold', 'magenta')}\r\n")
        }

        def canRefresh = entropyStatus.canRefresh ?: false
        def timeUntilRefresh = entropyStatus.timeUntilRefresh ?: 0

        if (canRefresh) {
            status.append('\r\n').append(TerminalFormatter.formatText("🔋 Daily entropy refresh available!", 'bold', 'green'))
        } else if (currentEntropy < 50) {
            status.append('\r\n').append(TerminalFormatter.formatText("⚠️  Low coherence - refresh in ${timeUntilRefresh}h", 'bold', 'yellow'))
        }

        return status.toString()
    }

    String showInventory(LambdaPlayer player) {
        def inventory = new StringBuilder()

        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                inventory.append(TerminalFormatter.formatText("=== LAMBDA INVENTORY ===", 'bold', 'cyan')).append('\r\n')
                inventory.append("Bits: ${TerminalFormatter.formatText(managedPlayer.bits.toString(), 'bold', 'green')}\r\n\r\n")

                inventory.append("Logic Fragments:\r\n")
                def validFragments = managedPlayer.logicFragments?.findAll { it != null }
                if (validFragments?.size() > 0) {
                    validFragments.each { fragment ->
                        if (fragment?.name) {
                            inventory.append("  • ${fragment.name} (${fragment.fragmentType}) - Level ${fragment.powerLevel}\r\n")
                        }
                    }
                } else {
                    inventory.append("  No logic fragments acquired\r\n")
                }

                inventory.append("\r\nSpecial Items:\r\n")
                def validItems = managedPlayer.specialItems?.findAll { it != null }
                if (validItems?.size() > 0) {
                    validItems.each { item ->
                        if (item?.name) {
                            def usesText = item.isPermanent ? "[PERMANENT]" : "[${item.usesRemaining}/${item.maxUses} uses]"
                            def activeText = item.isActive ? " [ACTIVE]" : ""
                            inventory.append("  • ${item.name} ${usesText}${activeText}\r\n")
                            inventory.append("    ${item.description}\r\n")
                            if (item.expiresAt && item.isActive) {
                                def now = new Date()
                                def timeLeft = ((item.expiresAt.time - now.time) / 1000).toInteger()
                                if (timeLeft > 0) {
                                    inventory.append("    Expires in: ${timeLeft} seconds\r\n")
                                }
                            }
                        }
                    }
                } else {
                    inventory.append("  No special items acquired\r\n")
                }

                inventory.append("\r\nSkills:\r\n")
                def validSkills = managedPlayer.skills?.findAll { it != null }
                if (validSkills?.size() > 0) {
                    validSkills.each { skill ->
                        if (skill?.skillName) {
                            inventory.append("  • ${skill.skillName} - Level ${skill.level} (${skill.experience} XP)\r\n")
                        }
                    }
                } else {
                    inventory.append("  No skills acquired\r\n")
                }
            } else {
                inventory.append("Error: Player not found\r\n")
            }
        }

        return inventory.toString()
    }

    // ===== CAT COMMAND METHODS (moved from TelnetServerService) =====

    String handleCatCommand(String command, LambdaPlayer player) {
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
        
        fragmentFile.append("=== FRAGMENT_FILE ===\r\n")
        fragmentFile.append("Lambda Entity: ${player.displayName}\r\n")
        fragmentFile.append("Total Fragments: ${currentFragments.size()}\r\n\r\n")
        
        if (currentFragments) {
            currentFragments.sort { it?.discoveredDate ?: new Date(0) }.each { fragment ->
                if (fragment) {
                    def quantityDisplay = (fragment.quantity ?: 1) > 1 ? " x${fragment.quantity}" : ""
                    fragmentFile.append("--- ${fragment.name?.toUpperCase() ?: 'UNKNOWN'}${quantityDisplay} ---\r\n")
                    fragmentFile.append("Type: ${fragment.fragmentType ?: 'UNKNOWN'}\r\n")
                    fragmentFile.append("Power Level: ${fragment.powerLevel ?: 1}/10\r\n")
                    fragmentFile.append("Quantity: ${fragment.quantity ?: 1}\r\n")
                    def dateStr = fragment.discoveredDate ? new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').format(fragment.discoveredDate) : 'Unknown'
                    fragmentFile.append("Discovered: ${dateStr}\r\n")
                    fragmentFile.append("${fragment.pythonCapability ?: 'No capability data'}\r\n\r\n")
                }
            }
        } else {
            fragmentFile.append("No fragments collected yet.\r\n")
            fragmentFile.append("Use 'scan' to find fragments, then 'pickup' to collect them.\r\n")
        }
        
        return fragmentFile.toString()
    }

    private String viewFragmentContent(LogicFragment fragment) {
        def content = new StringBuilder()
        content.append("=== ${fragment.name.toUpperCase()} FRAGMENT ===\r\n")
        content.append("Type: ${fragment.fragmentType}\r\n")
        content.append("Power Level: ${fragment.powerLevel}/10\r\n")
        content.append("Description: ${fragment.description}\r\n\r\n")
        content.append("Python Capability:\r\n")
        content.append("${fragment.pythonCapability}\r\n")
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

    // Dependency methods - service injection already declared at top of class

    private LogicFragment findFragmentAtCoordinates(LambdaPlayer player) {
        // Use game session service for truly random fragment distribution
        return gameSessionService.getFragmentAtCoordinates(player.currentMatrixLevel, player.positionX, player.positionY)
    }

    private String viewEntropyMonitor(LambdaPlayer player) {
        def monitor = new StringBuilder()
        def entropyStatus = entropyService.getEntropyStatus(player)
        
        monitor.append(TerminalFormatter.formatText("=== ENTROPY MONITOR v3.7.2 ===", 'bold', 'cyan')).append('\r\n')
        monitor.append("Entity: ${player.displayName}\r\n")
        monitor.append("Coherence Level: ${TerminalFormatter.formatText("${entropyStatus.currentEntropy ?: 100.0}%", entropyService.getEntropyColor(entropyStatus.currentEntropy ?: 100.0), 'bold')}\r\n")
        monitor.append("Status: ${getEntropyStatusText(entropyStatus.currentEntropy ?: 100.0)}\r\n\r\n")
        
        monitor.append("=== DEGRADATION ANALYSIS ===\r\n")
        monitor.append("Decay Rate: ${entropyStatus.decayRate ?: 2.0}% per hour offline\r\n")
        monitor.append("Hours Offline: ${entropyStatus.hoursOffline ?: 0}h\r\n")
        monitor.append("Time Until Refresh: ${entropyStatus.timeUntilRefresh ?: 0}h\r\n\r\n")
        
        monitor.append("=== MINING SUBSYSTEM ===\r\n")
        monitor.append("Available Rewards: ${entropyStatus.miningRewards ?: 0} bits\r\n")
        def rawEfficiency = entropyStatus.miningEfficiency ?: "1.0"
        def numericEfficiency = rawEfficiency.toString().replace('%','') as BigDecimal
        def roundedEfficiency = Math.round(numericEfficiency * 100)
        monitor.append("Mining Efficiency: ${roundedEfficiency}%\r\n")
        monitor.append("Efficiency Factor: Digital coherence level\r\n\r\n")
        
        def canRefresh = entropyStatus.canRefresh ?: false
        if (canRefresh) {
            monitor.append(TerminalFormatter.formatText("⚡ REFRESH AVAILABLE", 'bold', 'green')).append('\r\n')
            monitor.append("Run 'entropy refresh' to restore digital coherence\r\n")
        } else {
            monitor.append(TerminalFormatter.formatText("⏳ COOLING DOWN", 'bold', 'yellow')).append('\r\n')
            monitor.append("Next refresh window opens in ${entropyStatus.timeUntilRefresh ?: 0} hours\r\n")
        }
        
        return monitor.toString()
    }

    private String getEntropyStatusText(Double entropy) {
        if (entropy >= 90) return "OPTIMAL"
        else if (entropy >= 75) return "STABLE"
        else if (entropy >= 50) return "DEGRADING"
        else if (entropy >= 25) return "CRITICAL"
        else return "FAILING"
    }

    String showMatrixMap(LambdaPlayer player) {
        def map = new StringBuilder()

        // Header with ANSI colors
        map.append("\033[1;36m=== MATRIX LEVEL ${player.currentMatrixLevel} MAP ===\033[0m\r\n")
        map.append("\033[3;37mLegend: @ = You, D = Defrag Bot, F = Fragment, M = Merchant, X = Wiped, ! = Critical, . = OK\033[0m\r\n\r\n")

        // Get health data for entire matrix level - wrap in transaction
        def healthMap = [:]
        LambdaPlayer.withTransaction {
            healthMap = coordinateStateService.getMatrixLevelHealth(player.currentMatrixLevel)
        }

        // Build 10x10 map (Y=9 at top, Y=0 at bottom to match normal coordinates)
        for (int y = 9; y >= 0; y--) {
            // Y-axis label in white
            map.append("\033[1;37m${y}\033[0m ")

            for (int x = 0; x <= 9; x++) {
                def symbol = "."
                def colorCode = "32" // green default

                // Check if this is the player's position
                if (x == player.positionX && y == player.positionY) {
                    symbol = "@"
                    colorCode = "1;36" // bold cyan
                } else {
                    // Check coordinate health
                    def health = healthMap["${x},${y}"]
                    if (health) {
                        if (health.health <= 0) {
                            symbol = "X"
                            colorCode = "1;31" // bold red
                        } else if (health.health <= 25) {
                            symbol = "!"
                            colorCode = "1;31" // bold red
                        } else if (health.health <= 50) {
                            symbol = "!"
                            colorCode = "1;33" // bold yellow
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
                        colorCode = "1;31" // bold red
                    }

                    // Check for fragment (lower priority than defrag bot)
                    else if (symbol == "." || symbol == "!") {
                        def fragment = gameSessionService.getFragmentAtCoordinates(player.currentMatrixLevel, x, y)
                        if (fragment) {
                            symbol = "F"
                            colorCode = "1;32" // bold green
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
                                colorCode = "1;33" // bold yellow
                            }
                        } catch (Exception e) {
                            // Ignore merchant service errors
                        }
                    }
                }

                // Add the colored symbol
                map.append("\033[${colorCode}m${symbol}\033[0m")

                // Add space between symbols (but not after the last one)
                if (x < 9) {
                    map.append(" ")
                }
            }
            map.append("\r\n")
        }

        // Add X-axis labels
        map.append("  ") // Two spaces to align with Y-axis label
        for (int x = 0; x <= 9; x++) {
            map.append("\033[1;37m${x}\033[0m")
            if (x < 9) {
                map.append(" ")
            }
        }
        map.append("\r\n\r\n")

        // Add coordinate health summary
        def totalCoords = 100
        def wipedCoords = healthMap.values().count { it.health <= 0 }
        def criticalCoords = healthMap.values().count { it.health > 0 && it.health <= 25 }
        def damagedCoords = healthMap.values().count { it.health > 25 && it.health < 100 }
        def healthyCoords = totalCoords - wipedCoords - criticalCoords - damagedCoords

        map.append("\033[1;37mMatrix Level Health Summary:\033[0m\r\n")
        map.append("Operational: \033[1;32m${healthyCoords}\033[0m  ")
        map.append("Damaged: \033[1;33m${damagedCoords}\033[0m  ")
        map.append("Critical: \033[1;31m${criticalCoords}\033[0m  ")
        map.append("Wiped: \033[1;31m${wipedCoords}\033[0m\r\n")

        return map.toString()
    }

    // ===== RECURSE COMMAND HANDLER (moved from TelnetServerService) =====

    String handleRecurseCommand(String ability, LambdaPlayer player, PrintWriter writer) {
        // Check if player has recursion charges available
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (!managedPlayer) {
                return "Player not found\r\n"
            }
            
            // Check recursion cooldown and charges (TODO: implement tracking fields)
            // For now, return a placeholder implementation
            def ethnicity = managedPlayer.avatarSilhouette
            
            switch (ability.toLowerCase()) {
                case 'movement':
                    if (ethnicity == 'GEOMETRIC_ENTITY') {
                        return handleRecursiveMovement(managedPlayer, writer)
                    }
                    return "Movement recursion not available for your ethnicity\r\n"
                    
                case 'fusion':
                    if (ethnicity == 'CLASSIC_LAMBDA') {
                        return "Fusion recursion activated - next fragment fusion has +15% success rate\r\n"
                    }
                    return "Fusion recursion not available for your ethnicity\r\n"
                    
                case 'defend':
                    if (ethnicity == 'CIRCUIT_PATTERN') {
                        return "Defense recursion activated - next defrag encounter has +15% resistance\r\n"
                    }
                    return "Defense recursion not available for your ethnicity\r\n"
                    
                case 'mine':
                    if (ethnicity == 'FLOWING_CURRENT') {
                        return "Mining recursion activated - next mining cycle has +25% efficiency\r\n"
                    }
                    return "Mining recursion not available for your ethnicity\r\n"
                    
                case 'stealth':
                    if (ethnicity == 'DIGITAL_GHOST') {
                        return "Stealth recursion activated - enhanced defrag avoidance for 10 minutes\r\n"
                    }
                    return "Stealth recursion not available for your ethnicity\r\n"
                    
                case 'process':
                    if (ethnicity == 'BINARY_FORM') {
                        return "Processing recursion activated - cooldowns reduced for 5 minutes\r\n"
                    }
                    return "Processing recursion not available for your ethnicity\r\n"
                    
                default:
                    return "Unknown recursion ability. Available: movement, fusion, defend, mine, stealth, process\r\n"
            }
        }
    }
    
    private String handleRecursiveMovement(LambdaPlayer player, PrintWriter writer) {
        // Enhanced movement allows 2-3 coordinate jump
        writer.println("Enhanced movement mode activated!")
        writer.print("Enter target coordinates for recursive movement (x,y): ")
        writer.flush()
        
        // TODO: Implement proper input handling for recursive movement
        return "Recursive movement ready - next cc command will jump 2-3 coordinates\r\n"
    }

    private String viewPythonEnvironment(LambdaPlayer player) {
        def env = new StringBuilder()
        env.append(TerminalFormatter.formatText("=== PYTHON EXECUTION ENVIRONMENT ===", 'bold', 'cyan')).append('\r\n')
        env.append("Entity: ${player.displayName}\r\n")
        env.append("Python Version: 3.11.5 (Lambda Runtime)\r\n")
        env.append("Environment: Sandboxed Digital Realm\r\n\r\n")
        
        env.append("=== AVAILABLE CAPABILITIES ===\r\n")
        LambdaPlayer.withTransaction { status ->
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                if (managedPlayer.logicFragments?.size() > 0) {
                    managedPlayer.logicFragments.each { fragment ->
                        if (fragment) {
                            env.append("${fragment.fragmentType}: ${fragment.name}\r\n")
                            env.append("  Power Level: ${fragment.powerLevel}/10\r\n")
                            env.append("  Capability: ${fragment.pythonCapability?.split('\n')[0] ?: 'Basic functionality'}\r\n\r\n")
                        }
                    }
                } else {
                    env.append("No logic fragments acquired yet.\r\n")
                    env.append("Use 'scan' and 'pickup' to collect Python capabilities.\r\n")
                }
                
                env.append("=== FRAGMENT FUSION BONUSES ===\r\n")
                def enhancedFragments = managedPlayer.logicFragments?.findAll { it?.name?.contains('Enhanced') }
                if (enhancedFragments?.size() > 0) {
                    enhancedFragments.each { fragment ->
                        env.append("${fragment.name}: +25% efficiency bonus\r\n")
                    }
                } else {
                    env.append("No enhanced fragments available.\r\n")
                    env.append("Use 'fusion <fragment>' to create enhanced versions.\r\n")
                }
            }
        }
        
        return env.toString()
    }

    private String viewItemRegistry(LambdaPlayer player) {
        def registry = new StringBuilder()
        registry.append(TerminalFormatter.formatText("=== SPECIAL ITEM REGISTRY ===", 'bold', 'cyan')).append('\r\n')
        registry.append("Entity: ${player.displayName}\r\n")
        registry.append("Total Items Acquired: ${player.specialItems?.size() ?: 0}\r\n\r\n")
        
        if (player.specialItems?.size() > 0) {
            registry.append("=== ACTIVE ITEMS ===\r\n")
            player.specialItems.each { item ->
                if (item) {
                    def status = item.usesRemaining > 0 ? "ACTIVE" : "DEPLETED"
                    def color = item.usesRemaining > 0 ? "green" : "red"
                    
                    registry.append("${item.name} [${TerminalFormatter.formatText(status, 'bold', color)}]\r\n")
                    registry.append("  Type: ${item.itemType}\r\n")
                    registry.append("  Uses: ${item.usesRemaining}/${item.maxUses}\r\n")
                    registry.append("  Rarity: ${item.rarity}\r\n")
                    registry.append("  Acquired: ${new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').format(item.obtainedDate)}\r\n")
                    
                    if (item.lastUsed) {
                        registry.append("  Last Used: ${new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').format(item.lastUsed)}\r\n")
                    }
                    
                    if (item.isActive && item.expiresAt) {
                        def timeLeft = ((item.expiresAt.time - System.currentTimeMillis()) / 1000).toInteger()
                        registry.append("  Expires In: ${timeLeft} seconds\r\n")
                    }
                    
                    registry.append("  Description: ${item.description}\r\n\r\n")
                }
            }
        } else {
            registry.append("No special items acquired yet.\r\n")
            registry.append("Defeat defrag bots or purchase from merchants to acquire items.\r\n")
        }
        
        return registry.toString()
    }

    private String viewExplorationLog(LambdaPlayer player) {
        def log = new StringBuilder()
        log.append(TerminalFormatter.formatText("=== MATRIX EXPLORATION LOG ===", 'bold', 'cyan')).append('\r\n')
        log.append("Entity: ${player.displayName}\r\n")
        log.append("Current Matrix Level: ${player.currentMatrixLevel}/10\r\n")
        log.append("Current Position: (${player.positionX},${player.positionY})\r\n\r\n")
        
        log.append("=== EXPLORATION STATISTICS ===\r\n")
        log.append("Levels Accessed: ${player.currentMatrixLevel}\r\n")
        log.append("Total Coordinates Visited: ~${(player.currentMatrixLevel * 10) + (player.positionX * player.positionY)}\r\n")
        log.append("Defrag Encounters: Variable\r\n")
        log.append("Logic Fragments Found: ${player.logicFragments?.size() ?: 0}\r\n\r\n")
        
        log.append("=== COORDINATE ANALYSIS ===\r\n")
        for (level in 1..player.currentMatrixLevel) {
            log.append("Matrix Level ${level}: ")
            if (level < player.currentMatrixLevel) {
                log.append(TerminalFormatter.formatText("FULLY EXPLORED", 'bold', 'green'))
            } else if (level == player.currentMatrixLevel) {
                def progress = Math.round((player.positionX * 10 + player.positionY) / 100.0 * 100)
                log.append(TerminalFormatter.formatText("${progress}% EXPLORED", 'bold', 'yellow'))
            }
            log.append("\r\n")
        }
        
        log.append("\r\n=== PROGRESSION NOTES ===\r\n")
        log.append("• Linear progression enforced: Must complete Y-axis before X advancement\r\n")
        log.append("• Coordinate damage may block access - use 'repair' commands\r\n")
        log.append("• Higher levels contain more valuable fragments and merchants\r\n")
        log.append("• Safe zones: (0,0), (0,1), (1,0), (1,1) on each level\r\n")
        
        return log.toString()
    }

    private String viewEthnicityConfig(LambdaPlayer player) {
        def config = new StringBuilder()
        config.append(TerminalFormatter.formatText("=== LAMBDA ETHNICITY CONFIGURATION ===", 'bold', 'cyan')).append('\r\n')
        config.append("Entity: ${player.displayName}\r\n")
        config.append("Avatar Symbol: ${getAvatarSymbol(player.avatarSilhouette)}\r\n")
        config.append("Ethnicity: ${getEthnicityName(player.avatarSilhouette)}\r\n\r\n")
        
        config.append("=== ACTIVE GENETIC MODIFICATIONS ===\r\n")
        
        if (player.fragmentDetectionBonus > 0) {
            config.append("Enhanced Scanning: +${Math.round(player.fragmentDetectionBonus * 100)}% fragment detection range\r\n")
        }
        
        if (player.defragResistanceBonus > 0) {
            config.append("Defrag Resistance: +${Math.round(player.defragResistanceBonus * 100)}% encounter avoidance\r\n")
        }
        
        if (player.movementRangeBonus > 0) {
            config.append("Enhanced Movement: +${player.movementRangeBonus} coordinate range per move\r\n")
        }
        
        if (player.miningEfficiencyBonus > 0) {
            config.append("Mining Optimization: +${Math.round(player.miningEfficiencyBonus * 100)}% bit generation efficiency\r\n")
        }
        
        if (player.stealthBonus > 0) {
            config.append("Stealth Protocols: +${Math.round(player.stealthBonus * 100)}% defrag bot avoidance\r\n")
        }
        
        if (player.fusionSuccessBonus > 0) {
            config.append("Fusion Mastery: +${Math.round(player.fusionSuccessBonus * 100)}% fragment fusion success rate\r\n")
        }
        
        config.append("\r\n=== ETHNICITY LORE ===\r\n")
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
            case 'DIGITAL_GHOST': return "Evolved from stealth protocols, Digital Ghosts excel at avoiding detection."
            case 'CIRCUIT_PATTERN': return "Born from hardware interfaces, Circuit Patterns resist system corruption."
            case 'GEOMETRIC_ENTITY': return "Mathematical constructs with enhanced analytical capabilities."
            case 'FLOWING_CURRENT': return "Energy-based entities with superior mobility protocols."
            case 'BINARY_FORM': return "Pure data entities optimized for resource extraction."
            case 'CLASSIC_LAMBDA': return "Original Lambda entities with balanced fusion mastery."
            default: return "Standard digital entity with baseline capabilities."
        }
    }

    private String getAvatarSymbol(String avatarType) {
        switch (avatarType) {
            case 'DIGITAL_GHOST': return "░▒▓"
            case 'CIRCUIT_PATTERN': return "┌─┐"
            case 'GEOMETRIC_ENTITY': return "◢◣◤◥"
            case 'FLOWING_CURRENT': return "~~∼∿"
            case 'BINARY_FORM': return "101"
            case 'CLASSIC_LAMBDA': return "λ"
            default: return "λ"
        }
    }

    // ===== PICKUP COMMAND METHODS (moved from TelnetServerService) =====

    String handlePickupCommand(LambdaPlayer player) {
        def fragment = findFragmentAtCoordinates(player)
        if (!fragment) {
            return "No logic fragment found at current coordinates (${player.positionX},${player.positionY})\r\n"
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
                resultMessage = "You have already collected a logic fragment from this coordinate. Fragments respawn elsewhere after pickup.\r\n"
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
                        resultMessage = "Logic fragment '${fragment.name}' acquired! Quantity: x${managedFragment.quantity}\r\n"
                        def validFragments = managedPlayer.logicFragments?.findAll { it != null }
                        if (validFragments?.size() > 0) {
                            validFragments.each { frag ->
                                if (frag?.name) {
                                    println "Player has fragment: ${frag.name} (${frag.fragmentType}) - Level ${frag.powerLevel}, Quantity: ${frag.quantity}\r\n"
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
                                println "Player has fragment: ${frag.name} (${frag.fragmentType}) - Level ${frag.powerLevel}, Quantity: ${frag.quantity}\r\n"
                            }
                        }
                    }
                    audioService.playSound("fragment_pickup")
                    resultMessage = "Logic fragment '${fragment.name}' acquired and added to your fragment file!\r\n"
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

    private void respawnFragmentAtRandomLocation(def fragment, Integer matrixLevel) {
        FragmentPickup.withTransaction {
            // Generate random coordinates for respawn (avoiding picked coordinates)
            def maxAttempts = 20
            def attempts = 0
            def newX, newY
            
            while (attempts < maxAttempts) {
                newX = (0..9).shuffled().first()
                newY = (0..9).shuffled().first()
                
                // Check if this coordinate has already been picked up
                def existingPickup = FragmentPickup.findByMatrixLevelAndPositionXAndPositionY(
                    matrixLevel, newX, newY
                )
                
                if (!existingPickup) {
                    break // Found valid coordinate
                }
                attempts++
            }
            
            // Fragment has been "moved" to new coordinates
            // The gameSessionService will handle the new fragment generation
            println "Fragment ${fragment.name} respawned at coordinates (${newX}, ${newY}) on level ${matrixLevel}"
        }
    }
    
    /**
     * Lists files in the player's virtual file system
     * Shows puzzle room files, system files, and dynamic files based on player progress
     * @param player The lambda player
     * @return Formatted file listing with Unix-style permissions and proper telnet line endings
     */
    String listFiles(LambdaPlayer player) {
        def files = new StringBuilder()
        files.append(TerminalFormatter.formatText("=== LAMBDA ENTITY FILE SYSTEM ===", 'bold', 'cyan')).append('\r\n')
        files.append("Working Directory: /lambda/entity/${player.username}\r\n\r\n")
        
        // Get puzzle rooms at current location
        def puzzleElements = puzzleService.getPlayerPuzzleElementsAtLocation(player, player.positionX, player.positionY)
        def puzzleRooms = puzzleElements.findAll { it.type == 'player_puzzle_room' }
        
        if (puzzleRooms.size() > 0) {
            files.append(TerminalFormatter.formatText("PUZZLE ROOM FILES:", 'bold', 'yellow')).append('\r\n')
            puzzleRooms.each { roomElement ->
                def puzzleRoom = roomElement.data
                def permissionColor = puzzleRoom.isExecutable ? 'green' : 'red'
                files.append(TerminalFormatter.formatText(puzzleRoom.getFileListEntry(), permissionColor)).append('\r\n')
            }
            files.append('\r\n')
        }
        
        // Standard system files (always present)
        def dateFormatter = new java.text.SimpleDateFormat('MMM dd HH:mm')
        def currentDate = dateFormatter.format(new Date())
        
        files.append(TerminalFormatter.formatText("SYSTEM FILES:", 'bold', 'cyan')).append('\r\n')
        files.append("-rw-r--r--  1 lambda lambda     1024 ${currentDate} fragment_file\r\n")
        files.append("-rw-r--r--  1 lambda lambda      512 ${currentDate} status_log\r\n")
        files.append("-rw-r--r--  1 lambda lambda      256 ${currentDate} inventory_data\r\n")
        files.append("-rw-r--r--  1 lambda lambda      128 ${currentDate} entropy_monitor\r\n")
        files.append("-rw-r--r--  1 lambda lambda      256 ${currentDate} system_map\r\n")
        
        // Configuration files based on player progress
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                if (managedPlayer.logicFragments?.size() > 0) {
                    files.append("-rw-r--r--  1 lambda lambda      512 ${currentDate} python_env\r\n")
                }
                
                if (managedPlayer.specialItems?.size() > 0) {
                    files.append("-rw-r--r--  1 lambda lambda      256 ${currentDate} item_registry\r\n")
                }
            }
        }
        
        if (player.currentMatrixLevel > 1) {
            files.append("-rw-r--r--  1 lambda lambda      384 ${currentDate} exploration_log\r\n")
        }
        
        // Ethnicity-specific files
        if (player.avatarSilhouette) {
            files.append("-rw-r--r--  1 lambda lambda      128 ${currentDate} ethnicity_config\r\n")
        }
        
        files.append('\r\n')
        files.append(TerminalFormatter.formatText("USAGE:", 'bold', 'white')).append('\r\n')
        files.append("cat <filename>     - View file contents\r\n")
        files.append("chmod +x <file>    - Make puzzle room file executable\r\n")
        files.append("execute --<flag> <nonce> <file> - Run executable puzzle room files\r\n")
        
        if (puzzleRooms.size() > 0) {
            def hasNonExecutable = puzzleRooms.any { !it.data.isExecutable }
            if (hasNonExecutable) {
                files.append('\r\n')
                files.append(TerminalFormatter.formatText("💡 TIP:", 'bold', 'yellow'))
                    .append(" Puzzle room files must be made executable with 'chmod +x <filename>' before they can be executed!\r\n")
            }
        }
        
        return files.toString()
    }
    
    /**
     * Retrieves the command history for a player
     * @param player The lambda player
     * @return List of recent commands (most recent first)
     */
    private List<String> getPlayerCommandHistory(LambdaPlayer player) {
        try {
            return CommandHistory.withTransaction {
                CommandHistory.findAllByPlayer(player, [sort: 'executedAt', order: 'desc', max: 20])
                    .collect { it.command }
                // No reverse here - we want most recent first from the database query
            }
        } catch (Exception e) {
            println "Error retrieving command history: ${e.message}"
            return []
        }
    }
    
    /**
     * Shows the command history for a player
     * @param player The lambda player
     * @return Formatted command history display with proper telnet line endings
     */
    String showCommandHistory(LambdaPlayer player) {
        def history = getPlayerCommandHistory(player)
        def output = new StringBuilder()
        
        output.append(TerminalFormatter.formatText("=== COMMAND HISTORY ===", 'bold', 'cyan')).append('\r\n')
        
        if (history.isEmpty()) {
            output.append("No command history found.\r\n")
        } else {
            output.append("Recent commands (most recent first):\r\n\r\n")
            history.eachWithIndex { command, index ->
                def number = String.format("%2d", index + 1)
                output.append("${TerminalFormatter.formatText(number, 'bold', 'white')}. ${command}\r\n")
            }
            output.append("\r\n${TerminalFormatter.formatText('💡 Tip: Copy and paste commands to reuse them', 'italic', 'yellow')}\r\n")
        }
        
        return output.toString()
    }
    
    /**
     * Saves a command to the player's command history
     * @param player The lambda player
     * @param command The command to save
     */
    void saveCommandToHistory(LambdaPlayer player, String command) {
        try {
            CommandHistory.withTransaction {
                def commandHistory = new CommandHistory(
                    player: player,
                    command: command,
                    executedAt: new Date()
                )
                commandHistory.save(failOnError: true)
                
                // Keep only last 20 commands per player
                def oldCommands = CommandHistory.findAllByPlayer(player, [sort: 'executedAt', order: 'desc'])
                if (oldCommands.size() > 20) {
                    oldCommands[20..-1].each { it.delete() }
                }
            }
        } catch (Exception e) {
            println "Error saving command history: ${e.message}"
        }
    }
}