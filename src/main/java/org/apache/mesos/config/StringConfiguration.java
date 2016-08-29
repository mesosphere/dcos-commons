package org.apache.mesos.config;

import org.json.JSONObject;

import java.io.*;
import java.util.Map;
import java.util.Optional;

/**
 * This class implements the Configuration interface for configurations stored as Strings.
 */
public class StringConfiguration implements Configuration<String, String> {

    /**
     * Factory which performs the inverse of {@link StringConfiguration#getBytes()}.
     *
     */
    public static class Factory implements ConfigurationFactory<StringConfiguration> {
        @Override
        public StringConfiguration parse(byte[] bytes) throws ConfigStoreException {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            try {
                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
                Map<String, String> map = (Map<String, String>) objectInputStream.readObject();
                return new StringConfiguration(map);
            } catch (ClassNotFoundException | IOException e) {
                throw new ConfigStoreException(e);
            }
        }
    }

    private final Map<String, String> configuration;

    public StringConfiguration(Map<String, String> configuration) {
        this.configuration = configuration;
    }

    @Override
    public byte[] getBytes() throws ConfigStoreException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = null;
        try {
            objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(configuration);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new ConfigStoreException(e);
        }
    }

    @Override
    @SuppressWarnings("PMD.IfStmtsMustUseBraces")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringConfiguration that = (StringConfiguration) o;

        return configuration != null ? configuration.equals(that.configuration) : that.configuration == null;

    }

    @Override
    public int hashCode() {
        return configuration != null ? configuration.hashCode() : 0;
    }

    @Override
    public String toJsonString() {
        return new JSONObject(configuration).toString();
    }

    @Override
    public Optional<String> get(String key) {
        String value = configuration.get(key);
        if (value != null) {
            return Optional.of(value);
        } else {
            return Optional.empty();
        }
    }
}
