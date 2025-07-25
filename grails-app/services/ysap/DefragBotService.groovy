package ysap

import grails.gorm.transactions.Transactional

@Transactional
class DefragBotService {
    
    def coordinateStateService

    def spawnDefragBot(Integer matrixLevel, Integer sector, Integer positionX, Integer positionY) {
        // Don't spawn defrag bots in early safe zones or chat areas
        if (positionX <= 1 && positionY <= 1) {
            return null
        }
        
        def botId = "DF${System.currentTimeMillis().toString().takeRight(6)}"
        def processId = (1000..9999).shuffled().first()
        
        // Calculate difficulty based on floor number (X coordinate)
        def floorNumber = positionX
        def difficulty
        switch (floorNumber) {
            case 0:
            case 1:
                difficulty = 1 // Very easy on floors 0-1
                break
            case 2:
                difficulty = 2 // Easy on floor 2  
                break
            case 3:
            case 4:
                difficulty = 3 // Normal on floors 3-4
                break
            case 5:
            case 6:
                difficulty = Math.min(6, 4 + (floorNumber - 5)) // Challenging 4-5
                break
            case 7:
            case 8:
                difficulty = Math.min(8, 6 + (floorNumber - 7)) // Hard 6-7
                break
            default:
                difficulty = Math.min(10, 8 + (floorNumber - 9)) // Extreme 8-10
                break
        }
        
        def bot = new DefragBot(
            matrixLevel: matrixLevel,
            sector: sector,
            positionX: positionX,
            positionY: positionY,
            botId: botId,
            difficultyLevel: difficulty,
            timeLimit: 60 + (difficulty * 15), // More time for harder bots
            processId: processId,
            lastBitDrain: new Date()
        )
        
        bot.flags = bot.generateRandomFlags()
        bot.fileContent = bot.generateFileContent()
        bot.save(failOnError: true)
        
        return bot
    }
    
    def getActiveBotAt(Integer matrixLevel, Integer positionX, Integer positionY) {
        return DefragBot.findByMatrixLevelAndPositionXAndPositionYAndIsActive(
            matrixLevel, positionX, positionY, true
        )
    }
    
    def handleDefragCommand(DefragBot bot, String command, LambdaPlayer player) {
        def result = [:]
        def trimmedCommand = command.trim()
        
        // Check if 5 seconds have passed since last bit drain
        def now = new Date()
        if (now.time - bot.lastBitDrain.time >= 5000) {
            try {
                drainPlayerBits(player, bot)
                bot.lastBitDrain = now
                bot.save(failOnError: true)
            } catch (Exception e) {
                println "Error draining bits: ${e.message}"
                // Reset bit drain timer to avoid repeated failures
                bot.lastBitDrain = now
                bot.save(failOnError: true)
            }
        }
        
        if (trimmedCommand.equalsIgnoreCase("defrag -h") || trimmedCommand.equalsIgnoreCase("defrag --help")) {
            result.success = false
            result.output = bot.getGrepInstructions()
            result.action = "help"
            return result
        }
        
        // Handle file system commands - validate bot ID
        if (trimmedCommand.startsWith("cat /proc/defrag/")) {
            def requestedBotId = trimmedCommand.replace("cat /proc/defrag/", "").trim()
            if (requestedBotId == bot.botId) {
                result.success = false
                result.output = bot.fileContent
                result.action = "file_view"
                return result
            } else {
                result.success = false
                result.output = "cat: /proc/defrag/${requestedBotId}: No such file or directory"
                result.action = "file_error"
                return result
            }
        }
        
        // Handle grep commands
        if (trimmedCommand.startsWith("grep")) {
            return handleGrepCommand(bot, trimmedCommand, player)
        }
        
        // Handle kill commands (only if PID has been properly acquired)
        if (trimmedCommand.startsWith("kill")) {
            def hasAcquiredPid = false
            LambdaPlayer.withTransaction {
                def managedPlayer = LambdaPlayer.get(player.id)
                hasAcquiredPid = managedPlayer?.hasAcquiredPid ?: false
            }
            
            if (hasAcquiredPid) {
                return handleKillCommand(bot, trimmedCommand, player)
            } else {
                def pidResult = [:]
                pidResult.success = false
                pidResult.output = "PID not acquired. Use grep with proper regex to find the PID first."
                pidResult.action = "failed"
                return pidResult
            }
        }
        
        // Invalid command - bot gets stronger or player takes damage
        result.success = false
        result.output = "Invalid command. Defrag process continues..."
        result.action = "failed"
        
        return result
    }
    
