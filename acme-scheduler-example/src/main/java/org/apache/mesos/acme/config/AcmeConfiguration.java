package org.apache.mesos.acme.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Objects;

/**
 */
public class AcmeConfiguration {

  @JsonProperty("acmeZkUri")
  private String acmeZkUri;

  @JsonProperty("zkAddress")
  private String zkAddress;

  @JsonCreator
  public AcmeConfiguration(
    @JsonProperty("acmeZkUri") String acmeZkUri,
    @JsonProperty("zkAddress") String zkAddress) {
    this.acmeZkUri = acmeZkUri;
    this.zkAddress = zkAddress;
  }

  public String getAcmeZkUri() {
    return acmeZkUri;
  }

  @JsonProperty("acmeZkUri")
  public void setAcmeZkUri(String acmeZkUri) {
    this.acmeZkUri = acmeZkUri;
  }

  public String getZkAddress() {
    return zkAddress;
  }

  @JsonProperty("zkAddress")
  public void setZkAddress(String zkAddress) {
    this.zkAddress = zkAddress;
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(acmeZkUri, zkAddress);
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }
}
