package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;

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
    @NotNull
    @Size(min = 1)
    String role;
    @NotNull
    @Size(min = 1)
    String principal;

    @JsonCreator
    public DefaultResourceSet(
            @JsonProperty("id") String id,
            @JsonProperty("resource_specifications") Collection<ResourceSpecification> resources,
            @JsonProperty("volume_specifications") Collection<VolumeSpecification> volumes,
            @JsonProperty("role") String role,
            @JsonProperty("principal") String principal) {
        this.id = id;
        this.resources = resources;
        this.volumes = volumes;
        this.role = role;
        this.principal = principal;
    }

    private DefaultResourceSet(Builder builder) {
        this(
                builder.id,
                builder.resources,
                builder.volumes,
                builder.role,
                builder.principal);
    }

    public static Builder newBuilder(String role, String principal) {
        return new Builder(role, principal);
    }

    public static Builder newBuilder(DefaultResourceSet copy) {
        Builder builder = new Builder(copy.role, copy.principal);
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
        private String role;
        private String principal;

        private Builder(String role, String principal) {
            this.role = role;
            this.principal = principal;
            resources = new LinkedList<>();
            volumes = new LinkedList<>();
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
            DefaultResourceSpecification cpuResource = DefaultResourceSpecification.newBuilder()
                    .name("cpus")
                    .role(role)
                    .principal(principal)
                    .value(Protos.Value.newBuilder()
                            .setType(Protos.Value.Type.SCALAR)
                            .setScalar(Protos.Value.Scalar.newBuilder().setValue(cpus))
                            .build())
                    .build();
            if (resources.stream()
                    .anyMatch(resourceSpecification -> Objects.equals(resourceSpecification.getName(), "cpus"))) {
                throw new IllegalStateException("Cannot configure multiple cpus resources in a single ResourceSet");
            }

            resources.add(cpuResource);
            return this;
        }

        public Builder memory(Double memory) {
            DefaultResourceSpecification memoryResource = DefaultResourceSpecification.newBuilder()
                    .name("mem")
                    .role(role)
                    .principal(principal)
                    .value(Protos.Value.newBuilder()
                            .setType(Protos.Value.Type.SCALAR)
                            .setScalar(Protos.Value.Scalar.newBuilder().setValue(memory))
                            .build())
                    .build();
            if (resources.stream()
                    .anyMatch(resourceSpecification -> Objects.equals(resourceSpecification.getName(), "mem"))) {
                throw new IllegalStateException("Cannot configure multiple memory resources in a single ResourceSet");
            }
            resources.add(memoryResource);
            return this;
        }

        public Builder addVolume(String volumeType,
                                 Double size,
                                 String containerPath) {
            DefaultVolumeSpecification volume = new DefaultVolumeSpecification(
                    size,
                    VolumeSpecification.Type.valueOf(volumeType),
                    containerPath,
                    role,
                    principal,
                    "DISK_SIZE");
            if (volumes.stream()
                    .anyMatch(volumeSpecification ->
                            Objects.equals(volumeSpecification.getContainerPath(), containerPath))) {
                throw new IllegalStateException("Cannot configure multiple volumes with the same containerPath");
            }
            volumes.add(volume);
            return this;
        }

        public Builder addPort(Integer port) {
            resources.add(DefaultResourceSpecification.newBuilder()
                    .name("port")
                    .role(role)
                    .principal(principal)
                    .value(Protos.Value.newBuilder()
                            .setType(Protos.Value.Type.SCALAR)
                            .setScalar(Protos.Value.Scalar.newBuilder()
                                    .setValue(port)
                                    .build())
                            .build())
                    .build());
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
