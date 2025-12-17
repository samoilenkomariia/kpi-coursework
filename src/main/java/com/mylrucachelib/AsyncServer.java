package com.mylrucachelib;

import com.mylrucachelib.persistence.StringSerializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AsyncServer {
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_FILE = "lru-cache.dump";
    private LRUCache<String,String> cache;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private volatile boolean running = false;
    private static final Logger logger = Logger.getLogger(AsyncServer.class.getName());

    static class ServerClientState {
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        Queue<ByteBuffer> writeQueue = new LinkedList<>();
    }

    public static void main(String[] args) throws IOException {
        int capacity = 100;
        int concurrencyLevel = 16;
        int port = DEFAULT_PORT;
        if (args.length > 0) capacity = Integer.parseInt(args[0]);
        if (args.length > 1) concurrencyLevel = Integer.parseInt(args[1]);
        if (args.length > 2) port = Integer.parseInt(args[2]);
        AsyncServer service = new AsyncServer();
        service.start(capacity, concurrencyLevel, port, DEFAULT_FILE);
    }

    public void stop() {
        running = false;
        if (selector != null) {
            selector.wakeup(); // Unblock the select() call immediately
        }
        if (cache != null) {
            cache.removeShutdownHook();
            cache.shutdown();
        }
    }

    private void close() {
        try {
            if (selector != null) selector.close();
            if (serverSocketChannel != null) serverSocketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start(int cap, int concLevel, int port, String filePath) throws IOException {
        LoggerSetup.setupLogger(AsyncServer.class.getName(), "async-server.log", true);
        this.cache = new LRUCache<>(cap, concLevel);
        this.cache.enablePersistence(
                filePath,
                new StringSerializer(),
                new StringSerializer()
        );
        this.cache.addShutdownHook();
        this.selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost", port));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        port = getPort();
        logger.info("Nio LRU Cache Server started on port " + port);
        running = true;
        while (running && selector.isOpen()) {
            int readyChannels = selector.select();
            if (!running) break;
            if (readyChannels == 0) continue;
            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();
                try {
                    if (!key.isValid()) continue;
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    }
                    if (key.isValid() && key.isReadable()) {
                        handleRead(key);
                    }
                    if (key.isValid() && key.isWritable()) {
                        handleWrite(key);
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "IO Error in event loop", e);
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException ignored) {}
                }
            }
        }
        close();
        logger.info("Server stopped on port" + port);
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, new ServerClientState());
        logger.fine("Accepted new connection: " + client.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ServerClientState state = (ServerClientState) key.attachment();
        int bytes = channel.read(state.readBuffer);
        if (bytes == -1) {
            channel.close();
            return;
        }

        state.readBuffer.flip(); // to read mode
        while (true) {
            int limit = state.readBuffer.limit();
            int position = state.readBuffer.position();
            int newlineIdx = -1;
            for (int i = position; i < limit; i++) {
                if (state.readBuffer.get(i) == '\n') {
                    newlineIdx = i;
                    break;
                }
            }
            // no \n foud=nd
            if (newlineIdx == -1) {
                if (state.readBuffer.limit() == state.readBuffer.capacity()) {
                    int newCap = state.readBuffer.capacity()*2;
                    if (newCap > 1024*1024) {
                        logger.log(Level.SEVERE, "Request too large, closing.");
                        channel.close();
                        return;
                    }
                    ByteBuffer newBuf = ByteBuffer.allocate(newCap);
                    newBuf.put(state.readBuffer);
                    newBuf.flip(); // to read mode
                    state.readBuffer = newBuf;
                }
                break;
            }
            // \n found, get the line
            int lineLength = newlineIdx - position;
            byte[] lineBytes = new byte[lineLength];
            state.readBuffer.get(lineBytes);
            state.readBuffer.get(); // skip \n
            String line = new String(lineBytes, StandardCharsets.UTF_8).trim();

            String response = processCommand(line) + "\n";
            state.writeQueue.add(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
            key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        }
        state.readBuffer.compact();
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ServerClientState state = (ServerClientState) key.attachment();

        // handle client abrupt disconnection
        try {
            while (!state.writeQueue.isEmpty()) {
                ByteBuffer buf = state.writeQueue.peek();
                channel.write(buf);
                // if socket buffer is full, stop writing and wait for next op_write trigger
                if (buf.hasRemaining()) {
                    return;
                } else {
                    state.writeQueue.poll();
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "IO Error in event loop", e);
            key.cancel();
            try {
                key.channel().close();
            } catch (IOException ignored) {}
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    private String processCommand(String line) {
        if (line == null || line.isEmpty()) return "ERROR_EMPTY_COMMAND";
        int firstSpace = line.indexOf(' ');
        String command;
        String key = null;
        String value = null;
        long ttl = 0;
        if (firstSpace == -1) {
            command = line.toUpperCase();
        } else {
            command = line.substring(0, firstSpace).toUpperCase();
            int secondSpace = line.indexOf(' ', firstSpace + 1);
            if (secondSpace == -1) {
                key = line.substring(firstSpace + 1);
            } else {
                key = line.substring(firstSpace + 1, secondSpace);
                value = line.substring(secondSpace + 1);
            }
            int thirdSpace = line.indexOf(' ', secondSpace + 1);
            if (thirdSpace != -1) {
                try {
                    ttl = Long.parseLong(line.substring(thirdSpace + 1));
                    value = line.substring(secondSpace + 1, thirdSpace);
                } catch (NumberFormatException e) {
                    ttl = 0;
                }
            }
        }

        try {
            switch (command) {
                case "PUT" -> {
                    if (key == null || value == null || key.isEmpty() || value.isEmpty()) {
                        return "ERROR_USAGE_PUT";
                    }
                    this.cache.put(key, value, ttl);
                    return "OK";
                }
                case "GET" -> {
                    if (key == null || key.isEmpty()) {
                        return "ERROR_USAGE_GET";
                    }
                    String result = this.cache.get(key);
                    if (result == null) {
                        return "NOT_FOUND";
                    }
                    return "VALUE " + result;
                }
                default -> {
                    return "ERROR_UNKNOWN_COMMAND";
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing command: " + line, e);
            return "ERROR_INTERNAL " + e.getMessage();
        }
    }

    public int getPort() {
        try {
            if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
                java.net.SocketAddress addr = serverSocketChannel.getLocalAddress();
                if (addr instanceof InetSocketAddress) {
                    return ((InetSocketAddress) addr).getPort();
                }
            }
        } catch (IOException ignored) {}
        return 0;
    }
}
