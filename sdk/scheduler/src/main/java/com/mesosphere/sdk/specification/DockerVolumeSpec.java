package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.Protos;

import java.util.Map;
import java.util.Optional;

public interface DockerVolumeSpec extends ExternalVolumeSpec {

    @Override
    default Type getType() {
        return Type.DOCKER;
    }

    // Name of the driver to use
    @JsonProperty("driver-name")
    String getDriverName();

    // driver-options: String of options to pass to the driver. This is opaque to the SDK.
    @JsonProperty("driver-options")
    Map<String, String> getDriverOptions();

    // volume-name: Name of the volume exposed to the provider.
    @JsonProperty("volume-name")
    String getVolumeName();

    // volume-mode: Optional Whether volume is read-write or read-only. Defaults to read-write mode.
    @JsonProperty("volume-mode")
    Optional<Protos.Volume.Mode> getVolumeMode();
}
