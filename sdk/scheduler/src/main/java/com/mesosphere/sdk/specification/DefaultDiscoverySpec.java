package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import javax.validation.Valid;
import java.util.Optional;

/**
 * Default implementation of a {@link DiscoverySpec}.
 */
public class DefaultDiscoverySpec implements DiscoverySpec {
    @Valid
    private final String prefix;
    @Valid
    private final Protos.DiscoveryInfo.Visibility visibility;

    @JsonCreator
    public DefaultDiscoverySpec(
            @JsonProperty("prefix") String prefix,
            @JsonProperty("visibility") Protos.DiscoveryInfo.Visibility visibility) {
        this.prefix = prefix;
        this.visibility = visibility;
    }

    @Override
    public Optional<String> getPrefix() {
        return Optional.ofNullable(prefix);
    }

    @Override
    public Optional<Protos.DiscoveryInfo.Visibility> getVisibility() {
        return Optional.ofNullable(visibility);
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
