package de.ganskef.shortcircuit.proxy.examples;

import de.ganskef.shortcircuit.proxy.SslContextFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;

/**
 * This is a HTTP server based on the Netty
 * <a href="http://netty.io/wiki/user-guide-for-4.x.html>documentation</a>.
 * 
 * It's an example to demonstrate a multiply handler application. This way it's
 * possible to divide different server and/or proxy requirements into
 * independent components.
 */
public class EvaluationServer {

    private final int port;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    public EvaluationServer(int port) {
        this(port, new NioEventLoopGroup(), new NioEventLoopGroup());
    }

    public EvaluationServer(int port, EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        this.port = port;
        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;
    }

    public void run() {
        try {
            ChannelFuture f = start();
            waitUntilInterrupted(f);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            stop();
        }
    }

    public ChannelFuture start() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup);
        b.channel(NioServerSocketChannel.class);
        b.handler(new LoggingHandler("boss"));
        b.childHandler(new EvaluationServerInitializer(new SslContextFactory()));
        b.option(ChannelOption.SO_BACKLOG, 128);
        b.childOption(ChannelOption.SO_KEEPALIVE, true);

        // Start server...
        ChannelFuture f = b.bind(port).sync();
        return f;
    }

    public void waitUntilInterrupted(ChannelFuture f) throws InterruptedException {
        // ...and listening until close.
        f.channel().closeFuture().sync();
    }

    public void stop() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    public static void main(String[] args) {
        new EvaluationServer(9090).run();
    }

}
