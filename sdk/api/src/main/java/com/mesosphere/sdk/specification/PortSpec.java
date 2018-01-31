package com.mesosphere.sdk.specification;

import java.util.Collection;

import org.apache.mesos.Protos.DiscoveryInfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class represents a single port, with associated environment name.
 */
public interface PortSpec extends ResourceSpec {

    @JsonProperty("port-name")
    public String getPortName();

    @JsonProperty("visibility")
    public DiscoveryInfo.Visibility getVisibility();

    @JsonProperty("network-names")
    public Collection<String> getNetworkNames();

    @JsonProperty("env-key")
    public String getEnvKey();

    @JsonIgnore
    default long getPort() {
        return getValue().getRanges().getRange(0).getBegin();
    }
}
