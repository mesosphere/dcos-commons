package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.Constants;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;

/**
 * This class provides a default implementation of the VolumeSpec interface.
 */
public class DefaultVolumeSpec extends DefaultResourceSpec implements VolumeSpec {

    /**
     * Disallow slashes in the container path. If a slash is used, Mesos will silently ignore the mount operation for
     * the persistent volume. See also:
     *       mesos:src/slave/containerizer/mesos/isolators/filesystem/linux.cpp#L628
     *       mesos:src/slave/containerizer/docker.cpp#L473
     *
     * To play it safe, we explicitly whitelist the valid characters here.
     */
    private static final Pattern VALID_CONTAINER_PATH_PATTERN = Pattern.compile("[a-zA-Z0-9]+([a-zA-Z0-9_-]*)*");

    private final Type type;
    private final String containerPath;
    private final List<String> profiles;

    public DefaultVolumeSpec(
            double diskSize,
            Type type,
            String containerPath,
            List<String> profiles,
            String role,
            String preReservedRole,
            String principal) {
        this(
                type,
                containerPath,
                profiles,
                Constants.DISK_RESOURCE_TYPE,
                scalarValue(diskSize),
                role,
                preReservedRole,
                principal);

        validateResource();
        ValidationUtils.matchesRegex(this, "containerPath", containerPath, VALID_CONTAINER_PATH_PATTERN);
    }

    @JsonCreator
    private DefaultVolumeSpec(
            @JsonProperty("type") Type type,
            @JsonProperty("container-path") String containerPath,
            @JsonProperty("profiles") List<String> profiles,
            @JsonProperty("name") String name,
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("pre-reserved-role") String preReservedRole,
            @JsonProperty("principal")  String principal) {
        super(name, value, role, preReservedRole, principal);
        this.type = type;
        this.containerPath = containerPath;
        this.profiles = profiles;
    }

    @Override
    @JsonProperty("type")
    public Type getType() {
        return type;
    }

    @Override
    @JsonProperty("container-path")
    public String getContainerPath() {
        return containerPath;
    }

    @Override
    @JsonProperty("profiles")
    public List<String> getProfiles() {
        return profiles;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    private static Protos.Value scalarValue(double value) {
        Protos.Value.Builder builder = Protos.Value.newBuilder().setType(Protos.Value.Type.SCALAR);
        builder.getScalarBuilder().setValue(value);
        return builder.build();
    }
}
