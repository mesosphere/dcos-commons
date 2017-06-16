package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;

/**
 * Default implementation of {@link NetworkSpec}. This class encapsulates the Container Network Interface
 * (CNI) implementation within the SDK.
 */
public class DefaultNetworkSpec implements NetworkSpec {
    @Valid
    private String networkName;  // name of the network to join, checked against supported networks

    @Valid
    private Map<Integer, Integer> portMappings;  // key: host port, value: container port

    @Valid
    private Map<String, String> labels;  // user-defined K/V pairs passed to CNI plugin

    @JsonCreator
    public DefaultNetworkSpec(
            @JsonProperty("network-name") String networkName,
            @JsonProperty("port-mappings") Map<Integer, Integer> portMappings,
            @JsonProperty("network-labels") Map<String, String> labels) {
        this.networkName = networkName;
        this.portMappings = portMappings == null ? Collections.emptyMap() : portMappings;
        this.labels = labels == null ? Collections.emptyMap() : labels;
    }

    private DefaultNetworkSpec(Builder builder) {
        this(builder.networkName,  builder.portMap, builder.labels);
        ValidationUtils.validate(this);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(NetworkSpec copy) {
        Builder builder = new Builder();
        builder.networkName = copy.getName();
        builder.portMap = copy.getPortMappings();
        builder.labels = copy.getLabels();

        return builder;
    }

    @Override
    public String getName() {
        return networkName;
    }

    @Override
    public Map<Integer, Integer> getPortMappings() {
        return portMappings;
    }

    @Override
    public Map<String, String> getLabels() {
        return labels;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    /**
     * {@code DefaultNetworkSpec} builder static inner class.
     */
    public static final class Builder {
        private String networkName;
        private Map<Integer, Integer> portMap;
        private Map<String, String> labels;

        private Builder() { }

        /**
         *
         * @param networkName name of the overlay network to join, usually "dcos"
         * @return
         */
        public Builder networkName(String networkName) {
            this.networkName = networkName;
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
         * Sets the network-labels, which are K/V pairs passed to the CNI plugin.
         *
         * @param labels Key/Value mappings to pass to CNI plugin
         * @return A reference to this builder
         */
        public Builder networkLabels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public DefaultNetworkSpec build() {
            return new DefaultNetworkSpec(this);
        }
    }
}
