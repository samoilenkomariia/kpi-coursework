import com.mylrucachelib.AsyncServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncServerSecurityBoundaryTest {
    private AsyncServer server;
    @TempDir Path tempDir;

    @BeforeEach
    void start() throws IOException {
        server = new AsyncServer();
        new Thread(() -> {
            try {
                server.start(100, 4, 0, tempDir.resolve("sec.dump").toString());
            } catch (IOException e) {}
        }).start();
        while (server.getPort() == 0) { Thread.yield(); }
    }

    @AfterEach
    void stop() { server.stop(); }

    @Test
    void testMassivePayloadRejection() throws IOException, InterruptedException {
        try (Socket socket = new Socket("localhost", server.getPort());
             OutputStream out = socket.getOutputStream()) {

            out.write("PUT huge_key ".getBytes());
            // generate 1MB + 1KB of data
            byte[] junk = new byte[1024 * 1024 + 1024];
            Arrays.fill(junk, (byte) 'A');

            // write chunked to avoid local buffer limits
            int chunk = 8192;
            for (int i = 0; i < junk.length; i += chunk) {
                out.write(junk, i, Math.min(chunk, junk.length - i));
                out.flush();
            }
            out.write("\n".getBytes());
            out.flush();

            // 4. Verify Server closed connection (Read returns -1)
            // It might take a moment for the server to process and close
            socket.setSoTimeout(2000);
            int result = socket.getInputStream().read();
            assertEquals(-1, result, "Server should have closed connection due to size limit");
        }
    }
}
