package ysap

import grails.gorm.transactions.Transactional
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

@Transactional
class EntropyService {

    def audioService

    String getEntropyColor(Double entropy) {
        def safeEntropy = entropy ?: 100.0
        if (safeEntropy >= 75) return 'green'
        if (safeEntropy >= 50) return 'yellow'
        if (safeEntropy >= 25) return 'red'
        return 'red'
    }

    def calculateEntropyDecay(LambdaPlayer player) {
        // Initialize entropy system for existing players
        initializeEntropyFields(player)
        
        def now = new Date()
        def lastRefresh = player.lastEntropyRefresh ?: player.createdDate ?: now
        def safeLastRefresh = lastRefresh ?: now
        def hoursOffline = (now.time - safeLastRefresh.time) / (1000 * 60 * 60)
        
        // Entropy decays 2% per hour offline (aggressive to encourage daily login)
        def decayRate = 2.0
        def currentEntropy = (player.entropy ?: 100.0) as Double
        def safeCurrentEntropy = currentEntropy ?: 100.0
        def safeHoursOffline = Math.max(0.0, (hoursOffline ?: 0.0) as Double)
        def entropyLoss = Math.min(safeHoursOffline * decayRate, safeCurrentEntropy)
        def finalEntropy = Math.max(0.0, safeCurrentEntropy - entropyLoss)
        
        return [
            currentEntropy: finalEntropy,
            hoursOffline: safeHoursOffline.round(1),
            entropyLoss: entropyLoss.round(1)
        ]
    }
    
    def initializeEntropyFields(LambdaPlayer player) {
        // Auto-initialize entropy fields for existing players
        if (player.entropy == null) {
            player.entropy = 100.0
        }
        if (player.lastEntropyRefresh == null) {
            player.lastEntropyRefresh = new Date()
        }
        if (player.lastBitMining == null) {
            player.lastBitMining = new Date()
        }
        if (player.miningRate == null) {
            player.miningRate = 1
        }
        if (player.fusionAttempts == null) {
            player.fusionAttempts = 0
        }
        if (player.lastFusionReset == null) {
            player.lastFusionReset = new Date()
        }
        
        // Always ensure non-null values with safe defaults
        player.entropy = player.entropy ?: 100.0
        player.miningRate = player.miningRate ?: 1
        player.fusionAttempts = player.fusionAttempts ?: 0
    }
    
