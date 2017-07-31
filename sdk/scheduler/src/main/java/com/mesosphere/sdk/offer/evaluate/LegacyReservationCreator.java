package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos;

import java.util.Optional;
import java.util.UUID;

/**
 * The LegacyReservationCreator adds a reservation to a {@link org.apache.mesos.Protos.Resource.Builder} or a resource
 * id to a builder that has already had its {@link org.apache.mesos.Protos.Resource.ReservationInfo} constructed. It is
 * designed for compatibility with an execution environment that does not support resource refinement.
 */
public class LegacyReservationCreator implements ReservationCreator {

    @Override
    public Protos.Resource.Builder withReservation(
            ResourceSpec resourceSpec, Protos.Resource.Builder resourceBuilder, Optional<String> resourceId) {
        Protos.Resource.ReservationInfo.Builder reservationBuilder =
                Protos.Resource.ReservationInfo.newBuilder().setPrincipal(resourceSpec.getPrincipal());

        if (resourceId.isPresent()) {
            AuxLabelAccess.setResourceId(reservationBuilder, resourceId.get());
        }
        resourceBuilder.setRole(resourceSpec.getRole());
        resourceBuilder.setReservation(reservationBuilder);

        return resourceBuilder;
    }

    @Override
    public Protos.Resource.Builder withNewResourceId(Protos.Resource.Builder resourceBuilder) {
        Optional<String> resourceId = AuxLabelAccess.getResourceId(resourceBuilder.getReservations(0));

        if (!resourceId.isPresent()) {
            AuxLabelAccess.setResourceId(resourceBuilder.getReservationBuilder(), UUID.randomUUID().toString());
        }

        return resourceBuilder;
    }

    @Override
    public Protos.Resource.Builder withResourceId(Protos.Resource.Builder resourceBuilder, String resourceId) {
        AuxLabelAccess.setResourceId(resourceBuilder.getReservationBuilder(), resourceId);
        return resourceBuilder;
    }
}
