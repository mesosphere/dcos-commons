package com.mesosphere.sdk.offer.evaluate.security;

import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.specification.TransportEncryptionSpec;

/**
 * Tests for {@link TLSArtifactPaths}.
 */
public class TLSArtifactPathsTest {

    private static final String SANS_HASH = "a-test-hash";
    private static final String ENCRYPTION_SPEC_NAME = "exposed";

    private static final String SECRET_NAME_PREFIX =
            String.format("%s__pod-0-task__%s__", SANS_HASH, ENCRYPTION_SPEC_NAME);
    private static final String SECRET_PATH_PREFIX = String.format("namespace/%s", SECRET_NAME_PREFIX);
    private static final String SECRET_NAME_KEYSTORE_PREFIX =
            String.format("__dcos_base64__%s__pod-0-task__%s__", SANS_HASH, ENCRYPTION_SPEC_NAME);
    private static final String SECRET_PATH_KEYSTORE_PREFIX =
            String.format("namespace/%s", SECRET_NAME_KEYSTORE_PREFIX);

    private static final TLSArtifactPaths TLS_ARTIFACT_PATHS = new TLSArtifactPaths("namespace", "pod-0-task", SANS_HASH);

    @Test
    public void testGetCertificatePath() throws Exception {
        Assert.assertEquals(
                SECRET_PATH_PREFIX + "certificate",
                TLS_ARTIFACT_PATHS.getSecretStorePath(TLSArtifact.CERTIFICATE, ENCRYPTION_SPEC_NAME));
    }

    @Test
    public void testGetPrivateKeyPath() throws Exception {
        Assert.assertEquals(
                SECRET_PATH_PREFIX + "private-key",
                TLS_ARTIFACT_PATHS.getSecretStorePath(TLSArtifact.PRIVATE_KEY, ENCRYPTION_SPEC_NAME));
    }

    @Test
    public void testGetRootCACertPath() throws Exception {
        Assert.assertEquals(
                SECRET_PATH_PREFIX + "root-ca-certificate",
                TLS_ARTIFACT_PATHS.getSecretStorePath(TLSArtifact.CA_CERTIFICATE, ENCRYPTION_SPEC_NAME));
    }

    @Test
    public void testGetKeyStorePath() throws Exception {
        Assert.assertEquals(
                SECRET_PATH_KEYSTORE_PREFIX + "keystore",
                TLS_ARTIFACT_PATHS.getSecretStorePath(TLSArtifact.KEYSTORE, ENCRYPTION_SPEC_NAME));
    }

    @Test
    public void testGetTrustStorePath() throws Exception {
        Assert.assertEquals(
                SECRET_PATH_KEYSTORE_PREFIX + "truststore",
                TLS_ARTIFACT_PATHS.getSecretStorePath(TLSArtifact.TRUSTSTORE, ENCRYPTION_SPEC_NAME));
    }

    @Test
    public void testGetAllNames() throws Exception {
        Collection<String> names = TLS_ARTIFACT_PATHS.getAllNames(ENCRYPTION_SPEC_NAME);
        Assert.assertEquals(names.toString(), 5, names.size());
        Assert.assertTrue(names.contains(SECRET_NAME_PREFIX + "certificate"));
        Assert.assertTrue(names.contains(SECRET_NAME_PREFIX + "private-key"));
        Assert.assertTrue(names.contains(SECRET_NAME_PREFIX + "root-ca-certificate"));
        Assert.assertTrue(names.contains(SECRET_NAME_KEYSTORE_PREFIX + "keystore"));
        Assert.assertTrue(names.contains(SECRET_NAME_KEYSTORE_PREFIX + "truststore"));
    }

    @Test
    public void testGetPathsTLS() throws Exception {
        List<TLSArtifactPaths.Entry> paths =
                TLS_ARTIFACT_PATHS.getPathsForType(TransportEncryptionSpec.Type.TLS, ENCRYPTION_SPEC_NAME);
        Assert.assertEquals(paths.toString(), 3, paths.size());
        Assert.assertEquals("exposed.crt", paths.get(0).mountPath);
        Assert.assertEquals(SECRET_PATH_PREFIX + "certificate", paths.get(0).secretStorePath);
        Assert.assertEquals("exposed.key", paths.get(1).mountPath);
        Assert.assertEquals(SECRET_PATH_PREFIX + "private-key", paths.get(1).secretStorePath);
        Assert.assertEquals("exposed.ca", paths.get(2).mountPath);
        Assert.assertEquals(SECRET_PATH_PREFIX + "root-ca-certificate", paths.get(2).secretStorePath);
    }

    @Test
    public void testGetPathsKeystore() throws Exception {
        List<TLSArtifactPaths.Entry> paths =
                TLS_ARTIFACT_PATHS.getPathsForType(TransportEncryptionSpec.Type.KEYSTORE, ENCRYPTION_SPEC_NAME);
        Assert.assertEquals(paths.toString(), 2, paths.size());
        Assert.assertEquals("exposed.keystore", paths.get(0).mountPath);
        Assert.assertEquals(SECRET_PATH_KEYSTORE_PREFIX + "keystore", paths.get(0).secretStorePath);
        Assert.assertEquals("exposed.truststore", paths.get(1).mountPath);
        Assert.assertEquals(SECRET_PATH_KEYSTORE_PREFIX + "truststore", paths.get(1).secretStorePath);
    }
}
