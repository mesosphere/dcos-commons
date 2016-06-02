package org.apache.mesos.config.configurator;

import org.apache.mesos.config.FrameworkConfigurationService;

/**
 * Implementations of Configurators are used to configure the
 * ConfigurationService.
 */
public interface Configurator {

  void configure(FrameworkConfigurationService frameworkConfigurationService);
}
