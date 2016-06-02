package org.apache.mesos.config.state;

import org.apache.curator.framework.CuratorFramework;
import org.apache.mesos.config.ConfigUtil;
import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.state.CuratorStateFactory;

/**
 * The factory for creating config state stores.
 */
public class ConfigStateFactory {

  private static final String FRAMEWORK_NAME_KEY = "framework.name";

  private static final String[] REQUIRED_PROPERTIES = {FRAMEWORK_NAME_KEY};

  public ConfigState getConfigState(ConfigurationService configurationService, String frameworkRootPath) {

    CuratorStateFactory stateFactory = new CuratorStateFactory();
    CuratorFramework curatorFramework = stateFactory.getCurator(configurationService);

    ConfigUtil.assertAllRequiredProperties(configurationService, REQUIRED_PROPERTIES);

    return new ConfigState(configurationService.get(FRAMEWORK_NAME_KEY),
      frameworkRootPath, curatorFramework);
  }
}
