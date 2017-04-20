package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.VolumeRequirement;
import com.mesosphere.sdk.specification.validation.ValidationUtils;

import java.util.*;

/**
 * This class provides a implementation of the VolumeSpec interface for DockerVolumes.
 */
public class DockerVolumeSpec extends DefaultVolumeSpec implements VolumeSpec {

    private final String volumeName;
    private final String driverName;
    private final Map<String, String> driverOptions;
    private final String driverOptionsString;

    public DockerVolumeSpec (
            double diskSize,
            Type type,
            String volumeName,
            String driverName,
            String driverOptions,
            String containerPath,
            String role,
            String principal,
            String envKey) {
        this(type, volumeName, driverName, driverOptions, containerPath, RESOURCE_NAME,
                scalarValue(diskSize), role, principal, envKey);
    }

    @JsonCreator
    private DockerVolumeSpec (
            @JsonProperty("type") Type type,
            @JsonProperty("volume-name") String volumeName,
            @JsonProperty("driver-name") String driverName,
            @JsonProperty("driver-options") String driverOptions,
            @JsonProperty("container-path") String containerPath,
            @JsonProperty("name") String name,
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("principal")  String principal,
            @JsonProperty("env-key")  String envKey) {

        super(type, containerPath, name, value, role, principal, envKey);

        if (volumeName == null || volumeName.isEmpty()) {
            throw new IllegalArgumentException(String.format("Volume name can not be empty for DOCKER volume"));
        }
        if (driverName == null || driverName.isEmpty()) {
            throw new IllegalArgumentException(String.format("Driver name can not be empty for DOCKER volume"));
        }

        this.volumeName = volumeName;
        this.driverName = driverName;

        this.driverOptionsString = driverOptions;
        this.driverOptions = new HashMap<String, String>();
        if (driverOptions != null && !driverOptions.isEmpty()) {
            List<String> options = Arrays.asList(driverOptions.split(","));
            for (String opt : options) {
                String[] kv = opt.split("=");
                // Ignore invalid options
                if (kv.length < 2) {
                    continue;
                }
                this.driverOptions.put(kv[0], kv[1]);
            }
        }

        ValidationUtils.validate(this);
    }

    @JsonProperty("volume-name")
    public String getVolumeName() {
        return volumeName;
    }

    @JsonProperty("driver-name")
    public String getDriverName() {
        return driverName;
    }

    @JsonProperty("driver-options")
    public String getDriverOptionsString() {
        return driverOptionsString;
    }

    public Map<String, String> getDriverOptions() {
        return driverOptions;
    }

    private static Protos.Value scalarValue(double value) {
        Protos.Value.Builder builder = Protos.Value.newBuilder().setType(Protos.Value.Type.SCALAR);
        builder.getScalarBuilder().setValue(value);
        return builder.build();
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

    @Override
    public VolumeRequirement getResourceRequirement(Protos.Resource resource) {
        if (resource != null) {
            return new VolumeRequirement(resource);
        }
        if (getType() != VolumeSpec.Type.DOCKER) {
            throw new IllegalArgumentException("Invalid volume type");
        }
        return new VolumeRequirement(
                ResourceUtils.getDesiredDockerVolume(
                        getRole(), getPrincipal(), getDriverName(), getVolumeName(),
                        getValue().getScalar().getValue(), getContainerPath()));
    }
}
