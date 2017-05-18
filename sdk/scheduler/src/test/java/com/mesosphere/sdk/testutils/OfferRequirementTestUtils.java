package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.ExecutorRequirement;
import com.mesosphere.sdk.offer.NamedVIPRequirement;
import com.mesosphere.sdk.offer.PortRequirement;
import com.mesosphere.sdk.offer.ResourceRequirement;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskRequirement;
import com.mesosphere.sdk.offer.VolumeRequirement;
import com.mesosphere.sdk.offer.evaluate.PortsRequirement;
import org.apache.commons.lang.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerFlags;

import java.util.*;

/**
 * This class provides utility methods for tests concerned with OfferRequirements.
 */
public class OfferRequirementTestUtils {
    public static Optional<HealthCheck> getReadinessCheck(TaskInfo taskInfo) throws TaskException {
        return new SchedulerLabelWriter(taskInfo) {
            @Override
            public Optional<HealthCheck> getReadinessCheck() throws TaskException {
                return super.getReadinessCheck();
            }
        }.getReadinessCheck();
    }

    private static String getIndexedName(String baseName, int index) {
        return index == 0 ? baseName : baseName + index;
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
