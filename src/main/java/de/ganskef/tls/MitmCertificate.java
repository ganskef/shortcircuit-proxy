package de.ganskef.tls;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.x509.X509V3CertificateGenerator;

/**
 * Class to create signed certificates, initially taken from the OkHttp project
 * HeldCertificate. It provides builders for root (Certificate Authority) and
 * fake (dynamically generated) certificates trusted by the root.
 * 
 * A certificate and its private key. This can be used on the server side by
 * HTTPS servers, or on the client side to verify those HTTPS servers. A held
 * certificate can also be used to sign other held certificates, as done in
 * practice by certificate authorities.
 */
public class MitmCertificate {

    /**
     * Current time minus 1 year, just in case software clock goes back due to
     * time synchronization
     */
    private static final Date NOT_BEFORE = new Date(System.currentTimeMillis() - 86400000L * 365);

    /**
     * The maximum possible value in X.509 specification: 9999-12-31 23:59:59,
     * new Date(253402300799000L), but Apple iOS 8 fails with a certificate
     * expiration date grater than Mon, 24 Jan 6084 02:07:59 GMT.
     * 
     * Hundred years in the future from starting the proxy should be enough.
     */
    private static final Date NOT_AFTER = new Date(System.currentTimeMillis() + 86400000L * 365 * 100);

    /**
     * The signature algorithm starting with the message digest to use when
     * signing certificates. On 64-bit systems this should be set to SHA512, on
     * 32-bit systems this is SHA256. On 64-bit systems, SHA512 generally
     * performs better than SHA256; see this question for details:
     * http://crypto.stackexchange.com/questions/26336/sha512-faster-than-
     * sha256
     */
    private static final String SIGNATURE_ALGORITHM = (is32BitJvm() ? "SHA256" : "SHA512") + "WithRSAEncryption";

    public final X509Certificate certificate;

    public final KeyPair keyPair;

    public MitmCertificate(X509Certificate certificate, KeyPair keyPair) {
        this.certificate = certificate;
        this.keyPair = keyPair;
    }

    public static final class FakeBuilder {
        static {
            Security.addProvider(new BouncyCastleProvider());
        }

        private final long duration = 1000L * 60 * 60 * 24; // One day.
        private String hostname;
        private String serialNumber;
        private KeyPair keyPair;
        private MitmCertificate issuedBy;
        private int maxIntermediateCas;

        public FakeBuilder serialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        /**
         * Set this certificate's name. Typically this is the URL hostname for
         * TLS certificates. This is the CN (common name) in the certificate.
         * Will be a random string if no value is provided.
         */
        public FakeBuilder commonName(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public FakeBuilder keyPair(KeyPair keyPair) {
            this.keyPair = keyPair;
            return this;
        }

        /**
         * Set the certificate that signs this certificate. If unset, a
         * self-signed certificate will be generated.
         */
        public FakeBuilder issuedBy(MitmCertificate signedBy) {
            this.issuedBy = signedBy;
            return this;
        }

        /**
         * Set this certificate to be a certificate authority, with up to
         * {@code maxIntermediateCas} intermediate certificate authorities
         * beneath it.
         */
        public FakeBuilder ca(int maxIntermediateCas) {
            this.maxIntermediateCas = maxIntermediateCas;
            return this;
        }

        public MitmCertificate build() throws GeneralSecurityException {
            BigInteger serial = (serialNumber != null) ? new BigInteger(serialNumber)
                    : BigInteger.valueOf(initRandomSerial());

            // Subject, public & private keys for this certificate.
            KeyPair heldKeyPair = keyPair != null ? keyPair : generateKeyPair();
            X500Principal subject = hostname != null ? new X500Principal("CN=" + hostname)
                    : new X500Principal("CN=" + UUID.randomUUID());

            // Subject, public & private keys for this certificate's signer. It
            // may be self signed!
            KeyPair signedByKeyPair;
            X500Principal signedByPrincipal;
            if (issuedBy != null) {
                signedByKeyPair = issuedBy.keyPair;
                signedByPrincipal = issuedBy.certificate.getSubjectX500Principal();
            } else {
                signedByKeyPair = heldKeyPair;
                signedByPrincipal = subject;
            }

            // Generate & sign the certificate.
            long now = System.currentTimeMillis();
            X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
            generator.setSerialNumber(serial);
            generator.setIssuerDN(signedByPrincipal);
            generator.setNotBefore(new Date(now));
            generator.setNotAfter(new Date(now + duration));
            generator.setSubjectDN(subject);
            generator.setPublicKey(heldKeyPair.getPublic());
            generator.setSignatureAlgorithm("SHA256WithRSAEncryption");

            if (maxIntermediateCas > 0) {
                generator.addExtension(X509Extensions.BasicConstraints, true, new BasicConstraints(maxIntermediateCas));
            }

            X509Certificate certificate = generator.generateX509Certificate(signedByKeyPair.getPrivate(), "BC");
            return new MitmCertificate(certificate, heldKeyPair);
        }

        public KeyPair generateKeyPair() throws GeneralSecurityException {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
            keyPairGenerator.initialize(1024, new SecureRandom());
            return keyPairGenerator.generateKeyPair();
        }
    }

