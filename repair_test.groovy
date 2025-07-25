// Simple test to verify SimpleRepairService logic
import ysap.*

// Mock a PrintWriter
class MockPrintWriter extends PrintWriter {
    List<String> output = []
    
    MockPrintWriter() {
        super(new StringWriter())
    }
    
    @Override
    void print(String s) {
        output.add(s)
        println "PRINT: $s"
    }
    
    @Override 
    void println(String s) {
        output.add(s + "\n")
        println "PRINTLN: $s"
    }
    
    @Override
    void flush() {
        // no-op for testing
    }
}

// Create mock player
def mockPlayer = new LambdaPlayer(
    username: "testuser",
    displayName: "TestUser", 
    currentMatrixLevel: 1,
    positionX: 0,
    positionY: 0
)

// Create mock writer  
def mockWriter = new MockPrintWriter()

// Test SimpleRepairService
def simpleRepairService = new SimpleRepairService()

println "=== TESTING SIMPLE REPAIR SERVICE ==="
println "1. Testing repair initiation..."

def result = simpleRepairService.initiateRepair(mockPlayer, 1, 0, mockWriter)
println "Initiate result: ${result}"

if (result.success) {
    println "\n2. Testing space bar presses..."
    
    // Test first space bar press
    def spaceResult1 = simpleRepairService.handleSpaceBarPress("testuser")
    println "First space bar: ${spaceResult1}"
    
    // Test second space bar press
    def spaceResult2 = simpleRepairService.handleSpaceBarPress("testuser")
    println "Second space bar: ${spaceResult2}"
    
    // Test third space bar press
    def spaceResult3 = simpleRepairService.handleSpaceBarPress("testuser")
    println "Third space bar: ${spaceResult3}"
    
    println "\n3. Output from repair session:"
    mockWriter.output.each { line ->
        println "OUTPUT: $line"
    }
} else {
    println "Failed to initiate repair: ${result.message}"
}

println "\n=== TEST COMPLETE ==="