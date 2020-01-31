package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML ResourceLimits.
 */
public class RawResourceLimits {
    private final String cpus;

    private final String mem;

    private RawResourceLimits(
            @JsonProperty("cpus") String cpus,
            @JsonProperty("gpus") String mem) {
       this.cpus = cpus;
       this.mem = mem;
    }

    public String getCpus() {
        return cpus;
    }

    public String getMem() {
        return mem;
    }
}
