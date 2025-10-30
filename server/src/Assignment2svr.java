import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.*;

public class Assignment2svr {
    private static final int BUFFER_SIZE = 1024;
    private static final String HEADER_PREFIX = "META:";
    private static final String HEADER_FILENAME = "META:FILENAME:";
    private static final String HEADER_END = "META:END";

    // Track all client sessions
    private static final ConcurrentHashMap<ClientKey, ClientSession> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Assignment2svr <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("UDP Server listening on port " + port);

            byte[] buf = new byte[BUFFER_SIZE];
            ExecutorService pool = Executors.newFixedThreadPool(8); // up to 8 clients concurrently

            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                ClientKey key = new ClientKey(packet.getAddress(), packet.getPort());
                String msg = new String(packet.getData(), 0, packet.getLength());

                // Handle headers first
                if (msg.startsWith(HEADER_PREFIX)) {
                    if (msg.startsWith(HEADER_FILENAME)) {
                        String filename = msg.substring(HEADER_FILENAME.length());
                        File file = getUniqueFile(filename);
                        ClientSession session = new ClientSession(socket, key, file);
                        sessions.put(key, session);
                        pool.submit(session);
                        session.sendAck();
                        System.out.printf("Started session for %s, saving as %s%n", key, file.getName());
                    } else if (msg.equals(HEADER_END)) {
                        ClientSession session = sessions.remove(key);
                        if (session != null) {
                            session.close();
                            System.out.printf("Completed transfer from %s (%d bytes)%n", key, session.getTotalBytes());
                        }
                        sendAck(socket, packet.getAddress(), packet.getPort());
                    }
                    continue;
                }

                // Handle data packets
                ClientSession session = sessions.get(key);
                if (session != null) {
                    session.queueData(Arrays.copyOf(packet.getData(), packet.getLength()));
                    sendAck(socket, packet.getAddress(), packet.getPort());
                } else {
                    System.err.println("Warning: received data without active session from " + key);
                }
            }
        }
    }

    private static void sendAck(DatagramSocket socket, InetAddress addr, int port) throws IOException {
        byte[] ack = {1};
        socket.send(new DatagramPacket(ack, ack.length, addr, port));
    }

    private static File getUniqueFile(String baseName) {
        File file = new File(baseName);
        if (!file.exists()) return file;
        String name = baseName, ext = "";
        int dot = baseName.lastIndexOf('.');
        if (dot != -1) {
            name = baseName.substring(0, dot);
            ext = baseName.substring(dot);
        }
        int count = 1;
        while (file.exists()) {
            file = new File(name + "(" + count + ")" + ext);
            count++;
        }
        return file;
    }

    // === Client key and session classes ===
    private static record ClientKey(InetAddress address, int port) {
        @Override public String toString() { return address.getHostAddress() + ":" + port; }
    }

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
            } catch (Exception e) {
                System.err.println("Session error for " + key + ": " + e.getMessage());
            }
        }

        public void queueData(byte[] data) { queue.offer(data); }

        public void close() { running = false; }

        public void sendAck() throws IOException {
            Assignment2svr.sendAck(socket, key.address(), key.port());
        }

        public long getTotalBytes() { return totalBytes; }
    }
}
