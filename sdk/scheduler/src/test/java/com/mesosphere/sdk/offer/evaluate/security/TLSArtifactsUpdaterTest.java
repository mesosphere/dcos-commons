package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.testutils.TestConstants;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link TLSArtifactsUpdater}.
 */
public class TLSArtifactsUpdaterTest {

    private static final String SPEC_NAME = "spec-name";
    private static final Map<Secret, String> GENERATED_SECRETS = new HashMap<>();
    static {
        GENERATED_SECRETS.put(Secret.CERTIFICATE, "cert");
        GENERATED_SECRETS.put(Secret.CA_CERTIFICATE, "ca-cert");
        GENERATED_SECRETS.put(Secret.TRUSTSTORE, "truststore");
    }

    @Mock private SecretsClient mockSecretsClient;
    @Mock private TLSArtifactsGenerator mockTLSArtifactsGenerator;
    @Mock private CertificateNamesGenerator mockCertificateNamesGenerator;
    @Mock private SecretStorePaths mockSecretStorePaths;

    private TLSArtifactsUpdater tlsArtifactsUpdater;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        when(mockSecretStorePaths.getTaskSecretsNamespace()).thenReturn(TestConstants.SERVICE_NAME);
        when(mockSecretStorePaths.getAllNames(SPEC_NAME)).thenReturn(Arrays.asList("secret1", "secret2", "secret3"));
        when(mockSecretStorePaths.getSecretStorePath(any(), eq(SPEC_NAME))).thenReturn("a-secret-path");
        tlsArtifactsUpdater = new TLSArtifactsUpdater(TestConstants.SERVICE_NAME, mockSecretsClient, mockTLSArtifactsGenerator);
    }

    @Test
    public void testUpdateAllPresent() throws Exception {
        when(mockSecretsClient.list(TestConstants.SERVICE_NAME))
                .thenReturn(Arrays.asList("secret1", "secret2", "secret3"));

        tlsArtifactsUpdater.update(mockSecretStorePaths, mockCertificateNamesGenerator, SPEC_NAME);

        verifyZeroInteractions(mockTLSArtifactsGenerator);
        verify(mockSecretsClient, only()).list(TestConstants.SERVICE_NAME);
    }

    @Test
    public void testUpdateSomeMissing() throws Exception {
        when(mockSecretsClient.list(TestConstants.SERVICE_NAME)).thenReturn(Arrays.asList("secret2"));
        when(mockTLSArtifactsGenerator.generate(mockCertificateNamesGenerator)).thenReturn(GENERATED_SECRETS);

        tlsArtifactsUpdater.update(mockSecretStorePaths, mockCertificateNamesGenerator, SPEC_NAME);

        verify(mockSecretsClient).list(TestConstants.SERVICE_NAME);
        verify(mockSecretsClient).delete(TestConstants.SERVICE_NAME + "/secret2");
        verifyGeneratedSecretsAdded(mockSecretsClient);
        verifyNoMoreInteractions(mockSecretsClient);
    }

    @Test
    public void testUpdateAllMissing() throws Exception {
        when(mockSecretsClient.list(TestConstants.SERVICE_NAME)).thenReturn(Collections.emptyList());
        when(mockTLSArtifactsGenerator.generate(mockCertificateNamesGenerator)).thenReturn(GENERATED_SECRETS);

        tlsArtifactsUpdater.update(mockSecretStorePaths, mockCertificateNamesGenerator, SPEC_NAME);

        verify(mockSecretsClient).list(TestConstants.SERVICE_NAME);
        verifyGeneratedSecretsAdded(mockSecretsClient);
        verifyNoMoreInteractions(mockSecretsClient);
    }

    @Test
    public void testUpdateSomeUnrecognized() throws Exception {
        when(mockSecretsClient.list(TestConstants.SERVICE_NAME)).thenReturn(
                Arrays.asList("secret1", "secret2", "secret3", "secret4", "secret5"));

        tlsArtifactsUpdater.update(mockSecretStorePaths, mockCertificateNamesGenerator, SPEC_NAME);

        verify(mockSecretsClient).list(TestConstants.SERVICE_NAME);
        verifyNoMoreInteractions(mockSecretsClient);
    }

    @Test
    public void testUpdateAllUnrecognized() throws Exception {
        when(mockSecretsClient.list(TestConstants.SERVICE_NAME)).thenReturn(Arrays.asList("secret4", "secret5"));
        when(mockTLSArtifactsGenerator.generate(mockCertificateNamesGenerator)).thenReturn(GENERATED_SECRETS);

        tlsArtifactsUpdater.update(mockSecretStorePaths, mockCertificateNamesGenerator, SPEC_NAME);

        verify(mockSecretsClient).list(TestConstants.SERVICE_NAME);
        verifyGeneratedSecretsAdded(mockSecretsClient);
        verifyNoMoreInteractions(mockSecretsClient);
    }

    @Test
    public void testUpdateMixedPresentUnrecognizedMissing() throws Exception {
        when(mockSecretsClient.list(TestConstants.SERVICE_NAME)).thenReturn(Arrays.asList("secret2", "secret4"));
        when(mockTLSArtifactsGenerator.generate(mockCertificateNamesGenerator)).thenReturn(GENERATED_SECRETS);

        tlsArtifactsUpdater.update(mockSecretStorePaths, mockCertificateNamesGenerator, SPEC_NAME);

        verify(mockSecretsClient).list(TestConstants.SERVICE_NAME);
        verify(mockSecretsClient).delete(TestConstants.SERVICE_NAME + "/secret2");
        verifyGeneratedSecretsAdded(mockSecretsClient);
        verifyNoMoreInteractions(mockSecretsClient);
    }

    private void verifyGeneratedSecretsAdded(SecretsClient mockSecretsClient) throws IOException {
        for (Map.Entry<Secret, String> entry : GENERATED_SECRETS.entrySet()) {
            verify(mockSecretsClient).create(
                    "a-secret-path",
                    new SecretsClient.Payload(TestConstants.SERVICE_NAME, entry.getValue(), entry.getKey().getDescription()));
        }
    }
}
