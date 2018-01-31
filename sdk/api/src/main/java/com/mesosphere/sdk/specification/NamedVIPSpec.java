package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class represents a port mapped to a DC/OS named VIP.
 */
public interface NamedVIPSpec extends PortSpec {
    @JsonProperty("protocol")
    public String getProtocol();

    @JsonProperty("vip-name")
    public String getVipName();

    @JsonProperty("vip-port")
    public Integer getVipPort();
}
