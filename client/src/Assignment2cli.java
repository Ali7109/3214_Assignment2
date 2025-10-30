import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Assignment2cli {
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT_MS = 2000; // 2 seconds timeout for ACKs
    private static final int MAX_RETRIES = 5;

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

            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress serverAddress = InetAddress.getByName(serverIP);

            System.out.println("Connecting to server " + serverIP + ":" + port);
            System.out.println("Sending file: " + filename + " (" + file.length() + " bytes)");

            // === Step 1: Send filename header ===
            sendWithAck(socket, serverAddress, port, ("META:FILENAME:" + file.getName()).getBytes(StandardCharsets.UTF_8), "filename");

            // === Step 2: Send file data ===
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesSent = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                sendWithAck(socket, serverAddress, port, chunk, "data");
                totalBytesSent += bytesRead;
                double progress = (totalBytesSent / (double) file.length()) * 100;
                System.out.printf("Sent %d bytes (%.2f%%)\n", totalBytesSent, progress);
            }

            // === Step 3: Send end signal ===
            sendWithAck(socket, serverAddress, port, "META:END".getBytes(StandardCharsets.UTF_8), "end signal");

            System.out.println("✅ File transfer completed successfully! Total bytes: " + totalBytesSent);

        } catch (SocketTimeoutException e) {
            System.err.println("❌ Timeout waiting for ACK from server. Transfer failed.");
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a packet and waits for an ACK (1-byte response) from the server.
     * Retries up to MAX_RETRIES times if no ACK is received.
     */
    private static void sendWithAck(DatagramSocket socket, InetAddress addr, int port, byte[] data, String stage) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        byte[] ackBuf = new byte[1];
        DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            socket.send(packet);
            try {
                socket.receive(ackPacket);
                if (ackBuf[0] == 1) {
                    return; // ACK received, success
                }
            } catch (SocketTimeoutException e) {
                System.err.printf("⚠️ No ACK for %s (attempt %d/%d)... retrying%n", stage, attempt, MAX_RETRIES);
            }
        }

        throw new IOException("No ACK received after " + MAX_RETRIES + " attempts for stage: " + stage);
    }

    private static int parsePort(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1024 and 65535");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + portStr);
        }
    }
}
