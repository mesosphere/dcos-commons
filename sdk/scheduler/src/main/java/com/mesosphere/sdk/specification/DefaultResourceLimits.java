package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

/**
 * Default implementation of {@link ResourceLimits}.
 */
public final class DefaultResourceLimits implements ResourceLimits {
    final Optional<String> cpus;
    final Optional<String> mem;

    public static final DefaultResourceLimits empty() {
        return new DefaultResourceLimits(null, null);
    }

    @JsonCreator
    public DefaultResourceLimits(
            @JsonProperty("cpus") String cpus,
            @JsonProperty("mem") String mem) {
        this.cpus = Optional.ofNullable(cpus);
        this.mem = Optional.ofNullable(mem);
    }

    @Override
    public Optional<String> getCpus() {
        return cpus;
    }

    @Override
    public Optional<String> getMem() { return mem; }

    private final Double interpretUnlimited(String s) throws NumberFormatException {
        if (s == null) {
            return null;
        } else if (s.equals("unlimited")) {
            return Double.POSITIVE_INFINITY;
        } else {
            return Double.parseDouble(s);
        }
    }

    public Optional<Double> getCpusDouble() {
        return cpus.map((c) -> interpretUnlimited(c));
    }

    public Optional<Double> getMemDouble() {
        return mem.map((m) -> interpretUnlimited(m));
    }
}
