import java.io.Serializable;

public class UDPMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Command {
        NEW_FIRE_EVENT,
        GET_NEXT_FIRE_EVENT,
        UPDATE_DRONE_STATUS,
        REPORT_FAULT,
        DRONE_ARRIVED_AT_ZONE,
        DRONE_RETURN_TO_BASE,
        UPDATE_ALL_EVENTS_DONE,
        COMPLETE_FIRE_EVENT,
        GET_COMPLETED_EVENT,
        REGISTER_DRONE,
        NOTIFY_TRANSITION,
        GET_ZONES
    }

    public Command command;
    public Object[] parameters;
    public Object response;

    public UDPMessage(Command command, Object... parameters) {
        this.command = command;
        this.parameters = parameters;
    }
}
