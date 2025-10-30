import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Assignment2cli {
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java Assignment2cli <server_ip> <port> <filename>");
            System.exit(1);
        }

        String serverIP = args[0];
        int port = parsePort(args[1]);
        String filename = args[2];

        File file = new File(filename);
        if (!file.exists() || !file.isFile()) {
            System.err.println("Error: File not found or not a regular file: " + filename);
            System.exit(1);
        }

        try (DatagramSocket socket = new DatagramSocket();
             FileInputStream fis = new FileInputStream(file)) {

            InetAddress serverAddress = InetAddress.getByName(serverIP);

            System.out.println("Connecting to server " + serverIP + ":" + port);
            System.out.println("Sending file: " + filename + " (" + file.length() + " bytes)");

            // Send filename first
            byte[] filenameBytes = file.getName().getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(filenameBytes, filenameBytes.length, serverAddress, port));
            System.out.println("Sent filename: " + file.getName());

            // Send file data in chunks
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesSent = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                DatagramPacket packet = new DatagramPacket(buffer, bytesRead, serverAddress, port);
                socket.send(packet);
                totalBytesSent += bytesRead;
                double progress = (totalBytesSent / (double) file.length()) * 100;
                System.out.printf("Sent %d bytes (%.2f%%)\n", totalBytesSent, progress);
            }

            // Optional: send end-of-file signal
            byte[] endSignal = "META:END".getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(endSignal, endSignal.length, serverAddress, port));

            System.out.println("File sent successfully! Total bytes: " + totalBytesSent);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int parsePort(String portStr) {
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                System.err.println("Error: Port must be between 1024 and 65535");
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid port number");
            System.exit(1);
            port = -1; // unreachable, but compiler needs it
        }
        return port;
    }
}
