import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Random.class)
public class LRUCacheServiceTest {

    private static final int PORT = 8080;

    @BeforeAll
    static void startServer() {
        Thread thread = new Thread(() -> {
            try {
                LRUCacheService.main(new String[]{});
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 5000) {
            try (Socket s = new Socket("localhost", PORT)) {
                return;
            } catch (IOException e) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("Server did not start within 5 seconds");
    }

    @Test
    void testPutAndGet() {
        try (Socket socket = new Socket("localhost", 8080);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println("put key-testPutAndGet value-1");
            String response = reader.readLine();
            assertEquals("OK", response, "expected: OK, got: %s".formatted(response));
            writer.println("get key-testPutAndGet");
            response = reader.readLine();
            assertEquals("VALUE value-1", response, "expected: VALUE value-1, got: %s".formatted(response));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testMalformedPutCommand() {
        try (Socket socket = new Socket("localhost", 8080);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println("put key-testMalformedCommands");
            String response = reader.readLine();
            assertEquals("ERROR_USAGE_PUT", response, "expected: ERROR_USAGE_PUT, got: %s".formatted(response));
            writer.println("put");
            response = reader.readLine();
            assertEquals("ERROR_USAGE_PUT", response, "expected: ERROR_USAGE_PUT, got: %s".formatted(response));
            writer.println("put key-testMalformedCommand data1 data2");
            response = reader.readLine();
            assertEquals("ERROR_USAGE_PUT", response, "expected: ERROR_USAGE_PUT, got: %s".formatted(response));
        } catch  (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testMalformedGetCommand() {
        try (Socket socket = new Socket("localhost", 8080);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println("get");
            String response = reader.readLine();
            assertEquals("ERROR_USAGE_GET", response, "expected: ERROR_USAGE_GET, got: %s".formatted(response));
            writer.println("get key val");
            response = reader.readLine();
            assertEquals("ERROR_USAGE_GET", response, "expected: ERROR_USAGE_GET, got: %s".formatted(response));
        } catch  (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testEmptyGetCommand() {
        try (Socket socket = new Socket("localhost", 8080);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println("get key-testEmptyGetCommand");
            String response = reader.readLine();
            assertEquals("NOT_FOUND", response, "expected: NOT_FOUND, got: %s".formatted(response));
        } catch  (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testUnknownCommands() {
        try (Socket socket = new Socket("localhost", 8080);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println("gte key");
            String response = reader.readLine();
            assertEquals("ERROR_UNKNOWN_COMMAND", response, "expected: ERROR_UNKNOWN_COMMAND, got: %s".formatted(response));
            writer.println(" ");
            response = reader.readLine();
            assertEquals("ERROR_UNKNOWN_COMMAND", response, "expected: ERROR_UNKNOWN_COMMAND, got: %s".formatted(response));
        } catch  (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testConcurrentClientsAtOnce() throws InterruptedException, ExecutionException {
        int clientCount = 100;
        ExecutorService pool = Executors.newFixedThreadPool(clientCount);
        Callable<Boolean> task = getBooleanCallable(clientCount);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < clientCount; i++) {
            results.add(pool.submit(task));
        }
        for (int i = 0; i < clientCount; i++) {
            assertTrue(results.get(i).get(), "Client " + i + " failed to put/get concurrently");
        }
        pool.shutdown();
    }

    private static Callable<Boolean> getBooleanCallable(int clientCount) {
        CountDownLatch latch = new CountDownLatch(clientCount);

        return () -> {
            try (Socket socket = new Socket("localhost", PORT);
                 PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                latch.countDown();
                latch.await();

                String threadKey = "key-" + (Thread.currentThread().getId() % 50);
                output.println("PUT " + threadKey + " val");
                String response = input.readLine();
                if (!"OK".equals(response)) {
                    System.out.printf("Client failed to put %s, response %s%n", threadKey, response);
                    return false;
                }
                output.println("GET " + threadKey);
                response = input.readLine();
                if (!("VALUE val").equals(response)) {
                    System.out.printf("Client failed to get %s, response %s%n", threadKey, response);
                }
                return ("VALUE val").equals(response);
            }
        };
    }

    private static Callable<Boolean> getBooleanCallable() {
        return () -> {
            try (Socket socket = new Socket("localhost", PORT);
                 PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String threadKey = "key_" + (Thread.currentThread().getId() % 50);
                output.println("PUT " + threadKey + " val");
                String response = input.readLine();
                if (!"OK".equals(response)) {
                    System.out.printf("Client failed to put %s, response %s%n", threadKey, response);
                    return false;
                }
                output.println("GET " + threadKey);
                response = input.readLine();
                if (!("VALUE val").equals(response)) {
                    System.out.printf("Client failed to get %s, response %s%n", threadKey, response);
                }
                return ("VALUE val").equals(response);
            }
        };
    }

    @Test
    void testManyRequestsFromUsers() throws ExecutionException, InterruptedException {
        int requestCount = 2000;
        int clientCount = 100;
        ExecutorService pool = Executors.newFixedThreadPool(clientCount);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            results.add(pool.submit(getBooleanCallable()));
        }
        for (int i = 0; i < requestCount; i++) {
            assertTrue(results.get(i).get(), "Client " + i + " failed to put/get concurrently");
        }
        pool.shutdown();
    }
}
