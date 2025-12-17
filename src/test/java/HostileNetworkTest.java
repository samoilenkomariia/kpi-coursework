import com.mylrucachelib.AsyncServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HostileNetworkTest {
    private static final int SERVER_PORT = 0; // Random
    private static final int PROXY_PORT = 9999;
    private AsyncServer server;
    private Thread serverThread;
    private ToxicProxy proxy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        String dumpPath = tempDir.resolve("toxic.dump").toAbsolutePath().toString();
        server = new AsyncServer();
        serverThread = new Thread(() -> {
            try {
                server.start(100, 16, SERVER_PORT, dumpPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        long start = System.currentTimeMillis();
        while (server.getPort() == 0 && (System.currentTimeMillis() - start) < 5000) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
    }

    @AfterEach
    void tearDown() {
        if (proxy != null) proxy.stop();
        if (server != null) server.stop();
    }

    // client sends 1 byte every 100ms, server must not crash
    @Test
    void testSlowLorisAttack() throws Exception {
        proxy = new ToxicProxy(PROXY_PORT, server.getPort(), ToxicProxy.Mode.SLOW_LORIS);
        proxy.start();

        try (Socket client = new Socket("localhost", PROXY_PORT)) {
            client.getOutputStream().write("PUT slow value\n".getBytes());
            client.getOutputStream().flush();
            byte[] buf = new byte[1024];
            int read = client.getInputStream().read(buf);
            String response = new String(buf, 0, read).trim();

            assertEquals("OK", response);
        }
    }

    // every byte is a separate tcp packet
    @Test
    void testExtremeFragmentation() throws Exception {
        proxy = new ToxicProxy(PROXY_PORT, server.getPort(), ToxicProxy.Mode.FRAGMENTED);
        proxy.start();

        try (Socket client = new Socket("localhost", PROXY_PORT)) {
            String cmd = "PUT frag test\n";
            client.getOutputStream().write(cmd.getBytes());
            client.getOutputStream().flush();

            byte[] buf = new byte[1024];
            int read = client.getInputStream().read(buf);
            String response = new String(buf, 0, read).trim();

            assertEquals("OK", response);
        }
    }

    // proxy server is between client and asyncserver
    static class ToxicProxy {
        enum Mode { NORMAL, SLOW_LORIS, FRAGMENTED }

        private final int localPort;
        private final int remotePort;
        private final Mode mode;
        private volatile boolean running = true;
        private ServerSocket serverSocket;
        private final CountDownLatch startLatch = new CountDownLatch(1);

        public ToxicProxy(int localPort, int remotePort, Mode mode) {
            this.localPort = localPort;
            this.remotePort = remotePort;
            this.mode = mode;
        }

        public void start() throws InterruptedException {
            new Thread(this::runServer).start();
            startLatch.await();
        }

        public void stop() {
            running = false;
            try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        }

        private void runServer() {
            try (ServerSocket ss = new ServerSocket(localPort)) {
                this.serverSocket = ss;
                startLatch.countDown();
                while (running) {
                    Socket clientSocket = ss.accept();
                    new Thread(() -> handleConnection(clientSocket)).start();
                }
            } catch (IOException ignored) {
                startLatch.countDown();
            }
        }

        private void handleConnection(Socket clientSocket) {
            try (Socket serverSocket = new Socket("localhost", remotePort);
                 InputStream clientIn = clientSocket.getInputStream();
                 OutputStream clientOut = clientSocket.getOutputStream();
                 InputStream serverIn = serverSocket.getInputStream();
                 OutputStream serverOut = serverSocket.getOutputStream()) {

                // thread to pump data from Server to Client at normal speed
                Thread responseThread = new Thread(() -> {
                    try {
                        serverIn.transferTo(clientOut);
                    } catch (IOException ignored) {}
                });
                responseThread.start();

                // pump Client -> Server at bad speed
                int b;
                while ((b = clientIn.read()) != -1) {
                    if (mode == Mode.SLOW_LORIS) {
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    }
                    serverOut.write(b);
                    if (mode == Mode.FRAGMENTED || mode == Mode.SLOW_LORIS) {
                        serverOut.flush();
                    }
                }
            } catch (IOException ignored) {}
        }
    }
}
