package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * This class provides a default implementation of the VolumeSpec interface.
 */
public class DefaultVolumeSpec extends DefaultResourceSpec implements VolumeSpec {

    private final Type type;

    /** Regexp in @Pattern will detect blank string. No need to use @NotEmpty or @NotBlank. */
    @NotNull
    /*  No Slash character is allowed in container-path if using Persistent Volume
         Mesos isolator/containerizer silently ignores mount operation:
            mesos: src/slave/containerizer/mesos/isolators/filesystem/linux.cpp#L628
            mesos:/src/slave/containerizer/docker.cpp#L473
      Previous pattern:
           @Pattern(regexp = "[a-zA-Z0-9]+([a-zA-Z0-9_-]*[/\\\\]*)*")
                someDir/anotherDir in SandBox is not a valid path
     */
    @Pattern(regexp = "[a-zA-Z0-9]+([a-zA-Z0-9_-]*)*")
    private final String containerPath;

    public DefaultVolumeSpec(
            double diskSize,
            Type type,
            String containerPath,
            String role,
            String preReservedRole,
            String principal) {
        this(
                type,
                containerPath,
                Constants.DISK_RESOURCE_TYPE,
                scalarValue(diskSize),
                role,
                preReservedRole,
                principal);
    }

    @JsonCreator
    private DefaultVolumeSpec(
            @JsonProperty("type") Type type,
            @JsonProperty("container-path") String containerPath,
            @JsonProperty("name") String name,
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("pre-reserved-role") String preReservedRole,
            @JsonProperty("principal")  String principal) {
        super(name, value, role, preReservedRole, principal);
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
    public String toString() {
        return String.format("%s, type: '%s', container-path: '%s'",
                super.toString(),
                getType(),
                getContainerPath());
    }

    @Override
    public VolumeSpec getVolumeSpec() {
        return this;
    }
}
