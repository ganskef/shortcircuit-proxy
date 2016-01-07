package de.ganskef.shortcircuit.proxy.examples;

import static org.junit.Assert.assertEquals;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import de.ganskef.test.Client;
import de.ganskef.test.IProxy;
import de.ganskef.test.Server;

public class NettyProxyTest {

    private static IProxy proxy;
    private static Server server;

    @AfterClass
    public static void afterClass() {
        server.stop();
        proxy.stop();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        server = new Server(9091).start();
    }

    @BeforeClass
    public static void initProxy() throws Exception {
        proxy = new IProxy() {

            private NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            private NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);

            @Override
            public int getProxyPort() {
                return 9092;
            }

            @Override
            public IProxy start() {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup);
                b.channel(NioServerSocketChannel.class);
                b.handler(new LoggingHandler(LogLevel.INFO));
                b.childHandler(new NettyProxyInitializer());
                b.childOption(ChannelOption.AUTO_READ, false);
                try {
                    b.bind(getProxyPort()).sync();
                    System.out.println("Proxy running at localhost:" + getProxyPort());
                } catch (Exception e) {
                    throw new IllegalStateException("Proxy start failed at localhost:" + getProxyPort(), e);
                }
                return this;
            }

            @Override
            public void stop() {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }

        }.start();
    }

    @Test
    public void testHttpProxy() throws Exception {
        String url = server.url("/LICENSE.txt");
        String direct = FileUtils.readFileToString(new Client().get(url));
        String proxied = FileUtils.readFileToString(new Client().get(url, proxy));
        assertEquals(direct, proxied);
    }

    @Test
    public void testMultipleTimesNotBlocking() throws Exception {
        String url = server.url("/LICENSE.txt");
        new Client().get(url, proxy);
        new Client().get(url, proxy);
    }

}
