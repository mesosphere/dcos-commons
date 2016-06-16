package org.apache.mesos.acme.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Objects;

/**
 *
 */
public class ServiceConfiguration {
  @JsonProperty("count")
  private int count;
  @JsonProperty("name")
  private String name;
  @JsonProperty("user")
  private String user;
  @JsonProperty("placementStrategy")
  private String placementStrategy;
  @JsonProperty("phaseStrategy")
  private String phaseStrategy;
  @JsonProperty("role")
  private String role;
  @JsonProperty("principal")
  private String principal;

  @JsonCreator
  public ServiceConfiguration(
    @JsonProperty("count") int count,
    @JsonProperty("name") String name,
    @JsonProperty("user") String user,
    @JsonProperty("placementStrategy") String placementStrategy,
    @JsonProperty("phaseStrategy") String phaseStrategy,
    @JsonProperty("role") String role,
    @JsonProperty("principal") String principal) {
    this.count = count;
    this.name = name;
    this.user = user;
    this.placementStrategy = placementStrategy;
    this.phaseStrategy = phaseStrategy;
    this.role = role;
    this.principal = principal;
  }

  public int getCount() {
    return count;
  }

  @JsonProperty("count")
  public void setCount(int count) {
    this.count = count;
  }

  public String getName() {
    return name;
  }

  @JsonProperty("name")
  public void setName(String name) {
    this.name = name;
  }

  public String getUser() {
    return user;
  }

  @JsonProperty("user")
  public void setUser(String user) {
    this.user = user;
  }

  public String getPlacementStrategy() {
    return placementStrategy;
  }

  @JsonProperty("placementStrategy")
  public void setPlacementStrategy(String placementStrategy) {
    this.placementStrategy = placementStrategy;
  }

  public String getPhaseStrategy() {
    return phaseStrategy;
  }

  @JsonProperty("phaseStrategy")
  public void setPhaseStrategy(String phaseStrategy) {
    this.phaseStrategy = phaseStrategy;
  }

  public String getRole() {
    return role;
  }

  @JsonProperty("role")
  public void setRole(String role) {
    this.role = role;
  }

  public String getPrincipal() {
    return principal;
  }

  @JsonProperty("principal")
  public void setPrincipal(String principal) {
    this.principal = principal;
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(count, name, user, placementStrategy, phaseStrategy, role, principal);
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }
}
