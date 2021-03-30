package com.spinthechoice.privateproxy;

import javax.net.ServerSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.spinthechoice.privateproxy.client.GiphyClient;
import com.spinthechoice.privateproxy.client.GiphyClient.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ProxyServerTest {
    private static final int PORT = 8443;
    private static final int DEFAULT_THREAD_COUNT = 4;
    private static final int DEFAULT_ITERATIONS = 10_000;

    private static ProxyServer server;

    @BeforeAll
    static void startServer() throws IOException {
        assumeFalse(apiKey() == null);

        server = new ProxyServer(
                PORT, ServerSocketFactory.getDefault(),
                Executors.newFixedThreadPool(threadCount()), threadCount());
        server.run();
    }

    private static String apiKey() {
        return System.getProperty("privateproxy.giphy.key");
    }

    private static int threadCount() {
        final String prop = System.getProperty("privateproxy.threads");
        return prop == null ? DEFAULT_THREAD_COUNT : Integer.parseInt(prop);
    }

    private static int iterations() {
        final String prop = System.getProperty("privateproxy.iters");
        return prop == null ? DEFAULT_ITERATIONS : Integer.parseInt(prop);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void once() throws IOException {
        final GiphyClient client = new GiphyClient("localhost", PORT, apiKey());
        System.out.println(client.search("donut"));
    }

    /**
     * This isn't a real test, just a quick and dirty performance summary.
     * Probably better off using jmeter, but whatever.
     * Also, Giphy is a bad service to run this against due to its rate limiting.
     * Probably best to setup your own server on a separate machine.
     * @throws Exception any errors
     */
    @Test
    void performance() throws Exception {
        // setup everything
        final GiphyClient client = new GiphyClient("localhost", PORT, apiKey());
        final List<String> words = words();
        final Random random = new Random();
        final ExecutorService clientThreads = Executors.newFixedThreadPool(threadCount());
        final int iters = iterations();
        final List<Future<Response>> futures = new ArrayList<>(iters);

        // run the test
        int successCount = 0;
        int failureCount = 0;
        final long start = System.nanoTime();
        for (int i = 0; i < iters; ++i) {
            final String word = words.get(random.nextInt(words.size()));
            futures.add(clientThreads.submit(() -> client.search(word)));
        }

        // wait for clients to complete
        for (Future<Response> f : futures) {
            Response response = f.get();
            if (response.getStatusCode() == 200) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        final long end = System.nanoTime();

        // summary
        System.out.println("Completed " + (successCount + failureCount) + " requests in " + (end - start) + " ns");
        System.out.println("Average time is " + ((end - start) / (double) iters) + " ns");
        System.out.println(successCount + " successful requests, " + failureCount + " failed requests");
    }

    private List<String> words() throws IOException {
        try (final BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        getClass().getResourceAsStream("/words.txt")))) {
            final List<String> words = new ArrayList<>();
            while (reader.ready()) {
                words.add(reader.readLine());
            }
            return words;
        }
    }
}
