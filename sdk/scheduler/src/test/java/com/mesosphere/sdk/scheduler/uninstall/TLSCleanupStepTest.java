package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactPaths;
import com.mesosphere.sdk.testutils.TestConstants;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class TLSCleanupStepTest {

    @Mock private SecretsClient mockSecretsClient;
    private TLSArtifactPaths tlsArtifactPaths;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        tlsArtifactPaths = new TLSArtifactPaths(
                TestConstants.SERVICE_NAME,
                String.format("%s-%d-%s", TestConstants.POD_TYPE, 0, TestConstants.TASK_NAME),
                "a-test-hash");
    }

    private TLSCleanupStep createTLSCleanupStep() {
        return new TLSCleanupStep(mockSecretsClient, TestConstants.SERVICE_NAME);
    }

    @Test
    public void testSecretsClientError() throws Exception {
        when(mockSecretsClient.list(TestConstants.SERVICE_NAME))
                .thenThrow(new IOException());

        TLSCleanupStep step = createTLSCleanupStep();
        step.start();

        verify(mockSecretsClient, never()).delete(any());

        Assert.assertTrue(step.hasErrors());
    }

    @Test
    public void testCleaningAllSecrets() throws Exception {
        when(mockSecretsClient.list(TestConstants.SERVICE_NAME))
                .thenReturn(tlsArtifactPaths.getAllNames("tls-test"));

        TLSCleanupStep step = createTLSCleanupStep();
        step.start();

        for (String secretName: tlsArtifactPaths.getAllNames("tls-test")) {
            verify(mockSecretsClient, times(1)).delete(TestConstants.SERVICE_NAME + "/" + secretName);
        }

        Assert.assertTrue(step.isComplete());
    }

    @Test
    public void testKeepingNonTLSSecrets() throws Exception {
        List<String> nonTLSSecrets = Arrays.asList("test", "test/nested");

        List<String> listResponse = new ArrayList<>(tlsArtifactPaths.getAllNames("tls-test"));
        listResponse.addAll(nonTLSSecrets);

        when(mockSecretsClient.list(TestConstants.SERVICE_NAME))
                .thenReturn(listResponse);

        TLSCleanupStep step = createTLSCleanupStep();
        step.start();

        for (String secretPath : nonTLSSecrets) {
            verify(mockSecretsClient, never()).delete(secretPath);
        }

        Assert.assertTrue(step.isComplete());
    }

    @Test
    public void testNoTLSSecrets() throws Exception {
        List<String> nonTLSSecrets = Arrays.asList("test", "test/nested");
        when(mockSecretsClient.list(TestConstants.SERVICE_NAME))
                .thenReturn(nonTLSSecrets);

        TLSCleanupStep step = createTLSCleanupStep();
        step.start();

        verify(mockSecretsClient, never()).delete(any());

        Assert.assertTrue(step.isComplete());
    }
}
