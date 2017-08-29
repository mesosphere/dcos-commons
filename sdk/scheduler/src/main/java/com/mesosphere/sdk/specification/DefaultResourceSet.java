package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;

/**
 * Default implementation of {@link ResourceSet}.
 */
public class DefaultResourceSet implements ResourceSet {

    private final String preReservedRole;
    @NotNull
    @Size(min = 1)
    private String id;
    @NotNull
    @Size(min = 1)
    @Valid
    private Collection<ResourceSpec> resources;
    @Valid
    private Collection<VolumeSpec> volumes;
    @NotNull
    @Size(min = 1)
    String role;
    @NotNull
    @Size(min = 1)
    String principal;

    @JsonCreator
    public DefaultResourceSet(
            @JsonProperty("id") String id,
            @JsonProperty("resource-specifications") Collection<ResourceSpec> resources,
            @JsonProperty("volume-specifications") Collection<VolumeSpec> volumes,
            @JsonProperty("role") String role,
            @JsonProperty("pre-reserved-role") String preReservedRole,
            @JsonProperty("principal") String principal) {
        this.id = id;
        this.resources = resources;
        this.volumes = volumes;
        this.role = role;
        this.preReservedRole = preReservedRole;
        this.principal = principal;
    }

    private DefaultResourceSet(Builder builder) {
        this(
                builder.id,
                builder.resources,
                builder.volumes,
                builder.role,
                builder.preReservedRole,
                builder.principal);
    }

    public static Builder newBuilder(String role, String preReservedRole, String principal) {
        return new Builder(role, preReservedRole, principal);
    }

    public static Builder newBuilder(DefaultResourceSet copy) {
        Builder builder = new Builder(copy.role, copy.preReservedRole, copy.principal);
        builder.id = copy.id;
        builder.resources = copy.resources;
        builder.volumes = copy.volumes;
        return builder;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getPrincipal() {
        return principal;
    }

    @Override
    public Collection<ResourceSpec> getResources() {
        return resources;
    }

    @Override
    public Collection<VolumeSpec> getVolumes() {
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
        private Collection<ResourceSpec> resources;
        private Collection<VolumeSpec> volumes;
        private String role;
        private String principal;
        public String preReservedRole;

        private Builder(String role, String preReservedRole, String principal) {
            this.role = role;
            this.preReservedRole = preReservedRole;
            this.principal = principal;
            resources = new LinkedList<>();
            volumes = new LinkedList<>();
        }

        private Builder addScalarResource(Double r, String resourceId) {
            DefaultResourceSpec resource = DefaultResourceSpec.newBuilder()
                    .name(resourceId)
                    .role(role)
                    .preReservedRole(preReservedRole)
                    .principal(principal)
                    .value(Protos.Value.newBuilder()
                            .setType(Protos.Value.Type.SCALAR)
                            .setScalar(Protos.Value.Scalar.newBuilder().setValue(r))
                            .build())
                    .build();
            if (resources.stream()
                    .anyMatch(resourceSpecification -> Objects.equals(resourceSpecification.getName(), resourceId))) {
                String msg = String.format("Cannot configure multiple %s resources in a single ResourceSet",
                        resourceId);
                throw new IllegalStateException(msg);
            }

            resources.add(resource);
            return this;
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

        public Builder cpus(Double cpus) {
            return addScalarResource(cpus, "cpus");
        }

        public Builder gpus(Double gpus) {
            return addScalarResource(gpus, "gpus");
        }

        public Builder memory(Double memory) {
            return addScalarResource(memory, "mem");
        }

        public Builder addVolume(String volumeType,
                                 Double size,
                                 String containerPath) {
            VolumeSpec.Type volumeTypeEnum;
            try {
                volumeTypeEnum = VolumeSpec.Type.valueOf(volumeType);
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format(
                        "Provided volume type '%s' for path '%s' is invalid. Expected type to be one of: %s",
                        volumeType, containerPath, Arrays.asList(VolumeSpec.Type.values())));
            }
            DefaultVolumeSpec volume =
                    new DefaultVolumeSpec(size, volumeTypeEnum, containerPath, role, preReservedRole, principal);
            if (volumes.stream()
                    .anyMatch(volumeSpecification ->
                            Objects.equals(volumeSpecification.getContainerPath(), containerPath))) {
                throw new IllegalStateException("Cannot configure multiple volumes with the same containerPath");
            }
            volumes.add(volume);
            return this;
        }

        /**
         * Adds {@code resource} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param resource the {@code resource} to add
         * @return a reference to this Builder
         */
        public Builder addResource(ResourceSpec resource) {
            resources.add(resource);
            return this;
        }

        /**
         * Adds a collection of {@code resource}s and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param resourceSpecCollection the {@code resource} to add
         * @return a reference to this Builder
         */
        public Builder addResource(Collection<ResourceSpec> resourceSpecCollection) {
            resources.addAll(resourceSpecCollection);
            return this;
        }

        /**
         * Sets the {@code volumes} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param volumes the {@code volumes} to set
         * @return a reference to this Builder
         */
        public Builder volumes(Collection<VolumeSpec> volumes) {
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
