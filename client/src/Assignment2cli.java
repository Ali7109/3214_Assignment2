import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.File;
import java.io.FileInputStream;

public class Assignment2cli {
    public static void main(String[] args) {
        // Check if correct number of arguments provided
        if (args.length != 3) {
            System.err.println("Usage: java Assignment2cli <server_ip> <port> <filename>");
            System.exit(1);
        }

        String serverIP = args[0];
        int port;
        String filename = args[2];

        // Validate port number
        try {
            port = Integer.parseInt(args[1]);
            if (port < 1024 || port > 65535) {
                System.err.println("Error: Port must be between 1024 and 65535");
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid port number");
            System.exit(1);
            return;
        }

        // Check if file exists
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("Error: File '" + filename + "' not found");
            System.exit(1);
        }
        if (!file.isFile()) {
            System.err.println("Error: '" + filename + "' is not a file");
            System.exit(1);
        }

        DatagramSocket socket = null;
        FileInputStream fis = null;

        try {
            // Create UDP socket
            socket = new DatagramSocket();
            InetAddress serverAddress = InetAddress.getByName(serverIP);

            System.out.println("Connecting to server " + serverIP + ":" + port);
            System.out.println("Sending file: " + filename);
            System.out.println("File size: " + file.length() + " bytes");

            // First, send the filename as the first packet
            byte[] filenameBytes = file.getName().getBytes();
            DatagramPacket filenamePacket = new DatagramPacket(
                    filenameBytes,
                    filenameBytes.length,
                    serverAddress,
                    port
            );
            socket.send(filenamePacket);
            System.out.println("Sent filename: " + file.getName());

            // Small delay to ensure filename packet is processed first
            Thread.sleep(50);

            // Open file for reading
            fis = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytesSent = 0;

            // Read and send file in chunks
            while ((bytesRead = fis.read(buffer)) != -1) {
                DatagramPacket packet = new DatagramPacket(
                        buffer,
                        bytesRead,
                        serverAddress,
                        port
                );
                socket.send(packet);
                totalBytesSent += bytesRead;
                System.out.println("Sent " + bytesRead + " bytes (Total: " + totalBytesSent + ")");
            }

            System.out.println("File sent successfully! Total bytes: " + totalBytesSent);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            try {
                if (fis != null) fis.close();
            } catch (Exception e) {
                System.err.println("Error closing file: " + e.getMessage());
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}