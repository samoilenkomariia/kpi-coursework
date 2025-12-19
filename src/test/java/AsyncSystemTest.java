import com.mylrucachelib.AsyncRunner;
import com.mylrucachelib.AsyncServer;
import com.mylrucachelib.LoggerSetup;
import com.mylrucachelib.Stats;
import com.mylrucachelib.util.CSVReporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncSystemTest {
    private static final int PORT = 0;
    private static final int CAPACITY = 100;
    private static final int CONC_LVL = 16;

    private AsyncServer server;
    private Thread serverThread;

    private static final Logger logger = Logger.getLogger(AsyncSystemTest.class.getName());
    static {
        LoggerSetup.setupLogger(AsyncSystemTest.class.getName(), "async-system-TEST.log", true);
    }

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
        while (server.getPort() == 0) {
            if (System.currentTimeMillis() - start > 5000) {
                logger.severe("Server failed to start");
                throw new RuntimeException("Server did not bind port within 5 seconds");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
        }
        System.out.println("AsyncSystemTest: Server started on port " + server.getPort());
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
            logger.info(stat.toString());
            CSVReporter.record(
                    this.getClass().getSimpleName(),
                    "testSmallLoad",
                    stat
            );
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
            logger.info(stat.toString());
            CSVReporter.record(
                    this.getClass().getSimpleName(),
                    "testMediumLoad",
                    stat
            );
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    // stress test
    // this tests if the Selector can handle many registered keys without crashing
    @Test
    void testHighConcurrency() {
        assertDoesNotThrow(() -> {
            int clients = 500;
            int reqsPerClient = 1000;
            int keyRange = 250;
            Stats stat = AsyncRunner.simulateStatic(server.getPort(), clients, reqsPerClient, keyRange);
            logger.info(stat.toString());
            CSVReporter.record(
                    this.getClass().getSimpleName(),
                    "testHighConcurrency",
                    stat
            );
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }

    // hot key
    // this tests the thread-safety of LRUCacheSegment.
    @Test
    void testHotKeyContention() {
        assertDoesNotThrow(() -> {
            int clients = 100;
            int reqsPerClient = 100;
            int keyRange = 5;

            Stats stat = AsyncRunner.simulateStatic(server.getPort(), clients, reqsPerClient, keyRange);
            logger.info(stat.toString());
            CSVReporter.record(
                    this.getClass().getSimpleName(),
                    "testHotKeyContention",
                    stat
            );
            assertEquals(stat.totalReqs(), stat.successfulReqs());
        });
    }
}
