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
    @JsonProperty("is_seed")
    private final boolean isSeed;

    @JsonCreator
    public Seeds(@JsonProperty("seeds") List<String> seeds, @JsonProperty("is_seed") boolean isSeed) {
        this.seeds = seeds;
        this.isSeed = isSeed;
    }

    @JsonIgnore
    public List<String> getSeeds() {
        return seeds;
    }

    @JsonIgnore
    public boolean isSeed() {
        return isSeed;
    }
}
