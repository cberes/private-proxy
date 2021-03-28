package com.spinthechoice.privateproxy;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.stream.IntStream.range;

/**
 * The proxy server.
 * Starts the {@link SocketHandler}s, which do most of the important work.
 */
public class ProxyServer implements Runnable, AutoCloseable {
    private static final int DEFAULT_THREAD_COUNT = 8;

    private final ServerSocket serverSocket;
    private final ExecutorService socketHandlerExecutor;
    private final int threadCount;
    /**
     * A separate pool for tunnel threads.
     * The number of tunnel threads is limited to the number of socket threads * 2.
     * It makes no sense to impose any other kind of limitation on tunnel threads.
     * According to the javadoc the cached thread pool should give better
     * performance than creating a new thread for every tunnel.
     */
    private final ExecutorService tunnelExecutor = Executors.newCachedThreadPool();

    /**
     * Creates a new proxy server.
     * @param port port
     * @param socketFactory socket factory
     * @param socketHandlerExecutor thread pool for SocketHandlers
     * @param threadCount number of threads to handle connections
     * @throws IOException any network errors
     */
    public ProxyServer(final int port, final ServerSocketFactory socketFactory,
                       final ExecutorService socketHandlerExecutor, final int threadCount) throws IOException {
        serverSocket = socketFactory.createServerSocket(port);
        this.socketHandlerExecutor = socketHandlerExecutor;
        this.threadCount = threadCount;
    }

    @Override
    public void run() {
        range(0, threadCount)
                .mapToObj(i -> newHandler())
                .forEach(socketHandlerExecutor::submit);
    }

    private Runnable newHandler() {
        return new LoopingSocketHandler(
                new SocketHandler(serverSocket, tunnelExecutor, enforceGiphy()));
    }

    private SocketHandler.Validator enforceGiphy() {
        return server -> {
            if ("api.giphy.com".equalsIgnoreCase(server.host().getHostName()) && server.port() == 443) {
                return null;
            } else {
                return server.host() + " is not trusted";
            }
        };
    }

    /**
     * Stops the server.
     */
    @Override
    public void close() {
        socketHandlerExecutor.shutdown();
        tunnelExecutor.shutdown();
        try {
            serverSocket.close();
        } catch (IOException e) { }
    }

    /**
     * Starts the proxy server
     * The arguments are
     * <ol>
     *     <li>port</li>
     *     <li>number of threads (optional, default is {@value #DEFAULT_THREAD_COUNT})</li>
     * </ol>
     * @param args arguments
     */
    public static void main(final String[] args) {
        // get arguments
        int port, threadCount;
        try {
            port = getPort(args);
            threadCount = getThreadCount(args);
        } catch (Exception e) {
            System.err.println("USAGE: java " + SocketHandler.class.getSimpleName() + " PORT [ THREAD_COUNT ]");
            return;
        }

        // run server
        try {
            new ProxyServer(
                    port,
                    ServerSocketFactory.getDefault(),
                    Executors.newFixedThreadPool(threadCount),
                    threadCount).run();
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
