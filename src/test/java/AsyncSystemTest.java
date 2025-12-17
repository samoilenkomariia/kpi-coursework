import com.mylrucachelib.AsyncRunner;
import com.mylrucachelib.AsyncServer;
import com.mylrucachelib.Stats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncSystemTest {
    private static final int PORT = 0;
    private static final int CAPACITY = 100;
    private static final int CONC_LVL = 16;

    private AsyncServer server;
    private Thread serverThread;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startServer() {
        String uniqueDumpFile = tempDir.resolve("server.dump").toAbsolutePath().toString();

        server = new AsyncServer();
        serverThread = new Thread(() -> {
            try {
                server.start(CAPACITY, CONC_LVL, PORT, uniqueDumpFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        long start = System.currentTimeMillis();
        int boundPort = 0;
        while (boundPort == 0) {
            try {
                boundPort = server.getPort();
                if (boundPort != 0) break;

                if (System.currentTimeMillis() - start > 5000) {
                    throw new RuntimeException("Server did not bind port within 5 seconds");
                }
                Thread.sleep(50);
            } catch (Exception ignored) {}
        }
        System.out.println("AsyncSystemTest: Server started on port " + boundPort);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
        try {
            if (serverThread != null) serverThread.join(100);
        } catch (InterruptedException ignored) {}
    }

    // smoke test
    @Test
    void testSmallLoad() {
        assertDoesNotThrow(() -> {
            int clients = 10;
            int reqsPerClient = 1000;
            int keyRange = 100;
            Stats stat = AsyncRunner.simulateStatic(server.getPort(), clients, reqsPerClient, keyRange);
            System.out.println(stat);
            assertEquals(clients * reqsPerClient, stat.totalReqs(), "Total requests mismatch");
            assertEquals(stat.totalReqs(), stat.successfulReqs(), "All requests should succeed");
            assertEquals(0, stat.failedReqs(), "There should be no failed requests");
        });
    }

    @Test
    void testMediumLoad() {
        assertDoesNotThrow(() -> {
            int clients = 50;
            int reqsPerClient = 2000;
            int keyRange = 100;

            Stats stat = AsyncRunner.simulateStatic(server.getPort(), clients, reqsPerClient, keyRange);
            System.out.println(stat);
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    // stress test
    // This tests if the Selector can handle many registered keys without crashing
    @Test
    void testHighConcurrency() {
        assertDoesNotThrow(() -> {
            int clients = 500;
            int reqsPerClient = 1000;
            int keyRange = 250;

            Stats stat = AsyncRunner.simulateStatic(server.getPort(), clients, reqsPerClient, keyRange);
            System.out.println(stat);

            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    // hot key
    // This tests the thread-safety (locking) of LRUCacheSegment.
    @Test
    void testHotKeyContention() {
        assertDoesNotThrow(() -> {
            int clients = 100;
            int reqsPerClient = 1000;
            int keyRange = 5;

            Stats stat = AsyncRunner.simulateStatic(server.getPort(), clients, reqsPerClient, keyRange);
            System.out.println(stat);

            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }
}
