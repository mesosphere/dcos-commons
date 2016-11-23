package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Collection;

/**
 * Default implementation of {@link ResourceSet}.
 */
public class DefaultResourceSet implements ResourceSet {
    @NotNull
    @Size(min = 1)
    private String id;
    @NotNull
    @Size(min = 1)
    @Valid
    private Collection<ResourceSpecification> resources;
    @Valid
    private Collection<VolumeSpecification> volumes;

    @JsonCreator
    public DefaultResourceSet(
            @JsonProperty("id") String id,
            @JsonProperty("resource_specifications") Collection<ResourceSpecification> resources,
            @JsonProperty("volume_specifications") Collection<VolumeSpecification> volumes) {
        this.id = id;
        this.resources = resources;
        this.volumes = volumes;
    }

    private DefaultResourceSet(Builder builder) {
        this(
                builder.id,
                builder.resources,
                builder.volumes);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(DefaultResourceSet copy) {
        Builder builder = new Builder();
        builder.resources = copy.resources;
        builder.volumes = copy.volumes;
        return builder;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Collection<ResourceSpecification> getResources() {
        return resources;
    }

    @Override
    public Collection<VolumeSpecification> getVolumes() {
        return volumes;
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
     * {@code DefaultResourceSet} builder static inner class.
     */
    public static final class Builder {
        private String id;
        private Collection<ResourceSpecification> resources;
        private Collection<VolumeSpecification> volumes;

        private Builder() {
        }

        /**
         * Sets the {@code id} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param id the {@code id} to set
         * @return a reference to this Builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the {@code resources} and returns a reference to this Builder so that the methods can be chained
         * together.
         *
         * @param resources the {@code resources} to set
         * @return a reference to this Builder
         */
        public Builder resources(Collection<ResourceSpecification> resources) {
            this.resources = resources;
            return this;
        }

        /**
         * Sets the {@code volumes} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param volumes the {@code volumes} to set
         * @return a reference to this Builder
         */
        public Builder volumes(Collection<VolumeSpecification> volumes) {
            this.volumes = volumes;
            return this;
        }

        /**
         * Returns a {@code DefaultResourceSet} built from the parameters previously set.
         *
         * @return a {@code DefaultResourceSet} built with parameters of this {@code DefaultResourceSet.Builder}
         */
        public DefaultResourceSet build() {
            return new DefaultResourceSet(this);
        }
    }
}
