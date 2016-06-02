package org.apache.mesos.config;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.config.configurator.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration service for the framework.  At this point it is strictly for the
 * scheduler.  It provides all the configuration and properties for the framework.
 * All access to the env, properties, etc. should be through this service.
 * <p/>
 * To add additional configurations use a configurator.
 */
@Singleton
public class FrameworkConfigurationService implements ConfigurationService, Configurable {

  public static final String CONFIG_ROOT_NAMESPACE = "*";
  public static final String REQUIRED_NAMESPACE = "framework.required";

  // used to add a property that will be used and saved but isn't used for change detection.
  public static final String IGNORED_NAMESPACE = "framework.ignored";
  public static final String CONFIGURATION_VERSION_PROPERTY = "system.configuration.version";

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private Map<String, Map<String, ConfigProperty>> nsPropertyMap = new HashMap<>();
  private Set<String> filters = new HashSet<>();

  public FrameworkConfigurationService() {
    Map<String, ConfigProperty> rootNsProperties = new HashMap<>();
    nsPropertyMap.put(CONFIG_ROOT_NAMESPACE, rootNsProperties);
  }

  @Override
  public long getVersion() {
    return ConfigUtil.getTypedValue(this, CONFIGURATION_VERSION_PROPERTY, 0L);
  }

  @Override
  public String get(String propertyName) {
    return get(CONFIG_ROOT_NAMESPACE, propertyName);
  }

  @Override
  public String get(String namespace, String propertyName) {
    ConfigProperty property = getProperty(namespace, propertyName);
    return property != null ? property.getValue() : "";
  }

  @Override
  public void setValue(String propertyName, String value) {
    setValue(CONFIG_ROOT_NAMESPACE, propertyName, value);
  }

  @Override
  public void setValue(String namespace, String propertyName, String value) {
    if (StringUtils.isBlank(propertyName)) {
      logger.error("Missing property name for set value.");
      return;
    }
    setProperty(namespace, new ConfigProperty(propertyName, value));
  }

  private void ensureInRootNs(String propertyName, ConfigProperty property) {
    getNamespaceMap(CONFIG_ROOT_NAMESPACE).put(propertyName, property);
  }

  @Override
  public ConfigProperty getProperty(String propertyName) {
    return getProperty(CONFIG_ROOT_NAMESPACE, propertyName);
  }

  @Override
  public ConfigProperty getProperty(String namespace, String propertyName) {
    return getNamespaceMap(namespace).get(propertyName);
  }

  @Override
  public void setProperty(ConfigProperty property) {
    setProperty(CONFIG_ROOT_NAMESPACE, property);
  }

  @Override
  public void setProperty(String namespace, ConfigProperty property) {
    String ns = CONFIG_ROOT_NAMESPACE;
    if (property == null || StringUtils.isBlank(property.getName()) || filters.contains(property.getName())) {
      if (property != null) {
        logger.info(String.format("Property %s is filtered.", property.getName()));
      }
      return;
    }
    if (StringUtils.isNotBlank(namespace)) {
      ns = namespace;
    }
    logger.info(String.format("%s: %s = %s", ns, property.getName(), property.getValue()));
    getNamespaceMap(ns).put(property.getName(), property);
    ensureInRootNs(property.getName(), property);
  }

  @Override
  public List<ConfigProperty> getAllProperties() {
    return new ArrayList<>(getNamespaceMap(CONFIG_ROOT_NAMESPACE).values());
  }

  @Override
  public List<ConfigProperty> getProperties(String namespace) {
    return new ArrayList<>(getNamespaceMap(namespace).values());
  }

  @Override
  public List<String> getNamespaces() {
    return new ArrayList<>(nsPropertyMap.keySet());
  }

  /**
   * Takes an existing ConfigProperty and adds it to the provided namespace.
   *
   * @param namespace
   * @param propertyName
   */
  @Override
  public void addToNameSpace(String namespace, String propertyName) {
    ConfigProperty property = getProperty(propertyName);

    if (StringUtils.isNotBlank(namespace) && property != null) {
      setProperty(namespace, property);
    }
  }

  @Inject
  public void init(Set<Configurator> configurators) {
    if (CollectionUtils.isNotEmpty(configurators)) {
      for (Configurator configurator : configurators) {
        logger.debug("Getting configurations from: " + configurator.toString());
        configurator.configure(this);
      }
    }

    logger.info("Configuration service initialized!");
  }

  public void addAllRootConfigurations(ConfigProperty[] configurations) {
    addAllConfigurations(CONFIG_ROOT_NAMESPACE, configurations);
  }

  public void addAllConfigurations(String namespace, ConfigProperty[] configurations) {
    addAllConfigurations(namespace, Arrays.asList(configurations));
  }

  public void addAllConfigurations(String namespace, Iterable<ConfigProperty> configurations) {
    if (!Iterables.isEmpty(configurations)) {
      String ns = StringUtils.isNoneBlank(namespace) ? namespace : CONFIG_ROOT_NAMESPACE;
      for (ConfigProperty property : configurations) {
        setProperty(ns, property);
      }
    }
  }

  private Map<String, ConfigProperty> getNamespaceMap(String namespace) {
    if (!nsPropertyMap.containsKey(namespace)) {
      nsPropertyMap.put(namespace, new HashMap<String, ConfigProperty>());
    }
    return nsPropertyMap.get(namespace);
  }

  public Map<String, Map<String, ConfigProperty>> getNsPropertyMap() {
    return Maps.newHashMap(nsPropertyMap);
  }

  @Override
  public void reconfigure(Configurator configurator) {

    resetNamespaces();

    Set<Configurator> configurators = new HashSet<>();
    configurators.add(configurator);
    init(configurators);
    resetRequiredProperties();
  }

  // ensures that the properties in the REQUIRED ns, are the same values as restored.
  private void resetRequiredProperties() {
    Set<String> requireProperties = nsPropertyMap.get(REQUIRED_NAMESPACE).keySet();
    for (String property : requireProperties) {
      nsPropertyMap.get(REQUIRED_NAMESPACE).put(
        property, nsPropertyMap.get(CONFIG_ROOT_NAMESPACE).get(property));
    }
  }

  @Override
  public void setFilters(Set<String> filters) {
    if (CollectionUtils.isNotEmpty(filters)) {
      for (String filter : filters) {
        addFilter(filter);
      }
    }
  }

  @Override
  public void addFilter(String filter) {
    if (StringUtils.isNotBlank(filter)) {
      this.filters.add(filter);
    }
  }

  // do NOT remove the required namespace, it may have come from a
  private void resetNamespaces() {
    Collection<String> namespaces = Collections2.filter(nsPropertyMap.keySet(), new Predicate<String>() {
      @Override
      public boolean apply(String s) {
        return !REQUIRED_NAMESPACE.equals(s);
      }
    });
    logger.info("Removing these namespaces: " + namespaces);

    Iterator<Map.Entry<String, Map<String, ConfigProperty>>> it = nsPropertyMap.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Map<String, ConfigProperty>> item = it.next();
      if (!namespaces.contains(item.getKey())) {
        it.remove();
      }
    }
  }

  public void addAllRootConfigurations(Iterable<ConfigProperty> configurations) {
    addAllConfigurations(CONFIG_ROOT_NAMESPACE, configurations);
  }
}
