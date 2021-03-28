package com.spinthechoice.privateproxy.client;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Giphy API client that searches for GIFs.
 * It connects to Giphy via a tunnel (over which SSL is negotiated).
 *
 * This is based on Oracle's example
 * <a href="https://docs.oracle.com/javase/10/security/sample-code-illustrating-secure-socket-connection-client-and-server.htm">SSLSocketClientWithTunnelling</a>.
 * As the client was not part of the coding challenge, this client is pretty bare-bones.
 */
public class GiphyClient {
    private final String tunnelHost;
    private final int tunnelPort;
    private final String apiKey;

    /**
     * Creates a client.
     * @param tunnelHost tunnel host (this is not Giphy)
     * @param tunnelPort tunnel port
     * @param apiKey Giphy API key
     */
    public GiphyClient(final String tunnelHost, final int tunnelPort, final String apiKey) {
        this.tunnelHost = tunnelHost;
        this.tunnelPort = tunnelPort;
        this.apiKey = apiKey;
    }

    // Exposed for testing
    protected String giphyApi() {
        return "api.giphy.com";
    }

    // Exposed for testing
    protected int giphyPort() {
        return 443;
    }

    // Exposed for testing
    protected String connect() {
        return "CONNECT";
    }

    private String userAgent() {
        return getClass().getName();
    }

    /**
     * Searches for GIFs based on the specified search term.
     * @param search search term
     * @return response
     * @throws IOException any communication errors
     */
    public String search(final String search) throws IOException {
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
        doTunnelHandshake(tunnel, giphyApi(), giphyPort());

        /*
         * Ok, let's overlay the tunnel socket with SSL.
         */
        SSLSocket socket =
                (SSLSocket)factory.createSocket(tunnel, giphyApi(), giphyPort(), true);

        /*
         * register a callback for handshaking completion event
         */
        socket.addHandshakeCompletedListener(
                new HandshakeCompletedListener() {
                    public void handshakeCompleted(HandshakeCompletedEvent event) {
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
        out.println("Host: " + giphyApi() + ":" + giphyPort());
        out.println("User-Agent: " + userAgent());
        out.println();
        out.flush();

        /*
         * Make sure there were no surprises
         */
        if (out.checkError())
            System.err.println(getClass().getSimpleName() + ":  java.io.PrintWriter error");

        /* read response */
        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                        socket.getInputStream()));

        final StringBuilder allInput = new StringBuilder();

        String inputLine;

        while ((inputLine = in.readLine()) != null) {
            allInput.append(inputLine).append(System.lineSeparator());
        }

        in.close();
        out.close();
        socket.close();
        tunnel.close();
        return allInput.toString();
    }

    /*
     * Tell our tunnel where we want to CONNECT, and look for the
     * right reply.  Throw IOException if anything goes wrong.
     */
    private void doTunnelHandshake(Socket tunnel, String host, int port) throws IOException {
        OutputStream out = tunnel.getOutputStream();
        String msg = connect() + " " + host + ":" + port + " HTTP/1.1\n"
                + "User-Agent: " + userAgent()
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

    /**
     * Search for GIFs via the client.
     * The arguments are
     * <ol>
     *     <li>tunnel host (this is not Giphy)</li>
     *     <li>tunnel port</li>
     *     <li>API key</li>
     *     <li>first search term*</li>
     * </ol>
     * *Additional search terms may be specified after this
     * @param args arguments
     * @throws Exception any errors
     */
    public static void main(final String[] args) throws Exception {
        final ExecutorService executor = Executors.newCachedThreadPool();
        for (int i = 3; i < args.length; ++i) {
            final String searchTerm = args[i];
            final boolean lastSearch = i == args.length - 1;
            executor.submit(() -> {
                final GiphyClient client = new GiphyClient(args[0], Integer.parseInt(args[1]), args[2]);
                final String result = client.search(searchTerm);
                System.out.println("Result length [" + searchTerm + "]: " + result.length());
                if (lastSearch) {
                    System.out.println(result);
                }
                return result;
            });
        }
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);
    }
}
