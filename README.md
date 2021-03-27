# private-proxy

Proxy to [Giphy](https://giphy.com/) [API](https://developers.giphy.com/docs/api/) that's intended to preserve users' privacy.

## Building the project
```
gradle clean build
```

## Start the server
```
gradle run --args="8443"
```
or
```
java -cp build/libs/private-proxy-all.jar \
com.spinthechoice.privateproxy.ProxyServer \
8443
```

## Start the client
| :point_up:    | Include your API key and search term instead of the placeholders |
|---------------|:-----------------------------------------------------------------|
```
java -cp build/libs/private-proxy-all.jar \
com.spinthechoice.privateproxy.client.GiphyClient \
localhost 8443 API_KEY SEARCH_TERM
```
