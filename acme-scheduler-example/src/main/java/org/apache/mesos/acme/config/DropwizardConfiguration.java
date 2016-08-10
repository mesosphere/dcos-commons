package org.apache.mesos.acme.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Objects;

/**
 */
public class DropwizardConfiguration extends Configuration {

  @JsonProperty("scheduler_configuration")
  AcmeSchedulerConfiguration schedulerConfiguration;

  @JsonCreator
  public DropwizardConfiguration(
    @JsonProperty("scheduler_configuration") AcmeSchedulerConfiguration schedulerConfiguration) {
    this.schedulerConfiguration = schedulerConfiguration;
  }

  public AcmeSchedulerConfiguration getSchedulerConfiguration() {
    return schedulerConfiguration;
  }

  @JsonProperty("scheduler_configuration")
  public void setSchedulerConfiguration(AcmeSchedulerConfiguration schedulerConfiguration) {
    this.schedulerConfiguration = schedulerConfiguration;
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(schedulerConfiguration);
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }
}
