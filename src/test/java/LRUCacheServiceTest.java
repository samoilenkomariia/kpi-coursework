import com.mylrucachelib.LRUCacheService;
import org.junit.jupiter.api.*;

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
    private static int PORT = 0;

    @BeforeAll
    static void startServer() {
        Thread server = new Thread(() -> {
            try {
                LRUCacheService.startService(1000, 16, PORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        server.setDaemon(true);
        server.start();
        long start = System.currentTimeMillis();
        while (LRUCacheService.getPort() == 0) {
            if (System.currentTimeMillis() - start > 5000) {
                throw new RuntimeException("Server did bind port within 5 seconds");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
        }
        PORT = LRUCacheService.getPort();
        System.out.println("LRUCacheService started on port " + PORT);
        start = System.currentTimeMillis();
        // sanity check
        while(System.currentTimeMillis() - start < 5000) {
            try (Socket ignored1 = new Socket("localhost", PORT)) {
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("Server did not start within 5 seconds");
    }
    @AfterAll
    static void stopServer() {
        LRUCacheService.stop();
    }

    @Test
    void testPutAndGet() {
        try (Socket socket = new Socket("localhost", PORT);
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
        try (Socket socket = new Socket("localhost", PORT);
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
        try (Socket socket = new Socket("localhost", PORT);
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
        try (Socket socket = new Socket("localhost", PORT);
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
        try (Socket socket = new Socket("localhost", PORT);
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

    // verify correctness of service work & responses concurrently
    @Test
    void testConcurrentClientsAtOnce() throws InterruptedException, ExecutionException {
        int clientCount = 1000;
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
                if (!"VALUE val".equals(response)) {
                    System.out.printf("Client failed to get %s, response %s%n", threadKey, response);
                }
                return ("VALUE val").equals(response);
            }
        };
    }
}