    def refreshPlayerEntropy(LambdaPlayer player) {
        def result = [success: false, message: "", rewards: [:]]
        def now = new Date()
        def lastRefresh = player.lastEntropyRefresh ?: player.createdDate
        def hoursSinceRefresh = (now.time - lastRefresh.time) / (1000 * 60 * 60)
        
        // Can only refresh entropy once every 20 hours (creates 4-hour window for optimal play)
        if (hoursSinceRefresh < 20) {
            def timeRemaining = (20 - hoursSinceRefresh).round(1)
            result.message = "Entropy refresh available in ${timeRemaining} hours"
            return result
        }
        
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                def oldEntropy = managedPlayer.entropy
                managedPlayer.entropy = 100.0
                managedPlayer.lastEntropyRefresh = now
                
                // Daily login bonus based on how low entropy got
                def bonusBits = 0
                if (oldEntropy < 50) {
                    bonusBits = 25  // Reward for logging in when entropy was critically low
                } else if (oldEntropy < 75) {
                    bonusBits = 15  // Moderate reward
                } else {
                    bonusBits = 10  // Base daily reward
                }
                
                managedPlayer.bits += bonusBits
                managedPlayer.save(failOnError: true)
                
                result.success = true
                result.message = "Digital entropy restored! Coherence: 100%"
                result.rewards.bits = bonusBits
                result.rewards.entropyRestored = 100.0 - oldEntropy
            }
        }
        
        return result
    }
    
    def calculateBitMining(LambdaPlayer player) {
        // Initialize entropy system for existing players
        initializeEntropyFields(player)
        
        def now = new Date()
        def lastMining = player.lastBitMining ?: player.createdDate ?: now
        def safeLastMining = lastMining ?: now
        def hoursOffline = (now.time - safeLastMining.time) / (1000 * 60 * 60)
        def safeHoursOffline = Math.max(0.0, hoursOffline ?: 0.0)
        
        if (safeHoursOffline < 1) {
            return [bitsEarned: 0, hoursOffline: safeHoursOffline.round(1)]
        }
        
        // Mining rate affected by entropy level
        def entropyDecay = calculateEntropyDecay(player)
        def currentEntropy = (entropyDecay?.currentEntropy ?: 100.0) as Double
        def safeCurrentEntropy = currentEntropy ?: 100.0
        def entropyMultiplier = safeCurrentEntropy / 100.0  // 0.0 to 1.0
        
        // Base mining rate * entropy efficiency * ethnicity bonus * hours offline (max 24 hours)
        def effectiveHours = Math.min(safeHoursOffline, 24.0)
        def miningRate = (player.miningRate ?: 1) as Integer
        def safeMiningRate = miningRate ?: 1
        def safeEntropyMultiplier = entropyMultiplier ?: 0.0
        def safeEffectiveHours = effectiveHours ?: 0.0
        
        // Flowing Current bonus: +25% mining efficiency
        def ethnicityBonus = 1.0 + (player.miningEfficiencyBonus ?: 0.0)
        def bitsEarned = ((safeMiningRate * safeEntropyMultiplier * safeEffectiveHours * ethnicityBonus) ?: 0.0).toInteger()
        
        return [
            bitsEarned: bitsEarned,
            hoursOffline: safeHoursOffline.round(1),
            entropyMultiplier: safeEntropyMultiplier,
            cappedAt24Hours: safeHoursOffline > 24
        ]
    }
    
    def collectMiningRewards(LambdaPlayer player) {
        def miningResult = calculateBitMining(player)
        def result = [success: false, message: "", rewards: [:]]
        
        if (miningResult.bitsEarned <= 0) {
            result.message = "No mining rewards available yet"
            return result
        }
        
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer) {
                managedPlayer.bits += miningResult.bitsEarned
                managedPlayer.lastBitMining = new Date()
                
                // Update entropy after mining collection
                def entropyDecay = calculateEntropyDecay(managedPlayer)
                managedPlayer.entropy = entropyDecay.currentEntropy
                
                managedPlayer.save(failOnError: true)
                
                result.success = true
                result.message = "Mining operation harvested ${miningResult.bitsEarned} bits"
                result.rewards = miningResult
            }
        }
        
        return result
    }
    
    def getEntropyStatus(LambdaPlayer player) {
        initializeEntropyFields(player)  // Initialize for existing players
        def entropyDecay = calculateEntropyDecay(player)
        def miningStatus = calculateBitMining(player)
        def now = new Date()
        def lastRefresh = player.lastEntropyRefresh ?: player.createdDate ?: now
        def safeLastRefresh = lastRefresh ?: now
        def hoursSinceRefresh = (now.time - safeLastRefresh.time) / (1000 * 60 * 60)
        def safeHoursSinceRefresh = Math.max(0.0, hoursSinceRefresh ?: 0.0)
        
        def status = [:]
        status.currentEntropy = (entropyDecay?.currentEntropy ?: 100.0).round(1)
        status.entropyDecayRate = "2% per hour"
        status.hoursOffline = entropyDecay?.hoursOffline ?: 0.0
        status.canRefresh = safeHoursSinceRefresh >= 20
        status.timeUntilRefresh = safeHoursSinceRefresh >= 20 ? 0 : (20 - safeHoursSinceRefresh).round(1)
        status.miningRewards = miningStatus?.bitsEarned ?: 0
        def safeEntropyMultiplier = miningStatus?.entropyMultiplier ?: 0.0
        def efficiencyPercent = ((safeEntropyMultiplier * 100) ?: 0.0).round(0)
        status.miningEfficiency = "${efficiencyPercent}%"
        
        return status
    }
    
    def resetDailyLimits(LambdaPlayer player) {
        // Reset fusion attempts daily
        def now = new Date()
        def lastReset = player.lastFusionReset ?: player.createdDate
        def hoursSinceReset = (now.time - lastReset.time) / (1000 * 60 * 60)
        
        if (hoursSinceReset >= 24) {
            LambdaPlayer.withTransaction {
                def managedPlayer = LambdaPlayer.get(player.id)
                if (managedPlayer) {
                    managedPlayer.fusionAttempts = 0
                    managedPlayer.lastFusionReset = now
                    managedPlayer.save(failOnError: true)
                }
            }
            return true
        }
        return false
    }
    
    def getFragmentFusionAttempts(LambdaPlayer player) {
        initializeEntropyFields(player)  // Initialize for existing players
        resetDailyLimits(player)  // Auto-reset if needed
        def maxAttempts = 5 + (player.processingLevel * 2)  // Scale with processing skill
        def fusionAttempts = player.fusionAttempts ?: 0
        return [
            used: fusionAttempts,
            max: maxAttempts,
            remaining: Math.max(0, maxAttempts - fusionAttempts)
        ]
    }

    // ===== ENTROPY COMMAND HANDLERS (moved from TelnetServerService) =====

    String handleEntropyCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(' ')
        def subCommand = parts.length > 1 ? parts[1].toLowerCase() : "status"
        
        switch (subCommand) {
            case 'status':
            case 'check':
                return showEntropyStatus(player)
            case 'refresh':
            case 'restore':
                return refreshEntropy(player)
            default:
                return "Usage: entropy [status|refresh]\r\n"
        }
    }
    
    private String showEntropyStatus(LambdaPlayer player) {
        def status = this.getEntropyStatus(player)
        def display = new StringBuilder()
        
        // Safe extraction of values with defaults
        def currentEntropy = status.currentEntropy ?: 100.0
        def hoursOffline = status.hoursOffline ?: 0
        def miningRewards = status.miningRewards ?: 0
        def miningEfficiency = status.miningEfficiency ?: "100%"
        def canRefresh = status.canRefresh ?: false
        def timeUntilRefresh = status.timeUntilRefresh ?: 0
        def decayRate = status.entropyDecayRate ?: "2% per hour"
        
        display.append(TerminalFormatter.formatText("=== DIGITAL ENTROPY STATUS ===", 'bold', 'cyan')).append('\r\n')
        display.append("Entity: ${player.displayName}\r\n")
        display.append("Current Coherence: ${TerminalFormatter.formatText("${currentEntropy}%", this.getEntropyColor(currentEntropy), 'bold')}\r\n")
        display.append("Hours Offline: ${hoursOffline}\r\n")
        display.append("Decay Rate: ${decayRate}\r\n\r\n")
        
        display.append("Mining Status:\r\n")
        display.append("Available Rewards: ${TerminalFormatter.formatText("${miningRewards} bits", 'bold', 'green')}\r\n")
        display.append("Mining Efficiency: ${miningEfficiency}\r\n\r\n")
        
        if (canRefresh) {
            display.append(TerminalFormatter.formatText("✅ Entropy refresh available! Use 'entropy refresh'", 'bold', 'green'))
        } else {
            display.append(TerminalFormatter.formatText("⏳ Next refresh in ${timeUntilRefresh} hours", 'italic', 'yellow'))
        }
        
        if (currentEntropy < 25) {
            display.append('\r\n').append(TerminalFormatter.formatText("⚠️  CRITICAL: Digital coherence failing! Refresh immediately!", 'bold', 'red'))
        }
        
        return display.toString()
    }
    
    private String refreshEntropy(LambdaPlayer player) {
        def result = this.refreshPlayerEntropy(player)
        
        if (result.success) {
            def response = new StringBuilder()
            audioService.playSound("entropy_refresh")
            response.append(TerminalFormatter.formatText("🔋 ENTROPY RESTORED!", 'bold', 'green')).append('\r\n')
            response.append(result.message).append('\r\n')
            response.append("Daily Login Bonus: ${TerminalFormatter.formatText("+${result.rewards.bits} bits", 'bold', 'yellow')}")
            
            return response.toString()
        } else {
            return TerminalFormatter.formatText(result.message, 'italic', 'yellow')
        }
    }

    // ===== MINING COMMAND HANDLER (moved from TelnetServerService) =====

    String handleMiningCommand(LambdaPlayer player) {
        def result = this.collectMiningRewards(player)
        
        if (result.success) {
            audioService.playSound("bits_earned")
            def response = new StringBuilder()
            response.append(TerminalFormatter.formatText("⛏️  MINING OPERATION COMPLETE!", 'bold', 'green')).append('\r\n')
            response.append(result.message).append('\r\n')
            response.append("Hours Offline: ${result.rewards.hoursOffline}h\r\n")
            response.append("Efficiency: ${Math.round(result.rewards.entropyMultiplier * 100)}%\r\n")
            if (result.rewards.cappedAt24Hours) {
                response.append(TerminalFormatter.formatText("⚠️  Mining capped at 24 hours", 'italic', 'yellow'))
            }
            
            return response.toString()
        } else {
            return TerminalFormatter.formatText(result.message, 'italic', 'yellow')
        }
    }

    // ===== FUSION COMMAND HANDLERS (moved from TelnetServerService) =====

    String handleFusionCommand(String command, LambdaPlayer player) {
        def parts = command.trim().split(' ')
        if (parts.length < 2) {
            return showFusionStatus(player)
        }
        
        def fragmentName = parts[1..-1].join(' ')
        return attemptFragmentFusion(player, fragmentName)
    }
    
    private String showFusionStatus(LambdaPlayer player) {
        def attempts = this.getFragmentFusionAttempts(player)
        def display = new StringBuilder()
        
        display.append(TerminalFormatter.formatText("=== FRAGMENT FUSION STATUS ===", 'bold', 'cyan')).append('\r\n')
        display.append("Daily Attempts: ${attempts.used}/${attempts.max}\r\n")
        display.append("Remaining: ${TerminalFormatter.formatText("${attempts.remaining}", 'bold', attempts.remaining > 0 ? 'green' : 'red')}\r\n\r\n")
        display.append("Usage: fusion <fragment_name>\r\n")
        display.append("Requires: 3+ identical fragments\r\n")
        display.append("Success Rate: Variable (higher with more fragments)\r\n\r\n")
        
        // Show fusible fragments
        def fusibleFragments = findFusibleFragments(player)
        if (fusibleFragments) {
            display.append("Available for Fusion:\r\n")
            fusibleFragments.each { fragment ->
                display.append("• ${fragment.name} x${fragment.quantity}\r\n")
            }
        } else {
            display.append("No fragments available for fusion\r\n")
        }
        
        return display.toString()
    }
    
    private String attemptFragmentFusion(LambdaPlayer player, String fragmentName) {
        def attempts = this.getFragmentFusionAttempts(player)
        if (attempts.remaining <= 0) {
            return TerminalFormatter.formatText("No fusion attempts remaining today", 'bold', 'red')
        }
        
        // Find fragment to fuse
        def targetFragment = findPlayerFragment(player, fragmentName)
        if (!targetFragment || targetFragment.quantity < 3) {
            return "Need at least 3 identical fragments to attempt fusion"
        }
        
        // Calculate success rate based on quantity and ethnicity bonus
        def baseSuccessRate = 30  // 30% base
        def bonusRate = (targetFragment.quantity - 3) * 10  // +10% per extra fragment
        def ethnicityBonus = (player.fusionSuccessBonus ?: 0.0) * 100  // Binary Form +15%
        def successRate = Math.min(95, baseSuccessRate + bonusRate + ethnicityBonus)  // Cap at 95%
        
        def success = Math.random() * 100 < successRate
        
        // Update player
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            def managedFragment = managedPlayer.logicFragments.find { it.id == targetFragment.id }
            
            if (managedFragment && managedPlayer) {
                managedPlayer.fusionAttempts += 1
                
                if (success) {
                    // Success: Remove 3 fragments, create 1 enhanced version
                    managedFragment.quantity -= 3
                    
                    // Create enhanced version regardless of remaining quantity
                    def enhancedFragment = new LogicFragment(
                        name: "${managedFragment.name} Enhanced",
                        description: "Fused variant with improved capabilities",
                        fragmentType: managedFragment.fragmentType,
                        powerLevel: Math.min(10, managedFragment.powerLevel + 1),
                        pythonCapability: enhanceFragmentCapability(managedFragment.pythonCapability),
                        quantity: 1,
                        isActive: true,
                        discoveredDate: new Date(),
                        owner: managedPlayer
                    )
                    enhancedFragment.save(failOnError: true)
                    managedPlayer.addToLogicFragments(enhancedFragment)
                    
                    // Remove original if quantity is now 0
                    if (managedFragment.quantity <= 0) {
                        managedPlayer.removeFromLogicFragments(managedFragment)
                        managedFragment.delete()
                    } else {
                        managedFragment.save(failOnError: true)
                    }
                } else {
                    // Failure: Lose 1 fragment
                    managedFragment.quantity -= 1
                    if (managedFragment.quantity <= 0) {
                        managedPlayer.removeFromLogicFragments(managedFragment)
                        managedFragment.delete()
                    } else {
                        managedFragment.save(failOnError: true)
                    }
                }
                
                managedPlayer.save(failOnError: true)
            }
        }
        
        def response = new StringBuilder()
        if (success) {
            audioService.playSound("fusion_success")
            response.append(TerminalFormatter.formatText("✨ FUSION SUCCESS!", 'bold', 'green')).append('\r\n')
            response.append("Created enhanced ${fragmentName} with +1 power level!")
        } else {
            audioService.playSound("fusion_fail")
            response.append(TerminalFormatter.formatText("💥 Fusion Failed", 'bold', 'red')).append('\r\n')
            response.append("Lost 1 fragment in the process. Better luck next time!")
        }
        response.append("\r\nFusion attempts remaining: ${attempts.remaining - 1}")
        
        return response.toString()
    }

    private List findFusibleFragments(LambdaPlayer player) {
        def fragments = []
        LambdaPlayer.withTransaction {
            def managedPlayer = LambdaPlayer.get(player.id)
            if (managedPlayer?.logicFragments) {
                fragments = managedPlayer.logicFragments.findAll { it != null && it.quantity >= 3 }
            }
        }
        return fragments
    }
    
    private String enhanceFragmentCapability(String originalCapability) {
        return "${originalCapability}\r\n\r\n# Enhanced fusion variant with 25% improved efficiency"
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
}