# private-proxy

Proxy to [Giphy](https://giphy.com/) [API](https://developers.giphy.com/docs/api/) that's intended to preserve users' privacy. The client negotiates TLS directly with Giphy, which keeps the user anonymous, while this server cannot decrypt the client's requests to external services. [More info](https://signal.org/blog/giphy-experiment/)

## Requirements

- Java 16
- Gradle 7.0+

## Building the project
```
gradle clean build
```

## Start the server
### From the uberjar
```
java -cp build/libs/private-proxy-all.jar \
com.spinthechoice.privateproxy.ProxyServer \
8443
```
or
```
gradle run --args="8443"
```
You can also specify the number of threads to use
```
gradle run --args="8443 8"
```
### In your code
```java
import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.spinthechoice.privateproxy.ProxyServer;

class Example {
    void startServer() throws IOException {
        int port = 8443;
        int threads = 4;
        ServerSocketFactory socketFactory = ServerSocketFactory.getDefault();
        ExecutorService threadPool = Executors.newFixedThreadPool(threadCount);

        new ProxyServer(
                port,
                socketFactory,
                threadPool,
                threadCount).run();
    }
}
```

## Start the client
| :point_up:    | Include your API key and search term instead of the placeholders |
|---------------|:-----------------------------------------------------------------|
```
java -cp build/libs/private-proxy-all.jar \
com.spinthechoice.privateproxy.client.GiphyClient \
localhost 8443 API_KEY SEARCH_TERM
```
Multiple search terms can be specified, and the client will perform each search in a new thread.

## Notes

### Acknowledgements

I'm not familiar with socket programming, so I had to do some research. I found [an implementation of a proxy tunnel server](https://github.com/openjdk/jdk/blob/05a764f4ffb8030d6b768f2d362c388e5aabd92d/test/jdk/sun/net/www/protocol/https/HttpsURLConnection/ProxyTunnelServer.java) and [examples for secure client/server connections](https://docs.oracle.com/javase/10/security/sample-code-illustrating-secure-socket-connection-client-and-server.htm), on which I based my solution.

### HTTP version

The server always responds with a version of `HTTP/1.1`. However, it could easily read the version from incoming requests and include the same version in responses. I don't know what the proper behavior is for this.

### Giphy client

I included a client for Giphy. Because it wasn't part of the coding task, it's a quick-and-dirty extension of an [example client](https://docs.oracle.com/javase/10/security/sample-code-illustrating-secure-socket-connection-client-and-server.htm).

## Next steps

This code would need additional work if it were to be used in production.

### Server configuration

The ability to configure the server should be added depending on how the server would be deployed. If the [server](src/main/java/com/spinthechoice/privateproxy/ProxyServer.java) will be deployed as an artifact for use by other applications, then the third-party code could do its own configuration. However, the server should support some kind of configuration if it will be deployed as a standalone application.

Also, `ProxyServer` already has a decent amount of configuration. If another dependency is added, it could benefit from having its dependencies moved to a configuration class.

### Additional services

The Giphy service is hard-coded as the only trusted service. If additional services were supported, I would need to find a better way to represent the trusted services.

### Testing

It feels silly to write exhaustive unit tests for code that will never be used. If this were production code, it would need thorough unit tests. Also load testing should be done to ensure it meets any performance requirements.

### Logging

All output is simply sent to stdout and stderr. I would replace this with a logging framework, which would allow for easy configuration of output (whether it's sent to stdout or a file and how often that file is rotated). Additionally, some exceptions are ignored (mostly around closing resources). It's possible these are not useful to log, but it's probably better to log them and decide later that their logging isn't useful.

I don't know that any exceptions that are output would violate a user's privacy, but that's something to keep in mind given this code's purpose.

### Static analysis

If I expected this code to receive updates in the future, I would setup static analysis such as Checkstyle.

### Remove the client

If I were releasing this as a library, I would remove the client. I included it just to verify that the server works. I wouldn't want to get in a situation where clients depend on it and its removal would be a breaking change.
