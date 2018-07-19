package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import org.json.JSONObject;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class provides utility methods for tests that use {@link SchedulerConfig}s.
 */
public class SchedulerConfigTestUtils {

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public static SchedulerConfig getTestSchedulerConfig() {
        SchedulerConfig schedulerConfig = mock(SchedulerConfig.class);
        when(schedulerConfig.isDeadlockExitEnabled()).thenReturn(true);
        when(schedulerConfig.getApiServerPort()).thenReturn(TestConstants.PORT_API_VALUE);
        when(schedulerConfig.getJavaURI()).thenReturn("test-java-uri");
        when(schedulerConfig.getBootstrapURI()).thenReturn("test-bootstrap-uri");
        when(schedulerConfig.getLibmesosURI()).thenReturn("test-libmesos-uri");
        when(schedulerConfig.getDcosSpace()).thenReturn("/");
        when(schedulerConfig.getSecretsNamespace(TestConstants.SERVICE_NAME)).thenReturn(TestConstants.SERVICE_NAME);
        when(schedulerConfig.getApiServerInitTimeout()).thenReturn(Duration.ofSeconds(10));
        when(schedulerConfig.getServiceTLD()).thenReturn(Constants.DNS_TLD);
        when(schedulerConfig.getSchedulerRegion()).thenReturn(Optional.of("test-region"));
        when(schedulerConfig.getMultiServiceRemovalTimeout()).thenReturn(Duration.ofSeconds(60));
        when(schedulerConfig.getSchedulerIP()).thenReturn("127.0.0.1");
        when(schedulerConfig.getBuildInfo()).thenReturn(new JSONObject());
        return schedulerConfig;
    }
}
