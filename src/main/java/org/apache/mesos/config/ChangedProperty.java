package org.apache.mesos.config;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Represents a configuration property that has a value change.
 */
public class ChangedProperty {

  private String name;
  private String oldValue;
  private String newValue;

  public ChangedProperty(String name, String oldValue, String newValue) {
    this.name = name;
    this.oldValue = oldValue;
    this.newValue = newValue;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getOldValue() {
    return oldValue;
  }

  public void setOldValue(String oldValue) {
    this.oldValue = oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public void setNewValue(String newValue) {
    this.newValue = newValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ChangedProperty that = (ChangedProperty) o;

    return new EqualsBuilder()
      .append(name, that.name)
      .append(oldValue, that.oldValue)
      .append(newValue, that.newValue)
      .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
      .append(name)
      .append(oldValue)
      .append(newValue)
      .toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("name", name)
      .append("oldValue", oldValue)
      .append("newValue", newValue)
      .toString();
  }
}
