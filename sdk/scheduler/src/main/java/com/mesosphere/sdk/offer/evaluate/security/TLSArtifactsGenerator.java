package com.mesosphere.sdk.offer.evaluate.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.clients.CertificateAuthorityClient;

/**
 * Handles the creation of secrets to be stored against a given namespace.
 */
class TLSArtifactsGenerator {

    private static final String KEYSTORE_PRIVATE_KEY_ALIAS = "default";
    private static final String KEYSTORE_ROOT_CA_CERT_ALIAS = "dcos-root";
    // A default password used for securing keystore and private key in the keystore
    @VisibleForTesting
    static final char[] KEYSTORE_PASSWORD = "notsecure".toCharArray();

    private final CertificateAuthorityClient caClient;
    private final KeyPairGenerator keyPairGenerator;

    public TLSArtifactsGenerator(CertificateAuthorityClient caClient) {
        this(caClient, getDefaultKeyPairGenerator());
    }

    /**
     * Wrapper to allow construction in {@code this()} above.
     */
    private static KeyPairGenerator getDefaultKeyPairGenerator() {
        try {
            return KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @VisibleForTesting
    TLSArtifactsGenerator(CertificateAuthorityClient caClient, KeyPairGenerator keyPairGenerator) {
        this.caClient = caClient;
        this.keyPairGenerator = keyPairGenerator;
    }

    /**
     * Returns a mapping of {@link TLSArtifact} types to generated secret content, to be stored in a SecretStore.
     */
    Map<TLSArtifact, String> generate(CertificateNamesGenerator certificateNamesGenerator) throws Exception {
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Get new end-entity certificate from CA
        X509Certificate certificate = caClient.sign(generateCSR(keyPair, certificateNamesGenerator));

        // Get end-entity bundle with Root CA certificate
        List<X509Certificate> certificateChain = new ArrayList<>();
        certificateChain.addAll(caClient.chainWithRootCert(certificate));

        // Build end-entity certificate with CA chain without Root CA certificate
        Collection<X509Certificate> endEntityCertificateWithChain = new ArrayList<>();
        endEntityCertificateWithChain.add(certificate);
        // Add all possible certificates in the chain
        if (certificateChain.size() > 1) {
            endEntityCertificateWithChain.addAll(certificateChain.subList(0, certificateChain.size() - 1));
        }

        Map<TLSArtifact, String> values = new HashMap<>();

        // Convert to pem and join to a single string
        values.put(TLSArtifact.CERTIFICATE, endEntityCertificateWithChain.stream()
                .map(cert -> {
                    try {
                        return PEMUtils.toPEM(cert);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .collect(Collectors.joining()));

        // Serialize private key and Root CA cert to PEM format
        values.put(TLSArtifact.PRIVATE_KEY, PEMUtils.toPEM(keyPair.getPrivate()));
        values.put(TLSArtifact.CA_CERTIFICATE, PEMUtils.toPEM(certificateChain.get(certificateChain.size() - 1)));

        // KeyStore expects complete chain with end-entity certificate
        certificateChain.add(0, certificate);
        Certificate[] keyStoreChain = certificateChain.toArray(new Certificate[certificateChain.size()]);

        KeyStore keyStore = createEmptyKeyStore();
        keyStore.setKeyEntry(
                KEYSTORE_PRIVATE_KEY_ALIAS, // TODO(mh): Make configurable "default" identifier
                keyPair.getPrivate(),
                KEYSTORE_PASSWORD,
                keyStoreChain);
        values.put(TLSArtifact.KEYSTORE, base64Encode(keyStore));

        KeyStore trustStore = createEmptyKeyStore();
        trustStore.setCertificateEntry(
                KEYSTORE_ROOT_CA_CERT_ALIAS,
                certificateChain.get(certificateChain.size() - 1));
        values.put(TLSArtifact.TRUSTSTORE, base64Encode(trustStore));

        return values;
    }

    private static String base64Encode(KeyStore keyStore) throws Exception {
        ByteArrayOutputStream keyStoreStream = new ByteArrayOutputStream();
        keyStore.store(keyStoreStream, KEYSTORE_PASSWORD);
        return Base64.getEncoder().encodeToString(keyStoreStream.toByteArray());
    }

    private static KeyStore createEmptyKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        return keyStore;
    }

    private static byte[] generateCSR(KeyPair keyPair, CertificateNamesGenerator certificateNamesGenerator)
            throws IOException, OperatorCreationException {
        ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();
        extensionsGenerator.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        extensionsGenerator.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(
                        new KeyPurposeId[] {
                                KeyPurposeId.id_kp_clientAuth,
                                KeyPurposeId.id_kp_serverAuth
                        }
                ));
        extensionsGenerator.addExtension(Extension.subjectAlternativeName, true, certificateNamesGenerator.getSANs());

        PKCS10CertificationRequest csr =
                new JcaPKCS10CertificationRequestBuilder(certificateNamesGenerator.getSubject(), keyPair.getPublic())
                .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensionsGenerator.generate())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate()));
        return PEMUtils.toPEM(csr);
    }
}
