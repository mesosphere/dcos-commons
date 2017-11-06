package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos;

import java.util.Optional;
import java.util.UUID;

/**
 * The HierarchicalReservationCreator adds a reservation to a {@link org.apache.mesos.Protos.Resource.Builder} or a
 * resource id to a builder that has already had its {@link org.apache.mesos.Protos.Resource.ReservationInfo}
 * constructed.
 */
public class HierarchicalReservationCreator implements ReservationCreator {

    @Override
    public Protos.Resource.Builder withReservation(
            ResourceSpec resourceSpec, Protos.Resource.Builder resourceBuilder, Optional<String> resourceId) {
        if (!resourceSpec.getPreReservedRole().equals(Constants.ANY_ROLE) && !resourceId.isPresent()) {
            resourceBuilder.addReservations(
                    Protos.Resource.ReservationInfo.newBuilder()
                    .setRole(resourceSpec.getPreReservedRole())
                    .setType(Protos.Resource.ReservationInfo.Type.STATIC));
        }

        Protos.Resource.ReservationInfo.Builder reservationBuilder =
                Protos.Resource.ReservationInfo.newBuilder()
                        .setRole(resourceSpec.getRole())
                        .setType(Protos.Resource.ReservationInfo.Type.DYNAMIC)
                        .setPrincipal(resourceSpec.getPrincipal());

        if (resourceId.isPresent()) {
            AuxLabelAccess.setResourceId(reservationBuilder, resourceId.get());
        }
        resourceBuilder.addReservations(reservationBuilder);

        return resourceBuilder;
    }

    @Override
    public Protos.Resource.Builder withNewResourceId(Protos.Resource.Builder resourceBuilder) {
        Optional<String> resourceId = AuxLabelAccess.getResourceId(resourceBuilder.getReservations(0));

        if (!resourceId.isPresent()) {
            AuxLabelAccess.setResourceId(resourceBuilder.getReservationsBuilder(0), UUID.randomUUID().toString());
        }

        return resourceBuilder;
    }

    @Override
    public Protos.Resource.Builder withResourceId(Protos.Resource.Builder resourceBuilder, String resourceId) {
        AuxLabelAccess.setResourceId(resourceBuilder.getReservationsBuilder(0), resourceId);
        return resourceBuilder;
    }
}
