package com.mylrucachelib;

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

public class AsyncServer {
    private static final int DEFAULT_PORT = 8080;

    private LRUCache<String,String> cache;
    private ServerSocketChannel serverSocket;
    private Selector selector;

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
        service.start(capacity, concurrencyLevel, port);
    }

    public void start(int cap, int concLevel, int port) throws IOException {
        this.cache = new LRUCache<>(cap, concLevel);

        this.selector = Selector.open();
        serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress("localhost", port));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Nio LRUCacheServer started on port " + port);
        while (true) {
            selector.select();
            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();
                try {
                    if (!key.isValid()) continue;
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    }
                    if (key.isReadable()) {
                        handleRead(key);
                    }
                    if (key.isWritable()) {
                        handleWrite(key);
                    }
                } catch (IOException e) {
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, new ServerClientState());
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ServerClientState state = (ServerClientState) key.attachment();
        int bytes = channel.read(state.readBuffer);
        if (bytes == -1) {
            channel.close();
            return;
        }

        state.readBuffer.flip();
        while (true) {
            int limit = state.readBuffer.limit();
            int position = state.readBuffer.position();
            int newlineIdx = -1;
            for (int i = 0; i < limit; i++) {
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
                        System.err.println("Request too large, closing.");
                        channel.close();
                        return;
                    }
                    ByteBuffer newBuf = ByteBuffer.allocate(newCap);
                    state.readBuffer.position(0);
                    newBuf.put(state.readBuffer);
                    state.readBuffer = newBuf;
                }
                break;
            }
            // \n found, get the line
            int lineLength = newlineIdx - position;
            byte[] lineBytes = new byte[lineLength];
            state.readBuffer.get(lineBytes);
            state.readBuffer.get(); // skip \n
            String line = new String(lineBytes, StandardCharsets.UTF_8);

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
        }

        try {
            switch (command) {
                case "PUT" -> {
                    if (key == null || value == null || key.isEmpty() || value.isEmpty()) {
                        return "ERROR_USAGE_PUT";
                    }
                    this.cache.put(key, value);
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
            return "ERROR_INTERNAL " + e.getMessage();
        }
    }

    public int getPort() {
        return serverSocket != null && !serverSocket.socket().isClosed() ? serverSocket.socket().getLocalPort() : 0;
    }
}
