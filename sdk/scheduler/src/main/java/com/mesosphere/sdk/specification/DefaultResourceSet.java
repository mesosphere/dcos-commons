package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.Constants;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;

import com.mesosphere.sdk.specification.yaml.RawEndpoint;
import com.mesosphere.sdk.specification.yaml.RawVip;

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

    private static final String DEFAULT_VIP_PROTOCOL = "tcp";
    public static final DiscoveryInfo.Visibility PUBLIC_VIP_VISIBILITY = DiscoveryInfo.Visibility.EXTERNAL;

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
            VolumeSpecification.Type volumeTypeEnum;
            try {
                volumeTypeEnum = VolumeSpecification.Type.valueOf(volumeType);
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format(
                        "Provided volume type '%s' for path '%s' is invalid. Expected type to be one of: %s",
                        volumeType, containerPath, Arrays.asList(VolumeSpecification.Type.values())));
            }
            DefaultVolumeSpecification volume = new DefaultVolumeSpecification(
                    size,
                    volumeTypeEnum,
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

        public Builder addEndpoint(String name, RawEndpoint rawEndpoint) {
            Protos.Value.Builder portValueBuilder = Protos.Value.newBuilder()
                    .setType(Protos.Value.Type.RANGES);
            portValueBuilder.getRangesBuilder().addRangeBuilder()
                    .setBegin(rawEndpoint.getPort())
                    .setEnd(rawEndpoint.getPort());

            if (rawEndpoint.getVip() != null) {
                final RawVip rawVip = rawEndpoint.getVip();
                final String protocol =
                        StringUtils.isEmpty(rawVip.getProtocol()) ? DEFAULT_VIP_PROTOCOL : rawVip.getProtocol();
                final String vipName = StringUtils.isEmpty(rawVip.getPrefix()) ? name : rawVip.getPrefix();
                resources.add(new NamedVIPSpecification(
                        Constants.PORTS_RESOURCE_TYPE,
                        portValueBuilder.build(),
                        role,
                        principal,
                        rawEndpoint.getEnvKey(),
                        name,
                        protocol,
                        toVisibility(rawVip.isVisible()),
                        vipName,
                        rawVip.getPort()));
            } else {
                resources.add(new PortSpecification(
                        Constants.PORTS_RESOURCE_TYPE,
                        portValueBuilder.build(),
                        role,
                        principal,
                        rawEndpoint.getEnvKey(),
                        name));
            }
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

    /**
     * This visibility information isn't currently used by DC/OS Service Discovery. At the moment it's only enforced in
     * our own {@link com.mesosphere.sdk.api.EndpointsResource}.
     */
    private static DiscoveryInfo.Visibility toVisibility(Boolean rawIsVisible) {
        if (rawIsVisible == null) {
            return PUBLIC_VIP_VISIBILITY;
        }
        return rawIsVisible ? DiscoveryInfo.Visibility.EXTERNAL : DiscoveryInfo.Visibility.CLUSTER;
    }
}
