package de.ganskef.shortcircuit.proxy.examples;

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
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.SocketAddress;

import de.ganskef.shortcircuit.proxy.ProxyUtils;
import de.ganskef.shortcircuit.utils.HttpRequestUtil;

public class NettyProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private volatile Channel outboundChannel;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println(ctx.channel() + " channelActive");
        final Channel inboundChannel = ctx.channel();
        inboundChannel.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        System.out.println(ctx.channel() + " channelRead");
        if (msg instanceof HttpRequest) {
            final HttpRequest request = (HttpRequest) msg;
            System.err.println(request.uri());

            SocketAddress address = HttpRequestUtil.getInetSocketAddress(request);
            if (outboundChannel == null && address != null) {
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
                            p.addLast(// new LoggingHandler(LogLevel.INFO), //
                            new HttpRequestEncoder());

                            // There is no connection caching at the moment.
                            HttpUtil.setKeepAlive(request, false);

                            // URLConnecttion rejects if the proxied URL won't
                            // start with the query, see RFC 7230 section 5.3.1.
                            String adjustedUri = ProxyUtils.stripHost(request.uri());
                            request.setUri(adjustedUri);

                            writeAndFlush(ctx, request);
                        } else {
                            // Close the connection if the connection attempt
                            // has failed.
                            inboundChannel.close();
                        }
                    }
                });
            } else if (outboundChannel == null) {
                closeOnFlush(ctx.channel());
            } else if (outboundChannel.isActive()) {
                writeAndFlush(ctx, msg);
            } else {
                System.err.println("Expected request, but  " + msg);
            }
        }

    }

    private void writeAndFlush(final ChannelHandlerContext ctx, final Object msg) {
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
        System.out.println(ctx.channel() + " channelInactive");
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println(ctx.channel() + " exceptionCaught");
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        System.out.println(ch + " closeOnFlush");
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

}