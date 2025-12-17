import com.mylrucachelib.AsyncSimulator;
import com.mylrucachelib.Stats;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.*;

public class SimulatorTest {

    @Test
    void testSimulatorCountsRequests() throws Exception {
        int port = 9999;
        int clientCount = 2;
        int requests = 50;

        //  server replies OK
        Thread dummyServer = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                while (!Thread.interrupted()) {
                    Socket s = ss.accept();
                    new Thread(() -> {
                        try {
                            byte[] buf = new byte[1024];
                            while (s.getInputStream().read(buf) != -1) {
                                s.getOutputStream().write("OK\n".getBytes());
                            }
                        } catch (Exception e) {}
                    }).start();
                }
            } catch (Exception e) {}
        });
        dummyServer.setDaemon(true);
        dummyServer.start();

        AsyncSimulator simulator = new AsyncSimulator(port, clientCount, requests, 10);
        FutureTask<Stats> task = new FutureTask<>(simulator);
        new Thread(task).start();

        Stats stats = task.get();
        assertEquals(clientCount * requests, stats.totalReqs());
    }
}
