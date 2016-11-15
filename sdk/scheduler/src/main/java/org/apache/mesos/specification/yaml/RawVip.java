package org.apache.mesos.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML vip.
 */
public class RawVip {
    int port;
    String prefix;

    public int getPort() {
        return port;
    }

    @JsonProperty("port")
    public void setPort(int port) {
        this.port = port;
    }

    public String getPrefix() {
        return prefix;
    }

    @JsonProperty("prefix")
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
