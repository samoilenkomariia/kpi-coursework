package com.mylrucachelib;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class AsyncRunner {
    public static void main(String[] args) {
        int port = 8080;
        int clientCount = 50;
        int requestsPerClient = 1000;
        int keyRange = 50;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        if (args.length > 1) clientCount = Integer.parseInt(args[1]);
        if (args.length > 2) requestsPerClient = Integer.parseInt(args[2]);
        if (args.length > 3) keyRange = Integer.parseInt(args[3]);
        System.out.printf("Starting Async Simulator: %d clients, %d reqs/client...%n",
                clientCount, requestsPerClient);
        try {
            System.out.println(simulateStatic(port, clientCount, requestsPerClient, keyRange));
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static Stats simulateStatic(int port, int clientCount, int requestsPerClient, int keyRang) throws ExecutionException, InterruptedException {
        return (new AsyncRunner()).simulate(port, clientCount, requestsPerClient, keyRang);
    }

    public Stats simulate(int port, int clientCount, int requestsPerClient, int keyRange) throws ExecutionException, InterruptedException {
        AsyncSimulator simulator = new AsyncSimulator(port, clientCount, requestsPerClient, keyRange);
        FutureTask<Stats> future = new FutureTask<>(simulator);
        Thread simThread = new Thread(future);
        simThread.start();
        return future.get();
    }
}
