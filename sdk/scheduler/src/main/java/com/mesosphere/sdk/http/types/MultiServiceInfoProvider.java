package com.mesosphere.sdk.http.types;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;

/**
 * Retrieves objects which are necessary for servicing HTTP queries describing individual runs.
 */
public class MultiServiceInfoProvider {

    private final MultiServiceManager store;

    public MultiServiceInfoProvider(MultiServiceManager store) {
        this.store = store;
    }

    /**
     * Returns the {@link StateStore} for the specified run, or an empty {@link Optional} if the run was not found.
     */
    public Optional<StateStore> getStateStore(String runName) {
        Optional<AbstractScheduler> scheduler = store.getService(runName);
        return scheduler.isPresent() ? Optional.of(scheduler.get().getStateStore()) : Optional.empty();
    }

    /**
     * Returns the {@link ConfigStore} for the specified run, or an empty {@link Optional} if the run was not found.
     */
    public Optional<ConfigStore<ServiceSpec>> getConfigStore(String runName) {
        Optional<AbstractScheduler> scheduler = store.getService(runName);
        return scheduler.isPresent() ? Optional.of(scheduler.get().getConfigStore()) : Optional.empty();
    }

    /**
     * Returns the {@link PlanCoordinator} for the specified run, or an empty {@link Optional} if the run was not found.
     */
    public Optional<PlanCoordinator> getPlanCoordinator(String runName) {
        Optional<AbstractScheduler> scheduler = store.getService(runName);
        return scheduler.isPresent() ? Optional.of(scheduler.get().getPlanCoordinator()) : Optional.empty();
    }

    /**
     * Returns a list of custom endpoints to advertise for the specified run, or an empty Map if the run was not found
     * or there were no custom endpoints.
     */
    public Map<String, EndpointProducer> getCustomEndpoints(String runName) {
        Optional<AbstractScheduler> scheduler = store.getService(runName);
        return scheduler.isPresent() ? scheduler.get().getCustomEndpoints() : Collections.emptyMap();
    }
}
