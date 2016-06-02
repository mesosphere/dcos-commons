package org.apache.mesos.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Set;

/**
 */
public class ConfigTarget implements Serializable {

  @JsonProperty
  private String target;

  @JsonProperty
  private Set<String> properties;

  public ConfigTarget() {
  }

  public ConfigTarget(String target) {
    this.target = target;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public Set<String> getProperties() {
    return properties;
  }

  public void setProperties(Set<String> properties) {
    this.properties = properties;
  }
}
