package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos;

import java.util.Optional;
import java.util.UUID;

/**
 * The ReservationCreator interface specifies functions that add a reservation or a resource id to a
 * {@link org.apache.mesos.Protos.Resource.Builder}. Different implementations can be supplied depending on the required
 * format for reservations.
 */
public interface ReservationCreator {

    Protos.Resource.Builder withReservation(
            ResourceSpec resourceSpec, Protos.Resource.Builder resourceBuilder, Optional<String> resourceId);

    default Protos.Resource.Builder withReservation(ResourceSpec resourceSpec, Optional<String> resourceId) {
        return withReservation(resourceSpec, resourceSpec.getResource(), resourceId);
    }

    Protos.Resource.Builder withNewResourceId(Protos.Resource.Builder resourceBuilder);

    Protos.Resource.Builder withResourceId(Protos.Resource.Builder resourceBuilder, String resourceId);

    default Protos.Resource.Builder withPersistenceId(Protos.Resource.Builder resourceBuilder) {
        Optional<String> persistenceId = ResourceUtils.getPersistenceId(resourceBuilder);

        if (!persistenceId.isPresent()) {
            resourceBuilder.getDiskBuilder().getPersistenceBuilder().setId(UUID.randomUUID().toString());
        }

        return resourceBuilder;
    }
}
