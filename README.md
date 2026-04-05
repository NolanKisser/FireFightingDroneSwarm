# Firefighting Drone Swarm Simulation

## Project Overview
This project simulates a swarm of autonomous drones designed to detect and extinguish fires within specific zones. It utilizes a multi-threaded architecture to handle concurrent events, communication, and synchronization between fire detection systems and the drone swarm.

**Iteration 1**
* **Producer-Consumer Pattern:** The `FireIncidentSubsystem` produces events, and the `DroneSubsystem` consumes them via a synchronized `Scheduler`.
* **Concurrency:** Validates safe thread communication (wait/notify) and resource locking.
* **Simulation Logic:** Calculates travel times and extinguishing durations based on zone coordinates and fire severity.

**Iteration 2**
* **Drone Lifecycle:** The `DroneSubsystem` operates using defined states (`IDLE`, `EN_ROUTE`, `EXTINGUISHING`, `RETURNING`) to display drone behaviour.
* **Scheduler Logic:** The `Scheduler` now maintains operational states (`WAITING`, `EVENT_QUEUED`, `DRONE_ACTIVE`) to model system activity.

**Iteration 3**
* **UDP Networking:** Full transition from local thread-based communication to Datagram-based networking between subsystems
* **Scheduler Logic**: Enhanced Scheduler logic coordinates multiple drones, ensuring a balanced workload
* **GUI Visualization:** A simple GUI for monitoring drone activity

**Iteration 4**
* **Fault Handling:** Comprehensive fault injection and handling mechanisms for drone failures
* **Fault Types:** Three fault scenarios implemented:
  - `STUCK_IN_FLIGHT`: Drone freezes mid-flight during EN_ROUTE phase
  - `NOZZLE_JAMMED`: Nozzle doors fail to open during EXTINGUISHING phase
  - `COMMUNICATION_LOST`: Drone loses communication during flight
* **Fault Detection:** Real-time fault detection with graceful state transitions to FAULTED state
* **Error Reporting:** HARD_FAULT messages sent to Scheduler for critical failures
* **Enhanced Testing:** New fault scenario test cases validating failure handling

**Iteration 5**
* **Agent Capacity:** Added new state if there's enough agent to route that drone onto next mission.
* **State Diagram:** Updated state machine diagram for DroneSubSystem.

## Authors
* Jordan Grewal
* Nolan Kisser
* Celina Yang

## Project Structure

### Diagrams (`diagrams/`)
* **`Iteration 1/`**: Contains diagrams for the first iteration of the project.
* **`Iteration 2/`**: Contains diagrams for the second iteration of the project.
* **`Iteration 3/`**: Contains diagrams for the third iteration of the project.
* **`Iteration 4/`**: Detailed timing diagrams showing fault scenarios and message flows.
* **`Iteration 5/`**: Updated State Machine diagram and timing diagram for no fault situation

### Source Code (`src/`)
* **`Main.java`**: The entry point of the application. It initializes the `Scheduler`, starts the `FireIncidentSubsystem` and `DroneSubsystem` threads, and manages the simulation lifecycle.
* **`metrics/`**
  * **`MetricsTracker`**: Instrument class to calculate performace metrics of the simulation.
* **`model/`**
  * **`Drone.java`**: Data model representing the physical state and capabilities of a drone, including position, agent level, and state management.
  * **`FireEvent.java`**: A data transfer object representing a specific event (e.g., `FIRE_DETECTED`, `DRONE_REQUEST`) including details like time, zone ID, severity, and fault type.
  * **`Zone.java`**: Represents a physical area defined by coordinates (x1, y1) to (x2, y2). Includes logic to calculate the center point for drone travel.
* **`subsystems/`**
  * **`DroneSubsystem.java`**: The "Client" that simulates a physical drone using a lifecycle state machine (IDLE → EN_ROUTE → EXTINGUISHING → RETURNING → REFILLING → IDLE or FAULTED). It retrieves events from the `Scheduler`, calculates flight/extinguish times, handles fault scenarios, and reports completion.
  * **`FireIncidentSubsystem.java`**: The "Client" that acts as the input generator. It reads fire events from `event_file.csv` and submits them to the Scheduler.
  * **`Scheduler.java`**: Acts as the central server/monitor. It manages the queue of `FireEvent` objects, synchronizing access between the input subsystem and the drones. It maintains the drones operational states, coordinates drone notifications, handles fault reporting, and loads zone data.
* **`ui/`**
  * **`DroneSwarmMonitor.java`**: A simple GUI for monitoring drone activity.
  * **`ZoneMap.java`**: A static map of zones to display on the console.

### Data Files
* **`event_file.csv`**: Contains the list of fire incidents to simulate.
    * *Format:* `Time, ZoneID, Type, Severity, FaultType`
    * *Example:* `14:03:15, 1, FIRE_DETECTED, High, NONE`
    * *Example (with fault):* `14:10:00, 2, FIRE_DETECTED, Low, STUCK_IN_FLIGHT`
    * *Fault Types:* `NONE`, `STUCK_IN_FLIGHT`, `NOZZLE_JAMMED`, `COMMUNICATION_LOST`
