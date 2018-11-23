package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Utility methods around construction of loggers.
 */
public final class LoggingUtils {

  private LoggingUtils() {
  }

  /**
   * Creates a logger which is tagged with the provided class.
   *
   * @param clazz the class using this logger
   */
  public static Logger getLogger(Class<?> clazz) {
    return LoggerFactory.getLogger(getClassName(clazz));
  }

  /**
   * Creates a logger which is tagged with the provided class and the provided custom label.
   *
   * @param clazz the class using this logger
   * @param name  an additional context label detailing e.g. the name of the service being managed
   */
  public static Logger getLogger(Class<?> clazz, String name) {
    if (StringUtils.isBlank(name)) {
      return getLogger(clazz);
    } else {
      return LoggerFactory.getLogger(String.format("(%s) %s", name, getClassName(clazz)));
    }
  }

  /**
   * Creates a logger which is tagged with the provided class and optionally the provided custom label.
   */
  public static Logger getLogger(Class<?> clazz, Optional<String> name) {
    return name.isPresent() ? getLogger(clazz, name.get()) : getLogger(clazz);
  }

  /**
   * Returns a class name suitable for using in logs.
   *
   * <p>At the moment this results in a class name of just e.g. "{@code LoggingUtils}".
   */
  private static String getClassName(Class<?> clazz) {
    return clazz.getSimpleName();
  }
}
