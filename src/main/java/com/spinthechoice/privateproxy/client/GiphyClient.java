package com.spinthechoice.privateproxy.client;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;

/**
 * This example illustrates how to do proxy Tunneling to access a
 * secure web server from behind a firewall.
 *
 * Please set the following Java system properties
 * to the appropriate values:
 *
 *   https.proxyHost = <secure proxy server hostname>
 *   https.proxyPort = <secure proxy server port>
 * TODO update this obsolete comment
 */
public class GiphyClient {
    private static final String GIPHY_API = "api.giphy.com";
    private static final int GIPHY_PORT = 443;
    private static final String USER_AGENT = "gif-tunnel";

    private final String tunnelHost;
    private final int tunnelPort;

    public GiphyClient(final String tunnelHost, final int tunnelPort) {
        this.tunnelHost = tunnelHost;
        this.tunnelPort = tunnelPort;
    }

    public void doIt(final String apiKey, final String search) throws Exception {
        /*
         * Let's setup the SSLContext first, as there's a lot of
         * computations to be done.  If the socket were created
         * before the SSLContext, the server/proxy might timeout
         * waiting for the client to actually send something.
         */
        SSLSocketFactory factory =
                (SSLSocketFactory)SSLSocketFactory.getDefault();

        /*
         * Set up a socket to do tunneling through the proxy.
         * Start it off as a regular socket, then layer SSL
         * over the top of it.
         */
        Socket tunnel = new Socket(tunnelHost, tunnelPort);
        doTunnelHandshake(tunnel, GIPHY_API, GIPHY_PORT);

        /*
         * Ok, let's overlay the tunnel socket with SSL.
         */
        SSLSocket socket =
                (SSLSocket)factory.createSocket(tunnel, GIPHY_API, GIPHY_PORT, true);

        /*
         * register a callback for handshaking completion event
         */
        socket.addHandshakeCompletedListener(
                new HandshakeCompletedListener() {
                    public void handshakeCompleted(
                            HandshakeCompletedEvent event) {
                        System.out.println("Handshake finished!");
                        System.out.println(
                                "\t CipherSuite:" + event.getCipherSuite());
                        System.out.println(
                                "\t SessionId " + event.getSession());
                        System.out.println(
                                "\t PeerHost " + event.getSession().getPeerHost());
                    }
                }
        );

        /*
         * send http request
         *
         * See SSLSocketClient.java for more information about why
         * there is a forced handshake here when using PrintWriters.
         */
        socket.startHandshake();

        PrintWriter out = new PrintWriter(
                new BufferedWriter(
                        new OutputStreamWriter(
                                socket.getOutputStream())));

        out.println(String.format("GET /v1/gifs/search?api_key=%s&q=%s HTTP/1.0", apiKey, search));
        out.println("Host: " + GIPHY_API + ":" + GIPHY_PORT);
        out.println("User-Agent: " + USER_AGENT);
        out.println();
        out.flush();

        /*
         * Make sure there were no surprises
         */
        if (out.checkError())
            System.out.println(
                    "GiphyClient:  java.io.PrintWriter error");

        /* read response */
        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                        socket.getInputStream()));

        String inputLine;

        while ((inputLine = in.readLine()) != null)
            System.out.println(inputLine);

        in.close();
        out.close();
        socket.close();
        tunnel.close();
    }

    /*
     * Tell our tunnel where we want to CONNECT, and look for the
     * right reply.  Throw IOException if anything goes wrong.
     */
    private void doTunnelHandshake(Socket tunnel, String host, int port) throws IOException {
        OutputStream out = tunnel.getOutputStream();
        String msg = "CONNECT " + host + ":" + port + " HTTP/1.1\n"
                + "User-Agent: " + USER_AGENT
                + "\r\n\r\n";
        byte b[];
        try {
            /*
             * We really do want ASCII7 -- the http protocol doesn't change
             * with locale.
             */
            b = msg.getBytes("ASCII7");
        } catch (UnsupportedEncodingException ignored) {
            /*
             * If ASCII7 isn't there, something serious is wrong, but
             * Paranoia Is Good (tm)
             */
            b = msg.getBytes();
        }
        out.write(b);
        out.flush();

        /*
         * We need to store the reply so we can create a detailed
         * error message to the user.
         */
        byte            reply[] = new byte[200];
        int             replyLen = 0;
        int             newlinesSeen = 0;
        boolean         headerDone = false;     /* Done on first newline */

        InputStream     in = tunnel.getInputStream();
        boolean         error = false;

        while (newlinesSeen < 2) {
            int i = in.read();
            if (i < 0) {
                throw new IOException("Unexpected EOF from proxy");
            }
            if (i == '\n') {
                headerDone = true;
                ++newlinesSeen;
            } else if (i != '\r') {
                newlinesSeen = 0;
                if (!headerDone && replyLen < reply.length) {
                    reply[replyLen++] = (byte) i;
                }
            }
        }

        /*
         * Converting the byte array to a string is slightly wasteful
         * in the case where the connection was successful, but it's
         * insignificant compared to the network overhead.
         */
        String replyStr;
        try {
            replyStr = new String(reply, 0, replyLen, "ASCII7");
        } catch (UnsupportedEncodingException ignored) {
            replyStr = new String(reply, 0, replyLen);
        }

        /* We asked for HTTP/1.0, so we should get that back */
        if (!replyStr.startsWith("HTTP/1.1 200")) {
            throw new IOException("Unable to tunnel through "
                    + tunnelHost + ":" + tunnelPort
                    + ".  Proxy returns \"" + replyStr + "\"");
        }

        /* tunneling Handshake was successful! */
    }

    public static void main(final String[] args) throws Exception {
        new GiphyClient(args[0], Integer.parseInt(args[1])).doIt(args[2], args[3]);
    }
}