    public final static class RootBuilder {
        static {
            Security.addProvider(new BouncyCastleProvider());
        }

        /**
         * The P12 format has to be implemented by every vendor. Oracles
         * proprietary JKS type is not available in Android.
         */
        private static final String KEY_STORE_TYPE = "PKCS12";

        private static final String KEY_STORE_FILE_EXTENSION = ".p12";

        /**
         * Current browsers starts reject root certificates with smaller key
         * size.
         */
        private static final int ROOT_KEYSIZE = 2048;

        private Authority authority = new Authority();

        public KeyStore loadKeyStore() throws GeneralSecurityException {
            KeyStore ks = KeyStore.getInstance(KEY_STORE_TYPE);
            try (FileInputStream is = new FileInputStream(authority.aliasFile(KEY_STORE_FILE_EXTENSION))) {
                ks.load(is, authority.password());
            } catch (IOException e) {
                throw new GeneralSecurityException(e);
            }
            return ks;
        }

        public MitmCertificate build() throws GeneralSecurityException {
            if (!authority.aliasFile(KEY_STORE_FILE_EXTENSION).exists() || !authority.aliasFile(".pem").exists()) {
                initializeKeyStore();
            }
            KeyStore ks = loadKeyStore();
            X509Certificate certificate = (X509Certificate) ks.getCertificate(authority.alias());
            PrivateKey privateKey = (PrivateKey) ks.getKey(authority.alias(), authority.password());
            PublicKey publicKey = certificate.getPublicKey();
            KeyPair keyPair = new KeyPair(publicKey, privateKey);
            return new MitmCertificate(certificate, keyPair);
        }

        private void initializeKeyStore() throws GeneralSecurityException {
            try {
                KeyPair keyPair = generateKeyPair(ROOT_KEYSIZE);

                X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
                nameBuilder.addRDN(BCStyle.CN, authority.commonName());
                nameBuilder.addRDN(BCStyle.O, authority.organization());
                nameBuilder.addRDN(BCStyle.OU, authority.organizationalUnitName());

                X500Name issuer = nameBuilder.build();
                BigInteger serial = BigInteger.valueOf(initRandomSerial());
                X500Name subject = issuer;
                PublicKey pubKey = keyPair.getPublic();

                X509v3CertificateBuilder generator = new JcaX509v3CertificateBuilder(issuer, serial, NOT_BEFORE,
                        NOT_AFTER, subject, pubKey);

                generator.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(pubKey));
                generator.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

                KeyUsage usage = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature
                        | KeyUsage.keyEncipherment | KeyUsage.dataEncipherment | KeyUsage.cRLSign);
                generator.addExtension(Extension.keyUsage, false, usage);

                ASN1EncodableVector purposes = new ASN1EncodableVector();
                purposes.add(KeyPurposeId.id_kp_serverAuth);
                purposes.add(KeyPurposeId.id_kp_clientAuth);
                purposes.add(KeyPurposeId.anyExtendedKeyUsage);
                generator.addExtension(Extension.extendedKeyUsage, false, new DERSequence(purposes));

                X509Certificate cert = signCertificate(generator, keyPair.getPrivate());

                KeyStore keystore = KeyStore.getInstance(KEY_STORE_TYPE);
                keystore.load(null, null);
                keystore.setKeyEntry(authority.alias(), keyPair.getPrivate(), authority.password(),
                        new Certificate[] { cert });

                try (OutputStream os = new FileOutputStream(authority.aliasFile(KEY_STORE_FILE_EXTENSION))) {
                    keystore.store(os, authority.password());
                }

                Certificate ca = keystore.getCertificate(authority.alias());
                exportPem(authority.aliasFile(".pem"), ca);

            } catch (IOException | OperatorCreationException e) {
                throw new GeneralSecurityException(e);
            }
        }

