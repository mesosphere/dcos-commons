package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.Arrays;
import java.util.Collection;

import com.mesosphere.sdk.specification.PodInstance;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This rule enforces that a task be placed on the specified hostname, or enforces that the task
 * avoid that hostname.
 */
public class HostnameRule extends StringMatcherRule {

    @JsonCreator
    public HostnameRule(@JsonProperty("matcher") StringMatcher matcher) {
        super("HostnameRule", matcher);
    }

    @Override
    public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
        if (isAcceptable(offer, podInstance, tasks)) {
            return EvaluationOutcome.pass(
                    this,
                    "Offer hostname matches pattern: '%s'",
                    getMatcher().toString())
                    .build();
        } else {
            return EvaluationOutcome.fail(this, "Offer hostname didn't match pattern: '%s'", getMatcher().toString())
                    .build();
        }
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return Arrays.asList(PlacementField.HOSTNAME);
    }

    @Override
    public Collection<String> getKeys(Offer offer) {
        return Arrays.asList(offer.getHostname());
    }
}
