package com.mesosphere.sdk.scheduler.multi;

import java.util.Collection;

import com.mesosphere.sdk.scheduler.MesosEventClient.ClientStatusResponse;
import com.mesosphere.sdk.storage.PersisterException;

/**
 * Offer discipline implementation which imposes no limits on which services may receive offers at any given time.
 */
public class AllDiscipline implements OfferDiscipline {

    /**
     * Use {@link OfferDiscipline#create(SchedulerConfig, Persister)} to create a new instance.
     */
    AllDiscipline() {
    }

    /**
     * Does nothing: This discipline has no state.
     */
    @Override
    public void updateServices(Collection<String> serviceNames) throws PersisterException {
        // No-op, nothing to update
    }

    /**
     * Returns {@code true}: All services are enabled at all times.
     */
    @Override
    public boolean offersEnabled(String serviceName, ClientStatusResponse statusResponse) {
        return true;
    }
}
