package com.spinthechoice.privateproxy;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer implements Runnable {
    private static final int DEFAULT_THREAD_COUNT = 8;

    private final ServerSocket serverSocket;
    private final ExecutorService executor;

    public ProxyServer(final int port, final int threadCount) throws IOException {
        final ServerSocketFactory socketFactory =  ServerSocketFactory.getDefault();
        serverSocket = socketFactory.createServerSocket(port);
        executor = Executors.newFixedThreadPool(threadCount);
    }

    @Override
    public void run() {
        new SocketHandler(executor, serverSocket);
    }

    public void close() {
        executor.shutdown();
        try {
            serverSocket.close();
        } catch (IOException e) { }
    }

    public static void main(final String[] args) {
        int port, threadCount;
        try {
            port = getPort(args);
            threadCount = getThreadCount(args);
        } catch (Exception e) {
            System.err.println("USAGE: java " + SocketHandler.class.getSimpleName() + " PORT [ THREAD_COUNT ]");
            return;
        }

        try {
            new ProxyServer(port, threadCount).run();
        } catch (IOException e) {
            System.err.println("Unable to start " + SocketHandler.class.getSimpleName() + ": " +
                    e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private static int getPort(final String[] args) throws Exception {
        if (args.length < 1) {
            throw new Exception("Port is required");
        }

        return Integer.parseInt(args[0]);
    }

    private static int getThreadCount(final String[] args) throws Exception {
        if (args.length <= 1) {
            return DEFAULT_THREAD_COUNT;
        }

        return Integer.parseInt(args[1]);
    }
}
