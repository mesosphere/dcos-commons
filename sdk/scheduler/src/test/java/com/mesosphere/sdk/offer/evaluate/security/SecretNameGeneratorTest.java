package com.mesosphere.sdk.offer.evaluate.security;

import java.security.NoSuchAlgorithmException;

import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.GeneralNamesBuilder;
import org.junit.Assert;
import org.junit.Test;

public class SecretNameGeneratorTest {

    private static final String GENERAL_NAME = "test-name";
    /**
     * echo -n "test-name" | sha1sum
     */
    private static final String GENERAL_NAME_HASH = "77c8855f6be383eb4c48b91d72b8d9438e72c57d";
    private static final SecretNameGenerator DEFAULT_GENERATOR = createGenerator("service", "pod","task", "exposed", GENERAL_NAME);

    private static SecretNameGenerator createGenerator(
            String serviceName, String podName, String taskName, String transportEncryptionName, String generalName) {
        try {
            return new SecretNameGenerator(
                    serviceName,
                    String.format("%s-%d-%s", podName, 0, taskName),
                    transportEncryptionName,
                    new GeneralNamesBuilder().addName(new GeneralName(GeneralName.dNSName, generalName)).build());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void getCertificatePath() throws Exception {
        Assert.assertEquals(
                String.format("service/%s__pod-0-task__exposed__certificate", GENERAL_NAME_HASH),
                DEFAULT_GENERATOR.getCertificatePath());
    }

    @Test
    public void getPrivateKeyPath() throws Exception {
        Assert.assertEquals(
                String.format("service/%s__pod-0-task__exposed__private-key", GENERAL_NAME_HASH),
                DEFAULT_GENERATOR.getPrivateKeyPath());
    }

    @Test
    public void getRootCACertPath() throws Exception {
        Assert.assertEquals(
                String.format("service/%s__pod-0-task__exposed__root-ca-certificate", GENERAL_NAME_HASH),
                DEFAULT_GENERATOR.getRootCACertPath());
    }

    @Test
    public void getKeyStorePath() throws Exception {
        Assert.assertEquals(
                String.format("service/__dcos_base64__%s__pod-0-task__exposed__keystore", GENERAL_NAME_HASH),
                DEFAULT_GENERATOR.getKeyStorePath());
    }

    @Test
    public void getTrustStorePath() throws Exception {
        Assert.assertEquals(
                String.format("service/__dcos_base64__%s__pod-0-task__exposed__truststore", GENERAL_NAME_HASH),
                DEFAULT_GENERATOR.getTrustStorePath());
    }

    @Test
    public void getCertificateMountPath() throws Exception {
        Assert.assertEquals("exposed.crt", DEFAULT_GENERATOR.getCertificateMountPath());
    }

    @Test
    public void getPrivateKeyMountPath() throws Exception {
        Assert.assertEquals("exposed.key", DEFAULT_GENERATOR.getPrivateKeyMountPath());
    }

    @Test
    public void getRootCACertMountPath() throws Exception {
        Assert.assertEquals("exposed.ca", DEFAULT_GENERATOR.getRootCACertMountPath());
    }

    @Test
    public void getKeyStoreMountPath() throws Exception {
        Assert.assertEquals("exposed.keystore", DEFAULT_GENERATOR.getKeyStoreMountPath());
    }

    @Test
    public void getTrustStoreMountPath() throws Exception {
        Assert.assertEquals("exposed.truststore", DEFAULT_GENERATOR.getTrustStoreMountPath());
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
        // Testing for SHA-1(hello): echo -n "hello" | sha1sum
        SecretNameGenerator secretNameGenerator = createGenerator("service", "pod","task", "exposed", "hello");
        Assert.assertEquals(
                "service/aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d__pod-0-task__exposed__certificate",
                secretNameGenerator.getCertificatePath());
    }
}
