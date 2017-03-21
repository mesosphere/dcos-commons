package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link NetworkSpec}. This class encapsulates the Container Network Interface
 * (CNI) implementation within the SDK.
 */
public class DefaultNetworkSpec implements NetworkSpec {
    @Valid
    private String networkName;  // name of the overlay network to join, usually 'dcos'

    @Valid
    private Set<String> netgroups;

    @Valid
    private Map<Integer, Integer> portMappings;  // key: host port, value: container port

    @Valid
    private Set<String> ipAddresses;  // container requests for specific IP addresses

    @JsonCreator
    public DefaultNetworkSpec(
            @JsonProperty("network-name") String networkName,
            @JsonProperty("netgroups") Set<String> netgroups,
            @JsonProperty("port-mappings") Map<Integer, Integer> portMapings,
            @JsonProperty("ip-addresses") Set<String> ipAddresses) {
        this.networkName  = networkName;
        this.netgroups    = netgroups == null ? Collections.emptySet() : netgroups;
        this.portMappings = portMapings == null ? Collections.emptyMap() : portMapings;
        this.ipAddresses  = ipAddresses == null ? Collections.emptySet() : ipAddresses;
    }

    private DefaultNetworkSpec(Builder builder) {
        this(builder.networkName, builder.netgroups, builder.portMap, builder.ipAddresses);
        ValidationUtils.validate(this);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(NetworkSpec copy) {
        Builder builder = new Builder();
        builder.networkName = copy.getName();
        builder.netgroups   = copy.getNetgroups();
        builder.portMap     = copy.getPortMappings();
        builder.ipAddresses = copy.getIpAddresses();

        return builder;
    }

    @Override
    public String getName() {
        return networkName;
    }

    @Override
    public Set<String> getNetgroups() {
        return netgroups;
    }

    @Override
    public Set<String> getIpAddresses() {
        return ipAddresses;
    }

    @Override
    public Map<Integer, Integer> getPortMappings() {
        return portMappings;
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
     * {@code DefaultNetworkSpec} builder static inner class.
     */
    public static final class Builder {
        private Map<Integer, Integer> portMap;
        private String networkName;
        private Set<String> netgroups;
        private Set<String> ipAddresses;

        private Builder() { }

        public Builder networkName(String networkName) {
            this.networkName = networkName;
            return this;
        }

        /**
         * Sets the netgroups mappings.
         *
         * @param netgroups a set of netgroups (identifiers), only containers sharing netgroups will be allowed to
         *                  communicate with each other.
         * @return a reference to this builder
         */
        public Builder netgroups(Set<String> netgroups) {
            this.netgroups = netgroups;
            return this;
        }

        /**
         * Sets the host-to-container port mappings.
         *
         * @param portMappings Port mappings to set, host ports are the keys and the container ports are the values
         * @return a reference to this builder
         */
        public Builder portMappings(Map<Integer, Integer> portMappings) {
            this.portMap = portMappings;
            return this;
        }

        /**
         * Sets the IP/Container addresses that this network is going to request.
         *
         * @param ipAddresses list of IP addresses for the container to request
         * @return a reference to this builder
         */
        public Builder ipAddresses(Set<String> ipAddresses) {
            this.ipAddresses = ipAddresses;
            return this;
        }

        public DefaultNetworkSpec build() {
            return new DefaultNetworkSpec(this);
        }
    }
}
