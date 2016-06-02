package org.apache.mesos.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;
import java.util.Set;

/**
 * Used to capture a namespace and the properties that live in that namespace.
 */
public class NamespaceConfig implements Serializable {

  @JsonProperty
  private String name;
  @JsonProperty
  private Set<String> properties;

  public String getName() {
    return name;
  }

  public Set<String> getProperties() {
    return properties;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("name", name)
      .append("properties", properties)
      .toString();
  }
}
