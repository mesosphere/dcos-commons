package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.util.RLimit;
import com.mesosphere.sdk.specification.yaml.ContainerVolume;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Default implementation of {@link ContainerSpec}.
 */
public class DefaultContainerSpec implements ContainerSpec {
    @Size(min = 1)
    private String imageName;
    @Valid
    private Collection<RLimit> rlimits;
    @Valid
    private final Collection<ContainerVolume> volumes;

    @JsonCreator
    public DefaultContainerSpec(
            @JsonProperty("image-name") String imageName,
            @JsonProperty("rlimits") Collection<RLimit> rlimits,
            @JsonProperty("volumes") Collection<ContainerVolume> volumes) {
        this.imageName = imageName;
        this.rlimits = rlimits;
        this.volumes = volumes;
    }

    @Override
    public Optional<String> getImageName() {
        return Optional.ofNullable(imageName);
    }

    @Override
    public Collection<RLimit> getRLimits() {
        return rlimits == null ? Collections.emptyList() : rlimits;
    }

    @Override
    public Collection<ContainerVolume> getVolumes() {
        return volumes == null ? Collections.emptyList() : volumes;
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
