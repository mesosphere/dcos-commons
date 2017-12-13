package com.mesosphere.sdk.dcos.clients;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Response;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.offer.evaluate.security.PEMUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;

import static org.mockito.Mockito.when;

public class CertificateAuthorityClientTest {

    private KeyPairGenerator KEY_PAIR_GENERATOR;
    private int RSA_KEY_SIZE = 2048;
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private String TEST_IP_ADDR = "127.0.0.1";

    @Mock private DcosHttpExecutor mockHttpExecutor;
    @Mock private Response mockResponse;
    @Mock private HttpResponse mockHttpResponse;
    @Mock private HttpEntity mockHttpEntity;
    @Mock private StatusLine mockStatusLine;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() throws NoSuchAlgorithmException, MalformedURLException {
        KEY_PAIR_GENERATOR = KeyPairGenerator.getInstance("RSA");
        KEY_PAIR_GENERATOR.initialize(RSA_KEY_SIZE);
        MockitoAnnotations.initMocks(this);
    }

    private CertificateAuthorityClient createClientWithStatusLine(StatusLine statusLine) throws IOException {
        CertificateAuthorityClient client = new CertificateAuthorityClient(mockHttpExecutor);

        when(mockHttpExecutor.execute(Mockito.any())).thenReturn(mockResponse);
        when(mockResponse.returnResponse()).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getStatusLine()).thenReturn(statusLine);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(new byte[0]));

        return client;
    }

    private CertificateAuthorityClient createClientWithJsonContent(String content) throws IOException {
        CertificateAuthorityClient client = new CertificateAuthorityClient(mockHttpExecutor);

        when(mockHttpExecutor.execute(Mockito.any())).thenReturn(mockResponse);
        when(mockResponse.returnResponse()).thenReturn(mockHttpResponse);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        // Because of how CA client reads entity twice create 2 responses that represent same buffer.
        when(mockHttpEntity.getContent()).thenReturn(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        return client;
    }

    private byte[] createCSR() throws IOException, OperatorCreationException {
        KeyPair keyPair = KEY_PAIR_GENERATOR.generateKeyPair();

        X500Name name = new X500NameBuilder()
                .addRDN(BCStyle.CN, "issuer")
                .build();

        ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();

        extensionsGenerator.addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));


        extensionsGenerator.addExtension(
                Extension.extendedKeyUsage,
                true,
                new ExtendedKeyUsage(
                        new KeyPurposeId[] {
                                KeyPurposeId.id_kp_clientAuth,
                                KeyPurposeId.id_kp_serverAuth }
                ));

        GeneralNames subAtlNames = new GeneralNames(
                new GeneralName[]{
                        new GeneralName(GeneralName.dNSName, "test.com"),
                        new GeneralName(GeneralName.iPAddress, TEST_IP_ADDR),
                }
        );
        extensionsGenerator.addExtension(
                Extension.subjectAlternativeName, true, subAtlNames);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());

        PKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(name, keyPair.getPublic())
                .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensionsGenerator.generate());

        return PEMUtils.toPEM(csrBuilder.build(signer));
    }

    private X509Certificate createCertificate() throws Exception {
        KeyPair keyPair = KEY_PAIR_GENERATOR.generateKeyPair();

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

    private String readResourceJson(String name) throws IOException {
       return new String(
               Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource(name).getPath())),
               StandardCharsets.UTF_8);
    }

    @Test
    public void testSignWithCorrectResponse() throws Exception {
        CertificateAuthorityClient client = createClientWithJsonContent(
                readResourceJson("response-ca-sign-valid.json"));
        X509Certificate certificate = client.sign(createCSR());
        Assert.assertEquals(certificate.getSerialNumber(),
                new BigInteger("232536721977418639703314578745637408882101009293"));
        Assert.assertEquals(certificate.getSigAlgName(), "SHA256withRSA");
    }

    @Test
    public void testSignWithErrorInResponse() throws Exception {
        CertificateAuthorityClient client = createClientWithJsonContent(
                readResourceJson("response-ca-sign-with-error.json"));

        thrown.expect(Exception.class);
        thrown.expectMessage("[1234] Test error");

        client.sign(createCSR());
    }

    @Test
    public void testSignWithNon200Response() throws Exception {
        when(mockStatusLine.getStatusCode()).thenReturn(400);
        CertificateAuthorityClient client = createClientWithStatusLine(mockStatusLine);

        thrown.expect(Exception.class);
        thrown.expectMessage("400 - error from CA");

        client.sign(createCSR());
    }

    @Test
    public void testChainWithRootCertWithCorrectResponse() throws Exception {
        CertificateAuthorityClient client = createClientWithJsonContent(
                readResourceJson("response-ca-bundle-valid.json"));
        Collection<X509Certificate> chain = client.chainWithRootCert(createCertificate());
        Assert.assertTrue(chain.size() > 0);
    }

    @Test
    public void testChainWithRootCertWithErrorInResponse() throws Exception {
        CertificateAuthorityClient client = createClientWithJsonContent(
                readResourceJson("response-ca-bundle-with-error.json"));

        thrown.expect(Exception.class);
        thrown.expectMessage("[1234] Test message");

        client.chainWithRootCert(createCertificate());
    }

    @Test
    public void testChainWithRootCertWithNon200Response() throws Exception {
        when(mockStatusLine.getStatusCode()).thenReturn(400);
        CertificateAuthorityClient client = createClientWithStatusLine(mockStatusLine);

        thrown.expect(Exception.class);
        thrown.expectMessage("400 - error from CA");

        client.chainWithRootCert(createCertificate());
    }
}
