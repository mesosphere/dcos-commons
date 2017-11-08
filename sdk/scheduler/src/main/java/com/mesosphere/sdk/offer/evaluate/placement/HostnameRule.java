package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.Arrays;
import java.util.Collection;

import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
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

    private final StringMatcher matcher;

    @JsonCreator
    public HostnameRule(@JsonProperty("matcher") StringMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
        if (isAcceptable(matcher, offer, podInstance, tasks)) {
            return EvaluationOutcome.pass(this, "Offer hostname matches pattern: '%s'", matcher.toString()).build();
        } else {
            return EvaluationOutcome.fail(this, "Offer hostname didn't match pattern: '%s'", matcher.toString())
                    .build();
        }
    }

    @JsonProperty("matcher")
    private StringMatcher getMatcher() {
        return matcher;
    }

    @Override
    public String toString() {
        return String.format("HostnameRule{matcher=%s}", matcher);
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
    public Collection<String> getKeys(Offer offer) {
        return Arrays.asList(offer.getHostname());
    }
}
