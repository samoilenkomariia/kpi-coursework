import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LRUCacheService {
    private static LRUCache<String,String> cache;
    private static final int PORT = 8080;
    private static final int threadPoolSize = 50;
    private static volatile ServerSocket serverSocket;

    public static void main(String[] args) throws IOException {
        int capacity = 100;
        int concurrencyLevel = 16;
        int port = PORT;
        if (args.length > 0) capacity = Integer.parseInt(args[0]);
        if (args.length > 1) concurrencyLevel = Integer.parseInt(args[1]);
        if (args.length > 2) port = Integer.parseInt(args[2]);
        startService(capacity, concurrencyLevel, port);
    }

    public static void startService(int cap, int concLevel, int port) throws IOException {
        cache = new LRUCache<>(cap, concLevel);
        ExecutorService threadPool = Executors.newFixedThreadPool(threadPoolSize);

        try (ServerSocket socket = new ServerSocket(port)) {
            serverSocket = socket;
            port = socket.getLocalPort(); // when requested port = 0, then it will be random available port given by os
            System.out.println("Starting LRU Cache service on port " + port);

            while (!socket.isClosed()) {
                try {
                    Socket clientSocket = socket.accept();
                    threadPool.submit(new ClientHandler(clientSocket));
                } catch (IOException e) {
                    if (socket.isClosed()) {
                        threadPool.shutdown();
                        System.out.printf("Server is closed on port %d%n", port);
                        break;
                    }
                    e.printStackTrace();
                }
            }
        }
    }

    public static void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static int getPort() {
        return serverSocket != null && !serverSocket.isClosed() ? serverSocket.getLocalPort() : 0;
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter output = new PrintWriter(socket.getOutputStream(), true)
                    ) {
                String line;
                while ((line = input.readLine()) != null) {
                    processCommand(line, output);
                }
            } catch (IOException e) {
                //ignore
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    //ignore
                }
            }
        }

        private void processCommand(String line, PrintWriter output) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0) {
                output.println("ERROR_EMPTY_COMMAND");
                return;
            }
            String command = parts[0].toUpperCase();
            try {
                switch (command) {
                    case "PUT" -> {
                        if (parts.length != 3) {
                            output.println("ERROR_USAGE_PUT");
                        } else {
                            String key = parts[1];
                            String value = parts[2];
                            cache.put(key, value);
                            output.println("OK");
                        }
                    }
                    case "GET" -> {
                        if (parts.length != 2) {
                            output.println("ERROR_USAGE_GET");
                        } else {
                            String key = parts[1];
                            String value = cache.get(key);
                            if (value == null) {
                                output.println("NOT_FOUND");
                            } else {
                                output.println("VALUE " + value);
                            }
                        }
                    }
                    default -> output.println("ERROR_UNKNOWN_COMMAND");
                }
            } catch (Exception e) {
                output.println("ERROR_INTERNAL " + e.getMessage());
            }
        }
    }
}
