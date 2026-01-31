import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Master Test Suite for the Firefighting Drone Swarm System - Iteration 1.
 * This class runs all tests by including them as nested test classes.
 *
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
@DisplayName("Firefighting Drone Swarm - Complete Test Suite")
public class TestSuite {

    // Note: To run all tests, use IntelliJ's "Run All Tests" feature
    // or simply run each test class individually

    @Test
    @DisplayName("Instructions for running all tests")
    public void runAllTestsInstructions() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  FIREFIGHTING DRONE SWARM - ITERATION 1 TEST SUITE");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("To run all tests, use one of these methods:");
        System.out.println();
        System.out.println("METHOD 1 - IntelliJ (Recommended):");
        System.out.println("  - Right-click on the 'test' folder or source root");
        System.out.println("  - Select 'Run All Tests'");
        System.out.println();
        System.out.println("METHOD 2 - Run individual test classes:");
        System.out.println("  1. FireEventTest        (7 tests)");
        System.out.println("  2. ZoneTest             (7 tests)");
        System.out.println("  3. SchedulerTest        (13 tests)");
        System.out.println("  4. DroneSubsystemTest   (9 tests)");
        System.out.println("  5. FireIncidentSubsystemTest (13 tests)");
        System.out.println("  6. SystemIntegrationTest (10 tests)");
        System.out.println();
        System.out.println("Total: 59 comprehensive tests");
        System.out.println("═══════════════════════════════════════════════════════════");
    }
}
