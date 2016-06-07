package org.apache.mesos.storage;

import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.protobuf.LabelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Stores all persistent state relating to active and old configurations, in the form of serialized
 * JSON objects.
 */
public class ConfigStorage {

  private static final Logger logger = LoggerFactory.getLogger(ConfigStorage.class);

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
          .registerModule(new GuavaModule())
          .registerModule(new Jdk8Module());

  /**
   * Used both for storing the current target in ZK, as well as labeling TaskInfos with the target
   * they're running against.
   */
  private static final String CONFIG_TARGET_KEY = "config_target";

  /**
   * Storage node which points to the currently active target configuration.
   */
  private static final String CONFIG_TARGET_FILE = "/" + CONFIG_TARGET_KEY;

  /**
   * Storage node which contains all configurations: both old configs and the current target config.
   */
  private static final String CONFIG_DIR = "/configurations/versions";

  private final StorageAccess storageAccess;

  public ConfigStorage(StorageAccess storageAccess) {
    this.storageAccess = storageAccess;
  }

  /**
   * Returns the config target which this {@link TaskInfo} is configured with, or {@code null} if
   * no config target could be found.
   */
  public static String getConfigName(TaskInfo taskInfo) {
    for (Label label : taskInfo.getLabels().getLabelsList()) {
      if (label.getKey().equals(CONFIG_TARGET_KEY)) {
        return label.getValue();
      }
    }
    return null;
  }

  /**
   * Returns a copy of the provided {@link TaskInfo} which has been updated to use the provided
   * config.
   */
  public static TaskInfo withConfigName(
      TaskInfo taskInfo, String newConfigName) {
    LabelBuilder labelBuilder = new LabelBuilder();
    if (taskInfo.hasLabels()) {
      // Copy everything except config target label
      for (Label label : taskInfo.getLabels().getLabelsList()) {
        String key = label.getKey();
        if (key.equals(ConfigStorage.CONFIG_TARGET_KEY)) {
          // pass through
          labelBuilder.addLabel(key, label.getValue());
        }
      }
    }
    // replace (or insert) config target value
    labelBuilder.addLabel(ConfigStorage.CONFIG_TARGET_KEY, newConfigName);
    return TaskInfo.newBuilder().setLabels(labelBuilder.builder()).build();
  }

