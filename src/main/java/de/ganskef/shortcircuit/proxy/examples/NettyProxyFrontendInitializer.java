package de.ganskef.shortcircuit.proxy.examples;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;

public class NettyProxyFrontendInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    public void initChannel(SocketChannel ch) {
        /* Netty default: {@code maxInitialLineLength (4096)} */
        int maxInitialLineLength = 4096 * 2;
        /* Netty default: {@code maxHeaderSize (8192)} */
        int maxHeaderSize = 8192 * 2;
        /* Netty default: {@code maxChunkSize (8192)} */
        int maxChunkSize = 8192 * 2;
        int readerIdleTimeSeconds = 0;
        int writerIdleTimeSeconds = 0;
        int allIdleTimeSeconds = 10;
        ch.pipeline().addLast(new LoggingHandler(NettyProxyFrontendHandler.class), //
                new HttpRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize), //
                new IdleStateHandler(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds), //
                new NettyProxyFrontendHandler());
    }

}