    private def handleGrepCommand(DefragBot bot, String command, LambdaPlayer player) {
        def result = [:]
        def grepOutput = executeGrepCommand(bot, command)
        
        result.success = false
        result.action = "grep"
        result.output = grepOutput
        
        // Check if the grep output contains ONLY the PID (no other text)
        def cleanOutput = grepOutput.trim()
        def outputLines = cleanOutput.split('\n').findAll { it.trim() }
        
        // Perfect match: single line containing only the PID
        if (outputLines.size() == 1 && outputLines[0].trim() == bot.processId.toString()) {
            result.output = "✅ Defrag PID acquired: ${bot.processId}"
            result.pidAcquired = true
            // Set flag on player that they have acquired the PID
            LambdaPlayer.withTransaction {
                def managedPlayer = LambdaPlayer.get(player.id)
                if (managedPlayer) {
                    managedPlayer.hasAcquiredPid = true
                    managedPlayer.save(failOnError: true)
                }
            }
        } else if (cleanOutput.contains(bot.processId.toString())) {
            // Contains PID but not isolated - give helpful hint
            result.output = "${grepOutput}\n\n💡 PID found but not isolated. Try using -o flag with a more specific pattern."
        }
        
        return result
    }
    
    private def handleKillCommand(DefragBot bot, String command, LambdaPlayer player) {
        def result = [:]
        
        if (command.contains(bot.processId.toString())) {
            // Player successfully killed the defrag bot
            result.success = true
            result.output = "Process ${bot.processId} terminated successfully."
            result.action = "killed"
            result.rewards = generateRewards(bot.difficultyLevel, bot)
            
            // Reset player's PID acquisition flag and deactivate bot
            LambdaPlayer.withTransaction {
                def managedPlayer = LambdaPlayer.get(player.id)
                if (managedPlayer) {
                    managedPlayer.hasAcquiredPid = false
                    managedPlayer.save(failOnError: true)
                }
            }
            
            bot.isActive = false
            bot.save(failOnError: true)
            
            return result
        }
        
        result.success = false
        result.output = "Invalid kill command. Process continues..."
        result.action = "failed"
        
        return result
    }
    
    private String executeGrepCommand(DefragBot bot, String command) {
        def fileContent = bot.fileContent
        
        // Handle pipes in grep commands
        def pipedCommands = command.split('\\|')
        def currentOutput = fileContent
        
        for (int i = 0; i < pipedCommands.size(); i++) {
            def pipeCommand = pipedCommands[i].trim()
            
            if (pipeCommand.startsWith('grep')) {
                currentOutput = executeGrepOnContent(currentOutput, pipeCommand)
            } else if (pipeCommand.startsWith('head')) {
                currentOutput = executeHeadOnContent(currentOutput, pipeCommand)
            } else if (pipeCommand.startsWith('tail')) {
                currentOutput = executeTailOnContent(currentOutput, pipeCommand)
            } else if (pipeCommand.startsWith('uniq')) {
                currentOutput = executeUniqOnContent(currentOutput)
            }
        }
        
        return currentOutput
    }
    
    private String executeGrepOnContent(String content, String command) {
        def lines = content.split('\n')
        
        // Parse the grep command properly
        def grepInfo = parseGrepCommand(command)
        if (!grepInfo.pattern) {
            return "grep: invalid pattern"
        }
        
        def results = []
        def pattern = grepInfo.pattern
        
        // Handle different grep flags
        if (grepInfo.flags.contains('o')) {
            // -o flag: only show matching parts
            lines.each { line ->
                try {
                    def matcher = line =~ pattern
                    for (int i = 0; i < matcher.count; i++) {
                        def match = matcher[i]
                        if (match instanceof String) {
                            results.add(match)
                        } else if (match instanceof List && match.size() > 0) {
                            results.add(match[0].toString())
                        } else {
                            // Convert complex objects to string properly
                            results.add(match.toString())
                        }
                    }
                } catch (Exception e) {
                    // Ignore regex errors
                }
            }
        } else {
            // Default: show entire lines that match
            lines.each { line ->
                try {
                    if (line =~ pattern) {
                        results.add(line)
                    }
                } catch (Exception e) {
                    // Ignore regex errors
                }
            }
        }
        
        return results.join('\n')
    }
    
