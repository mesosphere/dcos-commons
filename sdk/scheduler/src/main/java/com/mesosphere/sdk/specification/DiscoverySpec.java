package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * Specification for a task's discovery info, including Mesos-DNS name prefix.
 */
@JsonDeserialize(as = DefaultDiscoverySpec.class)
public interface DiscoverySpec {
    @JsonProperty("prefix")
    Optional<String> getPrefix();

    @JsonProperty("visibility")
    Optional<Protos.DiscoveryInfo.Visibility> getVisibility();
}
