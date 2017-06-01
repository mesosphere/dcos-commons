package com.mesosphere.sdk.config;

import java.util.Map;

/**
 * Interface which maps configuration settings to task types, returning {@link ConfigNamespace}s on
 * a per-task-type basis.
 */
public interface TaskEnvRouter {

    /**
     * Returns a mapping of additional environment variables to inject tasks of the provided {@code podType}, or an
     * empty {@link Map} if nothing needs to be added.
     *
     * @see com.mesosphere.sdk.specification.PodSpec#getType()
     */
    Map<String, String> getConfig(String podType);
}
