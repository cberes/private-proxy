package com.spinthechoice.privateproxy;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;

public class ProxyServer implements Runnable {
    private final int port;

    public ProxyServer(final int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            ServerSocketFactory socketFactory =  ServerSocketFactory.getDefault();
            ServerSocket serverSocket = socketFactory.createServerSocket(port);
            new SocketHandler(serverSocket);
        } catch (IOException e) {
            System.err.println("Unable to start " + SocketHandler.class.getSimpleName() + ": " +
                    e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            System.err.println("USAGE: java " + SocketHandler.class.getSimpleName() + " port");
        }

        int port = Integer.parseInt(args[0]);
        new ProxyServer(port).run();
    }
}
