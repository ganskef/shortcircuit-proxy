package de.ganskef.shortcircuit.proxy.examples;

import java.net.SocketAddress;

import de.ganskef.shortcircuit.proxy.ProxyUtils;
import de.ganskef.shortcircuit.utils.HttpRequestUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.logging.LoggingHandler;

/**
 * A HTTP proxy handler for the frontend client usually a browser derived from
 * <a href=
 * "http://netty.io/5.0/xref/io/netty/example/proxy/HexDumpProxyFrontendHandler.html"
 * >io.netty.example.proxy.HexDumpProxyFrontendHandler</a>.
 */
public class NettyProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private volatile Channel outboundChannel;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        final Channel inboundChannel = ctx.channel();
        inboundChannel.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof HttpRequest) {
            final HttpRequest request = (HttpRequest) msg;
            SocketAddress address = HttpRequestUtil.getInetSocketAddress(request);
            if (address == null) {
                throw new IllegalStateException("Address not resolved, terminate " + msg);
            } else if (outboundChannel == null) {
                initOutboundChannel(ctx, request, address);
            } else if (outboundChannel.isActive()) {
                writeOutbound(ctx, msg);
            }
        } else if (msg instanceof LastHttpContent) {
            // Success, terminator received
        } else {
            // To get an URI to establish a connection to the upstream server
            // it's *necessary* to add a HttpRequestDecoder in the frontend
            // initializer. Doing so here's HttpRequest and LastHttpContent
            // expected only.
            throw new IllegalStateException("Expected request, but read " + msg);
        }
    }

    private void initOutboundChannel(final ChannelHandlerContext ctx, final HttpRequest request, SocketAddress address) {
        final Channel inboundChannel = ctx.channel();
        // Start the connection attempt.
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop());
        b.channel(ctx.channel().getClass());
        b.handler(new NettyProxyBackendHandler(inboundChannel));
        b.option(ChannelOption.AUTO_READ, false);
        ChannelFuture f = b.connect(address);
        outboundChannel = f.channel();
        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    ChannelPipeline p = outboundChannel.pipeline();
                    p.addLast(new LoggingHandler(NettyProxyBackendHandler.class), //
                            new HttpRequestEncoder());

                    // There is no connection caching at the moment.
                    // RFC 2616 HTTP/1.1 section 14.10 says:
                    // HTTP/1.1 applications that do not support persistent
                    // connections MUST include the "close" connection
                    // option
                    // in every message
                    HttpUtil.setKeepAlive(request, false);

                    // URLConnection rejects if the proxied URL won't start
                    // with the query, see RFC 7230 section 5.3.1.
                    String adjustedUri = ProxyUtils.stripHost(request.uri());
                    request.setUri(adjustedUri);

                    writeOutbound(ctx, request);
                } else {
                    // Close the connection if the connection attempt has
                    // failed.
                    inboundChannel.close();
                }
            }
        });
    }

    private void writeOutbound(final ChannelHandlerContext ctx, final Object msg) {
        outboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    // was able to flush out data, start to read the next chunk
                    ctx.channel().read();
                } else {
                    future.channel().close();
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        closeOnFlush(ctx.channel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

}