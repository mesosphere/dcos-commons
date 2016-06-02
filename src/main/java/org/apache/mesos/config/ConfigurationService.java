package org.apache.mesos.config;

import java.util.List;

/**
 * Interface for all configurations.
 * All access to the env, properties, etc. should be through this service.
 * <p/>
 * To add additional configurations use a configurator.
 */
public interface ConfigurationService {

  long getVersion();

  String get(String propertyName);

  String get(String namespace, String propertyName);

  ConfigProperty getProperty(String propertyName);

  ConfigProperty getProperty(String namespace, String propertyName);


  List<ConfigProperty> getAllProperties();

  List<ConfigProperty> getProperties(String namespace);

  List<String> getNamespaces();
}
