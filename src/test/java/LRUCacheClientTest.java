import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

public class LRUCacheClientTest {
    private static Thread server;

    @BeforeAll
    static void startServer() {
        server = new Thread(() -> {
            try {
                LRUCacheService.main(new String[]{"1000", "16"});
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        server.setDaemon(true);
        server.start();
        long start = System.currentTimeMillis();
        while(System.currentTimeMillis() - start < 5000) {
            try (Socket s = new Socket("localhost", 8080)) {
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("Server did not start within 5 seconds");
    }

    @Test
    void test10Client100ReqsRunningSuccessfully() {
        assertDoesNotThrow(() -> {
            LRUCacheClient.runTest(10, 100);
        }, "Client failed with execution during sanity test for 10 clients & 100 reqs");
        System.out.println();
    }

    @Test
    void testDefaultClientRequests() {
        assertDoesNotThrow(() -> {
            LRUCacheClient.runTest();
        }, "Client failed with execution during sanity test for default initial parameters");
        System.out.println();
    }

    @Test
    void test50Clients10000Requests() {
        assertDoesNotThrow(() -> {
            LRUCacheClient.runTest(50, 10000);
        }, "Client failed with execution during sanity test for 50 clients & 10000 reqs");
        System.out.println();
    }
}
