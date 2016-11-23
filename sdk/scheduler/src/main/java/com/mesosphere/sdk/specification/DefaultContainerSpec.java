package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.Optional;

/**
 * Default implementation of {@link ContainerSpec}.
 */
public class DefaultContainerSpec implements ContainerSpec {
    @Size(min = 1)
    private String imageName;
    @Valid
    private RLimitSpec rLimitSpec;

    @JsonCreator
    public DefaultContainerSpec(
            @JsonProperty("image-name") String imageName,
            @JsonProperty("rlimit_spec") RLimitSpec rLimitSpec) {
        this.imageName = imageName;
        this.rLimitSpec = rLimitSpec;
    }

    @Override
    public Optional<String> getImageName() {
        return Optional.ofNullable(imageName);
    }

    @Override
    public Optional<RLimitSpec> getRLimitSpec() {
        return Optional.ofNullable(rLimitSpec);
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
