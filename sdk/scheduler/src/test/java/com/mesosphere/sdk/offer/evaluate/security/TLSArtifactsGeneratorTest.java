package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.dcos.clients.CertificateAuthorityClient;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;

public class TLSArtifactsGeneratorTest {

    @Mock private KeyPairGenerator mockKeyPairGenerator;
    @Mock private CertificateAuthorityClient mockCAClient;
    @Mock private PodInstance mockPodInstance;
    @Mock private TaskSpec mockTaskSpec;
    @Mock private ResourceSet mockResourceSet;

    private static final KeyPairGenerator KEYPAIR_GENERATOR;
    private static final KeyPair KEYPAIR;
    static {
        try {
            KEYPAIR_GENERATOR = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        KEYPAIR = KEYPAIR_GENERATOR.generateKeyPair();
    }

    private CertificateNamesGenerator certificateNamesGenerator;
    private TLSArtifactsGenerator tlsArtifactsGenerator;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        when(mockKeyPairGenerator.generateKeyPair()).thenReturn(KEYPAIR);
        when(mockTaskSpec.getDiscovery()).thenReturn(Optional.empty());
        when(mockTaskSpec.getResourceSet()).thenReturn(mockResourceSet);
        when(mockResourceSet.getResources()).thenReturn(Collections.emptyList());

        certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance);
        tlsArtifactsGenerator = new TLSArtifactsGenerator(mockCAClient, mockKeyPairGenerator);
    }

    private X509Certificate createCertificate() throws Exception {
        X509CertificateHolder certHolder = new X509v3CertificateBuilder(
                new X500NameBuilder().addRDN(BCStyle.CN, "issuer").build(),
                new BigInteger("1000"),
                Date.from(Instant.now()),
                Date.from(Instant.now().plusSeconds(100000)),
                new X500NameBuilder().addRDN(BCStyle.CN, "subject").build(),
                SubjectPublicKeyInfo.getInstance(KEYPAIR.getPublic().getEncoded()))
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(KEYPAIR.getPrivate()));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certHolder.getEncoded()));
    }

    @Test
    public void provisionWithChain() throws Exception {
        X509Certificate endEntityCert = createCertificate();
        when(mockCAClient.sign(Matchers.<byte[]>any())).thenReturn(endEntityCert);

        List<X509Certificate> chain = Arrays.asList(createCertificate(), createCertificate(), createCertificate());
        when(mockCAClient.chainWithRootCert(Matchers.<X509Certificate>any())).thenReturn(chain);

        Map<TLSArtifact, String> tlsArtifacts = tlsArtifactsGenerator.generate(certificateNamesGenerator);
        Assert.assertTrue(tlsArtifacts.get(TLSArtifact.CERTIFICATE).contains(PEMUtils.toPEM(endEntityCert)));
        Assert.assertEquals(tlsArtifacts.get(TLSArtifact.PRIVATE_KEY), PEMUtils.toPEM(KEYPAIR.getPrivate()));
        Assert.assertEquals(tlsArtifacts.get(TLSArtifact.CA_CERTIFICATE), PEMUtils.toPEM(chain.get(chain.size() - 1)));
        validateEncodedKeyStore(tlsArtifacts.get(TLSArtifact.KEYSTORE));
        validateEncodedTrustStore(tlsArtifacts.get(TLSArtifact.TRUSTSTORE));
    }

    @Test
    public void provisionWithRootOnly() throws Exception {
        X509Certificate endEntityCert = createCertificate();
        when(mockCAClient.sign(Matchers.<byte[]>any())).thenReturn(endEntityCert);

        List<X509Certificate> chain = Arrays.asList(createCertificate());
        when(mockCAClient.chainWithRootCert(Matchers.<X509Certificate>any())).thenReturn(chain);

        Map<TLSArtifact, String> tlsArtifacts = tlsArtifactsGenerator.generate(certificateNamesGenerator);
        Assert.assertEquals(tlsArtifacts.get(TLSArtifact.CERTIFICATE), PEMUtils.toPEM(endEntityCert));
        Assert.assertEquals(tlsArtifacts.get(TLSArtifact.PRIVATE_KEY), PEMUtils.toPEM(KEYPAIR.getPrivate()));
        Assert.assertEquals(tlsArtifacts.get(TLSArtifact.CA_CERTIFICATE), PEMUtils.toPEM(chain.get(chain.size() - 1)));
        validateEncodedKeyStore(tlsArtifacts.get(TLSArtifact.KEYSTORE));
        validateEncodedTrustStore(tlsArtifacts.get(TLSArtifact.TRUSTSTORE));
    }

    private void validateEncodedKeyStore(String encoded) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
        // just check that we can access the data:
        keyStore.load(inputStream, TLSArtifactsGenerator.KEYSTORE_PASSWORD);
        keyStore.getKey("default", TLSArtifactsGenerator.KEYSTORE_PASSWORD);
    }

    private void validateEncodedTrustStore(String encoded) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
        // just check that we can access the data:
        keyStore.load(inputStream, TLSArtifactsGenerator.KEYSTORE_PASSWORD);
        keyStore.getKey("dcos-root", TLSArtifactsGenerator.KEYSTORE_PASSWORD);
    }
}
