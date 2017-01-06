package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.VolumeEvaluationStage;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.specification.validation.ValidationUtils;

/**
 * This class provides a default implementation of the VolumeSpecification interface.
 */
public class DefaultVolumeSpecification extends DefaultResourceSpecification implements VolumeSpecification {

    public static final String RESOURCE_NAME = "disk";

    private final Type type;
    private final String containerPath;

    public DefaultVolumeSpecification(
            double diskSize,
            Type type,
            String containerPath,
            String role,
            String principal,
            String envKey) {
        this(type, containerPath, RESOURCE_NAME, scalarValue(diskSize), role, principal, envKey);
    }

    @JsonCreator
    private DefaultVolumeSpecification(
            @JsonProperty("type") Type type,
            @JsonProperty("container_path") String containerPath,
            @JsonProperty("name") String name,
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("principal")  String principal,
            @JsonProperty("env_key")  String envKey) {
        super(name, value, role, principal, envKey);
        this.type = type;
        this.containerPath = containerPath;

        ValidationUtils.validate(this);
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String getContainerPath() {
        return containerPath;
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

    private static Protos.Value scalarValue(double value) {
        Protos.Value.Builder builder = Protos.Value.newBuilder().setType(Protos.Value.Type.SCALAR);
        builder.getScalarBuilder().setValue(value);
        return builder.build();
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(Protos.Resource resource, String taskName) {
        return new VolumeEvaluationStage(resource, taskName);
    }
}
