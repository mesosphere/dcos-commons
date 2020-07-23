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

    private final Provider provider = Provider.PWX;
    private final String containerPath;
    private final Integer size;
    private final String driverName;
    private final String driverOptions;
    private final String volumeName;
    private final Optional<Protos.Volume.Mode> volumeMode;

    @JsonCreator
    private PortworxVolumeSpec(
            @JsonProperty("size") Integer size,
            @JsonProperty("container-path") String containerPath,
            @JsonProperty("driver-name") String driverName,
            @JsonProperty("driver-options") String driverOptions,
            @JsonProperty("volume-name") String volumeName,
            @JsonProperty("mode") Optional<Protos.Volume.Mode> volumeMode) {

        this.size = size;
        this.containerPath = containerPath;
        this.driverName = driverName;
        this.driverOptions = driverOptions;
        this.volumeName = volumeName;
        this.volumeMode = volumeMode;
    }

    public static PortworxVolumeSpec.Builder newBuilder() {
        return new PortworxVolumeSpec.Builder();
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
    public Provider getProvider() {
        return provider;
    }

    @Override
    public int getSize() {
        return size;
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

    public static class Builder {
        private Provider provider;
        private Integer size;
        private String driverName;
        private String driverOptions;
        private String volumeName;
        private String containerPath;
        private Optional<Protos.Volume.Mode> volumeMode;

        private Builder() {
        }

        public PortworxVolumeSpec.Builder size(int size) {
            this.size = size;
            return this;
        }

        public PortworxVolumeSpec.Builder driverName(String driverName) {
            this.driverName = driverName;
            return this;
        }


        public PortworxVolumeSpec.Builder driverOptions(String driverOptions) {
            this.driverOptions = driverOptions;
            return this;
        }

        public PortworxVolumeSpec.Builder volumeName(String volumeName) {
            this.volumeName = volumeName;
            return this;
        }

        public PortworxVolumeSpec.Builder containerPath(String containerPath) {
            this.containerPath = containerPath;
            return this;
        }

        public PortworxVolumeSpec.Builder provider(String provider) {
            switch (provider) {
                case "PWX":
                    this.provider = Provider.PWX;
                    return this;
                default:
                    throw new IllegalArgumentException("Unsupported external volume mode.");
            }
        }

        public PortworxVolumeSpec.Builder mode(String mode) {
            if (mode == null || mode.isEmpty()) {
                this.volumeMode = Optional.empty();
                if (true) throw new IllegalArgumentException("EMPTY " + mode);

                return this;
            }

            switch (mode) {
                case "RW":
                    this.volumeMode = Optional.of(Protos.Volume.Mode.RW);
                    return this;
                case "RO":
                    this.volumeMode = Optional.of(Protos.Volume.Mode.RO);
                    return this;
                default:
                    throw new IllegalArgumentException("Unsupported external volume mode.");
            }
        }

        public PortworxVolumeSpec build() {
            return new PortworxVolumeSpec(
                    this.size,
                    this.containerPath,
                    this.driverName,
                    this.driverOptions,
                    this.volumeName,
                    this.volumeMode);
        }
    }

}
