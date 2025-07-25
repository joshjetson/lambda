package ysap

import grails.gorm.transactions.Transactional
import javax.sound.sampled.*
import java.util.concurrent.ConcurrentHashMap

@Transactional
class AudioService {
    
    private static final int SAMPLE_RATE = 44100
    private static final int SAMPLE_SIZE_IN_BITS = 16
    private static final int CHANNELS = 1
    private static final boolean SIGNED = true
    private static final boolean BIG_ENDIAN = false
    
    private AudioFormat audioFormat
    private Map<String, byte[]> soundCache = new ConcurrentHashMap<>()
    
    def init() {
        audioFormat = new AudioFormat(
            SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN
        )
        
        // Pre-generate common sounds for better performance
        preGenerateSounds()
    }
    
    private void preGenerateSounds() {
        // Movement sounds - subtle clicks
        soundCache.put("move_north", generateTone(520, 0.1, 0.3, "sine"))
        soundCache.put("move_south", generateTone(480, 0.1, 0.3, "sine"))
        soundCache.put("move_east", generateTone(500, 0.1, 0.3, "sine"))
        soundCache.put("move_west", generateTone(460, 0.1, 0.3, "sine"))
        
        // Victory sounds - rising chord progressions
        soundCache.put("defrag_victory", generateChord([261, 329, 392], 0.6, 0.7)) // C-E-G major chord
        soundCache.put("item_found", generateRisingTone(400, 600, 0.4, 0.5))
        soundCache.put("fragment_pickup", generateRisingTone(350, 500, 0.3, 0.6))
        soundCache.put("bits_earned", generateTinkle(440, 3, 0.3, 0.4))
        
        // System sounds - satisfying feedback
        soundCache.put("entropy_refresh", generateSwoop(300, 600, 0.8, 0.6))
        soundCache.put("fusion_success", generateSparkle(523, 5, 0.5, 0.7))
        soundCache.put("fusion_fail", generateDescendingTone(400, 250, 0.4, 0.4))
        soundCache.put("special_item_use", generateMagic(660, 0.6, 0.5))
        
        // Interface sounds - minimal but pleasant
        soundCache.put("scan_activate", generatePulse(600, 3, 0.2, 0.3))
        soundCache.put("menu_select", generateTone(440, 0.15, 0.2, "triangle"))
        soundCache.put("error", generateWarble(300, 250, 0.3, 0.3))
        soundCache.put("success", generateTone(523, 0.4, 0.5, "sine"))
        
        // Ambient/atmospheric
        soundCache.put("login", generateWelcomeChime())
        soundCache.put("logout", generateFarewell())
        soundCache.put("level_up", generateLevelUpFanfare())
    }
    
    def playSound(String soundName) {
        try {
            def soundData = soundCache.get(soundName)
            if (soundData) {
                // Play sound in background thread to avoid blocking game
                Thread.start {
                    playAudioData(soundData)
                }
            }
        } catch (Exception e) {
            // Silently fail - audio is enhancement, not critical
            println "Audio playback failed for ${soundName}: ${e.message}"
        }
    }
    
    def playSoundIfEnabled(String soundName, LambdaPlayer player) {
        // Check if player has audio enabled (could be a setting)
        def audioEnabled = true // TODO: Add player audio preference
        if (audioEnabled) {
            playSound(soundName)
        }
    }
    
    // Generate various types of sounds
    private byte[] generateTone(double frequency, double duration, double volume, String waveform = "sine") {
        int numSamples = (int) (SAMPLE_RATE * duration)
        byte[] audioData = new byte[numSamples * 2] // 16-bit samples
        
        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / SAMPLE_RATE
            double amplitude = volume * 32767
            
            double sample
            switch (waveform) {
                case "sine":
                    sample = Math.sin(2 * Math.PI * frequency * time)
                    break
                case "triangle":
                    sample = (2.0 / Math.PI) * Math.asin(Math.sin(2 * Math.PI * frequency * time))
                    break
                case "square":
                    sample = Math.sin(2 * Math.PI * frequency * time) > 0 ? 1 : -1
                    break
                case "sawtooth":
                    sample = 2 * (time * frequency - Math.floor(time * frequency + 0.5))
                    break
                default:
                    sample = Math.sin(2 * Math.PI * frequency * time)
                    break
            }
            
            // Apply envelope for smooth attack/decay
            double envelope = 1.0
            if (time < 0.05) {
                envelope = time / 0.05 // Attack
            } else if (time > duration - 0.05) {
                envelope = (duration - time) / 0.05 // Decay
            }
            
            short sampleValue = (short) (sample * amplitude * envelope)
            
            // Convert to bytes (little endian)
            audioData[i * 2] = (byte) (sampleValue & 0xFF)
            audioData[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF)
        }
        
