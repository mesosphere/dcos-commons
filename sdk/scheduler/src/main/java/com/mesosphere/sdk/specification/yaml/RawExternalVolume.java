package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RawHostVolume YAML spec.
 */
public final class RawExternalVolume {

    private final String type;

    private final String containerPath;

    private final String volumeName;

    private final String volumeMode;

    private final String driverName;

    private final String driverOptions;

    private RawExternalVolume(
            @JsonProperty("type") String type,
            @JsonProperty("volume-name") String volumeName,
            @JsonProperty("volume-mode") String volumeMode,
            @JsonProperty("driver-name") String driverName,
            @JsonProperty("driver-options") String driverOptions,
            @JsonProperty("container-path") String containerPath) {
        this.type = type;
        this.containerPath = containerPath;
        this.volumeName = volumeName;
        this.volumeMode = volumeMode;
        this.driverName = driverName;
        this.driverOptions = driverOptions;
    }


    public String getContainerPath() {
        return containerPath;
    }

    public String getType() {
        return type;
    }

    public String getVolumeName() {
        return volumeName;
    }

    public String getVolumeMode() {
        return volumeMode;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getDriverOptions() {
        return driverOptions;
    }
}
