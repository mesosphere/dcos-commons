package org.apache.mesos.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Set;

/**
 * Used to gather all the configurations from YAML.
 * Used to provide: 1) extra configurations and 2) namespace mappings.
 */
public class Configuration implements Serializable {

  @JsonProperty
  private Set<NamespaceConfig> namespaces;

  @JsonProperty
  private Set<ConfigProperty> configurations;

  @JsonProperty
  private Set<String> filters;

  @JsonProperty
  private Set<ConfigTarget> targets;

  public Set<NamespaceConfig> getNamespaces() {
    return namespaces;
  }

  public Set<ConfigProperty> getConfigurations() {
    return configurations;
  }

  public Set<String> getFilters() {
    return filters;
  }

  public Set<ConfigTarget> getTargets() {
    return targets;
  }

  public ConfigTarget getTarget(String targetName) {
    ConfigTarget target = null;

    if (targets != null && targetName != null) {
      for (ConfigTarget configTarget : targets) {
        if (targetName.equals(configTarget.getTarget())) {
          target = configTarget;
          break;
        }
      }
    }
    return target;
  }
}
