package com.mesosphere.sdk.framework;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

/**
 * Utility class for grabbing values from a mapping of flag values (typically the process env).
 */
public class EnvStore {
    /**
     * Exception which is thrown when failing to retrieve or parse a given flag value.
     */
    public static class ConfigException extends RuntimeException {

        /**
         * A machine-accessible error type.
         */
        public enum Type {
            UNKNOWN,
            NOT_FOUND,
            INVALID_VALUE
        }

        private static ConfigException notFound(String message) {
            return new ConfigException(Type.NOT_FOUND, message);
        }

        private static ConfigException invalidValue(String message) {
            return new ConfigException(Type.INVALID_VALUE, message);
        }

        private final Type type;

        private ConfigException(Type type, String message) {
            super(message);
            this.type = type;
        }

        public Type getType() {
            return type;
        }

        @Override
        public String getMessage() {
            return String.format("%s (errtype: %s)", super.getMessage(), type);
        }
    }

    private final Map<String, String> envMap;

    public static EnvStore fromEnv() {
        return new EnvStore(System.getenv());
    }

    public static EnvStore fromMap(Map<String, String> envMap) {
        return new EnvStore(envMap);
    }

    EnvStore(Map<String, String> envMap) {
        this.envMap = new HashMap<>(envMap);
    }

    public int getOptionalInt(String envKey, int defaultValue) {
        return toInt(envKey, getOptional(envKey, String.valueOf(defaultValue)));
    }

    public long getOptionalLong(String envKey, long defaultValue) {
        return toLong(envKey, getOptional(envKey, String.valueOf(defaultValue)));
    }

    public double getOptionalDouble(String envKey, double defaultValue) {
        return toDouble(envKey, getOptional(envKey, String.valueOf(defaultValue)));
    }

    /**
     * List of comma-separated strings. Any whitespace is cleaned up automatically.
     */
    public List<String> getOptionalStringList(String envKey, List<String> defaultValue) {
        return toStringList(envKey, getOptional(envKey, Joiner.on(',').join(defaultValue)));
    }

    public boolean getOptionalBoolean(String envKey, boolean defaultValue) {
        return toBoolean(envKey, getOptional(envKey, String.valueOf(defaultValue)));
    }

    public int getRequiredInt(String envKey) {
        return toInt(envKey, getRequired(envKey));
    }

    public long getRequiredLong(String envKey) {
        return toLong(envKey, getRequired(envKey));
    }

    public double getRequiredDouble(String envKey) {
        return toDouble(envKey, getRequired(envKey));
    }

    /**
     * Returns the requested value if set, or {@code defaultValue} if it's missing from the map entirely.
     */
    public String getOptional(String envKey, String defaultValue) {
        String value = envMap.get(envKey);
        return (value == null) ? defaultValue : value;
    }

    /**
     * Returns the requested value if set and non-empty, or {@code defaultValue} if it's missing from the map or is
     * empty or whitespace.
     */
    public String getOptionalNonEmpty(String envKey, String defaultValue) {
        String value = envMap.get(envKey);
        return (StringUtils.isBlank(value)) ? defaultValue : value;
    }

    /**
     * Returns the requested value if set, or throws an exception if it's missing from the map entirely.
     */
    public String getRequired(String envKey) {
        String value = envMap.get(envKey);
        if (value == null) {
            throw ConfigException.notFound(String.format("Missing required environment variable: %s", envKey));
        }
        return value;
    }

    public boolean isPresent(String envKey) {
        return envMap.containsKey(envKey);
    }

    /**
     * If the value cannot be parsed as an int, this points to the source envKey, and ensures that calls only throw
     * {@link ConfigException}.
     */
    private static int toInt(String envKey, String envVal) {
        try {
            return Integer.parseInt(envVal);
        } catch (NumberFormatException e) {
            throw ConfigException.invalidValue(String.format(
                    "Failed to parse configured environment variable '%s' as an integer: %s", envKey, envVal));
        }
    }

    /**
     * If the value cannot be parsed as a long, this points to the source envKey, and ensures that calls only throw
     * {@link ConfigException}.
     */
    private static long toLong(String envKey, String envVal) {
        try {
            return Long.parseLong(envVal);
        } catch (NumberFormatException e) {
            throw ConfigException.invalidValue(String.format(
                    "Failed to parse configured environment variable '%s' as a long integer: %s", envKey, envVal));
        }
    }

    /**
     * If the value cannot be parsed as a double, this points to the source envKey, and ensures that calls only throw
     * {@link ConfigException}.
     */
    private static double toDouble(String envKey, String envVal) {
        try {
            return Double.parseDouble(envVal);
        } catch (NumberFormatException e) {
            throw ConfigException.invalidValue(String.format(
                    "Failed to parse configured environment variable '%s' as a double: %s", envKey, envVal));
        }
    }

    private static boolean toBoolean(String envKey, String envVal) {
        if (envVal.trim().isEmpty()) {
            // Treat empty or whitespace-only envvar as false
            return false;
        }
        switch (envVal.trim().charAt(0)) {
        case 't':
        case 'T':
        case 'y':
        case 'Y':
            // true: "[tT]rue" and "[yY]es"
            return true;
        case 'f':
        case 'F':
        case 'n':
        case 'N':
            // false: "[fF]alse" and "[nN]o"
            return false;
        default:
            throw ConfigException.invalidValue(String.format(
                    "Failed to parse configured environment variable '%s' as a boolean: %s", envKey, envVal));
        }
    }

    private static List<String> toStringList(String envKey, String envVal) {
        return Splitter.on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(envVal);
    }
}
