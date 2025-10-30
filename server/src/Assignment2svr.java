import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Assignment2svr — This is a Concurrent UDP file receiver server
 * ----------------------------------------------------
 * Handles up to multiple simultaneous client uploads of files with possibly
 * same file names (we handle uniqueness on this server end).
 *
 * Each client is identified by its IP:port and runs in its own thread.
 *
 * Protocol summary:
 *  - Client sends "META:FILENAME:<filename>" to start
 *          -- This is so the filename is preserved from the client
 *  - Server responds with 1-byte ACK
 *          -- Used to ACK so we know the file reached appropriately
 *  - Client sends data packets
 *          -- Data to send in packets of 1KB or 1024 Bytes each
 *  - Client sends "META:END" when finished
 *          -- This completes our file send, so server knows it
 *             can stop listening for packets from this client thread
 *  - Server finalizes and closes file, acknowledging each step
 */
public class Assignment2svr {

    /**
     * Set up custom prefixes to make filename extraction and data extraction convenient between client and server
     */
    private static final int BUFFER_SIZE = 1024;
    private static final String HEADER_PREFIX = "META:";
    private static final String HEADER_FILENAME = "META:FILENAME:";
    private static final String HEADER_END = "META:END";

    // Track all active client upload sessions
    private static final ConcurrentHashMap<ClientKey, ClientSession> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Assignment2svr <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        if (!isPortSafe(port) || !isPortAvailable(port)) {
            System.err.println("Unsafe or unavailable port: " + port);
            System.exit(1);
        }

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("UDP Server listening on port " + port);

            byte[] buffer = new byte[BUFFER_SIZE];
            ExecutorService threadPool = Executors.newFixedThreadPool(8); // up to 8 clients concurrently

            // We assume the server wants to listen indefinitely, delegating to threads
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                ClientKey key = new ClientKey(packet.getAddress(), packet.getPort());
                String msg = new String(packet.getData(), 0, packet.getLength());

                // Handle control packets (META headers)
                if (msg.startsWith(HEADER_PREFIX)) {
                    if (msg.startsWith(HEADER_FILENAME)) {
                        handleFileStart(socket, threadPool, key, msg.substring(HEADER_FILENAME.length()));
                    } else if (msg.equals(HEADER_END)) {
                        handleFileEnd(socket, key);
                    }
                    continue;
                }

                // Handle raw data packets
                handleFileData(socket, key, packet);
            }
        }
    }

    // Actual file handlers

    private static void handleFileStart(DatagramSocket socket, ExecutorService pool, ClientKey key, String filename) throws IOException {
        File file = getUniqueFile(filename);
        ClientSession session = new ClientSession(socket, key, file);
        sessions.put(key, session);
        pool.submit(session);

        session.sendAck(); // Let client know, server has completed the file
        System.out.printf("Session started %s : saving file as '%s'%n", key, file.getName());
    }

    private static void handleFileEnd(DatagramSocket socket, ClientKey key) throws IOException {
        ClientSession session = sessions.remove(key);
        if (session != null) {
            session.close();
            System.out.printf("Completed transfer from %s (%d bytes)%n", key, session.getTotalBytes());
        }
        sendAck(socket, key.address(), key.port());
    }

    private static void handleFileData(DatagramSocket socket, ClientKey key, DatagramPacket packet) throws IOException {
        ClientSession session = sessions.get(key);
        if (session != null) {
            session.queueData(Arrays.copyOf(packet.getData(), packet.getLength()));
            sendAck(socket, key.address(), key.port());
        } else {
            System.err.printf("Data received without active session from %s%n", key);
        }
    }

    // Helper methods

    private static void sendAck(DatagramSocket socket, InetAddress addr, int port) throws IOException {
        // A byte empty request to let the client listening on the socket to ACKNOWLEDGE
        socket.send(new DatagramPacket(new byte[]{1}, 1, addr, port));
    }

    // Check if port is available, as in NOT in use
    private static boolean isPortAvailable(int port) {
        try (DatagramSocket ignored = new DatagramSocket(port)) {
            return true; // If it can bind, it’s free
        } catch (IOException e) {
            return false; // Already in use or restricted
        }
    }

    // Check if its a port in the appropriate range
    private static boolean isPortSafe(int port) {
        // Ports below 1024 are reserved, and 49152–65535 are dynamic/private (usually safe)
        return port >= 1024 && port <= 65535;
    }


    /**
     * We need to use this to generate a unique file name in case one already exists with it
     * locally on the server side.
     * Example: appending a copy number at the end, example.txt --> example(1).txt
     */
    private static File getUniqueFile(String baseName) {
        File file = new File(baseName);
        if (!file.exists()) return file;

        String name = baseName;
        String ext = "";
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex != -1) {
            name = baseName.substring(0, dotIndex);
            ext = baseName.substring(dotIndex);
        }

        int count = 1;
        while (file.exists()) {
            file = new File(name + "(" + count++ + ")" + ext);
        }
        return file;
    }

    /**
     * Represents a unique client identified by IP + port that we can use in the Concurrent Hash Map
     */
    private static record ClientKey(InetAddress address, int port) {
        @Override
        public String toString() {
            return address.getHostAddress() + ":" + port;
        }
    }

    /**
     *
     * Helper class for a client session. This handles individual client's and their thread
     * for file transfer. It handles taking incoming data from a queue in case the thread is
     * busy until file transfer is complete.
     */
    private static class ClientSession implements Runnable {
        private final DatagramSocket socket;
        private final ClientKey key;
        private final File file;
        private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        private volatile boolean running = true;
        private long totalBytes = 0;

        ClientSession(DatagramSocket socket, ClientKey key, File file) {
            this.socket = socket;
            this.key = key;
            this.file = file;
        }

        @Override
        public void run() {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                while (running || !queue.isEmpty()) {
                    byte[] data = queue.poll(1, TimeUnit.SECONDS);
                    if (data != null) {
                        fos.write(data);
                        totalBytes += data.length;
                    }
                }
                fos.flush();
            } catch (IOException | InterruptedException e) {
                System.err.printf("Error in session %s: %s%n", key, e.getMessage());
            }
        }

        public void queueData(byte[] data) {
            queue.offer(data);
        }

        public void close() {
            running = false;
        }

        public void sendAck() throws IOException {
            Assignment2svr.sendAck(socket, key.address(), key.port());
        }

        public long getTotalBytes() {
            return totalBytes;
        }
    }
}
