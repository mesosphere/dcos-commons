package org.apache.mesos.config.configurator;

import org.apache.mesos.config.ConfigProperty;
import org.apache.mesos.config.Configuration;
import org.apache.mesos.config.ConfigurationException;
import org.apache.mesos.config.FrameworkConfigurationService;
import org.apache.mesos.config.NamespaceConfig;
import org.apache.mesos.config.io.ConfigurationReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Configures the client namespace based on the config.yaml.
 */
public class NamespaceConfigurator implements Configurator {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ConfigurationReader reader;

  public NamespaceConfigurator() {
    this.reader = new ConfigurationReader();
  }

  public NamespaceConfigurator(String fileName) {
    this.reader = new ConfigurationReader(fileName);
  }

  @Override
  public void configure(FrameworkConfigurationService configurationService) {

    Configuration configurations = reader.getConfiguration();
    try {
      Set<ConfigProperty> properties = configurations.getConfigurations();
      configurationService.addAllRootConfigurations(properties);
    } catch (ConfigurationException e) {
      logger.error("Unable to set configuration based from Configuration", e);
    }

    configureNamespace(configurationService, configurations.getNamespaces());
  }

  private void configureNamespace(FrameworkConfigurationService configurationService,
    Set<NamespaceConfig> namespaces) {

    for (NamespaceConfig namespace : namespaces) {
      for (String property : namespace.getProperties()) {
        configurationService.addToNameSpace(namespace.getName(), property);
      }
    }
  }
}
