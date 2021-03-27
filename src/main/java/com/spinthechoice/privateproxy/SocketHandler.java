package com.spinthechoice.privateproxy;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import com.spinthechoice.privateproxy.ConnectParser.Server;
import com.spinthechoice.privateproxy.ConnectParser.InvalidConnectException;

class SocketHandler implements Runnable {
    private final ServerSocket serverSocket;
    private Socket clientSocket;

    SocketHandler(final ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        newListener();
    }

    /**
     * Create a new thread to listen.
     */
    private void newListener() {
        (new Thread(this)).start();
    }

    /**
     * The "listen" thread that accepts a connection to the
     * server, parses the header to obtain the file name
     * and sends back the bytes for the file (or error
     * if the file is not found or the response was malformed).
     */
    @Override
    public void run() {
        try {
            clientSocket = serverSocket.accept();
        } catch (Exception e) {
            System.err.println("Proxy Failed: " + e);
            e.printStackTrace(System.err);
            return;
        }

        newListener();

        try {
            handleMessages();
        } catch (IOException e) {
            // eat exception (could log error to log file, but
            // write out to stdout for now).
            System.err.println("error writing response: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private void handleMessages() throws IOException {
        final BufferedReader in = input();
        final String line = in.readLine();
        eatRestOfHeader(in);

        Server server;
        try {
            server = ConnectParser.fromStatusLine(line).parse();
        } catch (InvalidConnectException e) {
            badRequest(e.getMessage());
            return;
        }

        if (!isValidHost(server.host.getHostName())) {
            badRequest(server.host + " is not trusted");
            return;
        }

        ok();

        tunnelClientAndServer(server);
    }

    private BufferedReader input() throws IOException {
        return new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
    }

    private static void eatRestOfHeader(final BufferedReader in) throws IOException {
        String line;
        do {
            line = in.readLine();
        } while ((line.length() != 0) &&
                (line.charAt(0) != '\r') && (line.charAt(0) != '\n'));
    }

    private static boolean isValidHost(final String host) {
        return "api.giphy.com".equalsIgnoreCase(host);
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

    private void ok()  {
        try {
            final PrintWriter out = output();
            out.print("HTTP/1.1 200 OK\r\n\r\n");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    private void ok(final String body) {
        try {
            byte[] bytes = body.getBytes("ASCII7");
            final OutputStream rawOut = clientSocket.getOutputStream();
            final PrintWriter out = output(rawOut);
            out.print("HTTP/1.1 200 OK\r\n");
            out.print("Content-Length: " + bytes.length + "\r\n");
            out.print("Content-Type: text/html\r\n\r\n");
            out.flush();
            rawOut.write(bytes);
            rawOut.flush();
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    private void tunnelClientAndServer(final Server server) throws IOException {
        try (final Socket serverSocket = new Socket(server.host, server.port);
             final Tunnel clientToServer = new Tunnel(clientSocket, serverSocket);
             final Tunnel serverToClient = new Tunnel(serverSocket, clientSocket)) {

            clientToServer.start();
            serverToClient.start();

            join(clientToServer);
            join(serverToClient);
        }
    }

    private static void join(final Tunnel clientToServer) {
        try {
            clientToServer.join();
        } catch (InterruptedException e) {
            clientToServer.interrupt();
        }
    }
}
