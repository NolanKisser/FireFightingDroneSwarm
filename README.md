# Firefighting Drone Swarm Simulation

## Project Overview
This project simulates a swarm of autonomous drones designed to detect and extinguish fires within specific zones. It utilizes a multi-threaded architecture to handle concurrent events, communication, and synchronization between fire detection systems and the drone swarm.

**Current Iteration Features:**
* **Producer-Consumer Pattern:** The `FireIncidentSubsystem` produces events, and the `DroneSubsystem` consumes them via a synchronized `Scheduler`.
* **Concurrency:** Validates safe thread communication (wait/notify) and resource locking.
* **Simulation Logic:** Calculates travel times and extinguishing durations based on zone coordinates and fire severity.

**Iteration 2**
* **Drone Lifecycle:** The `DroneSubsystem` operates using defined states (`IDLE`, `EN_ROUTE`, `EXTINGUISHING`, `RETURNING`) to display drone behaviour.
* **Scheduler Logic:** The `Scheduler` now maintains operational states (`WAITING`, `EVENT_QUEUED`, `DRONE_ACTIVE`) to model system activity.


## Authors
* Jordan Grewal
* Ozan Kaya
* Nolan Kisser
* Celina Yang

## Project Structure

### Source Code (`src/`)
* **`DroneSubsystem.java`**: The "Client" that simulates a physical drone using a lifecycle state machine. It retrieves events from the `Scheduler`, calculates flight/extinguish times, and reports completion.
* **`DroneSwarmMonitor.java`**: A simple GUI for monitoring drone activity.
* **`FireIncidentSubsystem.java`**: The "Client" that acts as the input generator. It reads fire events from `event_file.csv` and submits them to the Scheduler.
* **`FireEvent.java`**: A data transfer object representing a specific event (e.g., `FIRE_DETECTED`, `DRONE_REQUEST`) including details like time, zone ID, and severity.
* **`Main.java`**: The entry point of the application. It initializes the `Scheduler`, starts the `FireIncidentSubsystem` and `DroneSubsystem` threads, and manages the simulation lifecycle.
* **`Scheduler.java`**: Acts as the central server/monitor. It manages the queue of `FireEvent` objects, synchronizing access between the input subsystem and the drones. It maintains the drones operational states and coordinates drone notifications. It also loads zone data.
* **`Zone.java`**: Represents a physical area defined by coordinates (x1, y1) to (x2, y2). Includes logic to calculate the center point for drone travel.
* **`ZoneMap.java`**: A static map of zones to display on the console.

### Data Files
* **`event_file.csv`**: Contains the list of fire incidents to simulate.
    * *Format:* `Time, ZoneID, Type, Severity`
    * *Example:* `14:03:15, 1, FIRE_DETECTED, High`
* **`zone_file.csv`**: Defines the geographical boundaries of the zones.
    * *Format:* `ZoneID, (StartX;StartY), (EndX;EndY)`
    * *Example:* `1, (0;0), (700;600)`

### Test Code (`test/`)
* **`FireEventTest.java`**: Unit tests for the FireEvent data structure (7 tests)
* **`ZoneTest.java`**: Unit tests for the Zone class and coordinate calculations (7 tests)
* **`SchedulerTest.java`**: Comprehensive tests for the Scheduler component including synchronization and event management (14 tests)
* **`DroneSubsystemTest.java`**: Tests for drone behavior and event processing (14 tests)
* **`FireIncidentSubsystemTest.java`**: Tests for CSV parsing and event submission (13 tests)
* **`SystemIntegrationTest.java`**: End-to-end integration tests for the complete system (10 tests)
* **`TestSuite.java`**: Master test suite for running all tests

**Total: 65 tests**

## Prerequisites
* **Java Development Kit (JDK):** Version 21 or higher.
* **IDE:** IntelliJ IDEA (recommended) or Eclipse.
* **JUnit 5:** Version 5.10.1 or higher (for running tests).

## Setup & Installation

### Option 1: IntelliJ IDEA (Recommended)
1.  Open IntelliJ IDEA.
2.  Select **File > Open** and navigate to the `FireFightingDroneSwarm` folder.
3.  Ensure the Project SDK is set to Java 17+ (File > Project Structure > Project > SDK).
4.  If using Maven, the project will automatically download dependencies. Otherwise, add JUnit 5.10.1 manually:
    * Go to **File > Project Structure > Libraries**
    * Click **+** and select **From Maven...**
    * Search for `org.junit.jupiter:junit-jupiter:5.10.1` and add it.
