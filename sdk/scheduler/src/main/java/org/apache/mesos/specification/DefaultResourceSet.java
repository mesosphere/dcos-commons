package org.apache.mesos.specification;

import java.util.Collection;

/**
 * Default implementation of {@link ResourceSet}.
 */
public class DefaultResourceSet implements ResourceSet {
    private String id;
    private Collection<ResourceSpecification> resources;
    private Collection<VolumeSpecification> volumes;

    private DefaultResourceSet(Builder builder) {
        id = builder.id;
        resources = builder.resources;
        volumes = builder.volumes;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(DefaultResourceSet copy) {
        Builder builder = new Builder();
        builder.id = copy.id;
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
         * Sets the {@code resources} and returns a reference to this Builder so that the methods can be chained together.
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
