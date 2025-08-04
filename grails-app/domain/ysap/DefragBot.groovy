package ysap

class DefragBot {
    Integer matrixLevel
    Integer sector
    Integer positionX
    Integer positionY
    String botId
    Integer difficultyLevel = 1
    Integer timeLimit = 60  // seconds
    String flags
    Integer processId
    Boolean isActive = true
    Date spawnedDate = new Date()
    Date lastBitDrain = new Date()
    String fileContent  // For grep/regex challenges
    Boolean hasStolenFragment = false
    String stolenFragmentName
    String stolenFragmentType
    Integer stolenFragmentPowerLevel
    
    static constraints = {
        matrixLevel min: 1, max: 10
        sector min: 1, max: 12
        positionX min: 0
        positionY min: 0
        botId blank: false, unique: true
        difficultyLevel min: 1, max: 10
        timeLimit min: 30, max: 300
        flags maxSize: 1000
        processId min: 1000, max: 9999
        fileContent maxSize: 2000, nullable: true
        stolenFragmentName nullable: true
        stolenFragmentType nullable: true
        stolenFragmentPowerLevel nullable: true
    }
    
    static mapping = {
        flags type: 'text'
        fileContent type: 'text'
    }
    
    String toString() {
        return "DefragBot-${botId} (Level ${difficultyLevel})"
    }
    
    String generateRandomFlags() {
        def flagOptions = [
            ['-v', '--verbose', 'Enable verbose output'],
            ['-q', '--quiet', 'Suppress output messages'], 
            ['-f', '--force', 'Force defragmentation'],
            ['-s', '--simulate', 'Simulate defrag process'],
            ['-r', '--recursive', 'Recursive directory scan'],
            ['-n', '--no-verify', 'Skip verification step'],
            ['-p', '--preserve', 'Preserve file timestamps'],
            ['-c', '--compress', 'Compress after defrag'],
            ['-b', '--backup', 'Create backup before defrag'],
            ['-x', '--exclude', 'Exclude system files'],
            ['-t', '--timeout', 'Set operation timeout'],
            ['-l', '--log', 'Enable logging'],
            ['-d', '--debug', 'Enable debug mode'],
            ['-k', '--kill', 'Terminate process']
        ]
        
        def selectedFlags = flagOptions.shuffled().take(4 + difficultyLevel)
        def helpText = new StringBuilder()
        helpText.append("defrag v2.1 - System Defragmentation Utility\\n")
        helpText.append("Process ID: ${processId}\\n\\n")
        helpText.append("Usage: defrag [OPTIONS]\\n\\n")
        helpText.append("Options:\\n")
        
        selectedFlags.each { flag ->
            helpText.append("  ${flag[0]}, ${flag[1]}\\t${flag[2]}\\n")
        }
        
        helpText.append("\\nTo terminate: defrag -k --pid ${processId}")
        
        return helpText.toString()
    }
    
    List<String> getKillCommands() {
        return [
            "defrag -k --pid ${processId}",
            "defrag --kill --pid ${processId}",
            "kill -9 ${processId}",
            "pkill -f defrag_${botId}"
        ]
    }
    
    Boolean isValidKillCommand(String command) {
        return getKillCommands().any { it.equalsIgnoreCase(command.trim()) }
    }
    
    String generateFileContent() {
        def difficulty = Math.max(1, Math.min(10, difficultyLevel))
        def baseContent = [
            "system_status: operational",
            "memory_usage: 67.3%",
            "cpu_load: 12.4%",
            "disk_space: 89.1%",
            "network_interfaces: eth0, lo",
            "running_processes: 156",
            "system_uptime: 3 days, 14 hours",
            "kernel_version: 5.4.0-74-generic",
            "hostname: lambda-matrix-${matrixLevel}",
            "timezone: UTC"
        ]
        
        def content = new StringBuilder()
        
        // Add base system info
        baseContent.each { line ->
            content.append("${line}\n")
        }
        
        // Add PID based on difficulty
        if (difficulty <= 3) {
            // Easy: PID clearly visible
            content.append("defrag_process_id: ${processId}\n")
        } else if (difficulty <= 6) {
            // Medium: PID mixed with other numbers but in a specific pattern
            content.append("active_processes: [1234, 5678, 9012]\n")
            content.append("defrag_process_id: ${processId}\n")
            content.append("background_processes: [2345, 6789, 3456]\n")
        } else {
            // Hard: PID buried in complex data requiring precise regex
            content.append("process_groups: {\n")
            content.append("  \"system_processes\": [1111, 2222, 3333],\n")
            content.append("  \"defrag_processes\": [${processId}],\n")
            content.append("  \"cleanup_processes\": [4444, 5555, 6666]\n")
            content.append("}\n")
            content.append("process_metadata: {\n")
            content.append("  \"${processId}\": {\"type\": \"defrag\", \"status\": \"active\"},\n")
            content.append("  \"1111\": {\"type\": \"system\", \"status\": \"idle\"}\n")
            content.append("}\n")
        }
        
        // Add minimal noise to keep focus on the PID
        def noiseLines = Math.min(difficulty, 3)
        (1..noiseLines).each { i ->
            content.append("log_entry_${i}: routine_scan_${i}${i}${i}${i}\n")
        }
        
        return content.toString()
    }
    
    String getGrepInstructions() {
        def difficulty = Math.max(1, Math.min(10, difficultyLevel))
        
        if (difficulty <= 3) {
            return """
Defrag Bot File System Access (Difficulty: ${difficulty}/10):
Use 'cat /proc/defrag/${botId}' to view process file
Use 'grep' with regex patterns to find the PID

Example commands:
  t
  grep -o 'defrag_process_id: [0-9]*' /proc/defrag/${botId}
  grep -o '[0-9][0-9][0-9][0-9]' /proc/defrag/${botId}

The PID must be extracted alone on its own line.
"""
        } else if (difficulty <= 6) {
            return """
Defrag Bot File System Access (Difficulty: ${difficulty}/10):
Use 'cat /proc/defrag/${botId}' to view process file
Use 'grep' with regex patterns to find the PID

Example commands:
  grep -o 'defrag_process_id: [0-9]*' /proc/defrag/${botId}
  grep -o ': [0-9]*' /proc/defrag/${botId} | grep -o '[0-9]*'

The PID must be extracted alone on its own line.
"""
        } else {
            return """
Defrag Bot File System Access (Difficulty: ${difficulty}/10):
Use 'cat /proc/defrag/${botId}' to view process file
Use 'grep' with advanced regex to find the PID

Example commands:
  grep -o 'defrag_processes.*\\[[0-9]*\\]' /proc/defrag/${botId}
  grep -o '\\[[0-9]*\\]' /proc/defrag/${botId} | grep -o '[0-9]*'

The PID must be extracted alone on its own line.
"""
        }
    }
}