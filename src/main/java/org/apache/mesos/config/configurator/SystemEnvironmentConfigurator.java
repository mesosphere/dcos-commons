package org.apache.mesos.config.configurator;

import org.apache.mesos.config.ConfigUtil;
import org.apache.mesos.config.FrameworkConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Takes system env variables adds them to the configuration.
 */
public class SystemEnvironmentConfigurator implements Configurator {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public static final String CONFIG_NAMESPACE = "env";

  @Override
  public void configure(FrameworkConfigurationService frameworkConfigurationService) {
    logger.info("Configuring System env into configuration service.");

    Map<String, String> map = System.getenv();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      frameworkConfigurationService.setValue(CONFIG_NAMESPACE,
        ConfigUtil.fromEnvVarNameToPropertyName(entry.getKey()), entry.getValue());
    }
  }
}
