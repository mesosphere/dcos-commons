package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.dcos.CertificateAuthorityClient;
import com.mesosphere.sdk.dcos.ca.PEMUtils;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Generates all necessary artifacts for given task.
 */
public class TLSArtifactsGenerator {

    private final KeyPairGenerator keyPairGenerator;
    private final CertificateAuthorityClient certificateAuthorityClient;

    private static final String KEYSTORE_PRIVATE_KEY_ALIAS = "default";
    private static final String KEYSTORE_ROOT_CA_CERT_ALIAS = "dcos-root";

    public TLSArtifactsGenerator(
            KeyPairGenerator keyPairGenerator,
            CertificateAuthorityClient certificateAuthorityClient) {
        this.keyPairGenerator = keyPairGenerator;
        this.certificateAuthorityClient = certificateAuthorityClient;
    }

    public TLSArtifacts generate(CertificateNamesGenerator certificateNamesGenerator) throws Exception {
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Get new end-entity certificate from CA
        X509Certificate certificate = certificateAuthorityClient.sign(
                generateCSR(keyPair, certificateNamesGenerator));

        // Get end-entity bundle with Root CA certificate
        ArrayList<X509Certificate> certificateChain = (ArrayList<X509Certificate>)
                certificateAuthorityClient.chainWithRootCert(certificate);

        // Build end-entity certificate with CA chain without Root CA certificate
        ArrayList<X509Certificate> endEntityCertificateWithChain = new ArrayList<>();
        endEntityCertificateWithChain.add(certificate);
        // Add all possible certificates in the chain
        if (certificateChain.size() > 1) {
            endEntityCertificateWithChain.addAll(certificateChain.subList(0, certificateChain.size() - 1));
        }
        // Convert to pem and join to a single string
        String certPEM = endEntityCertificateWithChain.stream()
                .map(cert -> {
                    try {
                        return PEMUtils.toPEM(cert);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .collect(Collectors.joining());

        // Serialize private key and Root CA cert to PEM format
        String privateKeyPEM = PEMUtils.toPEM(keyPair.getPrivate());
        String rootCACertPEM = PEMUtils.toPEM(
                certificateChain.get(certificateChain.size() - 1));

        // Create keystore and trust store
        KeyStore keyStore = createEmptyKeyStore();

        // KeyStore expects complete chain with end-entity certificate
        certificateChain.add(0, certificate);
        Certificate[] keyStoreChain = certificateChain.toArray(
                new Certificate[certificateChain.size()]);

        // TODO(mh): Make configurable "default" identifier
        keyStore.setKeyEntry(
                KEYSTORE_PRIVATE_KEY_ALIAS,
                keyPair.getPrivate(),
                TLSArtifacts.getKeystorePassword(),
                keyStoreChain);

        KeyStore trustStore = createEmptyKeyStore();
        trustStore.setCertificateEntry(
                KEYSTORE_ROOT_CA_CERT_ALIAS,
                certificateChain.get(certificateChain.size() - 1));

        return new TLSArtifacts(certPEM, privateKeyPEM, rootCACertPEM, keyStore, trustStore);
    }

    private KeyStore createEmptyKeyStore()
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        return keyStore;
    }

    private byte[] generateCSR(
            KeyPair keyPair,
            CertificateNamesGenerator certificateNamesGenerator) throws IOException, OperatorCreationException {

        ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();

        extensionsGenerator.addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));

        extensionsGenerator.addExtension(
                Extension.extendedKeyUsage,
                true,
                new ExtendedKeyUsage(
                        new KeyPurposeId[] {
                                KeyPurposeId.id_kp_clientAuth,
                                KeyPurposeId.id_kp_serverAuth
                        }
                ));

        extensionsGenerator.addExtension(
                Extension.subjectAlternativeName, true, certificateNamesGenerator.getSANs());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.getPrivate());

        PKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(
                certificateNamesGenerator.getSubject(), keyPair.getPublic())
                .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensionsGenerator.generate());
        PKCS10CertificationRequest csr = csrBuilder.build(signer);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PemWriter writer = new PemWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        writer.writeObject(new JcaMiscPEMGenerator(csr));
        writer.flush();

        return os.toByteArray();
    }

}
