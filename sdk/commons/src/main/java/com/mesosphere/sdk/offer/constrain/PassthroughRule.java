package com.mesosphere.sdk.offer.constrain;

import java.util.Collection;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.OfferRequirement;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * A no-op rule which allows all offers.
 */
public class PassthroughRule implements PlacementRule {

    @JsonCreator
    public PassthroughRule() {
    }

    @Override
    public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        return offer;
    }

    @Override
    public String toString() {
        return String.format("PassthroughRule{}");
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
