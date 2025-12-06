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
    private static final int PORT = 8080;
    private static int CLIENTS = 50;
    private static int REQUESTS_PER_CLIENT = 100000;
    private static final AtomicInteger successfulRequests = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);
    private static final AtomicLong latency = new AtomicLong(0);
    private static final double WRITE_PROBABILITY = 0.3;

    public static void main(String[] args) {
        System.out.printf("Starting LRUCacheClient targeting at %s:%d%n", HOST, PORT);
        ExecutorService pool = Executors.newFixedThreadPool(CLIENTS);
        List<Future<?>> tasks = new ArrayList<>();
        long start = System.nanoTime();
        for (int i = 1; i <= CLIENTS; i++) {
            tasks.add(pool.submit(new Client(i)));
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                System.err.println("Timed out");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long end = System.nanoTime();
        printStatistics(start, end);
    }

    public static void runTest() {
        runTest(CLIENTS, REQUESTS_PER_CLIENT);
    }

    public static void runTest(int clients, int requests) {
        if (clients <= 0 || requests <= 0) {
            throw new IllegalArgumentException("Number of clients and requests must be positive");
        }
        if (clients == CLIENTS && requests == REQUESTS_PER_CLIENT) {
            successfulRequests.set(0);
            failedRequests.set(0);
            latency.set(0);
            main(new String[]{});
            return;
        }
        int oldClients = CLIENTS;
        int oldReqs = REQUESTS_PER_CLIENT;
        try {
            CLIENTS = clients;
            REQUESTS_PER_CLIENT = requests;
            successfulRequests.set(0);
            failedRequests.set(0);
            latency.set(0);
            main(new String[]{});
        } finally {
            CLIENTS = oldClients;
            REQUESTS_PER_CLIENT = oldReqs;
        }
    }

    private static void printStatistics(long startTime, long endTime) {
        double time = (endTime - startTime)/1_000_000_000.0;
        int totalRequests = successfulRequests.get() + failedRequests.get();
        double throughputS = (double) totalRequests / time;
        double latencyMs = (double) latency.get() / totalRequests / 1_000_000.0;
        System.out.printf("""
                Threads %d, requests per thread %d
                Total requests %d
                Successful requests %d
                Failed requests %d
                Total time %fs
                Throughput %f req/s
                Latency %fms
                """, CLIENTS, REQUESTS_PER_CLIENT, totalRequests,
                successfulRequests.get(), failedRequests.get(), time,
                throughputS, latencyMs);
    }

    private static class Client implements Runnable {
        private final int id;

        public Client(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (
                    Socket socket = new Socket(HOST, PORT);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter output = new PrintWriter(socket.getOutputStream(), true)
            ) {
                for (int i = 0; i < REQUESTS_PER_CLIENT; i++) {
                    long opStartTime = System.nanoTime();
                    if (Math.random() < WRITE_PROBABILITY) {
                        String command = "put key" + this.id + " data" + this.id;
                        output.println(command);
                        String inputLine = input.readLine();
                        if (inputLine != null && inputLine.equals("OK")) {
                            successfulRequests.incrementAndGet();
                        } else {
                            failedRequests.incrementAndGet();
                        }
                    } else {
                        String key = "key" + this.id;
                        String command = "get " + key;
                        output.println(command);
                        String inputLine = input.readLine();
                        if (inputLine.contains("VALUE") || inputLine.contains("NOT_FOUND")) {
                            successfulRequests.incrementAndGet();
                        }  else failedRequests.incrementAndGet();
                    }
                    long opEndTime = System.nanoTime();
                    latency.addAndGet(opEndTime - opStartTime);
                }
            } catch (IOException e) {
                failedRequests.incrementAndGet();
            }
        }
    }
}
