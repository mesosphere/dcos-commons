package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class PortworxVolumeSpec implements DockerVolumeSpec {
    /**
     * Regexp for valid containerPath:
     * No leading slash character is allowed, but we allow more slashes.
     */
    private static final Pattern VALID_CONTAINER_PATH_PATTERN =
            Pattern.compile("([.a-zA-Z0-9]+([.a-zA-Z0-9_-]*[/\\\\]*)*)");

    private final String containerPath;
    private final Integer size;
    private final String driverName;
    private final Map<String, String> driverOptions;
    private final String volumeName;
    private final Optional<Protos.Volume.Mode> volumeMode;
    private final Sharing volumeSharing;

    @JsonCreator
    private PortworxVolumeSpec(
            @JsonProperty("size") Integer size,
            @JsonProperty("container-path") String containerPath,
            @JsonProperty("driver-name") String driverName,
            @JsonProperty("driver-options") Map<String, String> driverOptions,
            @JsonProperty("volume-name") String volumeName,
            @JsonProperty("mode") Optional<Protos.Volume.Mode> volumeMode,
            @JsonProperty("volume-sharing") Sharing volumeSharing
    ) {

        this.size = size;
        this.containerPath = containerPath;
        this.driverName = driverName;
        this.driverOptions = driverOptions;
        this.volumeName = volumeName;
        this.volumeMode = volumeMode;
        this.volumeSharing = volumeSharing;
    }

    public static PortworxVolumeSpec.Builder newBuilder() {
        return new PortworxVolumeSpec.Builder();
    }

    @Override
    public String getDriverName() {
        return driverName;
    }

    @Override
    public Map<String, String> getDriverOptions() {
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
    public int getSize() {
        return size;
    }

    @Override
    public String getContainerPath() {
        return containerPath;
    }

    @Override
    public Sharing getSharing() {
        return volumeSharing;
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
        private Integer size;
        private String driverName;
        private Map<String, String> driverOptions;
        private String volumeName;
        private String containerPath;
        private Optional<Protos.Volume.Mode> volumeMode;
        private Sharing volumeSharing;

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
            Map<String, String> driverOptionsMap = new HashMap<>();

            if (driverOptions != null && !driverOptions.isEmpty()) {
                String[] options = driverOptions.split(",");
                for (String opt : options) {
                    String[] kv = opt.split("=");

                    if (kv.length != 2) {
                        throw new IllegalArgumentException("Invalid driver option: " + Arrays.toString(kv));
                    }
                    driverOptionsMap.put(kv[0], kv[1]);
                }
            }

            this.driverOptions = driverOptionsMap;
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

        public PortworxVolumeSpec.Builder mode(String mode) {
            if (mode == null || mode.isEmpty()) {
                this.volumeMode = Optional.empty();

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
                    throw new IllegalArgumentException("Unsupported external volume mode: " + mode);
            }
        }

        public PortworxVolumeSpec.Builder sharing(String volumeSharing) {
            switch (volumeSharing) {
                case "POD_EXCLUSIVE":
                    this.volumeSharing = Sharing.POD_EXCLUSIVE;
                    return this;
                case "POD_SHARED":
                    this.volumeSharing = Sharing.POD_SHARED;
                    return this;
                default:
                    throw new IllegalArgumentException("Unsupported volume sharing: " + volumeSharing);
            }
        }

        public PortworxVolumeSpec build() {
            return new PortworxVolumeSpec(
                    this.size,
                    this.containerPath,
                    this.driverName,
                    this.driverOptions,
                    this.volumeName,
                    this.volumeMode,
                    this.volumeSharing
            );
        }
    }

}