5.  Navigate to `src/Main.java`.
6.  Right-click `Main.java` and select **Run 'Main.main()'**.

### Option 2: Command Line
1.  Navigate to the `src` directory:
    ```bash
    cd src
    ```
2.  Compile the Java files:
    ```bash
    javac Main.java Scheduler.java FireIncidentSubsystem.java DroneSubsystem.java FireEvent.java Zone.java
    ```
3.  Run the application (ensure CSV files are in the root project directory, you may need to move them to `src` or adjust paths in `Main.java` if running from CLI depending on your classpath):
    ```bash
    java Main
    ```

## Usage
1.  **Configure Zones:** Edit `zone_file.csv` to define the layout of the monitored area.
2.  **Create Scenarios:** Edit `event_file.csv` to add new fire events or requests.
3.  **Run Simulation:** Start the program. The console will output the status of the drones as they travel to zones, extinguish fires, and return to base.

## Output Example
```text
DroneID: 1 is enRoute to fire in zone 1. Expected travel time: 35 seconds
DroneID: 1 is extinguishing fire in zone 1. Expected completion time: 16 seconds
DroneID: 1 is returning from zone 1. Expected return time: 23 seconds
```

## Design Decisions

### Thread Synchronization
The `Scheduler` uses `wait()` and `notify()` to coordinate between the producer (FireIncidentSubsystem) and consumers (DroneSubsystem threads). This ensures:
* Events are processed in FIFO order
* No events are lost due to race conditions
* Threads properly block when no work is available

### Time Simulation
Rather than using real-time delays, the system uses `Thread.sleep()` with scaled-down durations (e.g., 1 second represents multiple seconds of flight time) to demonstrate the workflow without excessive wait times.

### CSV-Based Configuration
Both zones and events are externally configurable via CSV files, allowing easy scenario testing without code modifications.

## Known Limitations (Iteration 1)
* Single-threaded producer (one FireIncidentSubsystem)
* Thread-based communication only (UDP not yet implemented)
* No GUI visualization of drone positions
* Drones always return to base after each event (no chaining of nearby incidents)
* No collision detection between drones

## Testing

### Running Tests

#### Option 1: Run All Tests (Recommended)
1. In IntelliJ, right-click on the `test` folder in your Project view
2. Select **"Run 'All Tests'"**
3. View results in the Run window

#### Option 2: Run Individual Test Classes
Right-click on any test class and select **Run**:
* `FireEventTest` - Tests FireEvent data structure
* `ZoneTest` - Tests Zone coordinate calculations
* `SchedulerTest` - Tests scheduler synchronization and event management
* `DroneSubsystemTest` - Tests drone behavior
* `FireIncidentSubsystemTest` - Tests CSV parsing and event submission
* `SystemIntegrationTest` - Tests complete system integration

### Test Coverage
The test suite provides comprehensive coverage across all components:

**Unit Tests (65 tests):**
* Data structures (FireEvent, Zone)
* Scheduler event queue management and thread synchronization
* Drone event processing and state management
* CSV file parsing and validation

**Integration Tests (10 tests):**
* Complete system workflow with multiple drones
* Producer-consumer pattern verification
* Event ordering (FIFO) validation
* Concurrent event processing

### Test Quality Features
* **JUnit 5 Framework:** Industry-standard testing framework
* **Timeout Protection:** All tests have timeout limits to prevent hanging
* **Thread Safety Verification:** Tests validate proper synchronization
* **Edge Case Coverage:** Empty files, multiple events, concurrent processing
* **Reflection-Based Testing:** Isolates component behavior to avoid race conditions

## Troubleshooting

### "Cannot find symbol" errors
* Ensure all `.java` files are in the `src` directory
* Verify your JDK version is 17 or higher

### CSV file not found
* Ensure `event_file.csv` and `zone_file.csv` are in the project root directory
* Check file paths in `Main.java` match your file locations

### Tests not running
* Verify JUnit 5 is properly added to your project dependencies
* Check that test files are in the `test` source folder (marked as test sources in IntelliJ)
* Ensure all test classes end with `Test.java`

### Thread hangs or timeouts
* This is expected behavior if event/zone CSV files are missing or malformed
* Verify CSV file format matches the specification
