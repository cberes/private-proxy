package com.spinthechoice.privateproxy;

import org.junit.jupiter.api.Test;

import com.spinthechoice.privateproxy.ConnectParser.InvalidConnectException;
import com.spinthechoice.privateproxy.ConnectParser.Server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectParserTest {
    private static Server parse(final String s) throws InvalidConnectException {
        return ConnectParser.fromRequestLine(s).parse();
    }

    @Test
    void validConnect() throws InvalidConnectException {
        Server server = parse("CONNECT example.com:8080 HTTP/1.1");
        assertEquals("example.com", server.host().getHostName());
        assertEquals(8080, server.port());
    }

    @Test
    void extraSpaces() throws InvalidConnectException {
        Server server = parse("CONNECT   example.com:8080     HTTP/1.1");
        assertEquals("example.com", server.host().getHostName());
        assertEquals(8080, server.port());
    }

    @Test
    void nullMessage() {
        InvalidConnectException e = assertThrows(InvalidConnectException.class,
                () -> parse(null));
        assertEquals("Not a CONNECT message", e.getMessage());
    }

    @Test
    void emptyMessage() {
        InvalidConnectException e = assertThrows(InvalidConnectException.class,
                () -> parse(""));
        assertEquals("Not a CONNECT message", e.getMessage());
    }

    @Test
    void wrongMethod() {
        InvalidConnectException e = assertThrows(InvalidConnectException.class,
                () -> parse("CONNEKT example.com:8080 HTTP/1.1"));
        assertEquals("Not a CONNECT message", e.getMessage());
    }

    @Test
    void methodOnly() {
        InvalidConnectException e = assertThrows(InvalidConnectException.class,
                () -> parse("CONNECT   "));
        assertEquals("Invalid CONNECT message", e.getMessage());
    }

    @Test
    void noPort() {
        InvalidConnectException e = assertThrows(InvalidConnectException.class,
                () -> parse("CONNECT example.com HTTP/1.1"));
        assertEquals("Invalid server in CONNECT", e.getMessage());
    }

    @Test
    void unresolvableAddress() {
        InvalidConnectException e = assertThrows(InvalidConnectException.class,
                () -> parse("CONNECT invalid:8080 HTTP/1.1"));
        assertEquals("Invalid host in CONNECT", e.getMessage());
    }

    @Test
    void textPort() {
        InvalidConnectException e = assertThrows(InvalidConnectException.class,
                () -> parse("CONNECT example.com:eighty HTTP/1.1"));
        assertEquals("Invalid port in CONNECT", e.getMessage());
    }
}
