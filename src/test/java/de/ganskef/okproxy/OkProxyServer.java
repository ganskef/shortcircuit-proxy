package de.ganskef.okproxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.Okio;

/**
 * Evaluation of a proxy server (working, but incomplete) based on OkHttp - An
 * HTTP & HTTP/2 client for Android and Java applications.
 * <a href="http://square.github.io/okhttp/">http://square.github.io/okhttp/</a>
 */
public class OkProxyServer extends Dispatcher {
    private static final Pattern PROXY_PATTERN = Pattern.compile("https?://.*", Pattern.CASE_INSENSITIVE);
    private static final int MAX_CHUNK_SIZE = 1024 * 1024;
    // private final SSLContext sslContext;
    private final String root;
    private int port;
    private OkHttpClient client;

    public OkProxyServer(/* SSLContext sslContext, */String root, int port) {
        // this.sslContext = sslContext;
        this.root = root;
        this.port = port;
        this.client = new OkHttpClient();
    }

    public int getPort() {
        return port;
    }

    public void run() throws IOException {
        MockWebServer server = new MockWebServer();
        // server.useHttps(sslContext.getSocketFactory(), false);
        server.setDispatcher(this);
        server.start(port);
        port = server.getPort();
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) {
        String path = request.getPath();
        try {
            if (PROXY_PATTERN.matcher(path).matches() && request.getMethod().equals("GET")) {
                return proxyGetResponse(path, request.getHeaders());
            }
            return staticServerResponse(path);
        } catch (FileNotFoundException e) {
            return notFoundResponse(path);
        } catch (IOException e) {
            return errorResponse(e);
        }
    }

    private MockResponse proxyGetResponse(String path, Headers headers) throws IOException {
        Request request = new Request.Builder().url(path).headers(headers).get().build();
        Response response = client.newCall(request).execute();

        Buffer body = new Buffer();
        body.writeAll(response.body().source());

        return new MockResponse() //
                .setResponseCode(response.code()) //
                .setHeaders(response.headers()) //
                .setChunkedBody(body, MAX_CHUNK_SIZE);
    }

    private MockResponse errorResponse(IOException e) {
        return new MockResponse() //
                .setStatus("HTTP/1.1 500 Server Error") //
                .addHeader("content-type: text/plain; charset=utf-8") //
                .setBody("SERVER ERROR: " + e);
    }

    private MockResponse notFoundResponse(String path) {
        return new MockResponse() //
                .setStatus("HTTP/1.1 404 Not Found") //
                .addHeader("content-type: text/plain; charset=utf-8") //
                .setBody("NOT FOUND: " + path);
    }

    private MockResponse staticServerResponse(String path) throws FileNotFoundException, IOException {
        if (!path.startsWith("/") || path.contains("..")) {
            throw new FileNotFoundException();
        }
        File file = new File(root + path);
        return file.isDirectory() ? directoryToResponse(path, file) : fileToResponse(path, file);
    }

    private MockResponse directoryToResponse(String basePath, File directory) {
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }
        StringBuilder response = new StringBuilder();
        response.append(String.format("<html><head><title>%s</title></head><body>", basePath));
        response.append(String.format("<h1>%s</h1>", basePath));
        for (String file : directory.list()) {
            response.append(String.format("<div class='file'><a href='%s'>%s</a></div>", basePath + file, file));
        }
        response.append("</body></html>");

        return new MockResponse() //
                .setStatus("HTTP/1.1 200 OK") //
                .addHeader("content-type: text/html; charset=utf-8") //
                .setBody(response.toString());
    }

    private MockResponse fileToResponse(String path, File file) throws IOException {
        return new MockResponse() //
                .setStatus("HTTP/1.1 200 OK") //
                .setBody(fileToBytes(file)) //
                .addHeader("content-type: " + contentType(path));
    }

    private Buffer fileToBytes(File file) throws IOException {
        Buffer result = new Buffer();
        result.writeAll(Okio.source(file));
        return result;
    }

    private String contentType(String path) {
        if (path.endsWith(".png"))
            return "image/png";
        if (path.endsWith(".jpg"))
            return "image/jpeg";
        if (path.endsWith(".jpeg"))
            return "image/jpeg";
        if (path.endsWith(".gif"))
            return "image/gif";
        if (path.endsWith(".html"))
            return "text/html; charset=utf-8";
        if (path.endsWith(".txt"))
            return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    public static void main(String[] args) throws Exception {
        // if (args.length != 4) {
        // System.out.println("Usage: SampleServer <keystore> <password> <root
        // file>
        // <port>");
        // return;
        // }

        // String keystoreFile = args[0];
        // String password = args[1];
        String root = new File(".").getAbsolutePath();
        int port = 9090;

        // SSLContext sslContext = sslContext(keystoreFile, password);
        OkProxyServer server = new OkProxyServer(/* sslContext, */root, port);
        server.run();
    }
}
