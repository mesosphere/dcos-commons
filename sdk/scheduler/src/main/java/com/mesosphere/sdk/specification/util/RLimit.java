package com.mesosphere.sdk.specification.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;

import java.util.*;

/**
 * Representation of an individual rlimit, consisting of a name and optional soft/hard limits.
 *
 * A valid instance of this class has either both limits set or neither, with the further constraint that the soft limit
 * must be less than or equal to the hard limit.
 */
public class RLimit {
    private static final Map<String, Protos.RLimitInfo.RLimit.Type> RLIMITS = new HashMap<>();

    static {
        for (Protos.RLimitInfo.RLimit.Type rLimitType : Protos.RLimitInfo.RLimit.Type.values()) {
            RLIMITS.put(rLimitType.toString().replace("RLMT", "RLIMIT"), rLimitType);
        }
    }

    private final String name;
    private final Long soft;
    private final Long hard;

    public RLimit(
            @JsonProperty("name") String name,
            @JsonProperty("soft") Long soft,
            @JsonProperty("hard") Long hard) throws InvalidRLimitException {
        this.name = name;
        this.soft = soft;
        this.hard = hard;

        validate();
    }

    public String getName() {
        return name;
    }

    public Optional<Long> getSoft() {
        return Optional.ofNullable(soft);
    }

    public Optional<Long> getHard() {
        return Optional.ofNullable(hard);
    }

    @JsonIgnore
    public Protos.RLimitInfo.RLimit.Type getEnum() {
        return RLIMITS.get(name);
    }

    private void validate() throws InvalidRLimitException {
        if (!RLIMITS.containsKey(name)) {
            throw new InvalidRLimitException(
                    name + " is not a valid rlimit, expected one of: " + RLIMITS.toString() + ". See man setrlimit(2)");
        }

        if (!(soft == null && hard == null) && !(soft != null && hard != null)) {
            throw new InvalidRLimitException("soft and hard rlimits must be either both set or both unset");
        }

        if (soft != null && soft > hard) {
            throw new InvalidRLimitException("soft rlimit must be less than or equal to the hard rlimit");
        }
    }

    /**
     * An exception for errors pertaining to {@link RLimit}.
     */
    public static class InvalidRLimitException extends Exception {
        public InvalidRLimitException(String s) {
            super(s);
        }
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
