package com.spinthechoice.privateproxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

class Tunnel extends Thread implements AutoCloseable {
    private static final int BUFFER_SIZE = 400;

    private final Socket sockIn;
    private final Socket sockOut;
    private final InputStream input;
    private final OutputStream output;

    Tunnel(final Socket sockIn, final Socket sockOut) throws IOException {
        this.sockIn = sockIn;
        this.sockOut = sockOut;
        this.input = sockIn.getInputStream();
        this.output = sockOut.getOutputStream();
    }

    @Override
    public void run() {
        final byte[] buf = new byte[BUFFER_SIZE];
        int bytesRead;

        try {
            while ((bytesRead = input.read(buf)) >= 0) {
                output.write(buf, 0, bytesRead);
                output.flush();
            }
        } catch (IOException e) {
            close();
        }
    }

    @Override
    public void close() {
        close(sockIn);
        close(sockOut);
    }

    private void close(final Socket socket) {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) { }
    }
}
