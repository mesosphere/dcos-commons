package com.mesosphere.sdk.offer.evaluate.security;

import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.GeneralNamesBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SecretNameGeneratorTest {

    private SecretNameGenerator DEFAULT_GENERATOR;

    private SecretNameGenerator createGenerator(String serviceName, String podName, String taskName, String transportEncryptionName, String sanHash) {
        String taskInstanceName = String.format("%s-%d-%s", podName, 0, taskName);
        return new SecretNameGenerator(serviceName, taskInstanceName, transportEncryptionName, sanHash);
    }

    @Before
    public void init() {
        DEFAULT_GENERATOR = createGenerator(
                "service", "pod","task", "exposed", "");
    }

    @Test
    public void getCertificatePath() throws Exception {
        Assert.assertEquals(DEFAULT_GENERATOR.getCertificatePath(), "service/pod-0-task__exposed__certificate");
    }

    @Test
    public void getPrivateKeyPath() throws Exception {
        Assert.assertEquals(DEFAULT_GENERATOR.getPrivateKeyPath(), "service/pod-0-task__exposed__private-key");
    }

    @Test
    public void getRootCACertPath() throws Exception {
        Assert.assertEquals(DEFAULT_GENERATOR.getRootCACertPath(), "service/pod-0-task__exposed__root-ca-certificate");
    }

    @Test
    public void getKeyStorePath() throws Exception {
        Assert.assertEquals(DEFAULT_GENERATOR.getKeyStorePath(), "service/pod-0-task__exposed__keystore");
    }

    @Test
    public void getTrustStorePath() throws Exception {
        Assert.assertEquals(DEFAULT_GENERATOR.getTrustStorePath(), "service/pod-0-task__exposed__truststore");
    }

    @Test
    public void getCertificateMountPath() throws Exception {
        Assert.assertEquals(DEFAULT_GENERATOR.getCertificateMountPath(), "exposed.crt");
    }

    @Test
    public void getPrivateKeyMountPath() throws Exception {
        Assert.assertEquals(DEFAULT_GENERATOR.getPrivateKeyMountPath(), "exposed.key");
    }

    @Test
    public void getRootCACertMountPath() throws Exception {
        Assert.assertEquals(DEFAULT_GENERATOR.getRootCACertMountPath(), "exposed.ca");
    }

    @Test
    public void getKeyStoreMountPath() throws Exception {
        Assert.assertEquals(DEFAULT_GENERATOR.getKeyStoreMountPath(), "exposed.keystore.base64");
    }

    @Test
    public void getTrustStoreMountPath() throws Exception {
        Assert.assertEquals(DEFAULT_GENERATOR.getTrustStoreMountPath(), "exposed.truststore.base64");
    }

    @Test
    public void testSansHash() throws Exception {
        GeneralNames generalNames = new GeneralNamesBuilder()
                .addName(new GeneralName(GeneralName.dNSName, "one.test"))
                .addName(new GeneralName(GeneralName.dNSName, "two.test"))
                .build();
        String sansHash = SecretNameGenerator.getSansHash(generalNames);

        // Manually hashed via cli
        //
        // > echo -n "one.test;two.test" | shasum
        // > 06fb44edc20b19c5a17f166110663cb042b38aa4

        Assert.assertEquals("06fb44edc20b19c5a17f166110663cb042b38aa4", sansHash);
    }

    @Test
    public void testGeneratorWithSanHash() {
        // Testing for SHA-1(hello)
        String sanHash = "f572d396fae9206628714fb2ce00f72e94f2258f";
        SecretNameGenerator secretNameGenerator = createGenerator(
                "service", "pod","task", "exposed", sanHash);
        Assert.assertEquals(
                "service/f572d396fae9206628714fb2ce00f72e94f2258f__pod-0-task__exposed__certificate",
                secretNameGenerator.getCertificatePath());
    }

}
