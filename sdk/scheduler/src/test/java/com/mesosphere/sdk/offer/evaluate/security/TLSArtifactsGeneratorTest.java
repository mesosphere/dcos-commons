package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.dcos.CertificateAuthorityClient;
import com.mesosphere.sdk.dcos.ca.PEMUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
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
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.mockito.Mockito.when;

public class TLSArtifactsGeneratorTest {

    @Mock private KeyPairGenerator keyPairGeneratorMock;
    @Mock private CertificateAuthorityClient certificateAuthorityClientMock;

    private KeyPairGenerator KEYPAIR_GENERATOR;

    private CertificateNamesGenerator certificateNamesGenerator;

    @Before
    public void init() throws NoSuchAlgorithmException {
        KEYPAIR_GENERATOR = KeyPairGenerator.getInstance("RSA");
        MockitoAnnotations.initMocks(this);
        when(keyPairGeneratorMock.generateKeyPair()).thenReturn(
                KEYPAIR_GENERATOR.generateKeyPair());
        certificateNamesGenerator = new CertificateNamesGenerator(
                TestConstants.SERVICE_NAME,
                String.format("%s-%d-%s", TestConstants.POD_TYPE, TestConstants.TASK_INDEX, TestConstants.TASK_NAME),
                Optional.empty(),
                Collections.emptyList()
        );
    }

    private TLSArtifactsGenerator createTLSArtifactsGenerator() {
        return new TLSArtifactsGenerator(
                keyPairGeneratorMock,
                certificateAuthorityClientMock);
    }

    private X509Certificate createCertificate() throws Exception {
        KeyPair keyPair = KEYPAIR_GENERATOR.generateKeyPair();

        SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(
                keyPair.getPublic().getEncoded());

        X500Name issuer = new X500NameBuilder()
                .addRDN(BCStyle.CN, "issuer")
                .build();

        X500Name subject = new X500NameBuilder()
                .addRDN(BCStyle.CN, "subject")
                .build();

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509CertificateHolder certHolder = new X509v3CertificateBuilder(
                issuer,
                new BigInteger("1000"),
                Date.from(Instant.now()),
                Date.from(Instant.now().plusSeconds(100000)),
                subject,
                subjectPublicKeyInfo
        )
                .build(signer);
        return (X509Certificate) certificateFactory.
                generateCertificate(
                        new ByteArrayInputStream(certHolder.getEncoded()));
    }


    @Test
    public void provisionWithChain() throws Exception {
        X509Certificate endEntityCert = createCertificate();
        when(certificateAuthorityClientMock.sign(
                Matchers.<byte[]>any())).thenReturn(endEntityCert);

        ArrayList<X509Certificate> chain = new ArrayList<X509Certificate>() {{
            add(createCertificate());
            add(createCertificate());
            add(createCertificate());
        }};
        when(certificateAuthorityClientMock
                .chainWithRootCert(Matchers.<X509Certificate>any())).thenReturn(chain);

        TLSArtifacts tlsArtifacts = createTLSArtifactsGenerator().generate(certificateNamesGenerator);

        Assert.assertTrue(
                tlsArtifacts.getCertPEM().contains(
                    PEMUtils.toPEM(endEntityCert)));

        Assert.assertEquals(
                tlsArtifacts.getPrivateKeyPEM(),
                PEMUtils.toPEM(keyPairGeneratorMock.generateKeyPair().getPrivate()));

        Assert.assertEquals(
                tlsArtifacts.getRootCACertPEM(),
                PEMUtils.toPEM(chain.get(chain.size() - 1)));

        Assert.assertEquals(
                tlsArtifacts.getKeyStore().getKey("default", TLSArtifacts.getKeystorePassword()),
                keyPairGeneratorMock.generateKeyPair().getPrivate());

        Assert.assertEquals(
                tlsArtifacts.getTrustStore().getCertificate("dcos-root"),
                chain.get(chain.size() - 1));

    }

    @Test
    public void provisionWithRootOnly() throws Exception {
        X509Certificate endEntityCert = createCertificate();
        when(certificateAuthorityClientMock.sign(
                Matchers.<byte[]>any())).thenReturn(endEntityCert);

        ArrayList<X509Certificate> chain = new ArrayList<X509Certificate>() {{
            add(createCertificate());
        }};
        when(certificateAuthorityClientMock
                .chainWithRootCert(Matchers.<X509Certificate>any())).thenReturn(chain);

        TLSArtifacts tlsArtifacts = createTLSArtifactsGenerator().generate(certificateNamesGenerator);

        Assert.assertEquals(
                tlsArtifacts.getCertPEM(), PEMUtils.toPEM(endEntityCert));

        Assert.assertEquals(
                tlsArtifacts.getPrivateKeyPEM(),
                PEMUtils.toPEM(keyPairGeneratorMock.generateKeyPair().getPrivate()));

        Assert.assertEquals(
                tlsArtifacts.getRootCACertPEM(),
                PEMUtils.toPEM(chain.get(chain.size() - 1)));

        Assert.assertEquals(
                tlsArtifacts.getKeyStore().getKey("default", TLSArtifacts.getKeystorePassword()),
                keyPairGeneratorMock.generateKeyPair().getPrivate());

        Assert.assertEquals(
                tlsArtifacts.getTrustStore().getCertificate("dcos-root"),
                chain.get(chain.size() - 1));
    }
}
