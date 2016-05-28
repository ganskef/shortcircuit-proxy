package de.ganskef.shortcircuit.proxy.examples;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;

/**
 * A HTTP proxy derived from
 * <a href="http://netty.io/5.0/xref/io/netty/example/proxy/HexDumpProxy.html" >
 * io.netty.example.proxy.HexDumpProxy</a> with upstream address to connect the
 * server is taken from requested URI. Additionally the start up is divided into
 * methods to use in tests.
 */
public class NettyProxy {

    private static final int LOCAL_PORT = 9090;

    private static final int WORKER_THREAD_COUNT = 10;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    public static void main(String[] args) throws Exception {
        NettyProxy me = new NettyProxy();
        try {
            me.start(WORKER_THREAD_COUNT);
        } finally {
            me.stop();
        }
    }

    /**
     * Runs a Netty proxy with the given count of worker threads and default
     * parameters. Use {@link #startHook(ServerBootstrap)} to change the needed
     * behavior. Also, this method is the place to bind with the port to start
     * the proxy server.
     */
    public void start(int workerThreadCount) {
        // Configure the bootstrap.
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(workerThreadCount);
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup);
        b.channel(NioServerSocketChannel.class);
        b.handler(new LoggingHandler(NettyProxy.class));
        b.childHandler(new NettyProxyFrontendInitializer());
        b.childOption(ChannelOption.AUTO_READ, false);
        startHook(b);
    }

    /**
     * Bind the port and to override behavior. Default behavior is to run a
     * proxy and wait until close.
     */
    protected void startHook(ServerBootstrap b) {
        try {
            ChannelFuture f = b.bind(LOCAL_PORT).sync();
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            stop();
        }
    }

    /**
     * Shut down the workers gracefully and waits for quitting.
     */
    public void stop() {
        if (bossGroup != null) {
            try {
                bossGroup.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                // ignored in shutdown
            }
            bossGroup = null;
        }
        if (workerGroup != null) {
            try {
                workerGroup.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                // ignored in shutdown
            }
            workerGroup = null;
        }
    }

}
