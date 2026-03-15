interface DroneStates{
    void handleEvent(DroneSubsystem context);
    void displayState();
    String getState();
}

class idelState implements DroneStates {
    
    @Override
    public void handleEvent(DroneSubsystem context){
        System.out.println("Drone is waiting for task.");
    }
    @Override
    public void displayState(){
        System.out.println("Drone State: IDLE");
    }
    @Override
    public String getState(){
        return "IDLE";
    }
}
class enRouteState implements DroneStates {
    @Override
    public void handleEvent(DroneSubsystem context){
        System.out.println("Drone is on the way.");
    }
    @Override
    public void displayState(){
        System.out.println("Drone State: EN_ROUTE");
    }
    @Override
    public String getState(){ return "EN_ROUTE";}
}
class extinguishingState implements DroneStates {
    @Override
    public void handleEvent(DroneSubsystem context){
        System.out.println("Drone is extinguishing.");
    }
    @Override
    public void displayState(){
        System.out.println("Drone State: EXTINGUISHING");
    }
    @Override
    public String getState(){ return "EXTINGUISHING";}
}
class returningState implements DroneStates {
    @Override
    public void handleEvent(DroneSubsystem context){
        System.out.println("Drone is returning to base.");
    }
    @Override
    public void displayState(){
        System.out.println("Drone State: RETURNING");
    }
    @Override
    public String getState(){ return "RETURNING";}
}
class refillingState implements DroneStates {
    @Override
    public void handleEvent(DroneSubsystem context){
        System.out.println("Drone is refilling.");
    }
    @Override
    public void displayState(){
        System.out.println("Drone State: REFILLING");
    }
    @Override
    public String getState(){ return "REFILLING";}
}
class faultedState implements DroneStates {
    @Override
    public void handleEvent(DroneSubsystem context){
        System.out.println("Drone is faulted.");
    }
    @Override
    public void displayState(){
        System.out.println("Drone State: FAULTED");
    }
    @Override
    public String getState(){ return "FAULTED";}
}

