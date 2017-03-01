package com.mesosphere.sdk.kafka.upgrade.old;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;


/* copy from dcos-kafka-service */

/**
 * old config.
 */
public class StatsdConfig {

    @JsonProperty("host")
    private String host = "";

    @JsonProperty("port")
    private int port = 0;

    public StatsdConfig() {

    }

    public StatsdConfig(String host, int port) {
        super();
        this.host = host;
        this.port = port;
    }

    @JsonProperty("host")
    public void setHost(final String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    @JsonProperty("port")
    public void setPort(final int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    @JsonIgnore
    public String getPortString() {
        StringBuilder sb = new StringBuilder();
        sb.append(port);
        return sb.toString();
    }

    @JsonIgnore
    public boolean hasHost() {
        return !host.isEmpty();
    }

    @JsonIgnore
    public boolean hasPort() {
        return port != 0;
    }

    @JsonIgnore
    public boolean isReady() {
        return hasHost() && hasPort();
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StatsdConfig that = (StatsdConfig) o;
        if (!host.equals(that.host)) {
            return false;
        }
        if (port != that.port) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "StatsdConfig{host=" + host + ", port=" + port + "}";
    }

}
