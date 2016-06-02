package org.apache.mesos.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;

/**
 * Represents a name / value pair for purposes of configuration.
 */
public class ConfigProperty implements Serializable {

  @JsonProperty
  private String name;
  @JsonProperty
  private String value;
  @JsonProperty
  private String description;

  @JsonProperty
  private String type;

  // only useful for numbers
  @JsonProperty("increment-only")
  private Boolean incrementOnly;

  public ConfigProperty(String name, String value) {
    this.name = name;
    this.value = value;
  }

  public ConfigProperty(String name, int value) {
    this(name, value + "");
  }

  ConfigProperty() {
  }

  public String getName() {
    return name;
  }

  public String getValue() {
    return value.trim();
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getType() {
    return type;
  }

  public Boolean getIncrementOnly() {
    return incrementOnly;
  }

  /**
   * It is important to note that this equal is used to tell if there are
   * to properties that represent the same property name and NOT
   * if the values of the ConfigProperty are identical.  This
   * allows Sets of Configs to be restricted to unique values.
   *
   * @param o
   * @return
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ConfigProperty property = (ConfigProperty) o;

    return new EqualsBuilder()
      .append(name, property.name)
      .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
      .append(name)
      .toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("name", name)
      .append("value", value)
      .append("description", description)
      .toString();
  }
}
