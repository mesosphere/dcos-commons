package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.offer.TaskException;
import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.dcos.DcosCluster;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerFlags;

import java.time.Duration;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class provides utility methods for tests concerned with OfferRequirements.
 */
public class OfferRequirementTestUtils {

    private static class TestDcosCluster extends DcosCluster {
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

    public static DcosCluster getTestCluster(String version) {
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

    public static SchedulerFlags getTestSchedulerFlags() {
        SchedulerFlags schedulerFlags = mock(SchedulerFlags.class);
        when(schedulerFlags.getApiServerPort()).thenReturn(TestConstants.PORT_API_VALUE);
        when(schedulerFlags.getExecutorURI()).thenReturn("test-executor-uri");
        when(schedulerFlags.getJavaURI()).thenReturn("test-java-uri");
        when(schedulerFlags.getLibmesosURI()).thenReturn("test-libmesos-uri");
        when(schedulerFlags.getDcosSpace()).thenReturn("/");
        when(schedulerFlags.getApiServerInitTimeout()).thenReturn(Duration.ofSeconds(10));
        return schedulerFlags;
    }
}
