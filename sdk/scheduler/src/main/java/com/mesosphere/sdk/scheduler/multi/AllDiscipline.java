package com.mesosphere.sdk.scheduler.multi;

import java.util.Collection;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
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
     * Prunes any entries from the persister for services which no longer exist.
     */
    @Override
    public void updateServices(Collection<AbstractScheduler> services) throws PersisterException {
        // No-op, nothing to update
    }

    @Override
    public boolean offersEnabled(ClientStatusResponse statusResponse, AbstractScheduler service) {
        return true;
    }
}
