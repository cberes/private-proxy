package com.spinthechoice.privateproxy;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TunnelTest {
    private static class TestSocket extends Socket {
        private final InputStream input;
        private final OutputStream output;
        private boolean closed = false;

        TestSocket(final InputStream input, final OutputStream output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public synchronized void close() {
            closed = true;
        }
    }

    @Test
    void close() throws IOException {
        final TestSocket sockIn = new TestSocket(null, null);
        final TestSocket sockOut = new TestSocket(null, null);
        final Tunnel tunnel = new Tunnel(sockIn, sockOut);
        tunnel.close();

        assertTrue(sockIn.isClosed());
        assertTrue(sockOut.isClosed());
    }

    @Test
    void readsLargeFile() throws IOException {
        final String license = new Scanner(getClass().getResourceAsStream("/LICENSE"), StandardCharsets.UTF_8)
                .useDelimiter("\\A").next();
        tunnel(license);
    }

    @Test
    void readsZeroBytes() throws IOException {
        tunnel("");
    }

    private void tunnel(final String expected) throws IOException {
        final ByteArrayInputStream input = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final TestSocket sockIn = new TestSocket(input, null);
        final TestSocket sockOut = new TestSocket(null, output);
        final Tunnel tunnel = new Tunnel(sockIn, sockOut);

        tunnel.run();
        final String actual = output.toString(StandardCharsets.UTF_8);

        assertEquals(expected, actual);
        assertFalse(sockIn.isClosed());
        assertFalse(sockOut.isClosed());
    }
}
