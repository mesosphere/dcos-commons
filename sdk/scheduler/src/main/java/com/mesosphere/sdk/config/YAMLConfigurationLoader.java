package com.mesosphere.sdk.config;

import com.mesosphere.sdk.offer.LoggingUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class that helps load YAML configuration into a POJO, while ensuring that environment
 * variables can override pre-configured configuration values.
 */
public final class YAMLConfigurationLoader {
  private static final Logger LOGGER = LoggingUtils.getLogger(YAMLConfigurationLoader.class);

  private YAMLConfigurationLoader() {
  }

  public static <T> T loadConfigFromEnv(Class<T> configurationClass, final String path)
      throws IOException
  {
    LOGGER.info("Parsing configuration file from {} ", path);
    logProcessEnv();
    final Path configPath = Paths.get(path);
    final File file = configPath.toAbsolutePath().toFile();
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    final StrSubstitutor sub = new StrSubstitutor(new StrLookup<Object>() {
      @Override
      public String lookup(String key) {
        return System.getenv(key);
      }
    });
    sub.setEnableSubstitutionInVariables(true);

    final String conf = sub.replace(FileUtils.readFileToString(file));
    return mapper.readValue(conf, configurationClass);
  }

  private static void logProcessEnv() {
    LOGGER.info("Process environment:");
    System.getenv().forEach((key, value) -> LOGGER.info("{} = {}", key, value));
  }
}
