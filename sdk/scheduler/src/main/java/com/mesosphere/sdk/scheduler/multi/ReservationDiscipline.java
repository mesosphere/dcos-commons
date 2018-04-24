package com.mesosphere.sdk.scheduler.multi;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.MesosEventClient.ClientStatusResponse;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;

/**
 * Offer discipline implementation which only allows a subset of N deploying services to receive offers at the same
 * time. This avoids a potential deadlock where two deploying services in the same scheduler both want to consume the
 * same resource. If the limit is set to 1, then only one of those services would be growing footprint at a time.
 *
 * The limit value itself is a tradeoff between safety, speed, cluster topology, and any quotas configured by the
 * operator. The "safest" value is 1, but in practice it may be perfectly safe to use a greater value.
 */
public class ReservationDiscipline implements OfferDiscipline {

    private static final Logger LOGGER = LoggingUtils.getLogger(ReservationDiscipline.class);

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
    ReservationDiscipline(int reserveLimit, DisciplineSelectionStore selectionStore) {
        if (reserveLimit <= 0) {
            throw new IllegalArgumentException(
                    "INTERNAL ERROR: Reservation limit must be 1 or greater, was: " + reserveLimit);
        }
        this.reservingMax = reserveLimit;
        this.selectionStore = selectionStore;
        this.selectedReservingServices = Optional.empty();
    }

    /**
     * Prunes any entries from the persister for services which no longer exist.
     */
    @Override
    public void updateServices(Collection<AbstractScheduler> services) throws PersisterException {
        // Initial fetch is deferred until first update:
        if (!selectedReservingServices.isPresent()) {
            // Ensure stored set is mutable:
            selectedReservingServices = Optional.of(new HashSet<>(selectionStore.fetchSelectedServices()));
            if (!selectedReservingServices.get().isEmpty()) {
                LOGGER.info("Recovered selected services for deployment: {}", selectedReservingServices.get());
            }
        }

        // Prune any newly unknown services (in-place):
        Set<String> knownServices = services.stream()
                .map(s -> s.getServiceSpec().getName())
                .collect(Collectors.toSet());
        selectedReservingServices.get().retainAll(knownServices);

        try {
            throw new Exception("hi");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Store updated set (internally a no-op if nothing changes):
        if (selectionStore.storeSelectedServices(selectedReservingServices.get())) {
            LOGGER.info("Selected services for deployment: {}", selectedReservingServices.get());
        }
    }

    @Override
    public boolean offersEnabled(ClientStatusResponse statusResponse, AbstractScheduler service) {
        if (!selectedReservingServices.isPresent()) {
            throw new IllegalStateException("offersEnabled() called without any preceding call to updateServices()");
        }

        String serviceName = service.getServiceSpec().getName();
        Set<String> selected = selectedReservingServices.get();

        if (statusResponse.result == ClientStatusResponse.Result.RESERVING) {
            if (selected.size() < reservingMax) {
                // This service is reserving, and there's enough room for it to be selected (if it isn't already)
                selected.add(serviceName);
            }

            if (selected.contains(serviceName)) {
                return true;
            }

            // This service is in a reserving state, but limits are enabled and it is NOT selected.
            // Avoid providing it with offers until it's been selected.
            LOGGER.info("{} isn't selected for deployment: not sending offers ({}={})",
                    serviceName,
                    SchedulerConfig.RESERVE_DISCIPLINE_ENV, reservingMax);
            return false;
        } else {
            // This service is not reserving, remove it from the selection if present
            selected.remove(serviceName);

            // Services which aren't RESERVING can always get offers.
            return true;
        }

    }
}
