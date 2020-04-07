package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

/**
 * Default implementation of {@link ResourceLimits}.
 */
public final class DefaultResourceLimits implements ResourceLimits {
    final Optional<String> cpus;
    final Optional<String> memory;

    public static final DefaultResourceLimits empty() {
        return new DefaultResourceLimits(null, null);
    }

    private static final Optional<String> sanitize(String input) {
        return Optional.ofNullable(input)
                .map((c) -> c.trim())
                .filter((c) -> !c.isEmpty());
    }

    @JsonCreator
    public DefaultResourceLimits(
            @JsonProperty("cpus") String cpus,
            @JsonProperty("memory") String memory) {
        this.cpus = sanitize(cpus);
        this.memory = sanitize(memory);
    }

    @Override
    public Optional<String> getCpus() {
        return cpus;
    }

    @Override
    public Optional<String> getMemory() { return memory; }

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

    public Optional<Double> getMemoryDouble() {
        return memory.map((m) -> interpretUnlimited(m));
    }

    @Override
    public String toString() {
        return "DefaultResourceLimits{cpus=" + cpus + ", memory=" + memory + '}';
    }
}