    private String executeHeadOnContent(String content, String command) {
        def lines = content.split('\n')
        def parts = command.split(' ')
        def lineCount = 1
        
        if (parts.size() > 1) {
            try {
                if (parts[1].startsWith('-')) {
                    lineCount = Integer.parseInt(parts[1].substring(1))
                } else {
                    lineCount = Integer.parseInt(parts[1])
                }
            } catch (NumberFormatException e) {
                lineCount = 1
            }
        }
        
        def result = []
        for (int i = 0; i < Math.min(lineCount, lines.size()); i++) {
            result.add(lines[i])
        }
        
        return result.join('\n')
    }
    
    private String executeTailOnContent(String content, String command) {
        def lines = content.split('\n')
        def parts = command.split(' ')
        def lineCount = 1
        
        if (parts.size() > 1) {
            try {
                if (parts[1].startsWith('-')) {
                    lineCount = Integer.parseInt(parts[1].substring(1))
                } else {
                    lineCount = Integer.parseInt(parts[1])
                }
            } catch (NumberFormatException e) {
                lineCount = 1
            }
        }
        
        def result = []
        int startIndex = Math.max(0, lines.size() - lineCount)
        for (int i = startIndex; i < lines.size(); i++) {
            result.add(lines[i])
        }
        
        return result.join('\n')
    }
    
    private String executeUniqOnContent(String content) {
        def lines = content.split('\n')
        def result = []
        def lastLine = null
        
        lines.each { line ->
            if (line != lastLine) {
                result.add(line)
                lastLine = line
            }
        }
        
        return result.join('\n')
    }
    
    private Map parseGrepCommand(String command) {
        def result = [pattern: null, flags: [], file: null]
        
        // Split command but preserve quoted strings
        def parts = []
        def current = ""
        def inQuotes = false
        def quoteChar = null
        
        for (int i = 0; i < command.length(); i++) {
            def ch = command[i]
            
            if (!inQuotes && (ch == '"' || ch == "'")) {
                inQuotes = true
                quoteChar = ch
                current += ch
            } else if (inQuotes && ch == quoteChar) {
                inQuotes = false
                quoteChar = null
                current += ch
            } else if (!inQuotes && ch == ' ') {
                if (current.trim()) {
                    parts.add(current.trim())
                    current = ""
                }
            } else {
                current += ch
            }
        }
        
        if (current.trim()) {
            parts.add(current.trim())
        }
        
        // Parse the parts
        def expectingPattern = false
        for (int i = 0; i < parts.size(); i++) {
            def part = parts[i]
            
            if (part == "grep") {
                expectingPattern = true
                continue
            }
            
            if (expectingPattern) {
                if (part.startsWith("-")) {
                    // Handle flags
                    def flags = part.substring(1)
                    flags.each { flag ->
                        result.flags.add(flag)
                    }
                } else {
                    // This is our pattern
                    result.pattern = part
                    // Remove quotes if present
                    if ((result.pattern.startsWith('"') && result.pattern.endsWith('"')) ||
                        (result.pattern.startsWith("'") && result.pattern.endsWith("'"))) {
                        result.pattern = result.pattern[1..-2]
                    }
                    expectingPattern = false
                }
            } else if (!result.file) {
                // This should be the file path
                result.file = part
            }
        }
        
        return result
    }
    
