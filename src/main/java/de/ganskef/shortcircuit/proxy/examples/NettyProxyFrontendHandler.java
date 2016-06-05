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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * A HTTP proxy handler for the frontend client usually a browser derived from
 * <a href=
 * "http://netty.io/5.0/xref/io/netty/example/proxy/HexDumpProxyFrontendHandler.html"
 * >io.netty.example.proxy.HexDumpProxyFrontendHandler</a>.
 */
public class NettyProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NettyProxyFrontendHandler.class);

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
                // TODO could be a direct request to the proxy server
                // TODO could be a CONNECT request, HTTPS or proxy tunneling
                /*
                 * 405 Method Not Allowed
                 * 
                 * The method specified in the Request-Line is not allowed for
                 * the resource identified by the Request-URI. The response MUST
                 * include an Allow header containing a list of valid methods
                 * for the requested resource.
                 */
                // TODO answer with a 404 or 400 response here
                /*
                 * 400 Bad Request
                 * 
                 * The request could not be understood by the server due to
                 * malformed syntax. The client SHOULD NOT repeat the request
                 * without modifications.
                 */
                /*
                 * 404 Not Found
                 * 
                 * The server has not found anything matching the Request-URI.
                 * No indication is given of whether the condition is temporary
                 * or permanent. The 410 (Gone) status code SHOULD be used if
                 * the server knows, through some internally configurable
                 * mechanism, that an old resource is permanently unavailable
                 * and has no forwarding address. This status code is commonly
                 * used when the server does not wish to reveal exactly why the
                 * request has been refused, or when no other response is
                 * applicable.
                 */
//                HttpResponseStatus status = HttpResponseStatus.NOT_FOUND;
//                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
//                        Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
//                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
//                // Close the connection as soon as the error message is sent.
//                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
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

    private void initOutboundChannel(final ChannelHandlerContext ctx, final HttpRequest request,
            SocketAddress address) {
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
                    logger.warn("An exception was thrown:", future.cause());
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
        logger.error("An exception was thrown:", cause);
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