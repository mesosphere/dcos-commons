package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.validation.Valid;
import java.util.Map;

/**
 * Default implementation of {@link NetworkSpec}. This class encapsulates the Container Network Interface
 * (CNI) implementation within the SDK.
 */
public class DefaultNetworkSpec implements NetworkSpec {
    @Valid
    private String networkName;  // name of the overlay network to join, usually 'dcos'

    @Valid
    private Map<Integer, Integer> portMappings;  // key: host port, value: container port

    @JsonCreator
    public DefaultNetworkSpec(
            @JsonProperty("network-name") String networkName,
            @JsonProperty("port-mappings") Map<Integer, Integer> portMapings) {
        this.networkName  = networkName;
        this.portMappings = portMapings;
    }

    private DefaultNetworkSpec(Builder builder) {
        this(
                builder.networkName,
                builder.portMap);
        ValidationUtils.validate(this);
    }

    public static Builder newBuilder() { return new Builder(); }

    public static Builder newBuilder(NetworkSpec copy) {
        Builder builder = new Builder();
        builder.networkName = copy.getNetworkName();
        builder.portMap = copy.getPortMappings();

        return builder;
    }

    @Override
    public String getNetworkName() { return networkName; }

    @Override
    public Map<Integer, Integer> getPortMappings() { return portMappings; }

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

        private Builder() { }

        public Builder networkName(String networkName) {
            this.networkName = networkName;
            return this;
        }

        /**
         * Sets the CNI port mappings.
         *
         * @param portMappings Port mappings to set, host ports are the keys and the container ports are the values
         * @return a reference to this builder
         */
        public Builder portMappings(Map<Integer, Integer> portMappings) {
            this.portMap = portMappings;
            return this;
        }

        public void setPortMapping(Integer hostPort, Integer containerPort) {
            this.portMap.put(hostPort, containerPort);
        }

        public DefaultNetworkSpec build() {
            return new DefaultNetworkSpec(this);
        }
    }
}
