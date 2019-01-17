package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Raw YAML port.
 */
public final class RawPort {

  private final Integer port;

  private final String envKey;

  private final Boolean advertise;

  private final RawVip vip;

  private final List<RawRange> ranges;

  private RawPort(
      @JsonProperty("port") Integer port,
      @JsonProperty("env-key") String envKey,
      @JsonProperty("advertise") Boolean advertise,
      @JsonProperty("vip") RawVip vip,
      @JsonProperty("ranges") List<RawRange> ranges)
  {
    this.port = port;
    this.envKey = envKey;
    this.advertise = advertise;
    this.vip = vip;
    this.ranges = ranges;
  }

  public Integer getPort() {
    return port;
  }

  public String getEnvKey() {
    return envKey;
  }

  public boolean isAdvertised() {
    return advertise != null && advertise;
  }

  public RawVip getVip() {
    return vip;
  }

  public List<RawRange> getRanges() {
    return ranges;
  }
}
