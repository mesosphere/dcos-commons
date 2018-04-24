package com.mesosphere.sdk.scheduler.multi;

import java.util.Collection;

import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.MesosEventClient.ClientStatusResponse;
import com.mesosphere.sdk.storage.Persister;

/**
 * Interface used by {@link MultiServiceEventClient} to enforce the services which should or shouldn't be receiving
 * offers.
 */
public interface OfferDiscipline {

    /**
     * Default implementation for creating an instance, based on the {@link SchedulerConfig}.
     */
    public static OfferDiscipline create(SchedulerConfig schedulerConfig, Persister persister) {
        int reservingMax = schedulerConfig.getMultiServiceReserveDiscipline();
        return (reservingMax > 0)
                ? new ReservationDiscipline(reservingMax, new DisciplineSelectionStore(persister))
                : new AllDiscipline();
    }

    /**
     * Updates the discipline's internal state with a list of all known services. This may include services which are in
     * the process of uninstalling, etc. This is called before a series of zero or more calls to
     * {@link #offersEnabled(AbstractScheduler)}.
     *
     * @param services active services
     * @throws Exception if updating internal state has a problem
     */
    public void updateServices(Collection<AbstractScheduler> services) throws Exception;

    /**
     * Returns whether the specified service should be provided with offers, based on its status response.
     *
     * @param statusResponse the status response returned by the service
     * @param service the active service
     * @return whether to provide the service with offers
     */
    public boolean offersEnabled(ClientStatusResponse statusResponse, AbstractScheduler service);
}
