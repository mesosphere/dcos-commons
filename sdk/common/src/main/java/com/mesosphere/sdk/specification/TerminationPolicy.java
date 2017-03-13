package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This class provides encapsulates termination policies.
 */
public class TerminationPolicy {

    public static final TerminationPolicy KILL_EXECUTOR = new TerminationPolicy(0);
    public static final TerminationPolicy DO_NOTHING = new TerminationPolicy(1);

    private final int value;

    public TerminationPolicy(@JsonProperty("termination-policy") String value) {
        if (value == null) {
            this.value = 0;
        } else {
            this.value = Integer.valueOf(value);
        }
    }

    public TerminationPolicy(int value) {
        this.value = value;
    }

    @JsonProperty("termination-policy")
    public int getValue() {
        return value;
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
