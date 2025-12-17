import com.mylrucachelib.Client;
import com.mylrucachelib.ThreadedServer;
import com.mylrucachelib.Stats;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/*
    E2E test: load tests, stress test
 */

public class ClientTest {
    private static final int PORT = 0;
    private static final int CAPACITY = 100;
    private static final int CONC_LVL = 16;
    private ThreadedServer service;

    @BeforeEach
    void startServer() {
        service = new ThreadedServer();
        Thread server = new Thread(() -> {
            try {
                service.start(CAPACITY, CONC_LVL, PORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        server.setDaemon(true);
        server.start();
        long start = System.currentTimeMillis();
        while (service.getPort() == 0) {
            if (System.currentTimeMillis() - start > 5000) {
                throw new RuntimeException("Server did not bind port within 5 seconds");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
        }
        System.out.println("LRUCacheService started on port " + service.getPort());
        start = System.currentTimeMillis();
        // sanity check
        while(System.currentTimeMillis() - start < 5000) {
            try (Socket ignored = new Socket("localhost", service.getPort())) {
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("Server did not pass sanity check");
    }

    @AfterEach
    void stopServer() {
        if (service != null) {
            service.stop();
        }
    }

    // Throughput + successful requests testing
    @Test
    void test10Client100ReqsRunningSuccessfully() {
        assertDoesNotThrow(() -> {
            Stats stat = Client.runTest(10, 100, service.getPort(), 10);
            System.out.println(stat);
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    @Test
    void test50Clients10000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = Client.runTest(50, 10000, service.getPort(), 50);
            System.out.println(stat);
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    @Test
    void testDefaultClientRequests() {
        assertDoesNotThrow(() -> {
            Stats stat = Client.runTest(service.getPort()); // 50 threads, 1000 reqs per thread
            System.out.println(stat);
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    @Test
    void test100Clients1000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = Client.runTest(100, 1000, service.getPort(), 100);
            System.out.println(stat);
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    @Test
    void test200Clients1000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = Client.runTest(200, 1000, service.getPort(), 200);
            System.out.println(stat);
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    @Test
    void test500Clients1000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = Client.runTest(500, 1000, service.getPort(), 500);
            System.out.println(stat);
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    @Test
    void test1000Clients1000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = Client.runTest(1000, 1000, service.getPort(), 1000);
            System.out.println(stat);
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    // rivalry for 5 key by 100 clients
    @Test
    void testHotKeyContention100clients1000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = Client.runTest(100, 1000, service.getPort(), 5);
            System.out.println(stat);
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    @Test
    void testHotKeyContention200clients1000Requests() {
        assertDoesNotThrow(() -> {
            Stats stat = Client.runTest(200, 1000, service.getPort(), 5);
            System.out.println(stat);
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }
}
