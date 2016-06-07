[![Build Status](https://travis-ci.org/ganskef/shortcircuit-proxy.png?branch=master)](https://travis-ci.org/ganskef/shortcircuit-proxy)

# Short Circuit Proxy
A proxy library using the [Netty 5 API](http://netty.io/4.1/xref/). 

Starting 2016 I evaluate the Netty 5 API backported to 4.1 to solve my offline and retry handshake issues in [LittleProxy-mitm](https://github.com/ganskef/LittleProxy-mitm). I'm wondering to create a set of handlers with usage examples. The [Netty way](http://netty.io/4.1/api/io/netty/channel/ChannelPipeline.html) seems to be less coherent than LittleProxy does. Handlers could dynamically modify the pipeline to create the flow. Clients could be choose or add and combine handlers to fulfill their requirements. [Examples](https://github.com/ganskef/shortcircuit-proxy/tree/master/src/main/java/de/ganskef/shortcircuit/proxy/examples) and [Tests](https://github.com/ganskef/shortcircuit-proxy/tree/master/src/test/java/de/ganskef/shortcircuit) are starting points for usual cases.

The name Short Circuit Proxy is a reference to the [Short Circuit film](https://en.wikipedia.org/wiki/Short_Circuit_%281986_film%29) and Number 5 or "Johnny Five".


## The Idea

A HTTP proxy server is seldom like an other. There are tunneling, forwarding, 
reverse proxies, with significantly different requirements. HTTP transports 
little text files, images, large binary data, streams, ... Sometimes it should 
be intercepted and modified at application layer, sometimes it should be simply 
transmitted efficiently. Putting them all into one server leads to a huge and 
complex implementation. Huge requirements can lead to problems in the 
realization.

The [Netty project](http://netty.io/index.html) &#171;an asynchronous event-
driven network application framework for rapid development of maintainable high 
performance protocol servers & clients&#187; provides a way out. A Netty 
ChannelHandler is supposed to separate the concerns, passes events, modify the 
pipeline dynamically, and store information which is specific to the handler. 

A goal of *shortcircuit-proxy* is not to fight against the framework. If a thing 
is possible with Netty it should be done there. If it fails it should be fixed 
in *Netty* instead of introducing a workaround. A lot of handlers and tools are 
available. A good starting point is the fine *Netty* documentation 
[there](http://netty.io/wiki/user-guide-for-4.x.html#getting-started) and in the 
[examples](https://github.com/netty/netty/tree/4.1/example/src/main/java/io/netty/example).


## Using Handlers

A HTTP proxy server is basically a HTTP server which gets the contents from the 
upstream server. So the bootstrap is nearly the same. It's a `ServerBootstrap` 
with a parent `boss` and a child `worker` thread pool `EventLoopGroup`. 
`ChannelHandler`s will be added for `boss` and `worker` context. A special 
handler `ChannelInitializer` provides new instances per connection which can 
hold member variables (recommended), but it's possible to set single instance 
handlers instead the initializer too. Connecting upstream works like a HTTP 
client, so it requires a `Bootstrap` with a single thread pool taken from the 
inbound channel, and a separate set of handlers. Transfer state information 
between independent handlers in a context is provided by channel attributes. The 
inbound channel from client and outbound channel to upstream are separated of 
course. 

So, the same handlers, `LoggingHandler` for example, can be added tree times in 
the proxy application. This kind of flexibility causes another type of 
complexity, the complexity of choices. But, there is a reason for. Please refer 
to the Netty examples and the API documentation of the named classes at least. 


## Intercepting

A perfect example of intercepting data is the Netty `LoggingHandler`.


## Encryption 

Work in progress: The Transport Layer Security (TLS) code is taken from 
[ganskef/LittleProxy-mitm](https://github.com/ganskef/LittleProxy-mitm) merged 
with concepts from [OkHttp](http://square.github.io/okhttp/). It would be great 
to have a dedicated module with this stuff without a dependency to HTTP. This 
could be a very small module, but I haven't found such code in *BouncyCastle*.


## Logging

Why is no logging library like SLF4J used in the sources but in tests? A 
dependency to a logging framework should be avoided. As Netty does none 
additional dependency by the independent handlers should be enforced. 

Standardized logging is provided by `io.netty.channel.LoggingHandler` which is 
configured by a logback.xml in the classpath like 
`/shortcircuit-proxy/src/test/resources/logback.xml`.