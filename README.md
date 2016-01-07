[![Build Status](https://travis-ci.org/ganskef/shortcircuit-proxy.png?branch=master)](https://travis-ci.org/ganskef/shortcircuit-proxy)

# Short Circuit Proxy
A proxy library using the [Netty 5 API](http://netty.io/4.1/xref/). 

Starting 2016 I evaluate the Netty 5 API backported to 4.1 to solve my offline and retry handshake issues in [LittleProxy-mitm](https://github.com/ganskef/LittleProxy-mitm). I'm wondering to create a set of handlers with usage examples. The [Netty way](http://netty.io/4.1/api/io/netty/channel/ChannelPipeline.html) seems to be less coherent than LittleProxy does. Handlers could dynamically modify the pipeline to create the flow. Clients could be choose or add and combine handlers to fulfill their requirements. [Examples](https://github.com/ganskef/shortcircuit-proxy/tree/master/src/main/java/de/ganskef/shortcircuit/proxy/examples) and [Tests](https://github.com/ganskef/shortcircuit-proxy/tree/master/src/test/java/de/ganskef/shortcircuit) are starting points for usual cases.

The name Short Circuit Proxy is a reference to the [Short Circuit film](https://en.wikipedia.org/wiki/Short_Circuit_%281986_film%29) and Number 5 or "Johnny Five".
