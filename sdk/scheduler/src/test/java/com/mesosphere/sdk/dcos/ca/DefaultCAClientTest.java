package com.mesosphere.sdk.dcos.ca;

import com.mesosphere.sdk.dcos.auth.ConstantTokenProvider;
import com.mesosphere.sdk.dcos.http.DcosHttpClientBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
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

public class DefaultCAClientTest {

    private String TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiJhZG1pbiIsImV4cCI6MTQ5OTAxNTQ2NH0.PSEvegn859wXxswcmjMybGBbA20s2KSpag_CYRaDqeT4ICwiVwN2TkpxJUhnytuXFOchgucHGAW0UREJXnq88l0FgtcRIOxgyXFN5C4QOI5bt7wGtwudVTteDhaibN3NDqiMCvLmtgZDsqrDhepLpY4tFncNBSBj9NJWznLTzkjKgf0FCKw5c2bXUgB4D6EGMFaJg-JO4u5Aa7kf2nV7W0WBFnQOkClaBrr_--MbFYdz3Cls4H7YeHFiS3SnmH7NhEDbvhCIrNNhAGH_vvsrZEjyc0Zj18pl6bsH3_T1tafY_WwQHiIcTb4hmHQasl0ui0aRswierwSrhCZnzyaU-g";

    private KeyPairGenerator KEY_PAIR_GENERATOR;
    private int RSA_KEY_SIZE = 2048;
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private String TEST_IP_ADDR = "127.0.0.1";

    private URL CA_BASE_URL;

    @Mock private HttpClient httpClient;
    @Mock private HttpResponse httpResponse;
    @Mock private HttpEntity httpEntity;
    @Mock private StatusLine statusLine;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() throws NoSuchAlgorithmException, MalformedURLException {
        KEY_PAIR_GENERATOR = KeyPairGenerator.getInstance("RSA");
        KEY_PAIR_GENERATOR.initialize(RSA_KEY_SIZE);
        CA_BASE_URL = new URL("https://172.17.0.2/ca/api/v2/");
        MockitoAnnotations.initMocks(this);
    }

    private Executor createAuthenticatedExecutor() throws NoSuchAlgorithmException {
        HttpClient httpClient = new DcosHttpClientBuilder()
                .disableTLSVerification()
                .setTokenProvider(new ConstantTokenProvider(TOKEN))
                .build();
        return Executor.newInstance(httpClient);
    }

    private DefaultCAClient createClientWithStatusLine(StatusLine statusLine) throws IOException {
        DefaultCAClient client = new DefaultCAClient(Executor.newInstance(httpClient));

        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(httpClient.execute(
                Mockito.any(HttpUriRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(httpResponse);

        return client;
    }

    private DefaultCAClient createClientWithJsonContent(String content) throws IOException {
        DefaultCAClient client = new DefaultCAClient(Executor.newInstance(httpClient));

        when(statusLine.getStatusCode()).thenReturn(200);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        // Because of how CA client reads entity twice create 2 responses that represent same buffer.
        when(httpEntity.getContent()).thenReturn(
                new ByteArrayInputStream(content.getBytes("UTF-8")),
                new ByteArrayInputStream(content.getBytes("UTF-8")));
        when(httpClient.execute(
                Mockito.any(HttpUriRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(httpResponse);

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
        PKCS10CertificationRequest csr = csrBuilder.build(signer);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PemWriter writer = new PemWriter(new OutputStreamWriter(os, "UTF-8"));
        writer.writeObject(new JcaMiscPEMGenerator(csr));
        writer.flush();

        return os.toByteArray();
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
               Files.readAllBytes(
                       Paths.get(
                            getClass()
                                    .getClassLoader()
                                    .getResource(name)
                                    .getPath()
                       )
               ),
               "UTF-8");
    }

    @Ignore
    @Test
    public void testSignAgainstRunningCluster() throws Exception {
        DefaultCAClient client = new DefaultCAClient(CA_BASE_URL, createAuthenticatedExecutor());
        X509Certificate certificate = client.sign(createCSR());
        Assert.assertNotNull(certificate);
    }

    @Ignore
    @Test
    public void testBundleAgainstRunningCluster() throws Exception {
        DefaultCAClient client = new DefaultCAClient(CA_BASE_URL, createAuthenticatedExecutor());
        X509Certificate certificate = client.sign(createCSR());
        Collection<X509Certificate> certificates = client.chainWithRootCert(certificate);
        Assert.assertTrue(certificates.size() > 0);
    }

    @Test
    public void testSignWithCorrectResponse() throws Exception {
        DefaultCAClient client = createClientWithJsonContent(
                readResourceJson("response-ca-sign-valid.json"));
        X509Certificate certificate = client.sign(createCSR());
        Assert.assertEquals(certificate.getSerialNumber(),
                new BigInteger("232536721977418639703314578745637408882101009293"));
        Assert.assertEquals(certificate.getSigAlgName(), "SHA256withRSA");
    }

    @Test
    public void testSignWithErrorInResponse() throws Exception {
        DefaultCAClient client = createClientWithJsonContent(
                readResourceJson("response-ca-sign-with-error.json"));

        thrown.expect(CAException.class);
        thrown.expectMessage("[1234] Test error");

        client.sign(createCSR());
    }

    @Test
    public void testSignWithNon200Response() throws Exception {
        when(statusLine.getStatusCode()).thenReturn(400);
        DefaultCAClient client = createClientWithStatusLine(statusLine);

        thrown.expect(CAException.class);
        thrown.expectMessage("400 - error from CA");

        client.sign(createCSR());
    }

    @Test
    public void testChainWithRootCertWithCorrectResponse() throws Exception {
        DefaultCAClient client = createClientWithJsonContent(
                readResourceJson("response-ca-bundle-valid.json"));
        Collection<X509Certificate> chain = client.chainWithRootCert(createCertificate());
        Assert.assertTrue(chain.size() > 0);
    }

    @Test
    public void testChainWithRootCertWithErrorInResponse() throws Exception {
        DefaultCAClient client = createClientWithJsonContent(
                readResourceJson("response-ca-bundle-with-error.json"));

        thrown.expect(CAException.class);
        thrown.expectMessage("[1234] Test message");

        client.chainWithRootCert(createCertificate());
    }

    @Test
    public void testChainWithRootCertWithNon200Response() throws Exception {
        when(statusLine.getStatusCode()).thenReturn(400);
        DefaultCAClient client = createClientWithStatusLine(statusLine);

        thrown.expect(CAException.class);
        thrown.expectMessage("400 - error from CA");

        client.chainWithRootCert(createCertificate());
    }

}