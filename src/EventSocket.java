import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class EventSocket {
    private DatagramSocket socket;
    public EventSocket(){
        try {
            socket = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public EventSocket(int port){
        try {
            socket = new DatagramSocket(port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void send(Object event, InetAddress address, int port) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(event);
            objectOutputStream.flush(); // Ensure data is flushed
            byte[] msg = byteArrayOutputStream.toByteArray();

            DatagramPacket packet = new DatagramPacket(msg, msg.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("[EventSocket] Error sending event: " + e.getMessage());
        }
    }
    public DatagramPacket receivePacket() {
        byte[] data = new byte[4096];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        try {
            socket.receive(packet);
            return packet;
        } catch (Exception e) {
            return null;
        }
    }

    public Object extractObject(DatagramPacket packet) {
        try {
            if (packet == null || packet.getLength() == 0) return null;
            ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    public Object receive() {
        byte[] data = new byte[4096];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        try {
            socket.receive(packet);
            int length = packet.getLength();
            if (length == 0) {
                System.err.println("[EventSocket] Received an empty packet.");
                return null;
            }
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data, 0, length);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            Object event = objectInputStream.readObject();
            return event;
        } catch (SocketTimeoutException e) {
            // No packet received in time
            return null;
        } catch (EOFException e) {
            System.err.println("[EventSocket] EOFException during receive: " + e.getMessage());
            return null;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[EventSocket] IOException during receive: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Retrieves the DatagramSocket associated with this EventSocket.
     *
     * @return The DatagramSocket used for communication.
     */
    public DatagramSocket getSocket() {
        return socket;
    }

    /**
     * Closes the DatagramSocket if it is open.
     */
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
