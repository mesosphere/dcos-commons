package org.apache.mesos.config.io;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.config.Configuration;
import org.apache.mesos.config.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Provides common logic and error handling for services needing to
 * read the config.yaml.
 */
public class ConfigurationReader {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public static final String DEFAULT_CONFIG_FILENAME = "config.yaml";
  private String fileName = DEFAULT_CONFIG_FILENAME;
  private Configuration configuration = null;

  public ConfigurationReader() {
    this(DEFAULT_CONFIG_FILENAME);
  }

  public ConfigurationReader(String fileName) {
    if (StringUtils.isNotEmpty(fileName)) {
      this.fileName = fileName;
    }
  }

  public Configuration getConfiguration() {
    if (configuration == null) {
      logger.info("Configuring: reading configuration file: " + fileName);
      YamlConfigurationReader reader = new YamlConfigurationReader(fileName);
      try {
        configuration = reader.read(Configuration.class);
      } catch (IOException e) {
        throw new ConfigurationException("Issue reading YAML file: " + fileName, e);
      }
    }
    return configuration;
  }
}
