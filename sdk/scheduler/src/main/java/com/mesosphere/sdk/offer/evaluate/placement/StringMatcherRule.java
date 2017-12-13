package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * This interface defines the required methods for generic application of a PlacementRule which depends on the presence
 * of some key (e.g. attribute, hostname, region, zone ...).
 */
public abstract class StringMatcherRule implements PlacementRule {
    private final StringMatcher matcher;
    private final String name;

    protected StringMatcherRule(String name, StringMatcher matcher) {
        this.name = name;
        this.matcher = matcher;
    }

    @JsonProperty("matcher")
    protected StringMatcher getMatcher() {
        return matcher;
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
    public String toString() {
        return String.format("%s{matcher=%s}", name, matcher);
    }

    public abstract Collection<String> getKeys(Protos.Offer offer);

    protected boolean isAcceptable(Protos.Offer offer, PodInstance podInstance, Collection<Protos.TaskInfo> tasks) {
        for (String key : getKeys(offer)) {
            if (getMatcher().matches(key)) {
                return true;
            }
        }

        return false;
    }
}
