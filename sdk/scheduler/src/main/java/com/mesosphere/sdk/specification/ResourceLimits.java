package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Optional;

/**
 * Specification for a Task.
 */
@JsonDeserialize(as = DefaultResourceLimits.class)
public interface ResourceLimits {
  @JsonProperty("cpus")
  Optional<String> getCpus();

  @JsonProperty("memory")
  Optional<String> getMemory();

  @JsonIgnore
  public Optional<Double> getCpusDouble();

  @JsonIgnore
  public Optional<Double> getMemoryDouble();

  public static void isProperlyFormatted(ResourceLimits resourceLimits) {
    if (resourceLimits.getCpus().isPresent() && !resourceLimits.getCpusDouble().isPresent()) {
      throw new RuntimeException("Resource limit cpus must be a string 'undefined' or a parseable numeric value");
    }
    if (resourceLimits.getMemory().isPresent() && !resourceLimits.getMemoryDouble().isPresent()) {
      throw new RuntimeException("Resource limit mem must be a string 'undefined' or a parseable numeric value");
    }
  }
}
