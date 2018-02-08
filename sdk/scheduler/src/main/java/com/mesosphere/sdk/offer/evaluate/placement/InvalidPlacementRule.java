package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

import java.util.Collection;
import java.util.Collections;

/**
 * Wrapper for a placement rule that is ALWAYS invalid
 */
public class InvalidPlacementRule implements PlacementRule {
    private String constraint;
    private String exception;

    @JsonCreator
    public InvalidPlacementRule(String constraintString, String exception) {
        this.constraint = constraintString;
        this.exception = exception;
    }

    @Override
    public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
        return EvaluationOutcome.fail(this,
                String.format("Invalid placement constraint for %s: %s", podInstance.getName(), constraint)).build();
    }

    @Override
    public String toString() {
        return String.format("InvalidPlacementRule{constraint=%s, exception=%s}", constraint, exception);
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return Collections.emptyList();
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean isValid() {
        return false;
    }

}
