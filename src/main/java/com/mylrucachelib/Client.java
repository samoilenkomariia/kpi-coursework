package com.mylrucachelib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {
    private static final String HOST = "localhost";
    private static final int CLIENTS = 50;
    private static final int REQUESTS_PER_CLIENT = 1000;
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicLong latency = new AtomicLong(0);
    private static final double WRITE_PROBABILITY = 0.3;
    private static final Logger logger = Logger.getLogger(Client.class.getName());
    static {
        LoggerSetup.setupLogger(Client.class.getName(), "threaded-client.log", false);
    }

    public static void main(String[] args) throws InterruptedException {
        int port = 8080;
        int clients = CLIENTS;
        int reqs = REQUESTS_PER_CLIENT;
        int keyRange = CLIENTS;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        if (args.length > 1) clients = Integer.parseInt(args[1]);
        if (args.length > 2) reqs = Integer.parseInt(args[2]);
        if (args.length > 3) keyRange = Integer.parseInt(args[3]);
        Client client = new Client();
        System.out.println(client.startTest(clients, reqs, port, keyRange));
    }

    public static Stats runTest(int clients, int requests, int port, int keyRange) throws InterruptedException {
        return new com.mylrucachelib.Client().startTest(clients, requests, port, keyRange);
    }

    public static Stats runTest(int port) throws InterruptedException {
        return runTest(CLIENTS, REQUESTS_PER_CLIENT, port, CLIENTS);
    }

    private List<String> generateCommands(int keyRange) {
        List<String> commands = new ArrayList<>();
        for (int i = 0; i < keyRange; i++) {
            if (Math.random() < WRITE_PROBABILITY) {
                commands.add("put " + i + " value");
            } else {
                commands.add("get " + i);
            }
        }
        return commands;
    }

    public Stats startTest(int clients, int requests, int port, int keyRange) throws InterruptedException {
        if (clients <= 0 || requests <= 0) {
            logger.log(Level.SEVERE, "Client or requests must be greater than zero");
            throw new IllegalArgumentException("Invalid arguments");
        }
        List<String> commands = generateCommands(keyRange);
        logger.info(String.format("Starting LRUCacheClient targeting %s:%d (Clients: %d, Reqs: %d)%n",
                HOST, port, clients, requests));
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(clients);
        long start = System.nanoTime();
        for (int i = 1; i <= clients; i++) {
            pool.submit(new ClientWorker(port, requests, keyRange, commands, this, startGate, endGate));
        }
        startGate.countDown();
        try {
            endGate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdown();
        long end = System.nanoTime();
        return getStatistics(start, end, clients, requests);
    }

    private Stats getStatistics(long startTime, long endTime, int clientCount, int requestCount) {
        double time = (endTime - startTime)/1_000_000_000.0;
        int totalRequests = successfulRequests.get() + failedRequests.get();
        double throughputS = (double) totalRequests / time;
        double latencyMs = (double) latency.get() / totalRequests / 1_000_000.0;
        return new Stats(clientCount, requestCount, totalRequests,
                successfulRequests.get(), failedRequests.get(), time,
                throughputS, latencyMs);
    }

    private static class ClientWorker implements Runnable {
        private final int targetPort;
        private final int requests;
        private final int keyRange;
        private final List<String> commands;
        private final Client parent;
        private int successCount = 0;
        private int failedCount = 0;
        private long latency = 0L;
        private final CountDownLatch startGate;
        private final CountDownLatch endGate;

        public ClientWorker(int targetPort, int requestCount, int keyRange, List<String> commands, Client parent,
                            CountDownLatch startGate, CountDownLatch endGate) {
            this.targetPort = targetPort;
            this.requests = requestCount;
            this.commands = commands;
            this.keyRange = keyRange;
            this.parent = parent;
            this.startGate = startGate;
            this.endGate = endGate;
        }

        private boolean performRequest(PrintWriter output, BufferedReader input, int index) throws IOException {
            String command = this.commands.get(index);
            output.println(command);
            String response = input.readLine();
            if (response == null) return false;
            String[] validResponses = {"OK", "VALUE", "NOT_FOUND"};
            for (var r : validResponses) {
                if (response.contains(r)) return true;
            }
            return false;
        }

        @Override
        public void run() {
            int i = 0;
            try (
                    Socket socket = new Socket(HOST, targetPort);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter output = new PrintWriter(socket.getOutputStream(), true)
            ) {
                startGate.await();
                for (i = 0; i < requests; i++) {
                    int index = i % keyRange;
                    long opStartTime = System.nanoTime();
                    boolean success = performRequest(output, input, index);
                    long opEndTime = System.nanoTime();
                    this.latency += opEndTime - opStartTime;
                    if (success) this.successCount++;
                    else this.failedCount++;
                }
            } catch (IOException | InterruptedException e) {
                logger.warning("Error working on client: " + e.getMessage());
                this.failedCount -= i;
            } finally {
                parent.latency.addAndGet(this.latency);
                parent.successfulRequests.addAndGet(this.successCount);
                parent.failedRequests.addAndGet(this.failedCount);
                endGate.countDown();
            }
        }
    }
}
