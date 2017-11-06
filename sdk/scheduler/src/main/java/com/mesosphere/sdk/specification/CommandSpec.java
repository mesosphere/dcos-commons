package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import org.apache.mesos.Protos;

import java.util.Map;

/**
 * Specification for defining a command.
 */
@JsonDeserialize(as = DefaultCommandSpec.class)
public interface CommandSpec {
    @JsonProperty("value")
    String getValue();

    @JsonProperty("environment")
    Map<String, String> getEnvironment();

    default Protos.CommandInfo.Builder toProto() {
        return Protos.CommandInfo.newBuilder().setEnvironment(EnvUtils.toProto(getEnvironment())).setValue(getValue());
    }
}
