package com.mesosphere.sdk.config;

import java.nio.charset.StandardCharsets;

import java.util.Objects;

import com.mesosphere.sdk.state.ConfigStoreException;

/**
 * This class implements the Configuration interface for configurations stored as Strings.
 */
public class StringConfiguration implements Configuration {

    /**
     * Factory which performs the inverse of {@link StringConfiguration#getBytes()}.
     */
    public static class Factory implements ConfigurationFactory<StringConfiguration> {
        @Override
        public StringConfiguration parse(byte[] bytes) throws ConfigStoreException {
            return new StringConfiguration(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    /**
     * Compares two StringConfigurations for equality.
     */
    public static class Comparator implements ConfigurationComparator<StringConfiguration> {
        @Override
        public boolean equals(StringConfiguration first, StringConfiguration second) {
            return first.equals(second);
        }
    }

    private final String config;

    public StringConfiguration(String config) {
        this.config = config;
    }

    @Override
    public byte[] getBytes() throws ConfigStoreException {
        return config.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toJsonString() {
        return String.format("{ \"string\": \"%s\" }", config.replace("\"", "\\\""));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StringConfiguration)) {
            return false;
        }
        return Objects.equals(config, ((StringConfiguration) o).config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config);
    }
}
