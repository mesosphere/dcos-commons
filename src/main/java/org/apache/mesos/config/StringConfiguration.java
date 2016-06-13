package org.apache.mesos.config;

/**
 * This class implements the Configuration interface for configurations stored as Strings.
 */
public class StringConfiguration implements Configuration {
    private String config;

    public StringConfiguration(String config) {
        this.config = config;
    }

    @Override
    public byte[] getBytes() {
        return config.getBytes();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringConfiguration that = (StringConfiguration) o;

        return config != null ? config.equals(that.config) : that.config == null;

    }

    @Override
    public int hashCode() {
        return config != null ? config.hashCode() : 0;
    }
}
