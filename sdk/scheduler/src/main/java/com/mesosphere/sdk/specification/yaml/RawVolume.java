package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML volume.
 */
public class RawVolume {

    private final String path;
    private final String type;
    private final String dockerDriverName;
    private final String dockerVolumeName;
    private final String dockerDriverOptions;
    private final int size;

    private RawVolume(
            @JsonProperty("path") String path,
            @JsonProperty("type") String type,
            @JsonProperty("docker_volume_driver") String dockerDriverName,
            @JsonProperty("docker_volume_name") String dockerVolumeName,
            @JsonProperty("docker_driver_options") String dockerDriverOptions,
            @JsonProperty("size") int size) {
        this.path = path;
        this.type = type;
        this.size = size;
        this.dockerDriverName = dockerDriverName;
        this.dockerVolumeName = dockerVolumeName;
        this.dockerDriverOptions = dockerDriverOptions;
    }

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }

    public String getDockerDriverName() {
        return dockerDriverName;
    }

    public String getDockerVolumeName() {
        return dockerVolumeName;
    }

    public String getDockerDriverOptions() {
        return dockerDriverOptions;
    }

    public int getSize() {
        return size;
    }
}