* **`zone_file.csv`**: Defines the geographical boundaries of the zones.
    * *Format:* `ZoneID, (StartX;StartY), (EndX;EndY)`
    * *Example:* `1, (0;0), (700;600)`

### Test Code (`test/`)
* **`FireEventTest.java`**: Unit tests for the FireEvent data structure (7 tests)
* **`ZoneTest.java`**: Unit tests for the Zone class and coordinate calculations (7 tests)
* **`SchedulerTest.java`**: Comprehensive tests for the Scheduler component including synchronization and event management (14 tests)
* **`DroneSubsystemTest.java`**: Tests for drone behavior, event processing, and fault handling scenarios (20 tests)
  - Includes fault scenario tests: `testStuckInFlightFault()`, `testNozzleJammedFault()`, `testCommunicationLostFault()`
* **`FireIncidentSubsystemTest.java`**: Tests for CSV parsing and event submission (13 tests)
* **`SystemIntegrationTest.java`**: End-to-end integration tests for the complete system (10 tests)
* **`TestSuite.java`**: Master test suite for running all tests

**Total: 71 tests**

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
5.  Run each subsystem individually in the following order:
    * Right-click and run `Scheduler.java`.
    * Right-click and run `DroneSubsystem.java`. You can run multiple instances of this to simulate a larger swarm by passing a unique argument (e.g., Drone ID) in the Run Configuration for each drone.
    * Right-click and run `FireIncidentSubsystem.java` to start feeding the events.

### Option 2: Command Line
1.  Navigate to the `src` directory:
    ```bash
    cd src
    ```
2.  Compile the Java files:
    ```bash
    javac model/*.java subsystems/*.java ui/*.java Main.java
    ```
3.  Run the application by starting each subsystem in a separate terminal:
    
    **Terminal 1 (Scheduler):**
    ```bash
    java subsystems.Scheduler
    ```
    
    **Terminal 2 (Drone - run in multiple terminals for multiple drones):**
    ```bash
    java subsystems.DroneSubsystem 1  # Pass a unique ID as an argument
    ```
    
    **Terminal 3 (Fire Incidents):**
    ```bash
    java subsystems.FireIncidentSubsystem
    ```

## Usage
1.  **Configure Zones:** Edit `zone_file.csv` to define the layout of the monitored area.
2.  **Create Scenarios:** Edit `event_file.csv` to add new fire events or requests.
3.  **Run Simulation:** Start the subsystems in the sequence described above. The console will output the status of the drones as they travel to zones, extinguish fires, and return to base.

## Output Example

### Normal Operation
```text
[14:03:15] [Drone 1] Dispatched to Zone 1
[14:03:16] [Drone 1] En route to Zone 1. Travel time: 46.1s
[Scheduler] Drone 1 Status Update - Loc: (350.0, 300.0), Agent: 100.0%
[Scheduler] Notification: Drone 1 arrived at Zone 1
[14:03:20] [Drone 1] Opening nozzle doors... (0.5s)
[14:03:21] [Drone 1] Dropping 10.0% agent on Zone 1... (5.0s)
[Scheduler] Drone 1 Status Update - Loc: (350.0, 300.0), Agent: 90.0%
[14:03:26] [Drone 1] Closing nozzle doors... (0.5s)
[14:03:27] [Drone 1] Successfully extinguished fire in Zone 1!
[14:03:28] [Drone 1] Returning to base. Expected return time: 30.7 seconds
```

### Fault Scenario Example (STUCK_IN_FLIGHT)
```text
[14:10:00] [Drone 2] Dispatched to Zone 2
[14:10:01] [Drone 2] En route to Zone 2. Travel time: 52.3s
[Scheduler] Drone 2 Status Update - Loc: (100.0, 0.0), Agent: 100.0%
[Scheduler] Drone 2 Status Update - Loc: (200.0, 0.0), Agent: 100.0%
[14:10:05] [Drone 2] FAULT: Stuck mid-flight at (200.0, 0.0)! Commencing radio silence.
[Scheduler] WARNING: Lost contact with Drone 2
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

## Known Limitations (Iteration 2 Updated)
* Single-threaded producer (one FireIncidentSubsystem)
* Thread-based communication only (UDP not yet implemented)
* No GUI visualization of drone positions
* Drones always return to base after each event (no chaining of nearby incidents)
* No collision detection between drones
* No intelligent dispatch system (drones are assigned sequentially rather than proximity)

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

**Unit Tests (71 tests):**
* Data structures (FireEvent, Zone, Drone)
* Scheduler event queue management and thread synchronization
* Drone event processing and state management
* Fault handling scenarios (STUCK_IN_FLIGHT, NOZZLE_JAMMED, COMMUNICATION_LOST)
* CSV file parsing and validation

**Integration Tests (10 tests):**
* Complete system workflow with multiple drones
* Producer-consumer pattern verification
* Event ordering (FIFO) validation
* Concurrent event processing

**Fault Scenario Tests (3 tests):**
* `testStuckInFlightFault()` - Validates drone freezes mid-flight and enters FAULTED state
* `testNozzleJammedFault()` - Validates nozzle failure during extinguishing phase
* `testCommunicationLostFault()` - Validates communication loss handling during flight

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
