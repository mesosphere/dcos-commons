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

  @JsonCreator
  public DefaultResourceLimits(
      @JsonProperty("cpus") String cpus,
      @JsonProperty("memory") String memory)
  {
    this.cpus = sanitize(cpus);
    this.memory = sanitize(memory);
  }

  public static final DefaultResourceLimits empty() {
    return new DefaultResourceLimits(null, null);
  }

  private static final Optional<String> sanitize(String input) {
    return Optional.ofNullable(input)
        .map(c -> c.trim())
        .filter(c -> !c.isEmpty());
  }

  @Override
  public Optional<String> getCpus() {
    return cpus;
  }

  @Override
  public Optional<String> getMemory() {
    return memory;
  }

  private final Double interpretUnlimited(String s) throws NumberFormatException {
    // This line is to silence Checkstyle.
    String nullString = null;
    if (s.equals(nullString)) {
      return null;
    } else if ("unlimited".equals(s)) {
      return Double.POSITIVE_INFINITY;
    } else {
      return Double.parseDouble(s);
    }
  }

  public Optional<Double> getCpusDouble() {
    return cpus.map(c -> interpretUnlimited(c));
  }

  public Optional<Double> getMemoryDouble() {
    return memory.map(m -> interpretUnlimited(m));
  }

  @Override
  public String toString() {
    return "DefaultResourceLimits{cpus=" + cpus + ", memory=" + memory + '}';
  }
}
