import com.mylrucachelib.AsyncServer;
import com.mylrucachelib.LoggerSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncServerTest {
    private static final String HOST = "localhost";
    private static final int PORT = 0;
    private static final int CAPACITY = 100;
    private static final int CONC_LVL = 16;
    private static AsyncServer server;
    private static Thread serverThread;
    Logger logger = Logger.getLogger(AsyncServerTest.class.getName());
    static {
        LoggerSetup.setupLogger(AsyncServerTest.class.getName(), "async-server-TEST.log", true);
    }
    @TempDir
    Path tempDir;

    @BeforeEach
    void startServer() throws IOException {
        String uniqueDumpFile = tempDir.resolve("server.dump").toAbsolutePath().toString();
        server = new AsyncServer();
        serverThread = new Thread(() -> {
            try {
                server.start(CAPACITY, CONC_LVL, PORT, uniqueDumpFile);
            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        long start = System.currentTimeMillis();
        while (server.getPort() == 0) {
            if (System.currentTimeMillis() - start > 5000) {
                logger.severe("Server failed to start within 5s");
                throw new RuntimeException("Server did not bind port within 5 seconds");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
        }
        logger.fine("Async LRUCacheService started on port " + server.getPort());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testPartialPacketAssembly() throws IOException {
        try (
                Socket socket = new Socket(HOST, server.getPort());
                OutputStream out = socket.getOutputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.write("put par".getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.write("tial 123\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = in.readLine();
            assertEquals("OK", response);

            out.write("get partial\n".getBytes(StandardCharsets.UTF_8));
            assertEquals("VALUE 123", in.readLine());
        }
    }

    @Test
    void testPipelining() throws IOException {
        try (
                Socket socket = new Socket(HOST, server.getPort());
                OutputStream out = socket.getOutputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.write("PUT pipe 999\nGET pipe\n".getBytes());
            out.flush();
            assertEquals("OK", in.readLine());
            assertEquals("VALUE 999", in.readLine());
        }
    }

    @Test
    void testBufferResizingWithLargeValue() throws Exception {
        try (
                Socket socket = new Socket(HOST, server.getPort());
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            char[] chars = new char[5000];
            Arrays.fill(chars, 'A');
            String bigVal = new String(chars);

            out.println("PUT big " + bigVal);
            assertEquals("OK", in.readLine());

            out.println("GET big");
            String response = in.readLine();
            assertEquals("VALUE " + bigVal, response);
        }
    }

    @Test
    void testProtocolErrorHandling() throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort());
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            writer.println("DANCE key");
            assertEquals("ERROR_UNKNOWN_COMMAND", reader.readLine());
            writer.println("PUT key");
            assertEquals("ERROR_USAGE_PUT", reader.readLine());
            writer.println("PUT   ");
            String resp = reader.readLine();
            assertEquals("ERROR_USAGE_PUT", resp);
            writer.println("GET");
            assertEquals("ERROR_USAGE_GET", reader.readLine());
        }
    }
}
