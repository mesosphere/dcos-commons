package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.offer.TaskException;
import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.dcos.DcosCluster;
import com.mesosphere.sdk.scheduler.SchedulerFlags;

import java.util.*;

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
        return new SchedulerLabelWriter(taskInfo) {
            @Override
            public Optional<HealthCheck> getReadinessCheck() throws TaskException {
                return super.getReadinessCheck();
            }
        }.getReadinessCheck();
    }

    public static SchedulerFlags getTestSchedulerFlags() {
        Map<String, String> map = new HashMap<>();
        map.put("PORT_API", String.valueOf(TestConstants.PORT_API_VALUE));
        map.put("EXECUTOR_URI", "test-executor-uri");
        map.put("JAVA_URI", "test-java-uri");
        map.put("LIBMESOS_URI", "test-libmesos-uri");
        return SchedulerFlags.fromMap(map);
    }
}
