package com.mesosphere.sdk.config;

import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;

/**
 * Default implementation of a {@link TaskEnvRouter} which handles forwarding environment variables from two sources:
 * <ul>
 * <li>
 * Scheduler's system environment: searches for environment variables that start with {@code "TASKCFG_<TASKTYPE>_"}
 * and/or {@code "TASKCFG_ALL_"}, and returns appropriate Maps for task types based on that environment.
 * </li>
 * <li>
 * Manually-provided values: The service developer may provide custom values which will then be forwarded to tasks as
 * needed.
 * </li>
 * </ul>
 */
public class TaskEnvRouter {

    private static final String TASKCFG_PREFIX = "TASKCFG_";
    public static final String TASKCFG_GLOBAL_ENV_PREFIX = TASKCFG_PREFIX + "ALL_";

    // First priority: Scheduler TASKCFG_* environment
    private final Map<String, String> envConfig;
    // Second priority: Values for specific pods from the Java API
    private final Map<String, Map<String, String>> sdkPodConfigs;
    // Third priority: Global/wildcard values from the Java API
    private final Map<String, String> sdkGlobalConfig;

    /**
     * Creates a new config router which uses the current system environment for its configuration.
     *
     * @see System#getenv()
     */
    public TaskEnvRouter() {
        this(System.getenv());
    }

    /**
     * Creates a new config router which uses the provided environment variables, which should have {@code TASKCFG_*}
     * prefixes.
     */
    @VisibleForTesting
    public TaskEnvRouter(Map<String, String> envConfig) {
        this.envConfig = new TreeMap<>();
        for (Map.Entry<String, String> entry : envConfig.entrySet()) {
            if (entry.getKey().startsWith(TASKCFG_PREFIX)) {
                this.envConfig.put(entry.getKey(), entry.getValue());
            }
        }
        this.sdkGlobalConfig = new TreeMap<>();
        this.sdkPodConfigs = new TreeMap<>();
    }

    /**
     * Adds a custom environment value to be included in all pods. This may be overridden by the Scheduler environment
     * via {@code TASKCFG_ALL_*} or {@code TASKCFG_<POD_TYPE>_*} envvars, and by any per-pod types added via
     * {@link #setPodEnv(String, String, String)}.
     */
    public TaskEnvRouter setAllPodsEnv(String key, String value) {
        sdkGlobalConfig.put(key, value);
        return this;
    }

    /**
     * Adds a custom environment variable to be only included in the specified pod type. This may be overridden by the
     * Scheduler environment via {@code TASKCFG_ALL_*} or {@code TASKCFG_<POD_TYPE>_*} envvars. This takes precedence
     * over values specified via {@link #setAllPodsEnv(String, String)} for pods with this type.
     */
    public TaskEnvRouter setPodEnv(String podType, String key, String value) {
        Map<String, String> podConfig = sdkPodConfigs.get(podType.toLowerCase());
        if (podConfig == null) {
            podConfig = new TreeMap<>();
            sdkPodConfigs.put(podType.toLowerCase(), podConfig);
        }
        podConfig.put(key, value);
        return this;
    }

    /**
     * Returns the config to be injected into tasks with the provided {@code podType}.
     *
     * For example, given a task with type "index", this would match scheduler environment variables starting with
     * "TASKCFG_ALL_" and "TASKCFG_INDEX_" (with those prefixes removed), as well as any appropriate envvars which were
     * passed in via the Java API (with no modification). The prior takes priority over the latter.
     *
     * @param podType the pod type to be matched against
     * @return any environment variables to include in tasks of type {@code podType}, or an empty map otherwise
     */
    public Map<String, String> getConfig(String podType) {
        Map<String, String> matchingConfig = new TreeMap<>();

        // Third priority: API settings against "all":
        matchingConfig.putAll(sdkGlobalConfig);

        // Second priority: API settings against this pod:
        Map<String, String> sdkPodConfig = sdkPodConfigs.get(podType.toLowerCase());
        if (sdkPodConfig != null) {
            matchingConfig.putAll(sdkPodConfig);
        }

        // First priority: Scheduler env settings (allow end-user overrides):
        final String podTypePrefix = TASKCFG_PREFIX + EnvUtils.toEnvName(podType) + '_';
        for (Map.Entry<String, String> entry : envConfig.entrySet()) {
            final String key = entry.getKey();
            // Check both TASKCFG_ALL_* and TASKCFG_<POD_TYPE>_*:
            if (key.startsWith(TASKCFG_GLOBAL_ENV_PREFIX)) {
                // TASKCFG_ALL_FOO=BAR => FOO=BAR
                matchingConfig.put(key.substring(TASKCFG_GLOBAL_ENV_PREFIX.length()), entry.getValue());
            } else if (key.startsWith(podTypePrefix)) {
                // TASKCFG_PODTYPE_FOO=BAR => FOO=BAR
                matchingConfig.put(key.substring(podTypePrefix.length()), entry.getValue());
            }
        }

        return matchingConfig;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
