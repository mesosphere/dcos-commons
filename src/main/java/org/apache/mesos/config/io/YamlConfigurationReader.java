package org.apache.mesos.config.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * YAML configuration file reader.  It is generic and expects to be handled
 * a class that has jackson json annotations.
 */
public class YamlConfigurationReader {
  private final Log log = LogFactory.getLog(YamlConfigurationReader.class);

  String fileName;

  /**
   * @param fileName is expected to be on the classpath
   */
  public YamlConfigurationReader(String fileName) {
    this.fileName = fileName;
  }

  public <T> T read(Class<T> clazz) throws IOException {

    InputStream ios;
    ios = this.getClass().getClassLoader().getResourceAsStream(fileName);
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    return mapper.readValue(ios, clazz);
  }

  public <T> T readQuiet(Class<T> clazz) {
    T value;
    try {
      value = read(clazz);
    } catch (IOException e) {
      log.error("failed to read yaml: " + e.getMessage());
      value = null;
    }
    if (value == null) {
      try {
        value = clazz.newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
        value = null;
      }
    }
    return value;
  }
}
