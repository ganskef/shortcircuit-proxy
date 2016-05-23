package de.ganskef.shortcircuit.proxy.examples;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * A HTTP proxy handler for the backend server derived from <a href=
 * "http://netty.io/5.0/xref/io/netty/example/proxy/HexDumpProxyBackendHandler.html"
 * >io.netty.example.proxy.HexDumpProxyBackendHandler</a>.
 */
public class NettyProxyBackendHandler extends ChannelInboundHandlerAdapter {

    private final Channel inboundChannel;

    public NettyProxyBackendHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        inboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    future.channel().close();
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        NettyProxyFrontendHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        NettyProxyFrontendHandler.closeOnFlush(ctx.channel());
    }

}
