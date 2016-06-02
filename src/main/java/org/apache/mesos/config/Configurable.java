package org.apache.mesos.config;

import org.apache.mesos.config.configurator.Configurator;

import java.util.Map;
import java.util.Set;

/**
 */
public interface Configurable {

  void addToNameSpace(String namespace, String propertyName);

  // I hate to expose this, but it is currently necessary to
  // serialize and to check for changes
  Map<String, Map<String, ConfigProperty>> getNsPropertyMap();

  void reconfigure(Configurator configurator);

  void setFilters(Set<String> filters);

  void addFilter(String filter);

  void setProperty(ConfigProperty property);

  void setProperty(String namespace, ConfigProperty property);

  void setValue(String propertyName, String value);

  void setValue(String namespace, String propertyName, String value);

}
