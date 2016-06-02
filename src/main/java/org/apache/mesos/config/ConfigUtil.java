package org.apache.mesos.config;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility for common operations used with configurations including
 * converting from env var format (MESOS_CONFIG_FOO) to property format
 * (mesos.config.foo).
 */
public class ConfigUtil {

  /**
   * there are some variables which are using the hyphen which isn't
   * a valid env var name.  This is the placeholder.
   */
  private static final String DASH_MARKER = "__DASH__";

  public static String fromEnvVarNameToPropertyName(String env) {
    String property = "";
    if (StringUtils.isNotEmpty(env)) {
      property = env.replace(DASH_MARKER, "-");
      property = property.toLowerCase().replace("_", ".");
    }
    return property;
  }

  public static String fromPropertyNameToEnvVarName(String property) {
    String env = "";
    if (StringUtils.isNotEmpty(property)) {
      env = property.replace("-", DASH_MARKER);
      env = env.replace(".", "_").toUpperCase();
    }
    return env;
  }

  public static Set<String> collectMissing(ConfigurationService configurationService, String[] required) {
    Set<String> missing = new HashSet<>();
    for (String property : required) {
      if (StringUtils.isBlank(configurationService.get(property))) {
        missing.add(property);
      }
    }
    return missing;
  }

  public static void assertAllRequiredProperties(ConfigurationService configurationService, String[] required) {
    Set<String> missing = collectMissing(configurationService, required);
    if (missing.size() > 0) {
      String message = "The following properties are required to be configured: " + missing;
      message += ". Consider using a configurator.";
      throw new ConfigurationException(message);
    }
  }

  /**
   * Returns the value for the provided {@code propertyName} from the root namespace of the
   * provided {@code configurationService}. The returned value will be converted to match
   * {@code defaultValue}'s type. Returns {@code defaultValue} if no value was found for
   * {@code propertyName}. If the value to be retrieved resides in a custom namespace, it may
   * instead be retrieved from {@code configurationService} manually
   *
   * @throws IllegalArgumentException if the value couldn't be converted to defaultValue's type
   */
  public static <T> T getTypedValue(ConfigurationService configurationService,
    String propertyName, T defaultValue) {
    Optional<T> value =
      parseConfigValue(configurationService.get(propertyName), defaultValue.getClass());
    return value.isPresent() ? value.get() : defaultValue;
  }

  public static Iterable<ConfigProperty> filter(Collection<ConfigProperty> properties,
    Predicate<ConfigProperty> predicate) {
    return Collections2.filter(properties, predicate);
  }

  /**
   * Converts the provided String {@code value} into an instance of the provided {@code type}, or
   * returns an empty Optional if the value is null, empty, or blank. Supported types are Strings,
   * Booleans, and Numbers (with specialization for Long and Integer).
   *
   * @throws IllegalArgumentException if the input string couldn't be parsed as the provided type
   * @returns an instance of the provided {@code type}, or {@code null} if the type is unsupported
   */
  @SuppressWarnings("unchecked")
  public static <T> Optional<T> parseConfigValue(String value, Class<?> type) {
    if (StringUtils.isBlank(value)) {
      return Optional.<T>absent();
    }
    Object returnValue = null;
    if (Boolean.class.isAssignableFrom(type)) {
      returnValue = BooleanUtils.toBoolean(value);
    } else if (Integer.class.isAssignableFrom(type)) {
      returnValue = Integer.parseInt(value);
    } else if (Long.class.isAssignableFrom(type)) {
      returnValue = Long.parseLong(value);
    } else if (Number.class.isAssignableFrom(type)) {
      returnValue = Double.parseDouble(value);
    } else if (String.class.isAssignableFrom(type)) {
      returnValue = value;
    }
    return Optional.fromNullable((T) returnValue);
  }
}
