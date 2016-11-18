package org.apache.mesos.config;

import org.apache.mesos.specification.PodSpec;

/**
 * Interface which maps configuration settings to task types, returning {@link ConfigNamespace}s on
 * a per-task-type basis.
 */
public interface TaskConfigRouter {

    /**
     * Convenience method for retrieving a {@link ConfigNamespace} against the provided
     * {@link PodSpec}'s type.
     */
    static ConfigNamespace getConfig(TaskConfigRouter router, PodSpec podSpec) {
        return router.getConfig(podSpec.getType());
    }

    /**
     * Returns a {@link ConfigNamespace} for all configuration settings to inject into the provided
     * {@code taskType}. If no matching data was found, returns a new empty {@link ConfigNamespace}.
     */
    ConfigNamespace getConfig(String taskType);
}
