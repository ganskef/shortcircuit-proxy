package de.ganskef.okproxy;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static okhttp3.internal.Util.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
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

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Tests initially adapted from the tests of
 * {@link okhttp3.mockwebserver.MockWebServer}.
 */
public final class OkProxyServerTest {
    @Rule
    public final MockWebServer server = new MockWebServer();

    @After
    public void after() throws Exception {
        server.shutdown();
    }

    private static OkProxyServer proxy;

    @BeforeClass
    public static void beforeClass() throws Exception {
        String root = new File(".").getAbsolutePath();
        int port = 0;
        proxy = new OkProxyServer(root, port);
        proxy.run();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        // FIXME proxy.stop();
    }

    private static Proxy proxy() {
        SocketAddress sa = InetSocketAddress.createUnresolved("localhost", proxy.getPort());
        return new Proxy(Type.HTTP, sa);
    }

    @Test
    public void directProxyResponse() throws Exception {
        URL url = new URL(format("http://%s:%s/", "localhost", proxy.getPort()));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        InputStream in = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        // FIXME default should be 400 instead of directory listing
        assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());
        assertEquals("OK", connection.getResponseMessage());
        assertTrue(reader.readLine().startsWith("<html><head><title>/</title></head><body><h1>/</h1>"));
    }

    @Test(expected = FileNotFoundException.class)
    public void directProxyNotFoundResponse() throws Exception {
        URL url = new URL(format("http://%s:%s/xxx", "localhost", proxy.getPort()));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, connection.getResponseCode());
        assertEquals("Not Found", connection.getResponseMessage());
        connection.getInputStream();
    }

    @Test(expected = FileNotFoundException.class)
    public void directProxyDoubleDotShouldFail() throws Exception {
        URL url = new URL(format("http://%s:%s/../", "localhost", proxy.getPort()));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, connection.getResponseCode());
        assertEquals("Not Found", connection.getResponseMessage());
        connection.getInputStream();
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

    // @Test TODO returns 500
    public void nonHexadecimalChunkSize() throws Exception {
        server.enqueue(new MockResponse().setBody("G\r\nxxxxxxxxxxxxxxxx\r\n0\r\n\r\n").clearHeaders()
                .addHeader("Transfer-encoding: chunked"));

        URLConnection connection = server.url("/").url().openConnection(proxy());
        InputStream in = connection.getInputStream();
        try {
            in.read();
            fail();
        } catch (IOException expected) {
        }
    }

    /**
     * Throttle the request body by sleeping 500ms after every 3 bytes. With a
     * 6-byte request, this should yield one sleep for a total delay of 500ms.
     */
    // @Test
    public void throttleRequest() throws Exception {
        server.enqueue(new MockResponse().throttleBody(3, 500, TimeUnit.MILLISECONDS));

        long startNanos = System.nanoTime();
        URLConnection connection = server.url("/").url().openConnection(proxy());
        connection.setDoOutput(true);
        connection.getOutputStream().write("ABCDEF".getBytes("UTF-8"));
        InputStream in = connection.getInputStream();
        assertEquals(-1, in.read());
        long elapsedNanos = System.nanoTime() - startNanos;
        long elapsedMillis = NANOSECONDS.toMillis(elapsedNanos);

        assertTrue(format("Request + Response: %sms", elapsedMillis), elapsedMillis >= 500);
        assertTrue(format("Request + Response: %sms", elapsedMillis), elapsedMillis < 1000);
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

    // @Test TODO ???
    public void disconnectRequestHalfway() throws IOException {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_REQUEST_BODY));

        HttpURLConnection connection = (HttpURLConnection) server.url("/").url().openConnection(proxy());
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(1024 * 1024 * 1024); // 1 GB
        connection.connect();
        OutputStream out = connection.getOutputStream();

        byte[] data = new byte[1024 * 1024];
        int i;
        for (i = 0; i < 1024; i++) {
            try {
                out.write(data);
                out.flush();
            } catch (IOException e) {
                break;
            }
        }
        assertEquals(512f, i, 10f); // Halfway +/- 1%
    }

    // @Test TODO ???
    public void disconnectResponseHalfway() throws IOException {
        server.enqueue(new MockResponse().setBody("ab").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

        URLConnection connection = server.url("/").url().openConnection(proxy());
        assertEquals(2, connection.getContentLength());
        InputStream in = connection.getInputStream();
        assertEquals('a', in.read());
        try {
            int byteRead = in.read();
            // OpenJDK behavior: end of stream.
            assertEquals(-1, byteRead);
        } catch (ProtocolException e) {
            // On Android, HttpURLConnection is implemented by OkHttp v2. OkHttp
            // treats an incomplete response body as a ProtocolException.
        }
    }

}
