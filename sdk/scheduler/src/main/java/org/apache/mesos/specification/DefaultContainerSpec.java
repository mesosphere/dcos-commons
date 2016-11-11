package org.apache.mesos.specification;

import org.apache.mesos.Protos;

/**
 * Default implementation of {@link ContainerSpec}.
 */
public class DefaultContainerSpec implements ContainerSpec {
    private Protos.ContainerInfo containerInfo;

    private DefaultContainerSpec(Builder builder) {
        containerInfo = builder.containerInfo;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(DefaultContainerSpec copy) {
        Builder builder = new Builder();
        builder.containerInfo = copy.containerInfo;
        return builder;
    }

    @Override
    public Protos.ContainerInfo getContainerInfo() {
        return containerInfo;
    }


    /**
     * {@code DefaultContainerSpec} builder static inner class.
     */
    public static final class Builder {
        private Protos.ContainerInfo containerInfo;

        private Builder() {
        }

        /**
         * Sets the {@code containerInfo} and returns a reference to this Builder so that the methods can be chained
         * together.
         *
         * @param containerInfo the {@code containerInfo} to set
         * @return a reference to this Builder
         */
        public Builder containerInfo(Protos.ContainerInfo containerInfo) {
            this.containerInfo = containerInfo;
            return this;
        }

        /**
         * Returns a {@code DefaultContainerSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultContainerSpec} built with parameters of this {@code DefaultContainerSpec.Builder}
         */
        public DefaultContainerSpec build() {
            return new DefaultContainerSpec(this);
        }
    }
}
