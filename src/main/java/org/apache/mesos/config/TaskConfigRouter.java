package org.apache.mesos.config;

import org.apache.mesos.specification.TaskSet;

/**
 * Interface which maps configuration settings to task types, returning {@link ConfigNamespace}s on
 * a per-task-type basis.
 */
public interface TaskConfigRouter {

    /**
     * Convenience method for retrieving a {@link ConfigNamespace} against the provided
     * {@link TaskSet}'s name.
     */
    public static ConfigNamespace getConfig(TaskConfigRouter router, TaskSet taskSet) {
        return router.getConfig(taskSet.getName());
    }

    /**
     * Returns a {@link ConfigNamespace} for all configuration settings to inject into the provided
     * {@code taskType}. If no matching data was found, returns a new empty {@link ConfigNamespace}.
     */
    public ConfigNamespace getConfig(String taskType);
}
