package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Optional;
import java.util.regex.Pattern;

public class PortworxVolumeSpec implements DockerVolumeSpec {
    /**
     * Regexp for valid containerPath:
     * No leading slash character is allowed, but we allow more slashes.
     */
    private static final Pattern VALID_CONTAINER_PATH_PATTERN =
            Pattern.compile("([.a-zA-Z0-9]+([.a-zA-Z0-9_-]*[/\\\\]*)*)");
    
    private final Type type;
    private final Provider provider;
    private final Integer size;
    private final String driverName;
    private final String driverOptions;
    private final String volumeName;
    private final String containerPath;
    private final Optional<Protos.Volume.Mode> volumeMode;

    @JsonCreator
    private PortworxVolumeSpec(
            @JsonProperty("type") Type type,
            @JsonProperty("provider") Provider provider,
            @JsonProperty("size") Integer size,
            @JsonProperty("driver-name") String driverName,
            @JsonProperty("driver-options") String driverOptions,
            @JsonProperty("volume-name") String volumeName,

            @JsonProperty("container-path") String containerPath,
            @JsonProperty("mode") Optional<Protos.Volume.Mode> volumeMode) {

        this.type = type;
        this.provider = provider;
        this.size = size;
        this.driverName = driverName;
        this.driverOptions = driverOptions;
        this.volumeName = volumeName;

        this.containerPath = containerPath;
        this.volumeMode = volumeMode;
    }


    @Override
    public String getDriverName() {
        return driverName;
    }

    @Override
    public String getDriverOptions() {
        return driverOptions;
    }

    @Override
    public String getVolumeName() {
        return volumeName;
    }

    @Override
    public Optional<Protos.Volume.Mode> getVolumeMode() {
        return volumeMode;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Provider getProvider() {
        return provider;
    }

    @Override
    public int getSize() {
        return 0;
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
    
}
