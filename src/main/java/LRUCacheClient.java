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
        runTest(clients, reqs, port);
    }

    public static String runTest(int port) {
        return runTest(CLIENTS, REQUESTS_PER_CLIENT, port);
    }

    public static String runTest(int clients, int requests, int port) {
        if (clients <= 0 || requests <= 0) {
            throw new IllegalArgumentException("Number of clients and requests must be positive");
        }
        successfulRequests.set(0);
        failedRequests.set(0);
        latency.set(0);
        System.out.printf("Starting LRUCacheClient targeting %s:%d (Clients: %d, Reqs: %d)%n",
                HOST, port, clients, requests);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        List<Future<?>> tasks = new ArrayList<>();
        long start = System.nanoTime();
        for (int i = 1; i <= clients; i++) {
            tasks.add(pool.submit(new Client(i, port, requests)));
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
        return printStatistics(start, end, clients, requests);
    }

    private static String printStatistics(long startTime, long endTime, int clientCount, int requestCount) {
        double time = (endTime - startTime)/1_000_000_000.0;
        int totalRequests = successfulRequests.get() + failedRequests.get();
        double throughputS = (double) totalRequests / time;
        double latencyMs = (double) latency.get() / totalRequests / 1_000_000.0;
        return String.format("""
                Threads %d, requests per thread %d
                Total requests %d
                Successful requests %d
                Failed requests %d
                Total time %f s
                Throughput %f req/s
                Average latency %f ms
                """, clientCount, requestCount, totalRequests,
                successfulRequests.get(), failedRequests.get(), time,
                throughputS, latencyMs);
    }

    private static class Client implements Runnable {
        private final int id;
        private final int targertPort;
        private final int requests;

        public Client(int id, int targertPort, int requestCount) {
            this.id = id;
            this.targertPort = targertPort;
            this.requests = requestCount;
        }

        private boolean performRequest(PrintWriter output, BufferedReader input) throws IOException {
            if (Math.random() < WRITE_PROBABILITY) {
                String command = "put key" + this.id + " data" + this.id;
                output.println(command);
                String response = input.readLine();
                return response != null && response.equals("OK");
            } else {
                String key = "key" + this.id;
                String command = "get " + key;
                output.println(command);
                String response = input.readLine();
                return response != null && (response.contains("VALUE") || response.contains("NOT_FOUND"));
            }
        }

        @Override
        public void run() {
            try (
                    Socket socket = new Socket(HOST, targertPort);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter output = new PrintWriter(socket.getOutputStream(), true)
            ) {
                for (int i = 0; i < requests; i++) {
                    long opStartTime = System.nanoTime();
                    boolean success = performRequest(output, input);
                    long opEndTime = System.nanoTime();
                    latency.addAndGet(opEndTime - opStartTime);
                    if (success) successfulRequests.incrementAndGet();
                    else failedRequests.incrementAndGet();
                }
            } catch (IOException e) {
                failedRequests.addAndGet(requests);
            }
        }
    }
}
