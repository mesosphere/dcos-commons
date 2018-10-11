package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code CREATE} Operation.
 */
public class CreateOfferRecommendation implements OfferRecommendation {
    private final Protos.Offer offer;
    private final Protos.Offer.Operation operation;

    public CreateOfferRecommendation(Protos.Offer offer, Protos.Resource resource) {
        this.offer = offer;
        this.operation = Protos.Offer.Operation.newBuilder()
                .setType(Protos.Offer.Operation.Type.CREATE)
                .setCreate(Protos.Offer.Operation.Create.newBuilder().addVolumes(resource))
                .build();
    }

    @Override
    public Optional<Protos.Offer.Operation> getOperation() {
        return Optional.of(operation);
    }

    @Override
    public Protos.OfferID getOfferId() {
        return offer.getId();
    }

    @Override
    public Protos.SlaveID getAgentId() {
        return offer.getSlaveId();
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
