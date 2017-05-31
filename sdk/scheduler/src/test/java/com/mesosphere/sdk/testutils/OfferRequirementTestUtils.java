package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.scheduler.SchedulerFlags;

import java.util.*;

/**
 * This class provides utility methods for tests concerned with OfferRequirements.
 */
public class OfferRequirementTestUtils {

    public static SchedulerFlags getTestSchedulerFlags() {
        Map<String, String> map = new HashMap<>();
        map.put("PORT_API", String.valueOf(TestConstants.PORT_API_VALUE));
        map.put("EXECUTOR_URI", "test-executor-uri");
        map.put("JAVA_URI", "test-java-uri");
        map.put("LIBMESOS_URI", "test-libmesos-uri");
        return SchedulerFlags.fromMap(map);
    }
}
