package ysap

import grails.gorm.transactions.Transactional
import ysap.TerminalFormatter

@Transactional
class DefragBotService {
    
    def coordinateStateService
    def audioService
    def lambdaPlayerService
    def specialItemService
    def puzzleService

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
                pidResult.output = "PID not acquired. Use grep with proper regex to find the PID first.\r\n"
                pidResult.action = "failed"
                return pidResult
            }
        }
        
        // Invalid command - bot gets stronger or player takes damage
        result.success = false
        result.output = "Invalid command. Defrag process continues...\r\n"
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

    def handleDefragCommandFromTelnet(String command, LambdaPlayer player, PrintWriter writer) {
        // Check if there's a defrag bot at the player's current position
        def defragBot = null
        DefragBot.withTransaction {
            defragBot = DefragBot.findByMatrixLevelAndPositionXAndPositionY(
                player.currentMatrixLevel, player.positionX, player.positionY
            )
        }
        
        if (!defragBot) {
            return TerminalFormatter.formatText("No defrag bot present at current coordinates (${player.positionX},${player.positionY})\r\n", 'italic', 'yellow')
        }
        
        // If there is a defrag bot, start an encounter
        // Note: activeDefragSessions needs to be managed by caller
        
        def parts = command.trim().toLowerCase().split(' ')
        if (parts.length > 1 && parts[1] == '-h') {
            // Handle defrag -h help command
            def result = handleDefragCommand(defragBot, command, player)
            if (result.action == 'help') {
                def helpDisplay = new StringBuilder()
                helpDisplay.append(TerminalFormatter.formatText("⚠️  DEFRAG PROCESS ANALYSIS", 'bold', 'red')).append('\r\n')
                helpDisplay.append(result.output.replace('\\n', '\r\n')).append('\r\n')
                helpDisplay.append(TerminalFormatter.formatText("Find the kill command to terminate this process!", 'italic', 'yellow'))
                return helpDisplay.toString()
            }
        }
        
        // Regular defrag command without -h
        def encounter = new StringBuilder()
        encounter.append(TerminalFormatter.formatText("⚠️  DEFRAG BOT ENCOUNTERED!", 'bold', 'red')).append('\r\n')
        encounter.append(TerminalFormatter.formatText("System defragmentation process ${defragBot.botId} detected at (${player.positionX},${player.positionY})", 'italic', 'yellow')).append('\r\n')
        encounter.append(TerminalFormatter.formatText("Difficulty Level: ${defragBot.difficultyLevel}/10", 'bold', 'yellow')).append('\r\n')
        encounter.append(TerminalFormatter.formatText("Time limit: ${defragBot.timeLimit} seconds", 'bold', 'red')).append('\r\n')
        encounter.append(TerminalFormatter.formatText("⚠️  BITS DRAINING: -5 bits every 5 seconds!", 'bold', 'red')).append('\r\n')
        encounter.append("Type 'defrag -h' to learn how to find and terminate the process!\r\n")
        
        return encounter.toString()
    }

    def handleDefragEncounter(String command, LambdaPlayer player, PrintWriter writer, DefragBot defragBot) {
        if (!defragBot) {
            return "No active defrag encounter found."
        }
        
        def result = handleDefragCommand(defragBot, command, player)
        
        if (result.success) {
            // Player defeated the defrag bot
            audioService.playSound("defrag_victory")
            
            def response = new StringBuilder()
            response.append(TerminalFormatter.formatText("✅ DEFRAG BOT TERMINATED!", 'bold', 'green')).append('\r\n')
            response.append(result.output).append('\r\n')
            
            // Apply rewards
            if (result.rewards.bits) {
                lambdaPlayerService.addBits(player, result.rewards.bits)
                audioService.playSound("bits_earned")
                response.append(TerminalFormatter.formatText("Earned ${result.rewards.bits} bits!", 'bold', 'yellow')).append('\r\n')
                
                // Update session player object with new bits
                LambdaPlayer.withTransaction {
                    def updatedPlayer = LambdaPlayer.get(player.id)
                    if (updatedPlayer) {
                        player.bits = updatedPlayer.bits
                    }
                }
            }
            
            if (result.rewards.stolenFragment) {
                // Return stolen fragment to player
                def fragment = result.rewards.stolenFragment
                response.append(TerminalFormatter.formatText("Recovered stolen logic fragment: ${fragment.name}!", 'bold', 'cyan')).append('\r\n')
            }
            
            if (result.rewards.specialItem) {
                try {
                    println "DEBUG DefragBotService: Creating special item '${result.rewards.specialItem}' for player ${player.displayName} (ID: ${player.id})"
                    def item = specialItemService.createSpecialItem(player, result.rewards.specialItem)
                    audioService.playSound("item_found")
                    if (item) {
                        println "DEBUG DefragBotService: Item creation SUCCESS - ${item.name} (ID: ${item.id})"
                        response.append(TerminalFormatter.formatText("Found special item: ${item.name}!", 'bold', 'magenta')).append('\r\n')
                        response.append(TerminalFormatter.formatText("${item.description}", 'italic', 'white')).append('\r\n')
                    } else {
                        println "DEBUG DefragBotService: Item creation FAILED - createSpecialItem returned null"
                        response.append(TerminalFormatter.formatText("Found special item: ${result.rewards.specialItem}!", 'bold', 'magenta')).append('\r\n')
                        response.append(TerminalFormatter.formatText("⚠️ Item creation failed - please contact admin", 'italic', 'red')).append('\r\n')
                    }
                } catch (Exception e) {
                    println "DEBUG DefragBotService: Exception during item creation: ${e.class.simpleName}: ${e.message}"
                    e.printStackTrace()
                    response.append(TerminalFormatter.formatText("Found special item: ${result.rewards.specialItem}!", 'bold', 'magenta')).append('\r\n')
                    response.append(TerminalFormatter.formatText("⚠️ Item creation error - ${e.message}", 'italic', 'red')).append('\r\n')
                }
            }
            
            if (result.rewards.puzzleFragment) {
                try {
                    def puzzleResult = puzzleService.awardPuzzleFragment(player, result.rewards.puzzleFragment)
                    if (puzzleResult.success) {
                        audioService.playSound("fragment_pickup")
                        response.append(TerminalFormatter.formatText("${puzzleResult.message}!", 'bold', 'purple')).append('\r\n')
                        response.append(TerminalFormatter.formatText("Executable logic fragment acquired - use 'pinv' to view!", 'italic', 'white')).append('\r\n')
                    } else {
                        response.append(TerminalFormatter.formatText("Puzzle fragment found: ${result.rewards.puzzleFragment}!", 'bold', 'purple')).append('\r\n')
                        response.append(TerminalFormatter.formatText("⚠️ ${puzzleResult.message}", 'italic', 'red')).append('\r\n')
                    }
                } catch (Exception e) {
                    println "DEBUG DefragBotService: Exception during puzzle fragment award: ${e.message}"
                    response.append(TerminalFormatter.formatText("Puzzle fragment found: ${result.rewards.puzzleFragment}!", 'bold', 'purple')).append('\r\n')
                }
            }
            
            if (result.rewards.elementalNonce) {
                try {
                    def nonceResult = puzzleService.awardNonce(player, result.rewards.elementalNonce)
                    if (nonceResult.success) {
                        audioService.playSound("item_found")
                        response.append(TerminalFormatter.formatText("${nonceResult.message}!", 'bold', 'magenta')).append('\r\n')
                        response.append(TerminalFormatter.formatText("${nonceResult.nonce?.getChemicalHint() ?: 'Chemical signature detected!'}", 'italic', 'yellow')).append('\r\n')
                        response.append(TerminalFormatter.formatText("Use 'pinv' to view your nonce collection!", 'italic', 'white')).append('\r\n')
                    } else {
                        response.append(TerminalFormatter.formatText("Elemental nonce discovered: ${result.rewards.elementalNonce}!", 'bold', 'magenta')).append('\r\n')
                        response.append(TerminalFormatter.formatText("⚠️ ${nonceResult.message}", 'italic', 'red')).append('\r\n')
                    }
                } catch (Exception e) {
                    println "DEBUG DefragBotService: Exception during nonce award: ${e.message}"
                    response.append(TerminalFormatter.formatText("Elemental nonce discovered: ${result.rewards.elementalNonce}!", 'bold', 'magenta')).append('\r\n')
                }
            }
            
            if (result.rewards.logicFragment) {
                // Add logic fragment to player inventory
                def fragmentType = result.rewards.logicFragment
                def fragmentData = createLogicFragmentFromType(fragmentType, player)
                if (fragmentData) {
                    response.append(TerminalFormatter.formatText("Discovered logic fragment: ${fragmentData.name}!", 'bold', 'cyan')).append('\r\n')
                } else {
                    response.append(TerminalFormatter.formatText("Discovered logic fragment: ${result.rewards.logicFragment}!", 'bold', 'cyan')).append('\r\n')
                }
            }
            
            return response.toString()
            
        } else if (result.action == 'help') {
            // Show defrag help with file system instructions
            def helpDisplay = new StringBuilder()
            helpDisplay.append(TerminalFormatter.formatText("⚠️  DEFRAG PROCESS ANALYSIS", 'bold', 'red')).append('\r\n')
            helpDisplay.append(result.output.replace('\\n', '\r\n')).append('\r\n')
            return helpDisplay.toString()
            
        } else if (result.action == 'file_view') {
            // Show file content for cat command
            def fileDisplay = new StringBuilder()
            fileDisplay.append(TerminalFormatter.formatText("=== /proc/defrag/${defragBot.botId} ===", 'bold', 'cyan')).append('\r\n')
            fileDisplay.append(result.output).append('\r\n')
            return fileDisplay.toString()
            
        } else if (result.action == 'grep') {
            // Show grep output
            def grepDisplay = new StringBuilder()
            if (result.pidAcquired) {
                grepDisplay.append(TerminalFormatter.formatText(result.output, 'bold', 'green')).append('\r\n')
                grepDisplay.append(TerminalFormatter.formatText("Use 'kill -9 ${defragBot.processId}' to terminate process!\r\n", 'italic', 'yellow'))
            } else {
                grepDisplay.append(TerminalFormatter.formatText("Grep output:", 'bold', 'cyan')).append('\r\n')
                grepDisplay.append(result.output).append('\r\n')
                grepDisplay.append(TerminalFormatter.formatText("Refine your regex to isolate the PID on its own line.\r\n", 'italic', 'yellow'))
            }
            return grepDisplay.toString()
            
        } else {
            // Invalid command, timeout, or bit drain
            def errorDisplay = new StringBuilder()
            errorDisplay.append(TerminalFormatter.formatText("⚠️  ${result.output}\r\n", 'bold', 'red'))
            
            // Check if player got defragged
            LambdaPlayer.withTransaction {
                def refreshedPlayer = LambdaPlayer.get(player.id)
                if (refreshedPlayer && refreshedPlayer.bits <= 0) {
                    errorDisplay.append('\r\n').append(TerminalFormatter.formatText("💀 DEFRAGGED! Respawning at (0,0) with 10 bits...\r\n", 'bold', 'red'))
                    if (refreshedPlayer.logicFragments?.size() < player.logicFragments?.size()) {
                        errorDisplay.append('\r\n').append(TerminalFormatter.formatText("Logic fragment stolen by defrag bot!\r\n", 'bold', 'red'))
                    }
                }
            }
            
            return errorDisplay.toString()
        }
    }

    private def createLogicFragmentFromType(String fragmentType, LambdaPlayer player) {
        def fragmentDefinitions = [
                'CONDITIONAL': [name: 'Advanced Conditional', description: 'Enhanced if-else logic with nested conditions', powerLevel: 4],
                'LOOP': [name: 'Advanced Loop', description: 'Optimized iteration with break/continue support', powerLevel: 5],
                'FUNCTION': [name: 'Advanced Function', description: 'Higher-order functions with lambda support', powerLevel: 6],
                'CLASS': [name: 'Advanced Class', description: 'Inheritance and polymorphism capabilities', powerLevel: 8],
                'EXCEPTION_HANDLING': [name: 'Advanced Exception', description: 'Custom exceptions and error handling', powerLevel: 7]
        ]

        def fragmentData = fragmentDefinitions[fragmentType]
        if (!fragmentData) return null

        // Create the logic fragment and add it to player inventory
        def existingFragment = player.logicFragments?.find { 
            it.name == fragmentData.name && it.fragmentType == fragmentType 
        }
        
        if (existingFragment) {
            // Increment quantity instead of creating duplicate
            existingFragment.quantity = (existingFragment.quantity ?: 1) + 1
            existingFragment.save(failOnError: true)
            return fragmentData
        } else {
            def newFragment = new LogicFragment(
                name: fragmentData.name,
                description: fragmentData.description,
                fragmentType: fragmentType,
                powerLevel: fragmentData.powerLevel,
                pythonCapability: "# ${fragmentData.name}\n# Power Level: ${fragmentData.powerLevel}\nprint('${fragmentData.name} executed')",
                quantity: 1,
                discoveredDate: new Date()
            )
            
            LambdaPlayer.withTransaction {
                def managedPlayer = LambdaPlayer.get(player.id)
                if (managedPlayer) {
                    managedPlayer.addToLogicFragments(newFragment)
                    newFragment.save(failOnError: true)
                    managedPlayer.save(failOnError: true)
                }
            }
            
            return fragmentData
        }
    }
}