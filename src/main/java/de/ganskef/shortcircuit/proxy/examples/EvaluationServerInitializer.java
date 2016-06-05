package de.ganskef.shortcircuit.proxy.examples;

import de.ganskef.shortcircuit.proxy.SslContextFactory;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AttributeKey;

/**
 * This initializes a pipeline of a HTTP server with chained handlers answering
 * depending her capabilities or chaining the event up to the last handler.
 * 
 * It's an example to demonstrate a multiply handler application. This way it's
 * possible to divide different server and/or proxy requirements into
 * independent components.
 */
public class EvaluationServerInitializer extends ChannelInitializer<SocketChannel> {

    public static final AttributeKey<String> CONNECTED = AttributeKey.newInstance("CONNECTED_HOST_AND_PORT");

    private final SslContextFactory sslCtxFactory;

    public EvaluationServerInitializer(SslContextFactory sslContextFactory) {
        this.sslCtxFactory = sslContextFactory;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();
        if (sslCtxFactory != null) {
            p.addFirst(new EvaluationServerSslUpdateHandler(sslCtxFactory, CONNECTED));
        }
        p.addLast(new HttpServerCodec(), //
                new HttpObjectAggregator(65536), //
                new ChunkedWriteHandler(), //
                new LoggingHandler("work"), //
                new EvaluationServerConnectHandler(sslCtxFactory, CONNECTED), //
//                new EvaluationServerProxyHandler(sslCtxFactory, CONNECTED), //
                new EvaluationServerHomeHandler(), //
                new EvaluationServerFallbackHandler());
    }

}