        private void exportPem(File exportFile, Object... certs) throws IOException, CertificateEncodingException {
            try (JcaPEMWriter pw = new JcaPEMWriter(new FileWriter(exportFile))) {
                for (Object cert : certs) {
                    pw.writeObject(cert);
                    pw.flush();
                }
            }
        }

        public KeyPair generateKeyPair(int keysize) throws GeneralSecurityException {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
            keyPairGenerator.initialize(keysize, new SecureRandom());
            return keyPairGenerator.generateKeyPair();
        }

    }

    // TODO integrate Authorithy into builders
    @Deprecated
    public static final class Authority {

        private final File keyStoreDir;

        private final String alias;

        private final char[] password;

        private final String commonName;

        private final String organization;

        private final String organizationalUnitName;

        private final String certOrganization;

        private final String certOrganizationalUnitName;

        /**
         * Create a parameter object with example certificate and certificate
         * authority informations
         */
        public Authority() {
            keyStoreDir = new File(".");
            alias = "littleproxy-mitm"; // proxy id
            password = "Be Your Own Lantern".toCharArray();
            organization = "LittleProxy-mitm"; // proxy name
            commonName = organization + ", describe proxy here"; // MITM is bad
                                                                 // normally
            organizationalUnitName = "Certificate Authority";
            certOrganization = organization; // proxy name
            certOrganizationalUnitName = organization
                    + ", describe proxy purpose here, since Man-In-The-Middle is bad normally.";
        }

        /**
         * Create a parameter object with the given certificate and certificate
         * authority informations
         */
        public Authority(File keyStoreDir, String alias, char[] password, String commonName, String organization,
                String organizationalUnitName, String certOrganization, String certOrganizationalUnitName) {
            super();
            this.keyStoreDir = keyStoreDir;
            this.alias = alias;
            this.password = password;
            this.commonName = commonName;
            this.organization = organization;
            this.organizationalUnitName = organizationalUnitName;
            this.certOrganization = certOrganization;
            this.certOrganizationalUnitName = certOrganizationalUnitName;
        }

        public File aliasFile(String fileExtension) {
            return new File(keyStoreDir, alias + fileExtension);
        }

        public String alias() {
            return alias;
        }

        public char[] password() {
            return password;
        }

        public String commonName() {
            return commonName;
        }

        public String organization() {
            return organization;
        }

        public String organizationalUnitName() {
            return organizationalUnitName;
        }

        public String certOrganisation() {
            return certOrganization;
        }

        public String certOrganizationalUnitName() {
            return certOrganizationalUnitName;
        }

    }

    /**
     * Prevent browser certificate caches, cause of doubled serial numbers using
     * 48bit random number, 16 bit reserved for increasing, serials have to be
     * positive.
     */
    public static long initRandomSerial() {
        final Random rnd = new Random();
        rnd.setSeed(System.currentTimeMillis());
        long sl = ((long) rnd.nextInt()) << 32 | (rnd.nextInt() & 0xFFFFFFFFL);
        sl = sl & 0x0000FFFFFFFFFFFFL;
        return sl;
    }

    private static SubjectKeyIdentifier createSubjectKeyIdentifier(Key key) throws IOException {
        try (ASN1InputStream is = new ASN1InputStream(new ByteArrayInputStream(key.getEncoded()))) {
            ASN1Sequence seq = (ASN1Sequence) is.readObject();
            SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(seq);
            return new BcX509ExtensionUtils().createSubjectKeyIdentifier(info);
        }
    }

    private static X509Certificate signCertificate(X509v3CertificateBuilder certificateBuilder,
            PrivateKey signedWithPrivateKey) throws OperatorCreationException, CertificateException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider("BC")
                .build(signedWithPrivateKey);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateBuilder.build(signer));
    }

    /**
     * Uses the non-portable system property sun.arch.data.model to help
     * determine if we are running on a 32-bit JVM. Since the majority of modern
     * systems are 64 bits, this method "assumes" 64 bits and only returns true
     * if sun.arch.data.model explicitly indicates a 32-bit JVM.
     * 
     * TODO What's about Android? Validate...
     *
     * @return true if we can determine definitively that this is a 32-bit JVM,
     *         otherwise false
     */
    public static boolean is32BitJvm() {
        Integer bits = Integer.getInteger("sun.arch.data.model");
        return bits != null && bits == 32;
    }

}
