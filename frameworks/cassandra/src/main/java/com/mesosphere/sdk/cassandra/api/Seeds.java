package com.mesosphere.sdk.cassandra.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A class representing the seeds for a Cassandra cluster and whether the requester should consider itself a seed.
 */
public class Seeds {
    @JsonProperty("seeds")
    private final List<String> seeds;

    @JsonCreator
    public Seeds(@JsonProperty("seeds") List<String> seeds) {
        this.seeds = seeds;
    }

    @JsonIgnore
    public List<String> getSeeds() {
        return seeds;
    }
}
