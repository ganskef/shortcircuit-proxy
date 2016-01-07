package de.ganskef.shortcircuit.proxy.examples;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * An example of a minimal HTTP proxy.
 * 
 * @see io.netty.example.proxy.HexDumpProxy#main(String[])
 * */
public class NettyProxy {

    private static final int LOCAL_PORT = 9090;

    private static final int WORKER_THREAD_COUNT = 10;

    public static void main(String[] args) throws Exception {

        System.err.println("Proxying at localhost:" + LOCAL_PORT + " ...");

        // Configure the bootstrap.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(WORKER_THREAD_COUNT);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup);
            b.channel(NioServerSocketChannel.class);
            b.handler(new LoggingHandler(LogLevel.INFO));
            b.childHandler(new NettyProxyInitializer());
            b.childOption(ChannelOption.AUTO_READ, false);
            ChannelFuture f = b.bind(LOCAL_PORT).sync();
            f.sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
