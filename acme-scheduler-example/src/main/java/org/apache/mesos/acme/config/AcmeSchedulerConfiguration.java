package org.apache.mesos.acme.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 */
public class AcmeSchedulerConfiguration {

  @JsonProperty("service")
  private ServiceConfiguration serviceConfiguration;

  @JsonProperty("acme")
  private AcmeConfiguration acmeConfiguration;

}
