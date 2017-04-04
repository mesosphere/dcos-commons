package com.mesosphere.sdk.scheduler;

import java.time.Duration;

/**
 * This class encapuslates global Scheduler settings retrieved from the environment.
 */
public class SchedulerFlags {
    public static final String API_SERVER_TIMEOUT_S = "API_SERVER_TIMEOUT_S";
    public static final String DEFAULT_API_SERVER_TIMEOUT_S = "600";

    public static Duration getApiServerTimeout() {
        String apiServerTimeoutSecs = getEnvDefault(API_SERVER_TIMEOUT_S, DEFAULT_API_SERVER_TIMEOUT_S);
        return Duration.ofSeconds(Integer.parseInt(apiServerTimeoutSecs));
    }

    private static String getEnvDefault(String envKey, String dflt) {
        String value = System.getenv(envKey);
        if (value == null) {
            value = dflt;
        }

        return value;
    }
}
