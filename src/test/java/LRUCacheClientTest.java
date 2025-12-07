import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/*
    Smoke test: assert correct work of service & client
 */

public class LRUCacheClientTest {
    private static int PORT = 0;

    @BeforeAll
    static void startServer() {
        Thread server = new Thread(() -> {
            try {
                LRUCacheService.startService(1000, 512, PORT);
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
            try (Socket s = new Socket("localhost", PORT)) {
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
    @BeforeEach
    void init(TestInfo testInfo) {
        System.out.println();
        System.out.printf("STARTING TEST: %s%n", testInfo.getDisplayName());
    }

    @Test
    void test10Client100ReqsRunningSuccessfully() {
        assertDoesNotThrow(() -> {
            LRUCacheClient.runTest(10, 100, PORT);
        }, "Client failed with execution during sanity test for 10 clients & 100 reqs");
    }

    @Test
    void testDefaultClientRequests() {
        assertDoesNotThrow(() -> {
            LRUCacheClient.runTest(PORT); // 50 clients, 1000 requests
        }, "Client failed with execution during sanity test for default initial parameters");
    }

    @Test
    void test50Clients10000Requests() {
        assertDoesNotThrow(() -> {
            LRUCacheClient.runTest(50, 10000, PORT);
        }, "Client failed with execution during sanity test for 50 clients & 10000 reqs");
    }
}
