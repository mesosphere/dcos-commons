package org.apache.mesos.config;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.mesos.config.FrameworkConfigurationService.CONFIG_ROOT_NAMESPACE;
import static org.apache.mesos.config.FrameworkConfigurationService.IGNORED_NAMESPACE;

/**
 * Detects if there are any configuration changes from one set of configurations to another.
 * The expected use is pull configurations from a state store (like zk) along with another
 * map of properties from the ENV.  Although it will work for any 2 sets of maps.
 * <p>
 * It works with a `Map<String,Map<String,ConfigProperty>>` structure.  The first String is the
 * namespace.  The second string is the configuration property name.
 * The `ConfigurationChangeNamespaces` defines the required namespaces.  The typical ROOT_NAMESPACE is "*".
 * The other required namespace property is the `updatable` namespace.
 */
public class ConfigurationChangeDetector {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  // config that came from ENV
  private Map<String, Map<String, ConfigProperty>> newConfiguration;
  // config that came from zk
  private Map<String, Map<String, ConfigProperty>> olderConfiguration;

  private String rootNs;
  private String updateNs;

  private Set<String> ignoredSet = null;

  public ConfigurationChangeDetector(
    Map<String, Map<String, ConfigProperty>> newConfiguration,
    Map<String, Map<String, ConfigProperty>> olderConfiguration,
    ConfigurationChangeNamespaces namespaces) {
    if (newConfiguration == null || olderConfiguration == null) {
      throw new ConfigurationException("ChangeDetector requires neither map be null.");
    }
    this.newConfiguration = newConfiguration;
    this.olderConfiguration = olderConfiguration;
    this.rootNs = namespaces.getRoot();
    this.updateNs = namespaces.getUpdatable();
  }

  public boolean isChangeDetected() {
    // quick check
    if (!isSameNumberOfNamespaces() || !isSameNumberOfProperties()) {
      logger.info("Quick check indicates a change.");
      logger.debug(String.format("namespaces: %s, properties: %s",
        !isSameNumberOfNamespaces(), !isSameNumberOfProperties()));
      return true;
    }
    // deep check
    if (CollectionUtils.isNotEmpty(getExtraConfigs()) ||
      CollectionUtils.isNotEmpty(getMissingConfigs()) ||
      CollectionUtils.isNotEmpty(getChangedProperties())) {
      logger.info("Deep check indicates a change.");
      logger.debug(String.format("extra: %s, missing: %s, changed: %s",
        getExtraConfigs().size(), getMissingConfigs().size(), getChangedProperties()));
      return true;
    }
    return false;
  }

  // Configurations in the configured not in the versioned.
  public Set<ConfigProperty> getExtraConfigs() {
    Set<ConfigProperty> extras = new HashSet<>();
    final Map<String, ConfigProperty> configuredRootMap = newConfiguration.get(rootNs);
    for (Map.Entry<String, ConfigProperty> config : configuredRootMap.entrySet()) {
      if (!olderConfiguration.get(rootNs).keySet().contains(config.getKey())) {
        extras.add(configuredRootMap.get(config.getKey()));
      }

    }
    return extras;
  }

  // Configurations in the versioned not in the configured.
  public Set<ConfigProperty> getMissingConfigs() {
    Set<ConfigProperty> missing = new HashSet<>();
    final Map<String, ConfigProperty> versionedRootMap = olderConfiguration.get(rootNs);
    for (Map.Entry<String, ConfigProperty> config : versionedRootMap.entrySet()) {
      if (!newConfiguration.get(rootNs).keySet().contains(config.getKey())) {
        missing.add(versionedRootMap.get(config.getKey()));
      }
    }
    return missing;
  }

  // set of all changed properties.
  public Set<ChangedProperty> getChangedProperties() {
    // todo:  change to predicates
    Set<ChangedProperty> changed = new HashSet<>();
    final Map<String, ConfigProperty> configuredRootMap = newConfiguration.get(rootNs);
    for (Map.Entry<String, ConfigProperty> config : configuredRootMap.entrySet()) {
      if (!getIgnoredSet().contains(config.getKey())) {
        String newValue = config.getValue().getValue();
        if (olderConfiguration.get(rootNs).keySet().contains(config.getKey())) {
          String oldValue = olderConfiguration.get(rootNs).get(config.getKey()).getValue();
          if (!StringUtils.equals(oldValue, newValue)) {
            changed.add(new ChangedProperty(config.getKey(), oldValue, newValue));
          }
        }
      }
    }
    return changed;
  }

  // set of changed properties which are not allowed.
  public Set<ChangedProperty> getChangeViolationProperties() {
    Set<ChangedProperty> changed = getChangedProperties();
    Set<String> updatable = getUpdatableProperties();
    Set<ChangedProperty> updatedProperties = new HashSet<>();
    for (ChangedProperty property : changed) {
      if (!updatable.contains(property.getName())) {
        updatedProperties.add(property);
      }
    }
    return updatedProperties;
  }

  // set of changed properties which are allowed.
  public Set<ChangedProperty> getValidChangedProperties() {
    Set<ChangedProperty> changed = getChangedProperties();
    Set<String> updatable = getUpdatableProperties();
    Set<ChangedProperty> updatedProperties = new HashSet<>();
    for (ChangedProperty property : changed) {
      if (updatable.contains(property.getName())) {
        updatedProperties.add(property);
      }
    }
    return updatedProperties;
  }

  // set of properties which are allowed to change.
  public Set<String> getUpdatableProperties() {
    Map<String, ConfigProperty> propertiesMap = olderConfiguration.get(updateNs);
    return propertiesMap != null ? propertiesMap.keySet() : new HashSet<String>();
  }

  private boolean isSameNumberOfNamespaces() {
    return newConfiguration.size() == olderConfiguration.size();
  }

  private boolean isSameNumberOfProperties() {
    return newConfiguration.get(rootNs).size() == olderConfiguration.get(rootNs).size();
  }

  // lazily ignored and off the versioned config
  private Set<String> getIgnoredSet() {
    if (ignoredSet == null) {
      ignoredSet = new HashSet<>();
      if (olderConfiguration.get(IGNORED_NAMESPACE) != null) {
        ignoredSet.addAll(olderConfiguration.get(IGNORED_NAMESPACE).keySet());
      }
      // all ports are ignored
      for (String key : olderConfiguration.get(CONFIG_ROOT_NAMESPACE).keySet()) {
        if (key.startsWith("port")) {
          ignoredSet.add(key);
        }
      }
    }
    return ignoredSet;
  }
}
