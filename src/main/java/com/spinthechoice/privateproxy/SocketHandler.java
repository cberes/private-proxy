package com.spinthechoice.privateproxy;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.spinthechoice.privateproxy.ConnectParser.Server;
import com.spinthechoice.privateproxy.ConnectParser.InvalidConnectException;

/**
 * Listens for and handles connections to clients.
 * For each client, the handler expects to receive an HTTP CONNECT message.
 * Once the CONNECT message is received, the handler sets up a tunnel to another
 * server, and SSL is negotiated via that server. Data is transmitted between
 * the client and remote server until one end hangs up. Note that since SSL is
 * negotiated by the remote server, this server cannot read any data.
 */
class SocketHandler implements Runnable {
    /**
     * Interface to implement for additional checks against the server.
     */
    @FunctionalInterface
    interface Validator {
        /**
         * Check the server for any problems.
         * If the server is invalid, return an explanation.
         * Return an empty {@link String} or {@code null} otherwise.
         * @param server server
         * @return reason server is invalid, or {@code null} or empty String otherwise.
         */
        String checkServer(Server server);
    }

    private static class BadRequestException extends Exception {
        BadRequestException(final String message) {
            super(message);
        }
    }

    private final ServerSocket serverSocket;
    private final ExecutorService tunnelExecutor;
    private final Validator validator;
    private Socket clientSocket;

    /**
     * Creates a new handler.
     * @param serverSocket server socket
     * @param tunnelExecutor executor for tunnel threads
     */
    SocketHandler(final ServerSocket serverSocket, final ExecutorService tunnelExecutor) {
        this(serverSocket, tunnelExecutor, x -> null);
    }

    /**
     * Creates a new handler.
     * @param serverSocket server socket
     * @param tunnelExecutor executor for tunnel threads
     * @param validator additional validation of incoming messages
     */
    SocketHandler(final ServerSocket serverSocket, final ExecutorService tunnelExecutor, final Validator validator) {
        this.serverSocket = serverSocket;
        this.tunnelExecutor = tunnelExecutor;
        this.validator = validator;
    }

    /**
     * Returns whether the server is open (or running).
     * @return whether the server is open
     */
    boolean isServerOpen() {
        return !serverSocket.isClosed();
    }

    /**
     * The "listen" thread that accepts a connection to the server, parses the
     * header to obtain the server to which a tunnel will be opened, and
     * tunnels data between the client and server.
     */
    @Override
    public void run() {
        clientSocket = acceptConnection();

        if (clientSocket != null) {
            handleMessages();
        }
    }

    private Socket acceptConnection() {
        // isolate the error handling for accepting client connections
        // for any future errors, we know there is a client (or at one point there was)
        try {
            return serverSocket.accept();
        } catch (Exception e) {
            System.err.println("Proxy Failed: " + e);
            e.printStackTrace(System.err);
            return null;
        }
    }

    private void handleMessages() {
        try {
            handleMessagesThrowingErrors();
        } catch (IOException e) {
            System.err.println("error writing response: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            closeClient();
        }
    }

    private void handleMessagesThrowingErrors() throws IOException {
        try {

            Server server = getServer(readRequestLine());
            runValidator(server);
            sendOk();
            tunnelClientAndServer(server);

        } catch (BadRequestException e) {
            badRequest(e.getMessage());
        }
    }

    private Server getServer(final String requestLine) throws BadRequestException {
        try {
            return ConnectParser.fromRequestLine(requestLine).parse();
        } catch (InvalidConnectException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private String readRequestLine() throws IOException {
        final BufferedReader in = input();
        final String line = in.readLine();
        eatRestOfHeader(in);
        return line;
    }

    private BufferedReader input() throws IOException {
        return new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
    }

    private static void eatRestOfHeader(final BufferedReader in) throws IOException {
        String line;
        do {
            line = in.readLine();
        } while (line != null && line.length() != 0 &&
                line.charAt(0) != '\r' && line.charAt(0) != '\n');
    }

    private void runValidator(final Server server) throws BadRequestException {
        final String reason = validator.checkServer(server);
        if (reason != null && !reason.isEmpty()) {
            throw new BadRequestException(reason);
        }
    }

    private void sendOk()  {
        try {
            final PrintWriter out = output();
            out.print("HTTP/1.1 200 OK\r\n\r\n");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    private void tunnelClientAndServer(final Server server) throws IOException {
        try (final Socket serverSocket = new Socket(server.host(), server.port());
             final Tunnel clientToServer = new Tunnel(clientSocket, serverSocket);
             final Tunnel serverToClient = new Tunnel(serverSocket, clientSocket)) {

            final Future<?> clientFuture = tunnelExecutor.submit(clientToServer);
            final Future<?> serverFuture = tunnelExecutor.submit(serverToClient);

            waitFor(clientFuture);
            waitFor(serverFuture);
        }
    }

    private static void waitFor(final Future<?> future) throws IOException {
        try {
            future.get();
        } catch (ExecutionException e) {
            throw new IOException("Error in tunnel thread", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void badRequest(final String reason) throws IOException {
        final PrintWriter out = output();
        out.print("HTTP/1.1 400 " + reason + "\r\n");
        out.print("Content-Type: text/html\r\n\r\n");
        out.flush();
    }

    private PrintWriter output() throws IOException {
        return output(clientSocket.getOutputStream());
    }

    private PrintWriter output(final OutputStream rawOut) {
        return new PrintWriter(
                new BufferedWriter(
                        new OutputStreamWriter(rawOut)));
    }

    private void closeClient() {
        try {
            clientSocket.close();
        } catch (IOException e) {
        }
    }
}
