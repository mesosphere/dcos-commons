package org.apache.mesos.config;

import java.nio.charset.StandardCharsets;

import java.util.Objects;

/**
 * This class implements the Configuration interface for configurations stored as Strings.
 */
public class StringConfiguration implements Configuration {
    private String config;

    public StringConfiguration(String config) {
        this.config = config;
    }

    @Override
    public byte[] getBytes() throws ConfigStoreException {
        return config.getBytes(StandardCharsets.UTF_8);
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
