package org.apache.mesos.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML port.
 */
public class RawPort {
    private String name;
    private Integer port;
    private RawVip vip;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public Integer getPort() {
        return port;
    }

    @JsonProperty("port")
    public void setPort(Integer port) {
        this.port = port;
    }

    public RawVip getVip() {
        return vip;
    }

    @JsonProperty("vip")
    public void setVip(RawVip vip) {
        this.vip = vip;
    }
}
