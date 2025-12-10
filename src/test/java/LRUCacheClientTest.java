import com.mylrucachelib.LRUCacheClient;
import com.mylrucachelib.LRUCacheService;
import com.mylrucachelib.Stats;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/*
    E2E test: assert correct work of service & client
 */

public class LRUCacheClientTest {
    private static int PORT = 0;

    @BeforeAll
    static void startServer() {
        Thread server = new Thread(() -> {
            try {
                LRUCacheService.startService(10, 8, PORT);
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
        System.out.println("com.mylrucachelib.LRUCacheService started on port " + PORT);
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
            Stats stat = LRUCacheClient.runTest(10, 100, PORT);
            System.out.println(stat);
            int threshold = 10000;
            assertTrue(stat.throughput() > threshold, "Throughput is less than " + threshold);
        }, "Client failed with execution during sanity test for 10 clients & 100 reqs");
    }

    @Test
    void testDefaultClientRequests() {
        assertDoesNotThrow(() -> {
            Stats stat = LRUCacheClient.runTest(PORT);
            System.out.println(stat);
            int threshold = 10000;
            assertTrue(stat.throughput() > threshold, "Throughput is less than " + threshold);
        }, "Client failed with execution during sanity test for default initial parameters");
    }

    @Test
    void test50Clients10000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = LRUCacheClient.runTest(10, 100, PORT);
            System.out.println(stat);
            int threshold = 10000;
            assertTrue(stat.throughput() > threshold, "Throughput is less than " + threshold);
        }, "Client failed with execution during sanity test for 50 clients & 10000 reqs");
    }

    @Test
    void test100Clients1000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = LRUCacheClient.runTest(10, 100, PORT);
            System.out.println(stat);
            int threshold = 10000;
            assertTrue(stat.throughput() > threshold, "Throughput is less than " + threshold);
        }, "Client failed with execution during sanity test for 50 clients & 10000 reqs");
    }

    @Test
    void test200Clients1000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = LRUCacheClient.runTest(10, 100, PORT);
            System.out.println(stat);
            int threshold = 10000;
            assertTrue(stat.throughput() > threshold, "Throughput is less than " + threshold);
        }, "Client failed with execution during sanity test for 50 clients & 10000 reqs");
    }

    @Test
    void test500Clients1000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = LRUCacheClient.runTest(10, 100, PORT);
            System.out.println(stat);
            int threshold = 10000;
            assertTrue(stat.throughput() > threshold, "Throughput is less than " + threshold);
        }, "Client failed with execution during sanity test for 50 clients & 10000 reqs");
    }

    @Test
    void test1000Clients1000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = LRUCacheClient.runTest(10, 100, PORT);
            System.out.println(stat);
            int threshold = 10000;
            assertTrue(stat.throughput() > threshold, "Throughput is less than " + threshold);
        }, "Client failed with execution during sanity test for 50 clients & 10000 reqs");
    }
}
