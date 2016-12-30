package com.mesosphere.sdk.config;

/**
 * Interface which maps configuration settings to task types, returning {@link ConfigNamespace}s on
 * a per-task-type basis.
 */
public interface TaskConfigRouter {

    /**
     * Returns a {@link ConfigNamespace} for all configuration settings to inject into the provided
     * {@code podType}. If no matching data was found, returns a new empty {@link ConfigNamespace}.
     *
     * @see com.mesosphere.sdk.specification.PodSpec#getType()
     */
    ConfigNamespace getConfig(String podType);
}
