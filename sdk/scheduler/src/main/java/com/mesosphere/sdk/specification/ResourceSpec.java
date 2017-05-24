package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mesosphere.sdk.offer.ResourceRequirement;

import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * A ResourceSpec encapsulates a Mesos Resource that may be used by a Task and therefore specified in a
 * TaskSpecification.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface ResourceSpec {

    @JsonProperty("value")
    Protos.Value getValue();

    @JsonProperty("name")
    String getName();

    @JsonProperty("role")
    String getRole();

    @JsonProperty("principal")
    String getPrincipal();

    @JsonIgnore
    ResourceRequirement getResourceRequirement(Protos.Resource resource);

    default Optional<String> getEnvKey() {
        return Optional.of(getName());
    }
}
