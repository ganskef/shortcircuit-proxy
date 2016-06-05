package de.ganskef.shortcircuit.proxy;

import java.security.GeneralSecurityException;

import javax.net.ssl.SSLException;

import de.ganskef.tls.MitmCertificate;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

/**
 * Provides Netty {@link SslContext}s for servers with properly signed
 * certificates by common name.
 * 
 * TODO caching
 */
public class SslContextFactory {

    /**
     * Returns an {@link SslContext} for a server with properly signed
     * certificates for the given common name CN. The name is usually the fully
     * qualified domain name of the server, but it could contain wildcard
     * characters too.
     */
    public SslContext getSslContext(String commonName) throws GeneralSecurityException {
        try {
            MitmCertificate root = new MitmCertificate.RootBuilder() //
                    // TODO rebuild(boolean), Authority fields...
                    .build();
            MitmCertificate fake = new MitmCertificate.FakeBuilder() //
                    .commonName(commonName) //
                    .issuedBy(root) //
                    .build();
            return SslContextBuilder.forServer(fake.keyPair.getPrivate(), fake.certificate).build();
        } catch (SSLException e) {
            throw new GeneralSecurityException(e);
        }
    }

}
