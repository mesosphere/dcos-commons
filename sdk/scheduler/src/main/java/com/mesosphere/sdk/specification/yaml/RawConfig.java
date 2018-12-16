package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw YAML Configuration section.
 */
public final class RawConfig {

  private final String template;

  private final String dest;

  private RawConfig(
      @JsonProperty("template") String template,
      @JsonProperty("dest") String dest)
  {
    this.template = template;
    this.dest = dest;
  }

  public String getTemplate() {
    return template;
  }

  public String getDest() {
    return dest;
  }
}
