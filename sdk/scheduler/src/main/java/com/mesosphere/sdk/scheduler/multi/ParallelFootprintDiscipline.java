package com.mesosphere.sdk.scheduler.multi;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.MesosEventClient.ClientStatusResponse;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;

/**
 * Offer discipline implementation which only allows N services to be reserving footprint at the same time. This avoids
 * a potential deadlock where two deploying services in the same scheduler both want to consume the same resource. If
 * the limit is set to 1, then only one service may acquire footprint at a time.
 *
 * The limit value itself is a tradeoff between safety, deployment speed, cluster topology, and any quotas configured by
 * the operator. The "safest" value is 1, but it may be perfectly safe for operators to configure a larger value.
 */
public class ParallelFootprintDiscipline implements OfferDiscipline {

    private static final Logger LOGGER = LoggingUtils.getLogger(ParallelFootprintDiscipline.class);

    /**
     * A limit on the number of reserving services, or <=0 for no limit.
     */
    private final int reservingMax;

    /**
     * Storage of which services have a RESERVING status at a time, or an empty optional to disable (no limit). We may
     * limit the number of services that can be RESERVING at the same time. We persist this information to avoid
     * thrashing across scheduler restarts.
     */
    private final DisciplineSelectionStore selectionStore;

    private Optional<Set<String>> selectedReservingServices;

    /**
     * Use {@link OfferDiscipline#create(SchedulerConfig, Persister)} to create a new instance.
     *
     * @param reserveLimit the number of services which may consume footprint simultaneously
     * @param persister the persister where the list of allowed services will be persisted across restarts
     * @throws PersisterException if initialization with the persister failed
     */
    ParallelFootprintDiscipline(int reserveLimit, DisciplineSelectionStore selectionStore) {
        if (reserveLimit <= 0) {
            throw new IllegalArgumentException(
                    "INTERNAL ERROR: Reservation limit must be 1 or greater, was: " + reserveLimit);
        }
        this.reservingMax = reserveLimit;
        this.selectionStore = selectionStore;
        this.selectedReservingServices = Optional.empty();
    }

    /**
     * Updates the internal list of selected services. In particular, if any selected services were removed from the
     * scheduler, this call will also remove them from the internal selected list.
     */
    @Override
    public void updateServices(Collection<String> serviceNames) throws PersisterException {
        // Initial fetch is deferred until first update:
        if (!selectedReservingServices.isPresent()) {
            // Ensure stored set is mutable:
            selectedReservingServices = Optional.of(new HashSet<>(selectionStore.fetchSelectedServices()));
            if (!selectedReservingServices.get().isEmpty()) {
                LOGGER.info("Recovered selected services for deployment: {}", selectedReservingServices.get());
            }
        }

        // Prune any newly unknown services (in-place):
        selectedReservingServices.get().retainAll(serviceNames);

        // Store updated set (internally a no-op if nothing changes):
        if (selectionStore.storeSelectedServices(selectedReservingServices.get())) {
            LOGGER.info("Selected services for deployment: {}", selectedReservingServices.get());
        }
    }

    /**
     * Updates internal state to reflect the provided service's status, and returns whether offers should be allowed
     * for the service.
     *
     * Among services with a {@code RESERVING} status, only {@code reservingMax} may receive offers at a time.
     * Meanwhile, services with any status other than {@code RESERVING} do not have any limitation.
     */
    @Override
    public boolean updateServiceStatus(String serviceName, ClientStatusResponse statusResponse) {
        if (!selectedReservingServices.isPresent()) {
            throw new IllegalStateException("offersEnabled() called without any preceding call to updateServices()");
        }

        Set<String> selected = selectedReservingServices.get();

        if (statusResponse.result == ClientStatusResponse.Result.WORKING
                && statusResponse.workingStatus.state == ClientStatusResponse.WorkingStatus.State.FOOTPRINT) {
            // This service wants to reserve footprint. Should we let it?

            if (selected.size() < reservingMax) {
                // This service is acquiring footprint, and there's enough room for it to be selected
                // (if it isn't already in the selected set)
                selected.add(serviceName);
            }

            if (selected.contains(serviceName)) {
                return true;
            } else {
                // This service is in a reserving state, but limits are enabled and it is NOT selected.
                // Avoid providing it with offers until it's been selected.
                LOGGER.info("{} isn't selected for deployment: not sending offers ({}={})",
                        serviceName, SchedulerConfig.RESERVE_DISCIPLINE_ENV, reservingMax);
                return false;
            }
        } else {
            // This service is not reserving, remove it from the selection (if currently present)
            selected.remove(serviceName);

            // Services which aren't RESERVING can always get offers, as far as we're concerned.
            return true;
        }
    }
}
