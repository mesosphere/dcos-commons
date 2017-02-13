package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.PortRequirement;
import com.mesosphere.sdk.offer.ResourceRequirement;
import com.mesosphere.sdk.offer.ResourceUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Optional;

/**
 * This class represents a single port, with associated environment name.
 */
public class PortSpec extends DefaultResourceSpec implements ResourceSpec {
    @NotNull
    @Size(min = 1)
    private final String portName;
    private final String envKey;

    @JsonCreator
    public PortSpec(
            @JsonProperty("name") String name,
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("principal") String principal,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("port-name") String portName) {
        super(name, value, role, principal, envKey);
        this.portName = portName;
        this.envKey = envKey;
    }

    @JsonProperty("port-name")
    public String getPortName() {
        return portName;
    }

    @JsonProperty("env-key")
    @Override
    public Optional<String> getEnvKey() {
        return Optional.ofNullable(envKey);
    }

    /**
     *  if env key is not present: "PORT_" is added to the beginning of port name.
     */
    protected String generateEnvKey() {
        Optional<String> envKey = getEnvKey();
        if (envKey.isPresent()) {
            return envKey.get();
        }
        return Constants.PORT_NAME_LABEL_PREFIX + getPortName();
    }

    @Override
    public ResourceRequirement getResourceRequirement(Protos.Resource resource) {
        return new PortRequirement(
                resource == null ?
                        ResourceUtils.getDesiredResource(this) :
                        ResourceUtils.withValue(resource, getValue()),
                generateEnvKey(),
                (int) getValue().getRanges().getRange(0).getBegin());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