  /**
   * Returns the name of the currently active target configuration, or {@null} if none could be
   * found.
   */
  public String getTargetConfigName() throws StorageException {
    byte[] data = storageAccess.get(CONFIG_TARGET_FILE);
    try {
      return new String(data, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new StorageException("Unable to decode '" + Arrays.toString(data) + "' as UTF-8", e);
    }
  }

  /**
   * Stores the provided configuration and marks it as the new active target configuration, then
   * returns the result.
   *
   * @throws StorageException if the update didn't succeed
   */
  public <CONFIG> CONFIG updateAndGetTargetConfig(
      CONFIG newTargetConfig, Class<CONFIG> clazz, TaskStorage taskStorageToClean)
          throws StorageException {
    if (!storageAccess.exists(CONFIG_TARGET_FILE)) {
      // Initializing config from scratch. No cleanup needed.
      logger.info("Initializing config properties storage with new target.");
      storeTargetConfig(newTargetConfig);
    } else {
      String currTargetName = getTargetConfigName();
      if (currTargetName == null) {
        throw new StorageException("Couldn't retrieve current target name");
      }
      CONFIG currTargetConfig = getConfig(currTargetName, clazz);
      if (currTargetConfig == null) {
        throw new StorageException(String.format("Couldn't retrieve current config for target '%s'",
            currTargetName));
      }
      logger.info("Old config: " + currTargetConfig);
      logger.info("New config: " + newTargetConfig);

      if (currTargetConfig.equals(newTargetConfig)) {
        logger.info("No config properties changes detected.");
      } else {
        // Replacing an existing config. Use this opportunity to clean up any dangling config state.
        logger.info("Config change detected!");
        cleanOldAndDuplicateConfigs(taskStorageToClean, storeTargetConfig(newTargetConfig), clazz);
      }
    }
    return newTargetConfig;
  }

  /**
   * Returns the requested configuration named {@code configName}.
   */
  public <CONFIG> CONFIG getConfig(String configName, Class<CONFIG> clazz) throws StorageException {
    byte[] data = storageAccess.get(toConfigPath(configName));
    logger.info("Deserializing config '{}': {} bytes", configName, data.length);
    try {
      return JSON_MAPPER.readValue(data, clazz);
    } catch (Exception e) {
      throw new StorageException(String.format(
          "Failed to deserialize configuration '%s' from Zookeeper path '%s'",
          configName, toConfigPath(configName)), e);
    }
  }

  // ---

  /**
   * Stores the provided configuration against a new version name, and updates the target reference
   * file to point to that name.
   *
   * @return the new target config's name
   * @throws StorageException if storage failed
   */
  private <CONFIG> String storeTargetConfig(CONFIG configuration) throws StorageException {
    // Serialize the data
    byte[] data;
    try {
      data = JSON_MAPPER.writeValueAsBytes(configuration);
    } catch (Exception e) {
      throw new StorageException(
          "Failed to serialize new target configuration: " + configuration, e);
    }

    // Store the serialized data
    String targetConfigName = UUID.randomUUID().toString();
    String configPath = toConfigPath(targetConfigName);
    logger.info("Storing new configuration {} ({} bytes)", targetConfigName, data.length);
    storageAccess.set(configPath, data);

    // Then update the target pointer
    try {
      data = targetConfigName.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new StorageException(
          "Unable to encode '" + targetConfigName + "' as UTF-8", e);
    }
    logger.info("Assigning target config to '%s'", targetConfigName);
    storageAccess.set(CONFIG_TARGET_FILE, data);
    return targetConfigName;
  }

  /**
   * Finds any configs which are one of the following:
   * 1. Identical to the provided 'targetConfig'.
   * 2. Not referenced by any running {@link TaskInfo}s.
   *
   * Each of these configs is then deleted in favor of 'targetConfig'.
   * For case 1, any {@link TaskInfo}s running against duplicates are updated to reflect the change.
   *
   * @param taskStorageToClean Storage containing {@link TaskInfo}s to be cleaned as needed.
   * @param targetName The name of the (newly written) target config
   * @param clazz The type of the config
   */
  private <CONFIG> void cleanOldAndDuplicateConfigs(
      TaskStorage taskStorageToClean, String targetName, Class<CONFIG> clazz)
          throws StorageException {
    // Build a list of configs which are duplicates of the target.
    CONFIG targetConfig = getConfig(targetName, clazz);
    Set<String> duplicateConfigNames = new HashSet<String>();
    for (String configName : getAllConfigNames()) {
      if (configName.equals(targetName)) {
        continue;
      }
      CONFIG otherConfig = getConfig(configName, clazz);
      if (targetConfig.equals(otherConfig)) {
        logger.info("Found duplicate of target config '{}': {}", targetName, configName);
        duplicateConfigNames.add(configName);
      }
    }

    // Update TaskInfos via TaskStorage to no longer reference duplicate configs, then delete any
    // configs which are no longer referenced by the tidied-up TaskInfos.
    Set<String> activeConfigs = new HashSet<String>();
    activeConfigs.add(getTargetConfigName());
    for (TaskInfo taskInfo
        : taskStorageToClean.replaceTargetConfigs(duplicateConfigNames, targetName)) {
      activeConfigs.add(getConfigName(taskInfo));
    }

    logger.info("Cleaning all configs which are NOT in the active list: {}", activeConfigs);
    for (String configName : getAllConfigNames()) {
      if (!activeConfigs.contains(configName)) {
        logger.info("Removing stale config '{}'", configName);
        storageAccess.delete(toConfigPath(configName));
      }
    }
  }

  private List<String> getAllConfigNames() throws StorageException {
    return storageAccess.list(CONFIG_DIR);
  }

  private String toConfigPath(String configName) {
    return String.format("%s/%s", CONFIG_DIR, configName);
  }
}
