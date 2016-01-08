package de.ganskef.shortcircuit.proxy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @see org.littleshoot.proxy.impl.ProxyUtils
 */
public final class ProxyUtils {

    private static final Pattern URI_PATTERN = Pattern.compile("(?:https?://([^:/]+))?(?::(\\d+))?(/.*)",
            Pattern.CASE_INSENSITIVE);

    // private static Pattern HTTP_PREFIX = Pattern.compile("^https?://.*",
    // Pattern.CASE_INSENSITIVE);

    private ProxyUtils() {
    }

    /**
     * Strips the host from a URI string. This will turn "http://host.com/path"
     * into "/path".
     * 
     * @param uri
     *            The URI to transform.
     * @return A string with the URI stripped.
     */
    // public static String stripHost(final String uri) {
    // if (!HTTP_PREFIX.matcher(uri).matches()) {
    // // It's likely a URI path, not the full URI (i.e. the host is
    // // already stripped).
    // return uri;
    // }
    // final String noHttpUri = StringUtils.substringAfter(uri, "://");
    // final int slashIndex = noHttpUri.indexOf("/");
    // if (slashIndex == -1) {
    // return "/";
    // }
    // final String noHostUri = noHttpUri.substring(slashIndex);
    // return noHostUri;
    // }
    public static String stripHost(String uri) {
        Matcher m = URI_PATTERN.matcher(uri);
        if (m.matches()) {
            return m.group(3);
        }
        return "/";
    }

}
