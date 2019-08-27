package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Raw YAML network.
 */
public final class RawNetwork {
  private final List<Integer> hostPorts;

  private final List<Integer> containerPorts;

  private final String labelsCsv;

  @JsonCreator
  private RawNetwork(
      @JsonProperty("host-ports") List<Integer> hostPorts,
      @JsonProperty("container-ports") List<Integer> containerPorts,
      @JsonProperty("labels") String labels)
  {
    this.hostPorts = hostPorts;
    this.containerPorts = containerPorts;
    this.labelsCsv = labels;
  }

  /**
   * Included so that we support empty network specifications (e.g. a network of {@code networks: dcos:}).
   */
  @JsonCreator
  private RawNetwork() {
    this(Collections.emptyList(), Collections.emptyList(), "");
  }

  public List<Integer> getHostPorts() {
    return hostPorts;
  }

  public List<Integer> getContainerPorts() {
    return containerPorts;
  }

  public int numberOfPortMappings() throws IllegalArgumentException {
    if (hostPorts == null || containerPorts == null) {
      return 0;
    }
    if (hostPorts.size() != containerPorts.size()) {
      throw new IllegalStateException(
          "You need to specify the same number of host ports and container ports"
      );
    }

    return hostPorts.size();
  }

  public String getLabelsCsv() {
    return labelsCsv;
  }
}
