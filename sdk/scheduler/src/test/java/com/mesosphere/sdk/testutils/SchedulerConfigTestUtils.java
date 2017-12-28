package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.scheduler.SchedulerConfig;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class provides utility methods for tests that use {@link SchedulerConfig}s.
 */
public class SchedulerConfigTestUtils {

    public static SchedulerConfig getTestSchedulerConfig() {
        SchedulerConfig schedulerConfig = mock(SchedulerConfig.class);
        when(schedulerConfig.getApiServerPort()).thenReturn(TestConstants.PORT_API_VALUE);
        when(schedulerConfig.getExecutorURI()).thenReturn("test-executor-uri");
        when(schedulerConfig.getJavaURI()).thenReturn("test-java-uri");
        when(schedulerConfig.getBootstrapURI()).thenReturn("test-bootstrap-uri");
        when(schedulerConfig.getLibmesosURI()).thenReturn("test-libmesos-uri");
        when(schedulerConfig.getDcosSpace()).thenReturn("/");
        when(schedulerConfig.getSecretsNamespace(TestConstants.SERVICE_NAME)).thenReturn(TestConstants.SERVICE_NAME);
        when(schedulerConfig.getApiServerInitTimeout()).thenReturn(Duration.ofSeconds(10));
        return schedulerConfig;
    }
}
