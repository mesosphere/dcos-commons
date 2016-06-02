package org.apache.mesos.config;

/**
 * Used to define the required namespaces used by the ChangeDetector.
 */
public class ConfigurationChangeNamespaces {

  private String root;
  private String updatable;

  public ConfigurationChangeNamespaces(String rootNamespace, String updatableNamespace) {
    this.root = rootNamespace;
    this.updatable = updatableNamespace;
  }

  public String getRoot() {
    return root;
  }

  public String getUpdatable() {
    return updatable;
  }
}
