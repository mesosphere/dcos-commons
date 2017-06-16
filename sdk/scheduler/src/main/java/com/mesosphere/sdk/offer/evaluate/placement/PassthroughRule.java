package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.Collection;

import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * A no-op rule which allows all offers.
 */
public class PassthroughRule implements PlacementRule {

    @JsonCreator
    public PassthroughRule() {
    }

    @Override
    public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
        return EvaluationOutcome.pass(this, null, "Passthrough rule always passes.");
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
