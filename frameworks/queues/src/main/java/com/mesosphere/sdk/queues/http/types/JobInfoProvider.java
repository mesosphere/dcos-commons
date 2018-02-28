package com.mesosphere.sdk.queues.http.types;

import java.util.Collection;
import java.util.Optional;

import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;

/**
 * Retrieves objects which are necessary for servicing HTTP requests against per-job endpoints.
 */
public interface JobInfoProvider {

    /**
     * Returns the list of currently available jobs.
     */
    public Collection<String> getJobs();

    /**
     * Returns the {@link StateStore} for the specified job, or an empty {@link Optional} if the job was not found.
     */
    public Optional<StateStore> getStateStore(String jobName);

    /**
     * Returns the {@link ConfigStore} for the specified job, or an empty {@link Optional} if the job was not found.
     */
    public Optional<ConfigStore<ServiceSpec>> getConfigStore(String jobName);

    /**
     * Returns the {@link PlanCoordinator} for the specified job, or an empty {@link Optional} if the job was not found.
     */
    public Optional<PlanCoordinator> getPlanCoordinator(String jobName);
}
