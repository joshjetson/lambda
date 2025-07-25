package ysap

import grails.gorm.transactions.Transactional
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

@Transactional
class EntropyService {

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
}