    private def drainPlayerBits(LambdaPlayer player, DefragBot bot) {
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                def bitsToRemove = Math.min(5, managedPlayer.bits)
                if (bitsToRemove > 0) {
                    managedPlayer.bits -= bitsToRemove
                    managedPlayer.save(failOnError: true)
                    
                    // Check if player has no bits left
                    if (managedPlayer.bits <= 0) {
                        defragPlayer(managedPlayer, bot)
                    }
                }
            }
        }
    }
    
    private def defragPlayer(LambdaPlayer player, DefragBot bot) {
        // Reset player position to (0,0)
        player.positionX = 0
        player.positionY = 0
        player.bits = 10  // Reset to only 10 bits
        player.hasAcquiredPid = false
        
        // Steal a random logic fragment (but never Basic Print)
        def fragments = player.logicFragments?.findAll { 
            it.name != 'Basic Print' 
        }
        
        if (fragments && fragments.size() > 0) {
            def randomFragment = fragments[Math.random() * fragments.size()]
            
            // Store fragment in defrag bot
            bot.hasStolenFragment = true
            bot.stolenFragmentName = randomFragment.name
            bot.stolenFragmentType = randomFragment.fragmentType
            bot.stolenFragmentPowerLevel = randomFragment.powerLevel
            bot.save(failOnError: true)
            
            // Remove fragment from player
            player.removeFromLogicFragments(randomFragment)
            randomFragment.delete()
        }
        
        player.save(failOnError: true)
    }
    
    def defragTimerExpired(DefragBot bot, LambdaPlayer player) {
        // Player failed to stop defrag in time
        def result = [:]
        result.success = false
        result.output = "Defrag process completed. System buffer cleared."
        result.action = "timeout"
        result.penalty = calculatePenalty(player)
        
        bot.isActive = false
        bot.save(failOnError: true)
        
        return result
    }
    
    private Map generateRewards(Integer difficulty, DefragBot bot) {
        def rewards = [:]
        
        // Bits reward based on difficulty
        rewards.bits = 10 + (difficulty * 5) + (Math.random() * 20).toInteger()
        
        // Check if this bot has a stolen fragment to return
        if (bot.hasStolenFragment) {
            rewards.stolenFragment = [
                name: bot.stolenFragmentName,
                type: bot.stolenFragmentType,
                powerLevel: bot.stolenFragmentPowerLevel
            ]
        }
        
        // Chance for special items (higher difficulty = better chance)
        def itemChance = Math.random()
        if (itemChance < (0.1 + difficulty * 0.05)) {
            rewards.specialItem = generateRandomSpecialItem()
        }
        
        // TEMPORARY: Force special item drop for testing
        rewards.specialItem = generateRandomSpecialItem()
        
        
        // Rare chance for logic fragments (very rare)
        def fragmentChance = Math.random()
        if (fragmentChance < (0.02 + difficulty * 0.01)) {
            rewards.logicFragment = generateRandomLogicFragment()
        }
        
        // Chance for puzzle logic fragments (higher difficulty bots)
        def puzzleFragmentChance = Math.random()
        if (difficulty >= 6 && puzzleFragmentChance < (0.15 + difficulty * 0.03)) {
            rewards.puzzleFragment = generateRandomPuzzleFragment()
        }
        
        // Very rare chance for elemental nonces (epic difficulty only)
        def nonceChance = Math.random()
        if (difficulty >= 8 && nonceChance < 0.05) {
            rewards.elementalNonce = generateRandomNonce()
        }
        
        return rewards
    }
    
    private String generateRandomSpecialItem() {
        def items = [
            'RESPAWN_CACHE', 'SWAP_SPACE', 'BIT_MULTIPLIER', 'SCANNER_BOOST', 'STEALTH_CLOAK',
            'DEFRAG_DETECTOR', 'LOGIC_AMPLIFIER', 'MATRIX_MAPPER', 'ENTROPY_STABILIZER', 'FRAGMENT_MAGNET'
        ]
        return items[Math.random() * items.size()]
    }
    
    private String generateRandomLogicFragment() {
        def fragments = ['CONDITIONAL', 'LOOP', 'FUNCTION', 'CLASS', 'EXCEPTION_HANDLING']
        return fragments[Math.random() * fragments.size()]
    }
    
    private String generateRandomPuzzleFragment() {
        def fragments = [
            'Atmospheric Processor',
            'Thermal Signature Decoder', 
            'Geological Survey Tool',
            'Hydro-Chemical Validator'
        ]
        return fragments[Math.random() * fragments.size()]
    }
    
    private String generateRandomNonce() {
        def nonces = [
            'ATMOSPHERIC_KEY',
            'COMBUSTION_CIPHER',
            'GEOLOGICAL_TOKEN',
            'HYDRO_SEQUENCE'
        ]
        return nonces[Math.random() * nonces.size()]
    }
    
    private Map calculatePenalty(LambdaPlayer player) {
        def penalty = [:]
        
        // Move player back to matrix level root (0,0) or previous safe position
        penalty.positionReset = true
        penalty.newX = 0
        penalty.newY = 0
        
        // Logic fragment loss (but never the last print statement)
        if (player.logicFragments && player.logicFragments.size() > 1) {
            penalty.fragmentLoss = true
            penalty.fragmentToLose = player.logicFragments.find { it.fragmentType != 'FUNCTION' || it.name != 'Basic Print' }
        }
        
        return penalty
    }
    
    def cleanupExpiredBots() {
        def expiredBots = DefragBot.createCriteria().list {
            eq 'isActive', true
            lt 'spawnedDate', new Date(System.currentTimeMillis() - (30 * 60 * 1000)) // 30 minutes
        }
        
        expiredBots.each { bot ->
            bot.isActive = false
            bot.save(failOnError: true)
        }
        
        return expiredBots.size()
    }
    
    def processDefragBotCoordinateDamage() {
        // Find all active defrag bots and damage their coordinates over time
        def activeBots = DefragBot.findAllByIsActive(true)
        def damageCount = 0
        
        activeBots.each { bot ->
            // Random chance for bot to damage its current coordinate (20% per check)
            if (Math.random() < 0.2) {
                def damage = 5 + (bot.difficultyLevel * 2) // Higher difficulty = more damage
                coordinateStateService.damageCoordinate(bot.matrixLevel, bot.positionX, bot.positionY, damage)
                damageCount++
                
                // Occasionally, defrag bots move to nearby coordinates and damage them too
                if (Math.random() < 0.1) {
                    def newX = Math.max(0, Math.min(9, bot.positionX + [-1, 0, 1].shuffled().first()))
                    def newY = Math.max(0, Math.min(9, bot.positionY + [-1, 0, 1].shuffled().first()))
                    
                    // Don't damage safe zones
                    if (!(newX <= 1 && newY <= 1)) {
                        coordinateStateService.damageCoordinate(bot.matrixLevel, newX, newY, damage)
                        
                        // Update bot position
                        bot.positionX = newX
                        bot.positionY = newY
                        bot.save(failOnError: true)
                        damageCount++
                    }
                }
            }
        }
        
        return damageCount
    }
    
    def spawnRoamingDefragBots() {
        // Periodically spawn roaming defrag bots that move around and damage coordinates
        // This creates the progressive damage system
        
        def spawnChance = 0.15 // 15% chance per call
        if (Math.random() > spawnChance) {
            return 0
        }
        
        def spawnedCount = 0
        
        // Spawn bots on random matrix levels
        for (matrixLevel in 1..10) {
            // Higher levels get more aggressive defrag activity
            def maxBots = (matrixLevel / 3).intValue() + 1
            def currentBots = DefragBot.countByMatrixLevelAndIsActive(matrixLevel, true)
            
            if (currentBots < maxBots) {
                // Find random coordinates that aren't safe zones
                def attempts = 0
                while (attempts < 10) {
                    def x = (0..9).shuffled().first()
                    def y = (0..9).shuffled().first()
                    
                    if (!(x <= 1 && y <= 1)) { // Not in safe zone
                        def existingBot = DefragBot.findByMatrixLevelAndPositionXAndPositionYAndIsActive(matrixLevel, x, y, true)
                        if (!existingBot) {
                            spawnDefragBot(matrixLevel, 1, x, y)
                            spawnedCount++
                            break
                        }
                    }
                    attempts++
                }
            }
        }
        
        return spawnedCount
    }
}