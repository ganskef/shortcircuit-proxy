package de.ganskef.test;

import de.ganskef.shortcircuit.proxy.ProxyUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.example.http.file.HttpStaticFileServerInitializer;
import io.netty.handler.ssl.SslContext;

public class Server {

    private NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private NioEventLoopGroup workerGroup = new NioEventLoopGroup();

    private final int port;

    public Server(int port) {
        this.port = port;
    }

    public final int getPort() {
        return port;
    }

    public Server start() throws Exception {
        return start(null);
    }

    protected Server start(SslContext sslCtx) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup);
        b.channel(NioServerSocketChannel.class);
        b.childHandler(new HttpStaticFileServerInitializer(sslCtx));
        b.bind(getPort()).sync();
        return this;
    }

    public void stop() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    public String getBaseUrl() {
        if (port == 80) {
            return ("http://127.0.0.1");
        } else {
            return ("http://127.0.0.1:" + port);
        }
    }

    public static void main(String[] args) throws Exception {
        new Server(8082).start();
        waitUntilInterupted();
    }

    public static void waitUntilInterupted() {
        new Thread() {
            @Override
            public void run() {
                for (;;) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignore) {
                        break;
                    }
                }
            }
        }.run();
    }

    public String url(String uri) {
        return getBaseUrl() + ProxyUtils.stripHost(uri);
    }

}
