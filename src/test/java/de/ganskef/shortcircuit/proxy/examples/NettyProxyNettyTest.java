package de.ganskef.shortcircuit.proxy.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.FileNotFoundException;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.test.Client;
import de.ganskef.test.IClient;
import de.ganskef.test.IProxy;
import de.ganskef.test.SecureServer;
import de.ganskef.test.Server;

/** Tests for NettyProxy using Netty based servers. */
public class NettyProxyNettyTest {

    private static final Logger log = LoggerFactory.getLogger(NettyProxyNettyTest.class);

    private static IProxy proxy;
    private static Server server;
    private static Server secureServer;

    @AfterClass
    public static void afterClass() {
        server.stop();
        secureServer.stop();
        proxy.stop();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        server = new Server(9091).start();
        secureServer = new SecureServer(9093).start();
        proxy = NettyProxyOkHttpTest.createNettyProxy(9092).start();
    }

    private IClient getClient() {
        return new Client();
    }

    @Test
    public void testHttpProxy() throws Exception {
        String url = server.url("/LICENSE.txt");
        String direct = FileUtils.readFileToString(getClient().get(url));
        String proxied = FileUtils.readFileToString(getClient().get(url, proxy));
        assertEquals(direct, proxied);
    }

    @Test
    public void testMultipleTimedRequestsShouldNotBlocking() throws Exception {
        String url = server.url("/LICENSE.txt");
        log.info("testMultipleTimesNotBlocking first");
        getClient().get(url, proxy);
        log.info("testMultipleTimesNotBlocking second");
        getClient().get(url, proxy);
        log.info("testMultipleTimesNotBlocking third");
        getClient().get(url, proxy);
        // XXX investigate IOExceptions during tests
        log.info("testMultipleTimesNotBlocking done");
    }

    @Test(expected = FileNotFoundException.class)
    public void testDifferentOrMissedResultsShouldLeadToTestFailures() throws Exception {
        String first = FileUtils.readFileToString(getClient().get(server.url("/LICENSE.txt")));
        String second = FileUtils.readFileToString(getClient().get(server.url("/README.md")));
        assertNotEquals(first, second);
        FileUtils.readFileToString(getClient().get(server.url("/not_existing_file")));
    }

    @Test
    public void testSecuredGetEvaluation() throws Exception {
        String url = secureServer.url("/LICENSE.txt");
        getClient().get(url);
        //getClient().get(url, proxy);
    }
}
