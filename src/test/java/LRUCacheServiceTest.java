import com.mylrucachelib.LRUCacheService;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
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
                LRUCacheService.startService(100, 16, PORT);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
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
    void testConcurrentClientsAtOnce() {
        int clientCount = 1000;

        ExecutorService pool = Executors.newFixedThreadPool(clientCount);
        List<Future<?>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(clientCount);
        for (int i = 0; i < clientCount; i++) {
            futures.add(pool.submit(() -> {
                try (Socket socket = new Socket("localhost", PORT);
                     PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    latch.countDown();
                    latch.await();

                    String threadKey = "keyService-" + (Thread.currentThread().getId() % 50);
                    output.println("PUT " + threadKey + " val" + threadKey);
                    String expected = "OK";
                    String response = input.readLine();
                    if (!expected.equals(response)) {
                        fail(String.format("Client failed to put %s, expected %s, but got response %s%n", threadKey, expected, response));
                    }
                    output.println("GET " + threadKey);
                    response = input.readLine();
                    expected = "VALUE val"  + threadKey;
                    if (!expected.equals(response)) {
                        fail(String.format("Client failed to get %s, expected %s, but got %s%n", threadKey, expected, response));
                    }

                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        for (var future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                fail("Thread failed " + e.getMessage());
            }
        }
        pool.shutdown();
    }

    @Test
    void testAbruptDisconnect() {
        try (Socket badSocket = new Socket("localhost", PORT);
             PrintWriter badWriter = new PrintWriter(badSocket.getOutputStream(), true)) {
            badWriter.print("PUT key-broken ");
            badWriter.flush();
            badSocket.close();
        } catch (IOException e) {
            fail("Failed to connect bad client");
        }

        assertDoesNotThrow(() -> {
            try (Socket goodSocket = new Socket("localhost", PORT);
                 PrintWriter goodWriter = new PrintWriter(goodSocket.getOutputStream(), true);
                 BufferedReader goodReader = new BufferedReader(new InputStreamReader(goodSocket.getInputStream()))) {

                goodWriter.println("PUT key-alive value-alive");
                String response = goodReader.readLine();
                assertEquals("OK", response, "Server failed to recover from abrupt disconnect");
            }
        });
    }
}
