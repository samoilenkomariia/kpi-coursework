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

public class LRUCacheClient {
    private static final String HOST = "localhost";
    private static final int CLIENTS = 50;
    private static final int REQUESTS_PER_CLIENT = 1000;
    private static final AtomicInteger successfulRequests = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);
    private static final AtomicLong latency = new AtomicLong(0);
    private static final double WRITE_PROBABILITY = 0.3;

    public static void main(String[] args) {
        int port = 8080;
        int clients = CLIENTS;
        int reqs = REQUESTS_PER_CLIENT;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        if (args.length > 1) clients = Integer.parseInt(args[1]);
        if (args.length > 2) reqs = Integer.parseInt(args[2]);
        System.out.println(runTest(clients, reqs, port));
    }

    public static Stats runTest(int port) {
        return runTest(CLIENTS, REQUESTS_PER_CLIENT, port);
    }

    public static Stats runTest(int clients, int requests, int port) {
        if (clients <= 0 || requests <= 0) {
            throw new IllegalArgumentException("Number of clients and requests must be positive");
        }
        successfulRequests.set(0);
        failedRequests.set(0);
        latency.set(0);
        System.out.printf("Starting LRUCacheClient targeting %s:%d (Clients: %d, Reqs: %d)%n",
                HOST, port, clients, requests);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        long start = System.nanoTime();
        for (int i = 1; i <= clients; i++) {
            pool.submit(new Client(i, port, requests, clients));
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(2, TimeUnit.MINUTES)) {
                System.err.println("Client test timed out");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long end = System.nanoTime();
        return getStatistics(start, end, clients, requests);
    }

    private static Stats getStatistics(long startTime, long endTime, int clientCount, int requestCount) {
        double time = (endTime - startTime)/1_000_000_000.0;
        int totalRequests = successfulRequests.get() + failedRequests.get();
        double throughputS = (double) totalRequests / time;
        double latencyMs = (double) latency.get() / totalRequests / 1_000_000.0;
        return new Stats(clientCount, requestCount, totalRequests,
                successfulRequests.get(), failedRequests.get(), time,
                throughputS, latencyMs);
    }

    private static class Client implements Runnable {
        private final int id;
        private final int targertPort;
        private final int requests;
        private final int keyRange;

        public Client(int id, int targertPort, int requestCount, int keyRange) {
            this.id = id;
            this.targertPort = targertPort;
            this.requests = requestCount;
            this.keyRange = keyRange;
        }

        private boolean performRequest(PrintWriter output, BufferedReader input) throws IOException {
            int randomKey = ThreadLocalRandom.current().nextInt(keyRange);
            if (ThreadLocalRandom.current().nextDouble(1.0) < WRITE_PROBABILITY) {
                String command = "put key" + randomKey + " data" + this.id;
                output.println(command);
                String response = input.readLine();
                return response != null && response.equals("OK");
            } else {
                String key = "key" + randomKey;
                String command = "get " + key;
                output.println(command);
                String response = input.readLine();
                return response != null && (response.contains("VALUE") || response.contains("NOT_FOUND"));
            }
        }

        @Override
        public void run() {
            int i = 0;
            try (
                    Socket socket = new Socket(HOST, targertPort);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter output = new PrintWriter(socket.getOutputStream(), true)
            ) {
                for (i = 0; i < requests; i++) {
                    long opStartTime = System.nanoTime();
                    boolean success = performRequest(output, input);
                    long opEndTime = System.nanoTime();
                    latency.addAndGet(opEndTime - opStartTime);
                    if (success) successfulRequests.incrementAndGet();
                    else failedRequests.incrementAndGet();
                }
            } catch (IOException e) {
                failedRequests.addAndGet(requests - i);
            }
        }
    }
}
