package de.ganskef.shortcircuit.utils;

import static org.junit.Assert.assertEquals;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

import java.net.InetSocketAddress;

import org.junit.Test;

public class HttpRequestUtilTest {

    private HttpRequest createRequest(String uri) {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        return request;
    }

    @Test
    public void testHttpSimpleUri() {
        HttpRequest request = createRequest("http://localhost/");
        InetSocketAddress address = HttpRequestUtil.getInetSocketAddress(request);
        assertEquals("localhost", address.getHostName());
        assertEquals(80, address.getPort());
    }

    @Test
    public void testHttpMoreSlashes() {
        HttpRequest request = createRequest("http://localhost/dir/");
        InetSocketAddress address = HttpRequestUtil.getInetSocketAddress(request);
        assertEquals("localhost", address.getHostName());
        assertEquals(80, address.getPort());
    }

    @Test
    public void testHttpWithPort() {
        HttpRequest request = createRequest("http://localhost:8080/");
        InetSocketAddress address = HttpRequestUtil.getInetSocketAddress(request);
        assertEquals("localhost", address.getHostName());
        assertEquals(8080, address.getPort());
    }

    @Test
    public void testHttpSchemeMustNotCaseSensitive() {
        HttpRequest request = createRequest("HTTP://localhost/");
        InetSocketAddress address = HttpRequestUtil.getInetSocketAddress(request);
        assertEquals("localhost", address.getHostName());
        assertEquals(80, address.getPort());
    }

    @Test
    public void testDirectRequestUri() {
        HttpRequest request = createRequest("/");
        InetSocketAddress address = HttpRequestUtil.getInetSocketAddress(request);
        assertEquals(null, address);
    }

    @Test(expected = NullPointerException.class)
    public void testNull() {
        HttpRequestUtil.getInetSocketAddress(null);
    }

    @Test
    public void testSecureSimpleUri() {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "localhost:443");
        InetSocketAddress address = HttpRequestUtil.getInetSocketAddress(request);
        assertEquals("localhost", address.getHostName());
        assertEquals(443, address.getPort());
    }

}
