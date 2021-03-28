package com.spinthechoice.privateproxy;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.util.concurrent.Executors;

import com.spinthechoice.privateproxy.client.GiphyClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

class ProxyServerTest {
    private static final int PORT = 8443;

    private static ProxyServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = new ProxyServer(
                PORT, ServerSocketFactory.getDefault(),
                Executors.newSingleThreadExecutor(), 1);
        server.run();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @Test
    void badRequestIfNotConnect() {
        IOException e = assertThrows(IOException.class, this::wrongMethod);
        assertThat(e.getMessage(), containsString("\"HTTP/1.1 400 Not a CONNECT message\""));
    }

    @Test
    void badRequestIfUntrustedHost() {
        IOException e = assertThrows(IOException.class, this::untrustedHost);
        assertThat(e.getMessage(), allOf(
                containsString("\"HTTP/1.1 400 "),
                containsString("google.com"),
                containsString(" is not trusted\"")));
    }

    @Test
    void badRequestIfUnresolvableHost() {
        IOException e = assertThrows(IOException.class, this::unresolvableHost);
        assertThat(e.getMessage(), containsString("\"HTTP/1.1 400 Invalid host in CONNECT\""));
    }

    @Test
    void badRequestIfBadHostFormat() {
        IOException e = assertThrows(IOException.class, this::badHostFormat);
        assertThat(e.getMessage(), containsString("\"HTTP/1.1 400 Invalid server in CONNECT\""));
    }

    private static String request() throws IOException {
        final GiphyClient client = new GiphyClient("localhost", PORT, "TEST");
        return request(client);
    }

    private static String request(final GiphyClient client) throws IOException {
        return client.search( "giraffe");
    }

    private String wrongMethod() throws IOException {
        return request(new GiphyClient("localhost", PORT, "TEST") {
            @Override
            protected String connect() {
                return "KONNECT";
            }
        });
    }

    private String untrustedHost() throws IOException {
        return request(new GiphyClient("localhost", PORT, "TEST") {
            @Override
            protected String giphyApi() {
                return "google.com";
            }
        });
    }

    private String unresolvableHost() throws IOException {
        return request(new GiphyClient("localhost", PORT, "TEST") {
            @Override
            protected String giphyApi() {
                return "api.example.com";
            }
        });
    }

    private String badHostFormat() throws IOException {
        return request(new GiphyClient("localhost", PORT, "TEST") {
            @Override
            protected String giphyApi() {
                return "api.giphy.com:foo";
            }
        });
    }
}
