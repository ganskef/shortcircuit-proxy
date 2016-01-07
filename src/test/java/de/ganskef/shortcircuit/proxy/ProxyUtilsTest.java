package de.ganskef.shortcircuit.proxy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProxyUtilsTest {

    @Test(expected = RuntimeException.class)
    public void testInputNull() {
        ProxyUtils.stripHost(null);
    }

    @Test
    public void testHttpAddress() {
        String actual = ProxyUtils.stripHost("http://localhost/");
        assertEquals("/", actual);
    }

    @Test
    public void testHttpNumeric() {
        String actual = ProxyUtils.stripHost("http://127.0.0.1/");
        assertEquals("/", actual);
    }

    @Test
    public void testShemaNotCaseSensitive() {
        String actual = ProxyUtils.stripHost("HTTP://localhost/");
        assertEquals("/", actual);
    }

    @Test
    public void testSecureAddress() {
        String actual = ProxyUtils.stripHost("https://localhost/");
        assertEquals("/", actual);
    }

    @Test
    public void testHttpWithoutSlash() {
        String actual = ProxyUtils.stripHost("http://localhost");
        assertEquals("/", actual);
    }

    @Test
    public void testHttpWithPath() {
        String actual = ProxyUtils.stripHost("http://localhost/LICENSE.txt");
        assertEquals("/LICENSE.txt", actual);
    }

}
