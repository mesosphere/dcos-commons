package org.apache.mesos.util;

import com.google.common.base.Optional;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

/**
 * Utility for get environment variables and local working directory information.
 */
public final class Env {

  public static String get(final String key) {
    final Optional<String> opt = option(key);
    if (opt.isPresent()) {
      return opt.get();
    } else {
      throw new IllegalStateException(String.format("Environment variable %s is not defined", key));
    }
  }

  public static Optional<String> option(final String key) {
    return Optional.fromNullable(System.getenv(key));
  }

  public static Map<String, String> filterStartsWith(final String prefix, final boolean trimPrefix) {
    final Map<String, String> result = newHashMap();

    for (final Map.Entry<String, String> entry : System.getenv().entrySet()) {
      final String key = entry.getKey();
      if (key.startsWith(prefix)) {
        result.put(trimPrefix ? key.substring(prefix.length()) : key, entry.getValue());
      }
    }

    return result;
  }

  public static String workingDir(final String defaultFileName) {
    return System.getProperty("user.dir") + defaultFileName;
  }

  public static String osFromSystemProperty() {
    final String osName = System.getProperty("os.name").toLowerCase();
    final String os;
    if (osName.contains("mac") || osName.contains("darwin")) {
      os = "macosx";
    } else if (osName.contains("linux")) {
      os = "linux";
    } else {
      throw new IllegalArgumentException("Unknown OS " + osName);
    }
    return os;
  }
}
