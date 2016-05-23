package de.ganskef.shortcircuit.utils;

import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.handler.codec.http.HttpRequest;

public final class HttpRequestUtil {

    private static final Pattern URI_PATTERN = Pattern.compile("(?:http://([^:/]+)(?::(\\d+))?/.*|([^:]+):(\\d+))",
            Pattern.CASE_INSENSITIVE);

    private HttpRequestUtil() {
        // don't instantiate
    }

    public static InetSocketAddress getInetSocketAddress(HttpRequest request) {
        String remoteHost;
        int remotePort;
        Matcher m = URI_PATTERN.matcher(request.uri());
        if (m.matches()) {
            String port;
            if (m.group(1) != null) {
                remoteHost = m.group(1);
                port = m.group(2);
            } else {
                remoteHost = m.group(3);
                port = m.group(4);
            }
            remotePort = Integer.parseInt(port == null ? "80" : port);
            return new InetSocketAddress(remoteHost, remotePort);
        }
        return null;
    }

}
