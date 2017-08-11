package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.dcos.SecretsClient;
import com.mesosphere.sdk.offer.evaluate.security.SecretNameGenerator;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class TLSCleanupStepTest {

    @Mock
    private SecretsClient secretsClientMock;
    private SecretNameGenerator secretNameGenerator;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        String taskInstanceName = String.format("%s-%d-%s", TestConstants.POD_TYPE, 0, TestConstants.TASK_NAME);
        secretNameGenerator = new SecretNameGenerator(
                TestConstants.SERVICE_NAME,
                taskInstanceName,
                "tls-test",
                "");
    }

    private TLSCleanupStep createTLSCleanupStep() {
        return new TLSCleanupStep(
                Status.PENDING,
                secretsClientMock,
                TestConstants.SERVICE_NAME);
    }

    private List<String> getSecretsPathResponse() {
        return new ArrayList<>(secretNameGenerator.getAllSecretPaths())
                .stream()
                .map(path -> path.substring(TestConstants.SERVICE_NAME.length() + 1))
                .collect(Collectors.toList());
    }

    @Test
    public void testSecretsClientError() throws Exception {
        when(secretsClientMock.list(TestConstants.SERVICE_NAME))
                .thenThrow(new IOException());

        TLSCleanupStep step = createTLSCleanupStep();
        step.start();

        verify(secretsClientMock, never()).delete(any());

        Assert.assertEquals(step.getStatus(), Status.ERROR);
    }

    @Test
    public void testCleaningAllSecrets() throws Exception {
        when(secretsClientMock.list(TestConstants.SERVICE_NAME))
                .thenReturn(getSecretsPathResponse());

        TLSCleanupStep step = createTLSCleanupStep();
        step.start();

        for (String secretPath : secretNameGenerator.getAllSecretPaths()) {
            verify(secretsClientMock, times(1)).delete(secretPath);
        }

        Assert.assertEquals(step.getStatus(), Status.COMPLETE);
    }

    @Test
    public void testKeepingNonTLSSecrets() throws Exception {
        List<String> nonTLSSecrets = Arrays.asList("test", "test/nested");

        List<String> listResponse = new ArrayList<>(getSecretsPathResponse());
        listResponse.addAll(nonTLSSecrets);

        when(secretsClientMock.list(TestConstants.SERVICE_NAME))
                .thenReturn(listResponse);

        TLSCleanupStep step = createTLSCleanupStep();
        step.start();

        for (String secretPath : nonTLSSecrets) {
            verify(secretsClientMock, never()).delete(secretPath);
        }

        Assert.assertEquals(step.getStatus(), Status.COMPLETE);
    }

    @Test
    public void testNoTLSSecrets() throws Exception {
        List<String> nonTLSSecrets = Arrays.asList("test", "test/nested");
        when(secretsClientMock.list(TestConstants.SERVICE_NAME))
                .thenReturn(nonTLSSecrets);

        TLSCleanupStep step = createTLSCleanupStep();
        step.start();

        verify(secretsClientMock, never()).delete(any());

        Assert.assertEquals(step.getStatus(), Status.COMPLETE);
    }

}
