package com.mesosphere.sdk.specification;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representation of an individual rlimit, consisting of a name and optional soft/hard limits.
 *
 * A valid instance of this class has either both limits set or neither, with the further constraint that the soft limit
 * must be less than or equal to the hard limit.
 */
public interface RLimitSpec {

    @JsonProperty("name")
    public String getName();

    @JsonProperty("soft")
    public Optional<Long> getSoft();

    @JsonProperty("hard")
    public Optional<Long> getHard();
}
