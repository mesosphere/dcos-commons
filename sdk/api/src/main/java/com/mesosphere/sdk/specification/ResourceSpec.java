package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.mesos.Protos;

/**
 * A ResourceSpec encapsulates a Mesos Resource that may be used by a Task and therefore specified in a
 * TaskSpecification.
 *
 * Note: The visible=true annotation is needed in order to correctly handle ResourceSpec's subclasses in the
 * implementation.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true)
public interface ResourceSpec {

    @JsonProperty("value")
    Protos.Value getValue();

    @JsonProperty("name")
    String getName();

    @JsonProperty("role")
    String getRole();

    @JsonProperty("pre-reserved-role")
    String getPreReservedRole();

    @JsonProperty("principal")
    String getPrincipal();
}
