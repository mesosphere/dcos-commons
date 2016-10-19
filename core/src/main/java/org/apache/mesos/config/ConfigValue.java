package org.apache.mesos.config;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Wrapper around a string value, providing convenience functions for converting the string to
 * various types.
 */
public class ConfigValue {

    private final String debugName;
    private final String value; // Nullable

    /**
     * Creates a new configuration value.
     *
     * @param debugName debug label describing the origin of this value (e.g. the full originating
     *                environment variable with any prefixes)
     * @param value the value to be interpreted, or {@code null} if the value isn't available/set
     */
    public ConfigValue(String debugName, /* Nullable */ String value) {
        this.debugName = debugName;
        this.value = value;
    }

    // Required value: Throws if missing from env.

    /**
     * Returns the wrapped value as a {@link String}.
     *
     * @throws IllegalStateException if the value is missing
     */
    public String requiredString() {
        if (value == null) {
            throw missingException();
        }
        return value;
    }

    /**
     * Returns the wrapped value as a {@code boolean}.
     *
     * Mapping logic is as follows:
     * - t[rue]/T[RUE]/y[es]/Y[ES]/1 => true
     * - all others (including empty string) => false
     *
     * @throws IllegalStateException if the value is missing
     */
    public boolean requiredBoolean() {
        if (value == null) {
            throw missingException();
        }
        if (value.isEmpty()) {
            return false; // value set to empty. assume this means false
        }
        switch (value.charAt(0)) {
        case 't': // true
        case 'T': // TRUE
        case 'y': // yes
        case 'Y': // YES
        case '1': // one
          return true;
        default:
          return false;
        }
    }

    /**
     * Returns the wrapped value as an {@code int}.
     *
     * @throws IllegalStateException if the value is missing
     * @throws NumberFormatException if the value could not be parsed as an {@code int}
     */
    public int requiredInt() {
        if (value == null) {
            throw missingException();
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw formatException("integer");
        }
    }

    /**
     * Returns the wrapped value as a {@code long}.
     *
     * @throws IllegalStateException if the value is missing
     * @throws NumberFormatException if the value could not be parsed as a {@code long}
     */
    public long requiredLong() {
        if (value == null) {
            throw missingException();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw formatException("integer");
        }
    }

    /**
     * Returns the wrapped value as a {@code double}.
     *
     * @throws IllegalStateException if the value is missing
     * @throws NumberFormatException if the value could not be parsed as a {@code double}
     */
    public double requiredDouble() {
        if (value == null) {
            throw missingException();
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw formatException("double");
        }
    }

    // Optional value: Returns the provided default if missing from env.

    /**
     * Returns the wrapped value as a {@link String}, or returns the provided {@code defaultValue}
     * if the value is missing.
     */
    public String optionalString(String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return requiredString();
    }

    /**
     * Returns the wrapped value as a {@code boolean}, or returns the provided {@code defaultValue}
     * if the value is missing.
     *
     * Mapping logic is as follows:
     * - t[rue]/T[RUE]/y[es]/Y[ES]/1 => true
     * - all others (including empty string) => false
     */
    public boolean optionalBoolean(boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return requiredBoolean();
    }

    /**
     * Returns the wrapped value as an {@code int}, or returns the provided {@code defaultValue}
     * if the value is missing.
     *
     * @throws NumberFormatException if the value could not be parsed as an {@code int}
     */
    public int optionalInt(int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return requiredInt();
    }

    /**
     * Returns the wrapped value as a {@code long}, or returns the provided {@code defaultValue}
     * if the value is missing.
     *
     * @throws NumberFormatException if the value could not be parsed as a {@code long}
     */
    public long optionalLong(long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return requiredLong();
    }

    /**
     * Returns the wrapped value as a {@code double}, or returns the provided {@code defaultValue}
     * if the value is missing.
     *
     * @throws NumberFormatException if the value could not be parsed as a {@code double}
     */
    public double optionalDouble(double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return requiredDouble();
    }

    /**
     * Returns a debug string describing the string value and the original envvar name that it came
     * from.
     */
    @Override
    public String toString() {
        return String.format("%s=%s", debugName, value);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    private NumberFormatException formatException(String typeName) {
        return new NumberFormatException(
                String.format("Unable to parse envvar '%s' as %s: %s", debugName, typeName, value));
    }

    private IllegalStateException missingException() {
        return new IllegalStateException(
                String.format("Missing required envvar '%s'", debugName));
    }
}
