package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * This rule enforces that a task be placed on the specified zone, or enforces that the task
 * avoid that zone.
 */
public class ZoneRule extends StringMatcherRule {
    @JsonCreator
    public ZoneRule(@JsonProperty("matcher") StringMatcher matcher) {
        super("ZoneRule", matcher);
    }

    @Override
    public Collection<String> getKeys(Protos.Offer offer) {
        if (offer.hasDomain() && offer.getDomain().hasFaultDomain()) {
            return Arrays.asList(offer.getDomain().getFaultDomain().getZone().getName());
        }

        return Collections.emptyList();
    }

    @Override
    public EvaluationOutcome filter(Protos.Offer offer, PodInstance podInstance, Collection<Protos.TaskInfo> tasks) {
        if (!PlacementUtils.hasZone(offer)) {
            return EvaluationOutcome.fail(this, "Offer does not contain a zone.").build();
        } else if (isAcceptable(offer, podInstance, tasks)) {
            return EvaluationOutcome.pass(
                    this,
                    "Offer zone matches pattern: '%s'",
                    getMatcher().toString())
                    .build();
        } else {
            return EvaluationOutcome.fail(this, "Offer zone didn't match pattern: '%s'", getMatcher().toString())
                    .build();
        }
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return Arrays.asList(PlacementField.ZONE);
    }
}
