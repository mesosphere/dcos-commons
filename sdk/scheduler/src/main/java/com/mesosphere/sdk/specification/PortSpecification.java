package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.evaluate.PortEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * This class represents a port resource, with associated environment name.
 */
public class PortSpecification extends DefaultResourceSpecification implements ResourceSpecification {
    @NotNull
    @Size(min = 1)
    private final String portName;

    @JsonCreator
    public PortSpecification(
            @JsonProperty("port_name") String portName,
            @JsonProperty("name") String name,
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("principal") String principal,
            @JsonProperty("env_key") String envKey) {
        super(name, value, role, principal, envKey);
        this.portName = portName;

        ValidationUtils.validate(this);
    }

    @JsonProperty("port_name")
    public String getPortName() {
        return portName;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(Protos.Resource resource, String taskName) {
        return new PortEvaluationStage(
                resource, taskName, getPortName(), (int) getValue().getRanges().getRange(0).getBegin());
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
