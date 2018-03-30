package com.mesosphere.sdk.http.types;

import java.util.Collection;
import java.util.Optional;

import com.mesosphere.sdk.scheduler.AbstractScheduler;

/**
 * Interface for listing/adding/removing active services via HTTP management endpoints.
 */
public interface MultiServiceManager {

    /**
     * Returns the list of currently available services.
     */
    public Collection<String> getServiceNames();

    /**
     * Adds a run which is mapped for the specified name. Note: If the run was marked for uninstall via
     * {@link #uninstallService(String)}, it should continue to be added across scheduler restarts in order for
     * uninstall to complete. It should only be omitted after the uninstall callback has been invoked for it.
     *
     * @param service the client to add
     * @return {@code this}
     * @throws IllegalArgumentException if the run name is already present
     */
    public MultiServiceManager putService(AbstractScheduler service);

    /**
     * Returns the specified run, or an empty {@code Optional} if it's not found.
     */
    public Optional<AbstractScheduler> getService(String serviceName);

    /**
     * Triggers uninstall of the specified run. After uninstall is complete, the uninstall callback will be invoked and
     * it will be automatically removed.
     *
     * @throws IllegalArgumentException if the service wasn't found
     */
    public void uninstallService(String serviceName) throws IllegalArgumentException;
}
