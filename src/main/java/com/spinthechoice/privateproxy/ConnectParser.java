package com.spinthechoice.privateproxy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

/**
 * Parses HTTP CONNECT messages.
 */
class ConnectParser {
    static class InvalidConnectException extends Exception {
        InvalidConnectException(final String message) {
            super(message);
        }
    }

    /**
     * Result type.
     */
    static record Server (InetAddress host, int port) {}

    private static final String METHOD = "CONNECT";

    private final String requestLine;

    private ConnectParser(final String requestLine) {
        this.requestLine = requestLine == null ? "" : requestLine;
    }

    /**
     * Parses the message.
     * @return server info
     * @throws InvalidConnectException if the server is invalid
     */
    Server parse() throws InvalidConnectException {
        final StringTokenizer tokens = new StringTokenizer(requestLine);

        validateMethod(tokens);

        validateMessageFormat(tokens);

        final String[] parts = splitAndValidateFormat(tokens);

        return toServer(parts);
    }

    private static void validateMessageFormat(final StringTokenizer tokens) throws InvalidConnectException {
        if (!tokens.hasMoreTokens()) {
            throw new InvalidConnectException("Invalid " + METHOD + " message");
        }
    }

    private static void validateMethod(final StringTokenizer tokens) throws InvalidConnectException {
        final String method = tokens.hasMoreTokens() ? tokens.nextToken() : "";
        if (!METHOD.equalsIgnoreCase(method)) {
            throw new InvalidConnectException("Not a " + METHOD + " message");
        }
    }

    private static String[] splitAndValidateFormat(final StringTokenizer tokens) throws InvalidConnectException {
        final String server = tokens.nextToken();
        final String[] parts = server.split(":");
        if (parts.length != 2) {
            throw new InvalidConnectException("Invalid server in " + METHOD);
        }
        return parts;
    }

    private static Server toServer(final String[] parts) throws InvalidConnectException {
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
    static ConnectParser fromRequestLine(final String line) {
        return new ConnectParser(line);
    }
}
