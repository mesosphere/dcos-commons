package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.dcos.clients.DcosVersionClient;
import com.mesosphere.sdk.offer.TaskException;

import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerConfig;

import java.time.Duration;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class provides utility methods for tests concerned with OfferRequirements.
 */
public class OfferRequirementTestUtils {

    private static class TestDcosCluster extends DcosVersionClient {
        private static final String RESPONSE_TEMPLATE =
                "{ 'version': '%s', " +
                        "'dcos-image-commit': 'test-commit', " +
                        "'bootstrap-id': 'test-bootstrap-id' }";

        private final String version;

        TestDcosCluster(String version) {
            this.version = version;
        }

        @Override
        protected String fetchUri(String path) {
            return String.format(RESPONSE_TEMPLATE, version);
        }
    }

    public static DcosVersionClient getTestCluster(String version) {
        return new TestDcosCluster(version);
    }

    public static Optional<HealthCheck> getReadinessCheck(TaskInfo taskInfo) throws TaskException {
        return new TaskLabelWriter(taskInfo) {
            @Override
            public Optional<HealthCheck> getReadinessCheck() throws TaskException {
                return super.getReadinessCheck();
            }
        }.getReadinessCheck();
    }

    public static SchedulerConfig getTestSchedulerConfig() {
        SchedulerConfig schedulerConfig = mock(SchedulerConfig.class);
        when(schedulerConfig.getApiServerPort()).thenReturn(TestConstants.PORT_API_VALUE);
        when(schedulerConfig.getExecutorURI()).thenReturn("test-executor-uri");
        when(schedulerConfig.getJavaURI()).thenReturn("test-java-uri");
        when(schedulerConfig.getLibmesosURI()).thenReturn("test-libmesos-uri");
        when(schedulerConfig.getDcosSpace()).thenReturn("/");
        when(schedulerConfig.getSecretsNamespace(TestConstants.SERVICE_NAME)).thenReturn(TestConstants.SERVICE_NAME);
        when(schedulerConfig.getApiServerInitTimeout()).thenReturn(Duration.ofSeconds(10));
        return schedulerConfig;
    }
}
