package com.mylrucachelib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadedServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int THREAD_POOL_SIZE = 50;

    private LRUCache<String,String> cache;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;

    public static void main(String[] args) throws IOException {
        int capacity = 100;
        int concurrencyLevel = 16;
        int port = DEFAULT_PORT;
        if (args.length > 0) capacity = Integer.parseInt(args[0]);
        if (args.length > 1) concurrencyLevel = Integer.parseInt(args[1]);
        if (args.length > 2) port = Integer.parseInt(args[2]);
        ThreadedServer service = new ThreadedServer();
        service.start(capacity, concurrencyLevel, port);
    }

    public void start(int cap, int concLevel, int port) throws IOException {
        this.cache = new LRUCache<>(cap, concLevel);
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket socket = new ServerSocket(port)) {
            this.serverSocket = socket;
            port = socket.getLocalPort();
            System.out.println("Starting LRU Cache service on port " + socket.getLocalPort());

            while (!socket.isClosed()) {
                try {
                    Socket clientSocket = socket.accept();
                    threadPool.submit(new ClientHandler(clientSocket, this.cache));
                } catch (IOException e) {
                    if (socket.isClosed()) {
                        System.out.println("Server stopped on port " + port);
                        break;
                    }
                    e.printStackTrace();
                }
            }
        } finally {
            if (threadPool != null) threadPool.shutdown();
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public int getPort() {
        return serverSocket != null && !serverSocket.isClosed() ? serverSocket.getLocalPort() : 0;
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final LRUCache<String, String> cacheInstance;

        public ClientHandler(Socket socket, LRUCache<String, String> cache) {
            this.socket = socket;
            this.cacheInstance = cache;
        }

        @Override
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
                            this.cacheInstance.put(key, value);
                            output.println("OK");
                        }
                    }
                    case "GET" -> {
                        if (parts.length != 2) {
                            output.println("ERROR_USAGE_GET");
                        } else {
                            String key = parts[1];
                            String value = this.cacheInstance.get(key);
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
