package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A ResourceSpecification encapsulates a Mesos Resource that may be used by a Task and therefore specified in a
 * TaskSpecification.
 */
@JsonDeserialize(as = DefaultResourceSpecification.class)
public interface ResourceSpecification {

    @JsonProperty("value")
    Protos.Value getValue();

    @JsonProperty("name")
    String getName();

    @JsonProperty("role")
    String getRole();

    @JsonProperty("principal")
    String getPrincipal();
}
