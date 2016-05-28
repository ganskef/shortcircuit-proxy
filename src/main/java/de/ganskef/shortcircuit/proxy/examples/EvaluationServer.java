package de.ganskef.shortcircuit.proxy.examples;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * This is a HTTP server based on the Netty <a
 * href="http://netty.io/wiki/user-guide-for-4.x.html>documentation</a>.
 * 
 * It's an example to demonstrate a multiply handler application. This way it's
 * possible to divide different server and/or proxy requirements into
 * independent components.
 */
public class EvaluationServer {

    // private static final Logger log =
    // LoggerFactory.getLogger(EvaluationServer.class);

    private final int port;

    public EvaluationServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        new EvaluationServer(9091).run();
    }

    public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup);
            b.channel(NioServerSocketChannel.class);
            b.childHandler(new EvaluationServerInitializer());
            b.option(ChannelOption.SO_BACKLOG, 128);
            b.childOption(ChannelOption.SO_KEEPALIVE, true);

            // Start server...
            ChannelFuture f = b.bind(port).sync();
            // log.info("Server started and listening on port {}.", port);

            f.channel().closeFuture().sync();
            // log.info("Server channel listening on port {} closed.", port);

        } catch (InterruptedException e) {
            // log.error("Error in main:", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
