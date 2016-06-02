package org.apache.mesos.config.configurator;

import org.apache.mesos.config.ConfigUtil;
import org.apache.mesos.config.Configuration;
import org.apache.mesos.config.FrameworkConfigurationService;
import org.apache.mesos.config.io.ConfigurationReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Adds filters to the Configuration service which are properties to
 * ignore during configuration.
 */
public class FilterConfigurator implements Configurator {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ConfigurationReader reader;

  public FilterConfigurator() {
    this.reader = new ConfigurationReader();
  }

  public FilterConfigurator(String configFileName) {
    this.reader = new ConfigurationReader(configFileName);
  }

  @Override
  public void configure(FrameworkConfigurationService frameworkConfigurationService) {
    Configuration configuration = reader.getConfiguration();
    Set<String> filters = getFilters(configuration);
    logger.info("Setting configuration filters: " + filters);
    frameworkConfigurationService.setFilters(filters);
  }

  private Set<String> getFilters(Configuration configuration) {
    Set<String> filters = configuration.getFilters();
    Set<String> filterProperties = new HashSet<>();
    for (String filter : filters) {
      filterProperties.add(ConfigUtil.fromEnvVarNameToPropertyName(filter));
    }
    return filterProperties;
  }
}
