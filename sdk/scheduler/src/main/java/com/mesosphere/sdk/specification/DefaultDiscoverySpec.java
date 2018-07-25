package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * Default implementation of a {@link DiscoverySpec}.
 */
public class DefaultDiscoverySpec implements DiscoverySpec {

    private final String prefix;
    private final Protos.DiscoveryInfo.Visibility visibility;

    @JsonCreator
    private DefaultDiscoverySpec(
            @JsonProperty("prefix") String prefix,
            @JsonProperty("visibility") Protos.DiscoveryInfo.Visibility visibility) {
        this.prefix = prefix;
        this.visibility = visibility;
    }

    public static Builder newBuilder() {
        return new Builder();
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

    /**
     * Builder for {@link DefaultDiscoverySpec}.
     */
    public static class Builder {

        private String prefix;
        private Protos.DiscoveryInfo.Visibility visibility;

        private Builder() {
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder visibility(Protos.DiscoveryInfo.Visibility visibility) {
            this.visibility = visibility;
            return this;
        }

        public DefaultDiscoverySpec build() {
            return new DefaultDiscoverySpec(prefix, visibility);
        }
    }
}
