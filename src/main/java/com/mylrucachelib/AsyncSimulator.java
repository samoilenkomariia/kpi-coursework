package com.mylrucachelib;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AsyncSimulator implements Callable<Stats> {
    private static final String HOST = "localhost";
    private final int port;
    private final int clientCount;
    private int activeClients;
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicLong latency = new AtomicLong(0);
    private final int reqsPerClient;
    private Selector selector;
    private final List<List<String>> allClientCommands;
    private static final Logger logger = Logger.getLogger(AsyncSimulator.class.getName());

    public AsyncSimulator(int port, int clientCount, int requestsPerClient, int keyRange) {
        this.port = port;
        this.clientCount = clientCount;
        this.activeClients = clientCount;
        this.reqsPerClient = requestsPerClient;
        this.allClientCommands = new java.util.ArrayList<>();
        for(int i = 0; i < clientCount; i++) {
            allClientCommands.add(generateCommands(requestsPerClient, keyRange));
        }
        LoggerSetup.setupLogger(AsyncSimulator.class.getName(), "simulator.log", false);
    }

    static class ClientState {
        ByteBuffer writeBuffer = ByteBuffer.allocate(4096);
        ByteBuffer readBuffer = ByteBuffer.allocate(4096);
        List<String> commands;
        int currentRequestIndex = 0;
        long opStartTime;
        long latency;
        int successfulReqs = 0;
        int failedReqs = 0;
        public ClientState(List<String> commands) {
            this.commands = commands;
        }
    }

    @Override
    public Stats call() {
        try {
            selector = Selector.open();
            for (int i = 0; i < clientCount; i++) {
                SocketChannel channel = SocketChannel.open();
                channel.configureBlocking(false);
                channel.connect(new InetSocketAddress(HOST, port));
                ClientState state = new ClientState(allClientCommands.get(i));
                channel.register(selector, SelectionKey.OP_CONNECT, state);
            }
            long start = System.nanoTime();
            while (!Thread.currentThread().isInterrupted() && activeClients > 0) {
                int readyChannels = selector.select(1000);
                if (readyChannels == 0) continue;

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) continue;
                    try {
                        if (key.isConnectable()) {
                            handleConnect(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        }
                    } catch (IOException e) {
                        logger.log(Level.SEVERE,"Exception in client processing while handling key: " + key);
                        key.cancel();
                        try { key.channel().close(); } catch (IOException ignored) {}
                        activeClients--;
                    }
                    if (selector.keys().isEmpty()) break;
                }
            }
            long end = System.nanoTime();
            return getStatistics(start, end, clientCount, reqsPerClient);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error connecting to server", e);
            return null;
        } finally {
            try {
                if (selector != null) selector.close();
            } catch (IOException ignored) {}
        }
    }

    private Stats getStatistics(long startTime, long endTime, int clientCount, int requestCount) {
        double time = (endTime - startTime)/1_000_000_000.0;
        int totalRequests = successfulRequests.get() + failedRequests.get();
        double throughputS = (double) totalRequests / time;
        double latencyMs = (double) latency.get() / totalRequests / 1_000_000.0;
        return new Stats(clientCount, requestCount, totalRequests,
                successfulRequests.get(), failedRequests.get(), time,
                throughputS, latencyMs);
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        if (channel.isConnectionPending()) {
            channel.finishConnect();
        }
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();
        if (state.writeBuffer.position() == 0) {
            if (state.currentRequestIndex >= state.commands.size()) {
                updateGlobalStats(state);
                channel.close();
                key.cancel();
                activeClients--;
                return;
            }
            String command = state.commands.get(state.currentRequestIndex) + "\n";
            state.writeBuffer
                    .put(command.getBytes(StandardCharsets.UTF_8))
                    .flip(); // prepare buf to be read from
            state.opStartTime = System.nanoTime();
        }
        channel.write(state.writeBuffer);
        if (state.writeBuffer.hasRemaining()) {
            key.interestOps(SelectionKey.OP_WRITE);
        } else {
            state.writeBuffer.clear(); // prepare buf to be given data
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();
        int bytes = channel.read(state.readBuffer);
        if (bytes == -1) {
            channel.close();
            key.cancel();
            activeClients--;
            return;
        }
        state.readBuffer.flip();
        boolean foundLine = false;
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
                        logger.warning("Request too large (" + newCap + "), closing connection.");
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
            foundLine = true;
            int lineLength = newlineIdx - position;
            byte[] lineBytes = new byte[lineLength];
            state.readBuffer.get(lineBytes);
            state.readBuffer.get();

            String response = new String(lineBytes, StandardCharsets.UTF_8).trim();
            processResponse(state, response);
            state.currentRequestIndex++;
            if (state.currentRequestIndex >= state.commands.size()) {
                updateGlobalStats(state);
                channel.close();
                key.cancel();
                activeClients--;
                return;
            }
        }
        if (foundLine) {
            key.interestOps(SelectionKey.OP_WRITE);
        }
        state.readBuffer.compact();
    }

    private void processResponse(ClientState state, String response) {
        long duration = System.nanoTime() - state.opStartTime;
        state.latency += duration;
        if (response.equals("OK") || response.startsWith("VALUE") || response.equals("NOT_FOUND")) {
            state.successfulReqs++;
        } else {
            state.failedReqs++;
        }
    }
    private void updateGlobalStats(ClientState state) {
        this.latency.addAndGet(state.latency);
        this.successfulRequests.addAndGet(state.successfulReqs);
        this.failedRequests.addAndGet(state.failedReqs);
    }
    private List<String> generateCommands(int requests, int keyRange) {
        List<String> cmds = new ArrayList<>(requests);
        for (int i = 0; i < requests; i++) {
            if (ThreadLocalRandom.current().nextDouble() < 0.3) { // 30% write chance
                int key = ThreadLocalRandom.current().nextInt(keyRange);
                cmds.add("put key" + key + " value" + i);
            } else {
                int key = ThreadLocalRandom.current().nextInt(keyRange);
                cmds.add("get key" + key);
            }
        }
        return cmds;
    }
}
