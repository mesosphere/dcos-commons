package org.apache.mesos.config.state;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.mesos.config.ConfigJsonSerializer;
import org.apache.mesos.config.ConfigProperty;
import org.apache.mesos.config.FrameworkConfigurationService;
import org.apache.mesos.state.StateStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.mesos.config.FrameworkConfigurationService.CONFIGURATION_VERSION_PROPERTY;
import static org.apache.mesos.config.FrameworkConfigurationService.IGNORED_NAMESPACE;


/**
 * This provides access to the Config State Store.  In this case, it is Zookeeper.
 */
public class ConfigState {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private String zkConfigRootPath;
  private String zkVersionPath;
  private String zkComponentPath;

  private CuratorFramework zkCuratorStore;
  private ConfigJsonSerializer serializer;

  private static final String COMPONENT_PATH = "components";

  public ConfigState(String frameworkname, String rootZkPath, CuratorFramework curatorFramework) {
    zkConfigRootPath = rootZkPath + frameworkname + "/configurations/";
    zkVersionPath = zkConfigRootPath + "versions";
    zkComponentPath = zkConfigRootPath + COMPONENT_PATH;
    this.zkCuratorStore = curatorFramework;
    serializer = new ConfigJsonSerializer();
  }

  public void store(FrameworkConfigurationService configurationService, String version) throws StateStoreException {
    // always save the config verison with the config
    configurationService.setValue(IGNORED_NAMESPACE, CONFIGURATION_VERSION_PROPERTY, version);
    ensurePath(getVersionPath(version));
    try {
      final byte[] data = serializer.serialize(configurationService.getNsPropertyMap());
      logDataSize(data);
      zkCuratorStore.setData().forPath(getVersionPath(version), data);
    } catch (Exception e) {
      String msg = "Failure to store configurations.";
      logger.error(msg);
      throw new StateStoreException(msg, e);
    }
  }

  public void clear(String version) throws StateStoreException {
    try {
      zkCuratorStore.delete().forPath(getVersionPath(version));
    } catch (Exception e) {
      String msg = "Failure to delete configuration: " + version;
      logger.error(msg);
      throw new StateStoreException(msg, e);
    }
  }

  public List<String> getVersions() throws StateStoreException {
    try {
      return zkCuratorStore.getChildren().forPath(zkVersionPath);
    } catch (Exception e) {
      String msg = "Failure to get configuration versions.";
      logger.error(msg);
      throw new StateStoreException(msg, e);
    }
  }

  private String getVersionPath(String version) {
    return zkVersionPath + "/" + version;
  }

  public Map<String, Map<String, ConfigProperty>> fetch(String version) throws StateStoreException {
    return fetchForPath(getVersionPath(version));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Map<String, ConfigProperty>> fetchForPath(String path) {
    try {
      byte[] data = zkCuratorStore.getData().forPath(path);
      if (data == null) {
        return null;
      } else {
        logDataSize(data);
        Object obj = serializer.deserialize(data);
        return (Map<String, Map<String, ConfigProperty>>) obj;
      }
    } catch (Exception e) {
      String msg = "Failure to store configurations.";
      logger.error(msg);
      throw new StateStoreException(msg, e);
    }
  }

  private void logDataSize(byte[] data) {
    logger.info(String.format("Size of config to serialize: %s b.", data.length));
  }

  public String fetchCurrentVersion() {
    return fetchVersionForPath(zkConfigRootPath + CONFIGURATION_VERSION_PROPERTY);
  }

  public void storeVersion(String version) {
    ensurePath(zkConfigRootPath + CONFIGURATION_VERSION_PROPERTY);
    storeForPath(version, zkConfigRootPath + CONFIGURATION_VERSION_PROPERTY);
  }

  public void storeVersionForPath(String version, String lookup) {
    ensurePath(zkConfigRootPath + lookup);
    storeForPath(version, zkConfigRootPath + lookup);
  }

  public void storeVersionForComponent(String version, String component) {
    storeVersionForPath(version, COMPONENT_PATH + "/" + component);
  }

  private void ensurePath(String path) {
    try {
      zkCuratorStore.create().creatingParentsIfNeeded().forPath(path);
    } catch (Exception e) {
      logger.debug(String.format("Path for %s already exists!", path));
    }
  }

  private void storeForPath(String version, String path) {
    try {
      zkCuratorStore.setData().forPath(path, version.getBytes(Charset.defaultCharset()));
    } catch (Exception e) {
      String msg = "Failure to store configuration version.";
      logger.error(msg);
      throw new StateStoreException(msg, e);
    }
  }

  /**
   * Used by different components such as an executor or a task to look up the
   * version number for the configuration they should be using.
   *
   * @param lookup
   * @return
   */
  public String fetchVersionForLookup(String lookup) {
    return fetchVersionForPath(zkConfigRootPath + lookup);
  }

  private String fetchVersionForPath(String path) {
    String defaultVersion = "";  // 0 is a non configured value
    try {
      byte[] data = zkCuratorStore.getData().forPath(path);
      if (data == null) {
        return defaultVersion;
      } else {
        String version = new String(data, Charset.defaultCharset());
        return version;
      }
    } catch (Exception e) {
      String msg = "Failure to fetch the current configuration version for path:" + path;
      logger.error(msg);
      return defaultVersion;
    }
  }

  public String fetchVersionForComponent(String component) {
    return fetchVersionForPath(zkComponentPath + "/" + component);
  }

  public Map<String, Map<String, ConfigProperty>> fetchForComponent(String component) throws StateStoreException {
    return fetch(fetchVersionForComponent(component));
  }

  public void updateAllTasksToVersion(String version) {
    logger.info("Changing tasks to version: " + version);
    if (StringUtils.isNotBlank(version)) {
      for (String component : getComponentPointers()) {
        storeVersionForComponent(version, component);
      }
    }
  }

  public List<String> getComponentPointers() {
    try {
      return zkCuratorStore.getChildren().forPath(zkComponentPath);
    } catch (Exception e) {
      logger.error("Unable to retrieve component pointers.", e);
      return new ArrayList<>();
    }
  }
}
