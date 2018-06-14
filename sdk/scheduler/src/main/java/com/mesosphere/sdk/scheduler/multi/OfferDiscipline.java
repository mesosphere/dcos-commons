package com.mesosphere.sdk.scheduler.multi;

import java.util.Collection;

import com.mesosphere.sdk.scheduler.MesosEventClient.ClientStatusResponse;

/**
 * Interface used by {@link MultiServiceEventClient} to enforce the services which should or shouldn't be receiving
 * offers.
 */
public interface OfferDiscipline {

    /**
     * Updates the discipline's internal state with a list of all known services. This may include services which are in
     * the process of uninstalling, etc. This is called before a series of zero or more calls to
     * {@link #offersEnabled(AbstractScheduler)}.
     *
     * @param serviceNames names of active services
     * @throws Exception if updating internal state has a problem
     */
    public void updateServices(Collection<String> serviceNames) throws Exception;

    /**
     * Returns whether the specified service should be provided with offers, based on its status response.
     *
     * @param serviceName name of the active service
     * @param statusResponse the status response returned by the service
     * @return whether to provide the service with offers
     */
    public boolean offersEnabled(String serviceName, ClientStatusResponse statusResponse);
}