        return audioData
    }
    
    private byte[] generateChord(List<Double> frequencies, double duration, double volume) {
        int numSamples = (int) (SAMPLE_RATE * duration)
        byte[] audioData = new byte[numSamples * 2]
        
        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / SAMPLE_RATE
            double amplitude = volume * 32767 / frequencies.size()
            
            double combinedSample = 0
            frequencies.each { freq ->
                combinedSample += Math.sin(2 * Math.PI * freq * time)
            }
            
            // Apply envelope
            double envelope = 1.0
            if (time < 0.1) {
                envelope = time / 0.1
            } else if (time > duration - 0.2) {
                envelope = (duration - time) / 0.2
            }
            
            short sampleValue = (short) (combinedSample * amplitude * envelope)
            
            audioData[i * 2] = (byte) (sampleValue & 0xFF)
            audioData[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF)
        }
        
        return audioData
    }
    
    private byte[] generateRisingTone(double startFreq, double endFreq, double duration, double volume) {
        int numSamples = (int) (SAMPLE_RATE * duration)
        byte[] audioData = new byte[numSamples * 2]
        
        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / SAMPLE_RATE
            double progress = time / duration
            double frequency = startFreq + (endFreq - startFreq) * progress
            double amplitude = volume * 32767
            
            double sample = Math.sin(2 * Math.PI * frequency * time)
            
            // Envelope with slight attack and smooth decay
            double envelope = Math.min(1.0, Math.max(0.0, 1.0 - (progress * progress)))
            
            short sampleValue = (short) (sample * amplitude * envelope)
            
            audioData[i * 2] = (byte) (sampleValue & 0xFF)
            audioData[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF)
        }
        
        return audioData
    }
    
    private byte[] generateDescendingTone(double startFreq, double endFreq, double duration, double volume) {
        return generateRisingTone(startFreq, endFreq, duration, volume)
    }
    
    private byte[] generateTinkle(double baseFreq, int numTinkles, double duration, double volume) {
        int numSamples = (int) (SAMPLE_RATE * duration)
        byte[] audioData = new byte[numSamples * 2]
        
        double tinkleDuration = duration / numTinkles
        
        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / SAMPLE_RATE
            int tinkleIndex = (int) (time / tinkleDuration)
            double tinkleTime = time % tinkleDuration
            
            // Each tinkle is slightly higher pitch
            double frequency = baseFreq * (1 + tinkleIndex * 0.2)
            double amplitude = volume * 32767
            
            double sample = Math.sin(2 * Math.PI * frequency * tinkleTime)
            
            // Quick attack, longer decay for each tinkle
            double envelope = Math.exp(-tinkleTime * 8)
            
            short sampleValue = (short) (sample * amplitude * envelope)
            
            audioData[i * 2] = (byte) (sampleValue & 0xFF)
            audioData[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF)
        }
        
        return audioData
    }
    
    private byte[] generateSwoop(double startFreq, double endFreq, double duration, double volume) {
        int numSamples = (int) (SAMPLE_RATE * duration)
        byte[] audioData = new byte[numSamples * 2]
        
        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / SAMPLE_RATE
            double progress = time / duration
            
            // Exponential curve for more satisfying swoop
            double curveProgress = 1 - Math.exp(-progress * 3)
            double frequency = startFreq + (endFreq - startFreq) * curveProgress
            double amplitude = volume * 32767
            
            double sample = Math.sin(2 * Math.PI * frequency * time)
            
            // Smooth envelope
            double envelope = Math.sin(progress * Math.PI)
            
            short sampleValue = (short) (sample * amplitude * envelope)
            
            audioData[i * 2] = (byte) (sampleValue & 0xFF)
            audioData[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF)
        }
        
        return audioData
    }
    
    private byte[] generateSparkle(double baseFreq, int numSparkles, double duration, double volume) {
        int numSamples = (int) (SAMPLE_RATE * duration)
        byte[] audioData = new byte[numSamples * 2]
        
        Random random = new Random()
        
        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / SAMPLE_RATE
            double amplitude = volume * 32767
            
            double combinedSample = 0
            
            // Generate multiple harmonic sparkles
            for (int j = 0; j < numSparkles; j++) {
                double freq = baseFreq * (1 + j * 0.5 + random.nextGaussian() * 0.1)
                double phase = random.nextDouble() * 2 * Math.PI
                combinedSample += Math.sin(2 * Math.PI * freq * time + phase) / numSparkles
            }
            
            // Envelope with random sparkle timing
            double envelope = Math.exp(-time * 2) * (0.5 + 0.5 * Math.sin(time * 20))
            
            short sampleValue = (short) (combinedSample * amplitude * envelope)
            
            audioData[i * 2] = (byte) (sampleValue & 0xFF)
            audioData[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF)
        }
        
        return audioData
    }
    
    private byte[] generateMagic(double baseFreq, double duration, double volume) {
        int numSamples = (int) (SAMPLE_RATE * duration)
        byte[] audioData = new byte[numSamples * 2]
        
        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / SAMPLE_RATE
            double amplitude = volume * 32767
            
            // Magical shimmer effect with multiple harmonics
            double sample = 0
            sample += Math.sin(2 * Math.PI * baseFreq * time) * 0.4
            sample += Math.sin(2 * Math.PI * baseFreq * 1.5 * time) * 0.3
            sample += Math.sin(2 * Math.PI * baseFreq * 2.0 * time) * 0.2
            sample += Math.sin(2 * Math.PI * baseFreq * 3.0 * time) * 0.1
            
            // Tremolo effect for magical quality
            double tremolo = 1 + 0.3 * Math.sin(2 * Math.PI * 8 * time)
            
            // Envelope
            double envelope = Math.exp(-time * 1.5)
            
            short sampleValue = (short) (sample * amplitude * envelope * tremolo)
            
            audioData[i * 2] = (byte) (sampleValue & 0xFF)
            audioData[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF)
        }
        
        return audioData
    }
    
    private byte[] generatePulse(double frequency, int numPulses, double duration, double volume) {
        int numSamples = (int) (SAMPLE_RATE * duration)
        byte[] audioData = new byte[numSamples * 2]
        
        double pulseDuration = duration / numPulses
        
        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / SAMPLE_RATE
            double pulseTime = time % pulseDuration
            double amplitude = volume * 32767
            
            double sample = Math.sin(2 * Math.PI * frequency * pulseTime)
            
            // Sharp pulse envelope
            double envelope = pulseTime < pulseDuration * 0.3 ? 1.0 : 0.0
            
            short sampleValue = (short) (sample * amplitude * envelope)
            
            audioData[i * 2] = (byte) (sampleValue & 0xFF)
            audioData[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF)
        }
        
        return audioData
    }
    
    private byte[] generateWarble(double freq1, double freq2, double duration, double volume) {
        int numSamples = (int) (SAMPLE_RATE * duration)
        byte[] audioData = new byte[numSamples * 2]
        
        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / SAMPLE_RATE
            double amplitude = volume * 32767
            
            // Warble between two frequencies
            double warbleRate = 8.0 // Hz
            double freq = freq1 + (freq2 - freq1) * (0.5 + 0.5 * Math.sin(2 * Math.PI * warbleRate * time))
            
            double sample = Math.sin(2 * Math.PI * freq * time)
            
            // Envelope
            double envelope = Math.exp(-time * 3)
            
            short sampleValue = (short) (sample * amplitude * envelope)
            
            audioData[i * 2] = (byte) (sampleValue & 0xFF)
            audioData[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF)
        }
        
        return audioData
    }
    
    private byte[] generateWelcomeChime() {
        // C-E-G-C ascending arpeggio
        def frequencies = [261.63, 329.63, 392.00, 523.25]
        int totalSamples = 0
        List<byte[]> notes = []
        
        frequencies.each { freq ->
            byte[] note = generateTone(freq, 0.3, 0.6, "sine")
            notes.add(note)
            totalSamples += note.length
        }
        
        byte[] result = new byte[totalSamples]
        int offset = 0
        notes.each { note ->
            System.arraycopy(note, 0, result, offset, note.length)
            offset += note.length
        }
        
        return result
    }
    
    private byte[] generateFarewell() {
        // G-E-C descending
        return generateChord([392.00, 329.63, 261.63], 1.0, 0.5)
    }
    
    private byte[] generateLevelUpFanfare() {
        // Major scale run up
        def frequencies = [261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25]
        return generateArpeggio(frequencies, 1.2, 0.7)
    }
    
    private byte[] generateArpeggio(List<Double> frequencies, double totalDuration, double volume) {
        double noteDuration = totalDuration / frequencies.size()
        int totalSamples = 0
        List<byte[]> notes = []
        
        frequencies.each { freq ->
            byte[] note = generateTone(freq, noteDuration, volume, "sine")
            notes.add(note)
            totalSamples += note.length
        }
        
        byte[] result = new byte[totalSamples]
        int offset = 0
        notes.each { note ->
            System.arraycopy(note, 0, result, offset, note.length)
            offset += note.length
        }
        
        return result
    }
    
    private void playAudioData(byte[] audioData) {
        try {
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat)
            SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo)
            
            sourceDataLine.open(audioFormat)
            sourceDataLine.start()
            
            sourceDataLine.write(audioData, 0, audioData.length)
            
            sourceDataLine.drain()
            sourceDataLine.close()
        } catch (Exception e) {
            // Silently handle audio errors
            println "Audio playback error: ${e.message}"
        }
    }
}