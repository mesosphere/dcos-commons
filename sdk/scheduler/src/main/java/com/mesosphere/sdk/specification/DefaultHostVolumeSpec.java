package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Optional;

public class DefaultHostVolumeSpec implements HostVolumeSpec {
    private final String hostPath;
    private final String containerPath;
    private final String mode;

    @JsonCreator
    private DefaultHostVolumeSpec(
            @JsonProperty("host-path") String hostPath,
            @JsonProperty("container-path") String containerPath,
            @JsonProperty("mode") String mode) {
        this.hostPath = hostPath;
        this.containerPath = containerPath;
        this.mode = mode;
    }
    private DefaultHostVolumeSpec(Builder builder) {
        this(builder.hostPath, builder.containerPath, builder.mode);

        //ValidationUtils.nonEmpty(this, "secretPath", secretPath);
        //ValidationUtils.matchesRegexAllowNull(this, "filePath", filePath, VALID_FILE_PATH_PATTERN);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @JsonProperty("host-path")
    @Override
    public String getHostPath() {
        return hostPath;
    }

    @JsonProperty("container-path")
    @Override
    public String getContainerPath() {
        return containerPath;
    }

    @JsonProperty("mode")
    @Override
    public Optional<String> getMode() {
        return Optional.ofNullable(mode);
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

    /**
     * {@link DefaultSecretSpec} builder static inner class.
     */
    public static class Builder {

        private String hostPath;
        private String containerPath;
        private String mode;

        private Builder() {
        }

        public Builder hostPath(String hostPath) {
            this.hostPath = hostPath;
            return this;
        }

        public Builder containerPath(String containerPath) {
            this.containerPath = containerPath;
            return this;
        }

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public DefaultHostVolumeSpec build() {
            return new DefaultHostVolumeSpec(this);
        }
    }
}
