package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.dcos.SecretsClient;
import com.mesosphere.sdk.dcos.secrets.Secret;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

public class TLSArtifactsPersisterTest {

    @Mock
    private SecretsClient secretsClientMock;

    @Captor
    ArgumentCaptor<Secret> secretCaptor;

    private SecretNameGenerator secretNameGenerator;

    private KeyStore createEmptyKeyStore()
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, new char[0]);
        return keyStore;
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        String taskInstanceName = String.format("%s-%d-%s", "podName", 0, "taskName");
        secretNameGenerator = new SecretNameGenerator(
                "serviceName", taskInstanceName, "name", "");
    }

    @Test
    public void testPersist() throws Exception {
        TLSArtifactsPersister persister = new TLSArtifactsPersister(
                secretsClientMock, "serviceName");

        TLSArtifacts tlsArtifacts = new TLSArtifacts(
                "certPEM", "keyPEM", "rootCACertPEM",
                createEmptyKeyStore(), createEmptyKeyStore());

        persister.persist(secretNameGenerator, tlsArtifacts);

        verify(secretsClientMock, times(1)).create(
                eq(secretNameGenerator.getCertificatePath()),
                secretCaptor.capture());
        Assert.assertEquals(secretCaptor.getValue().getValue(), tlsArtifacts.getCertPEM());

        verify(secretsClientMock, times(1)).create(
                eq(secretNameGenerator.getPrivateKeyPath()),
                secretCaptor.capture());
        Assert.assertEquals(secretCaptor.getValue().getValue(), tlsArtifacts.getPrivateKeyPEM());

        verify(secretsClientMock, times(1)).create(
                eq(secretNameGenerator.getRootCACertPath()),
                secretCaptor.capture());
        Assert.assertEquals(secretCaptor.getValue().getValue(), tlsArtifacts.getRootCACertPEM());

        verify(secretsClientMock, times(1)).create(
                eq(secretNameGenerator.getKeyStorePath()),
                secretCaptor.capture());

        verify(secretsClientMock, times(1)).create(
                eq(secretNameGenerator.getTrustStorePath()),
                secretCaptor.capture());
    }

    @Test
    public void testIsArtifactCompleteWhenPartiallyPopullated() throws Exception {
        TLSArtifactsPersister persister = new TLSArtifactsPersister(
                secretsClientMock, "serviceName");

        List<String> list = Arrays.asList("private-key", "certificate");
        when(secretsClientMock
                .list(secretNameGenerator.getTaskSecretsNamespace()))
                .thenReturn(list);

        Assert.assertFalse(
                persister.isArtifactComplete(secretNameGenerator));
    }

    @Test
    public void testIsArtifactCompleteWhenComplete() throws Exception {
        TLSArtifactsPersister persister = new TLSArtifactsPersister(
                secretsClientMock, "serviceName");

        List<String> list = secretNameGenerator.getAllSecretPaths()
                .stream()
                .map(path -> path.substring(secretNameGenerator.getTaskSecretsNamespace().length() + 1))
                .collect(Collectors.toList());
        when(secretsClientMock
                .list(secretNameGenerator.getTaskSecretsNamespace()))
                .thenReturn(list);

        Assert.assertTrue(
                persister.isArtifactComplete(secretNameGenerator));
    }

    @Test
    public void testCleanUpSecrets() throws Exception {
        TLSArtifactsPersister persister = new TLSArtifactsPersister(
                secretsClientMock, "serviceName");

        List<String> list = Arrays.asList(
                secretNameGenerator
                        .getCertificatePath()
                        .substring(secretNameGenerator.getTaskSecretsNamespace().length() + 1));
        when(secretsClientMock
                .list(secretNameGenerator.getTaskSecretsNamespace()))
                .thenReturn(list);

        persister.cleanUpSecrets(secretNameGenerator);

        verify(secretsClientMock, times(1))
                .delete(secretNameGenerator.getCertificatePath());
    }

    @Test
    public void testCleanUpSecretsWhenNoStored() throws Exception {
        TLSArtifactsPersister persister = new TLSArtifactsPersister(
                secretsClientMock, "serviceName");

        List<String> list = Collections.emptyList();
        when(secretsClientMock
                .list(secretNameGenerator.getTaskSecretsNamespace()))
                .thenReturn(list);

        persister.cleanUpSecrets(secretNameGenerator);

        verify(secretsClientMock, never()).delete(anyString());
    }

}
