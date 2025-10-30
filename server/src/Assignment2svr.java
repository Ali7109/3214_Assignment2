import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;

public class Assignment2svr {
    public static void main(String[] args) {
        // Check if port number is provided
        if (args.length != 1) {
            System.err.println("Usage: java Assignment2svr <port>");
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
            if (port < 1024 || port > 65535) {
                System.err.println("Error: Port must be between 1024 and 65535");
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid port number");
            System.exit(1);
            return;
        }

        DatagramSocket socket = null;

        try {
            // Create UDP socket on specified port
            socket = new DatagramSocket(port);
            System.out.println("UDP Server started on port " + port);
            System.out.println("Waiting for incoming data...");

            // Buffer for receiving data
            byte[] buffer = new byte[1024];

            // Keep server running
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                // Wait to receive data
                socket.receive(packet);

                // For now, just print that we received something
                System.out.println("Received packet from " +
                        packet.getAddress() + ":" + packet.getPort() +
                        " - " + packet.getLength() + " bytes");
            }

        } catch (SocketException e) {
            System.err.println("Error creating socket: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("Server socket closed");
            }
        }
    }
}