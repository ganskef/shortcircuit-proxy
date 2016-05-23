package de.ganskef.test;

import java.io.File;

import javax.net.ssl.SSLException;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

public class SecureServer extends Server {

    public SecureServer(int port) {
        super(port);
    }

    public Server start() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate("localhost");
        return initServerContext(ssc.certificate(), ssc.privateKey());
    }

    protected Server initServerContext(File certChainFile, File keyFile)
            throws SSLException, InterruptedException {
        SslContext sslCtx = SslContextBuilder.forServer(certChainFile, keyFile).build();
        return super.start(sslCtx);
    }

    @Override
    public String getBaseUrl() {
        if (getPort() == 443) {
            return ("https://localhost");
        } else {
            return ("https://localhost:" + getPort());
        }
    }

    public static void main(String[] args) throws Exception {
        new SecureServer(8083).start();
        waitUntilInterupted();
    }

}
