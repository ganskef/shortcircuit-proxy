package de.ganskef.shortcircuit.proxy.examples;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static okhttp3.internal.Util.format;
import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.test.IProxy;
import io.netty.bootstrap.ServerBootstrap;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** Tests for NettyProxy using MockWebServer from OkHttp. */
public final class NettyProxyOkHttpTest {

    private static final Logger log = LoggerFactory.getLogger(NettyProxyOkHttpTest.class);
    @Rule
    public final MockWebServer server = new MockWebServer();

    private static IProxy proxy;

    @After
    public void after() throws IOException {
        server.shutdown();
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        proxy = createNettyProxy(9092).start();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        proxy.stop();
    }

    public static IProxy createNettyProxy(final int proxyPort) {
        return new IProxy() {

            private NettyProxy delegate = new NettyProxy() {
                protected void startHook(ServerBootstrap b) {
                    try {
                        b.bind(getProxyPort()).sync();
                        log.info("Proxy running at localhost:{}", getProxyPort());
                    } catch (Exception e) {
                        throw new IllegalStateException("Proxy start failed at localhost:" + getProxyPort(), e);
                    }
                }
            };

            @Override
            public int getProxyPort() {
                return proxyPort;
            }

            @Override
            public IProxy start() {
                delegate.start(1);
                return this;
            }

            @Override
            public void stop() {
                delegate.stop();
            }
        };
    }

    private static Proxy proxy() {
        SocketAddress sa = InetSocketAddress.createUnresolved("localhost", proxy.getProxyPort());
        return new Proxy(Type.HTTP, sa);
    }

    @Test
    public void regularResponse() throws Exception {
        server.enqueue(new MockResponse().setBody("hello world"));

        URL url = server.url("/").url();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy());
        connection.setRequestProperty("Accept-Language", "en-US");
        InputStream in = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());
        assertEquals("hello world", reader.readLine());

        RecordedRequest request = server.takeRequest();
        assertEquals("GET / HTTP/1.1", request.getRequestLine());
        assertEquals("en-US", request.getHeader("Accept-Language"));
    }

    // TODO @Test(expected=FileNotFoundException.class)
    public void directGetToProxy() throws Exception {
        URL url = new URL(format("http://%s:%s/", "localhost", proxy.getProxyPort()));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, connection.getResponseCode());
        assertEquals("Bad Request", connection.getResponseMessage());
        connection.getInputStream();
    }

    @Test
    public void redirect() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_MOVED_TEMP)
                .addHeader("Location: " + server.url("/new-path")).setBody("This page has moved!"));
        server.enqueue(new MockResponse().setBody("This is the new location!"));

        URLConnection connection = server.url("/").url().openConnection(proxy());
        InputStream in = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        assertEquals("This is the new location!", reader.readLine());

        RecordedRequest first = server.takeRequest();
        assertEquals("GET / HTTP/1.1", first.getRequestLine());
        RecordedRequest redirect = server.takeRequest();
        assertEquals("GET /new-path HTTP/1.1", redirect.getRequestLine());
    }

    /**
     * Test that MockWebServer blocks for a call to enqueue() if a request is
     * made before a mock response is ready.
     */
    @Test
    public void dispatchBlocksWaitingForEnqueue() throws Exception {
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                server.enqueue(new MockResponse().setBody("enqueued in the background"));
            }
        }.start();

        URLConnection connection = server.url("/").url().openConnection(proxy());
        InputStream in = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        assertEquals("enqueued in the background", reader.readLine());
    }

    /**
     * Throttle the response body by sleeping 500ms after every 3 bytes. With a
     * 6-byte response, this should yield one sleep for a total delay of 500ms.
     */
    @Test
    public void throttleResponse() throws Exception {
        server.enqueue(new MockResponse().setBody("ABCDEF").throttleBody(3, 500, TimeUnit.MILLISECONDS));

        long startNanos = System.nanoTime();
        URLConnection connection = server.url("/").url().openConnection(proxy());
        InputStream in = connection.getInputStream();
        assertEquals('A', in.read());
        assertEquals('B', in.read());
        assertEquals('C', in.read());
        assertEquals('D', in.read());
        assertEquals('E', in.read());
        assertEquals('F', in.read());
        assertEquals(-1, in.read());
        long elapsedNanos = System.nanoTime() - startNanos;
        long elapsedMillis = NANOSECONDS.toMillis(elapsedNanos);

        assertTrue(format("Request + Response: %sms", elapsedMillis), elapsedMillis >= 500);
        assertTrue(format("Request + Response: %sms", elapsedMillis), elapsedMillis < 1000);
    }

    /** Delay the response body by sleeping 1s. */
    @Test
    public void delayResponse() throws IOException {
        server.enqueue(new MockResponse().setBody("ABCDEF").setBodyDelay(1, SECONDS));

        long startNanos = System.nanoTime();
        URLConnection connection = server.url("/").url().openConnection(proxy());
        InputStream in = connection.getInputStream();
        assertEquals('A', in.read());
        long elapsedNanos = System.nanoTime() - startNanos;
        long elapsedMillis = NANOSECONDS.toMillis(elapsedNanos);
        assertTrue(format("Request + Response: %sms", elapsedMillis), elapsedMillis >= 1000);

        in.close();
    }

}
