package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.util.RLimit;
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
    private Collection<NetworkSpec> networks;
    @Valid
    private Collection<RLimit> rlimits;

    @JsonCreator
    public DefaultContainerSpec(
            @JsonProperty("image-name") String imageName,
            @JsonProperty("networks") Collection<NetworkSpec> networks,
            @JsonProperty("rlimits") Collection<RLimit> rlimits) {
        this.imageName = imageName;
        this.networks = networks;
        this.rlimits = rlimits;
    }

    @Override
    public Optional<String> getImageName() {
        return Optional.ofNullable(imageName);
    }

    @Override
    public Collection<NetworkSpec> getNetworks() {
        return networks == null ? Collections.emptyList() : networks;
    }

    @Override
    public Collection<RLimit> getRLimits() {
        return rlimits == null ? Collections.emptyList() : rlimits;
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
