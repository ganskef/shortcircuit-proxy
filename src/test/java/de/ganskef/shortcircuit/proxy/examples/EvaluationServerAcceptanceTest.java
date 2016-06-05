package de.ganskef.shortcircuit.proxy.examples;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

public class EvaluationServerAcceptanceTest {

    @Rule
    public final TestRule timeout = new Timeout(30, TimeUnit.SECONDS);
    // @Rule
    // public final MockWebServer server = new MockWebServer();
    // @Rule
    // public final InMemoryFileSystem fileSystem = new InMemoryFileSystem();

    private static EvaluationServer sut;
    private static OkHttpClient client;

    @AfterClass
    public static void after() throws Exception {
        sut.stop();
    }

    @BeforeClass
    public static void before() throws Exception {
        sut = new EvaluationServer(9092);
        sut.start();

        X509TrustManager trustManager = (X509TrustManager) InsecureTrustManagerFactory.INSTANCE.getTrustManagers()[0];
        SSLSocketFactory sslSocketFactory = createSslSocketFactory(trustManager);
        client = new OkHttpClient.Builder() //
                .sslSocketFactory(sslSocketFactory, trustManager) //
                .build();
    }

    private static SSLSocketFactory createSslSocketFactory(TrustManager trustManager) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] { trustManager }, null);
            return context.getSocketFactory();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void httpServerForProxyUI() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:9092/").openConnection();
        assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());
        // HTTP 1.1 persistent connection needs content-length
        assertEquals("HTTP/1.1 200 OK", connection.getHeaderField(null));
        assertEquals("25", connection.getHeaderField("content-length"));
        InputStream in = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        assertEquals("Response status: 200 OK", reader.readLine());
    }

    @Test
    public void httpServerUnknownUri() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:9092/xxx").openConnection();
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, connection.getResponseCode());
        // HTTP 1.1 persistent connection needs content-length
        assertEquals("HTTP/1.1 404 Not Found", connection.getHeaderField(null));
        assertEquals("24", connection.getHeaderField("content-length"));
        assertEquals("text/plain; charset=UTF-8", connection.getHeaderField("content-type"));
        InputStream in = connection.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        assertEquals("Failure: 404 Not Found", reader.readLine());
    }

    @Test
    public void securedServerForProxyUI() throws Exception {
        Request request = new Request.Builder().url("https://localhost:9092/").build();
        Response response = client.newCall(request).execute();
        assertEquals(HttpURLConnection.HTTP_OK, response.code());
        // HTTP 1.1 persistent connection needs content-length
        assertEquals(Protocol.HTTP_1_1, response.protocol());
        assertEquals("25", response.header("content-length"));
        assertEquals("text/plain; charset=UTF-8", response.header("content-type"));
        assertEquals("Response status: 200 OK", response.body().source().readUtf8Line());
    }

    @Test
    public void securedServerUnknownUri() throws Exception {
        Request request = new Request.Builder().url("https://localhost:9092/xxx").build();
        Response response = client.newCall(request).execute();
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.code());
        // HTTP 1.1 persistent connection needs content-length
        assertEquals(Protocol.HTTP_1_1, response.protocol());
        assertEquals("24", response.header("content-length"));
        assertEquals("text/plain; charset=UTF-8", response.header("content-type"));
        assertEquals("Failure: 404 Not Found", response.body().source().readUtf8Line());
    }

    @Test
    public void proxiedRequestToItsOwnUrlWithoutIllEffect() throws Exception {
        Request request = new Request.Builder().url("http://localhost:9092/").build();
        Proxy proxy = new Proxy(Type.HTTP, new InetSocketAddress("localhost", 9092));
        Response response = client.newBuilder().proxy(proxy).build().newCall(request).execute();
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.code());
        // HTTP 1.1 persistent connection needs content-length
        assertEquals(Protocol.HTTP_1_1, response.protocol());
        assertEquals("24", response.header("content-length"));
        assertEquals("text/plain; charset=UTF-8", response.header("content-type"));
        assertEquals("Failure: 404 Not Found", response.body().source().readUtf8Line());
        // since proxied URI is http://localhost:9092/ instead of /
        // TODO should respond cached or upstream content
    }

    @Test
    public void securedProxiedRequestToItsOwnUrlWithoutIllEffect() throws Exception {
        Request request = new Request.Builder().url("https://localhost:9092/").build();
        Proxy proxy = new Proxy(Type.HTTP, new InetSocketAddress("localhost", 9092));
        Response response = client.newBuilder().proxy(proxy).build().newCall(request).execute();
        assertEquals(HttpURLConnection.HTTP_OK, response.code());
        // HTTP 1.1 persistent connection needs content-length
        assertEquals(Protocol.HTTP_1_1, response.protocol());
        assertEquals("25", response.header("content-length"));
        assertEquals("text/plain; charset=UTF-8", response.header("content-type"));
        assertEquals("Response status: 200 OK", response.body().source().readUtf8Line());
        // since proxied URI is /, TODO needs host and port from CONNECT
        // TODO should respond cached or upstream content
    }

}
