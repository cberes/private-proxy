package com.spinthechoice.privateproxy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

class ConnectParser {
    static class InvalidConnectException extends Exception {
        InvalidConnectException(final String message) {
            super(message);
        }
    }

    static record Server (InetAddress host, int port) {}

    private static final String METHOD = "CONNECT";

    private final String statusLine;

    private ConnectParser(final String statusLine) {
        this.statusLine = statusLine;
    }

    Server parse() throws InvalidConnectException {
        final StringTokenizer tokens = new StringTokenizer(statusLine);
        final String method = tokens.nextToken();

        if (!METHOD.equalsIgnoreCase(method)) {
            throw new InvalidConnectException("Not a " + METHOD + " message");
        }

        if (!tokens.hasMoreTokens()) {
            throw new InvalidConnectException("Invalid " + METHOD + " message");
        }

        final String server = tokens.nextToken();
        final String[] parts = server.split(":");
        if (parts.length != 2) {
            throw new InvalidConnectException("Invalid server in " + METHOD);
        }

        try {
            final InetAddress name = InetAddress.getByName(parts[0]);
            final int port = Integer.parseInt(parts[1]);
            return new Server(name, port);
        } catch (UnknownHostException e) {
            throw new InvalidConnectException("Invalid host in " + METHOD);
        } catch (NumberFormatException e) {
            throw new InvalidConnectException("Invalid port in " + METHOD);
        }
    }

    /**
     * This method retrieves the hostname and port of the destination
     * that the connect request wants to establish a tunnel for
     * communication.
     * @param line CONNECT server-name:server-port HTTP/1.x
     * @return parser instance
     */
    static ConnectParser fromStatusLine(final String line) {
        return new ConnectParser(line);
    }
}
