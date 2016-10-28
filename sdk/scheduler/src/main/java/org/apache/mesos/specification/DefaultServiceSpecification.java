package org.apache.mesos.specification;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.ConfigurationFactory;
import org.apache.mesos.config.SerializationUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class is a default implementation of the ServiceSpecification interface.
 */
public class DefaultServiceSpecification implements ServiceSpecification {

    private static final Factory FACTORY = new Factory();
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Factory which performs the inverse of {@link DefaultServiceSpecification#getBytes()}.
     */
    public static class Factory implements ConfigurationFactory<ServiceSpecification> {

        /**
         * Call {@link DefaultServiceSpecification#getFactoryInstance()} instead.
         */
        private Factory() { }

        @Override
        public ServiceSpecification parse(byte[] bytes) throws ConfigStoreException {
            try {
                return SerializationUtils.fromJsonString(new String(bytes, CHARSET), DefaultServiceSpecification.class);
            } catch (IOException e) {
                throw new ConfigStoreException(
                        "Failed to deserialize DefaultServiceSpecification from JSON: " + e.getMessage(), e);
            }
        }
    }

    private final String name;
    private final List<PodSet> podSets;

    @JsonCreator
    public DefaultServiceSpecification(
            @JsonProperty("name") String name,
            @JsonProperty("pod_sets") List<PodSet> podSets) {
        this.name = name;
        this.podSets = podSets;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<PodSet> getPodSets() {
        return podSets;
    }

    @Override
    public byte[] getBytes() throws ConfigStoreException {
        try {
            return toJsonString().getBytes(CHARSET);
        } catch (Exception e) {
            throw new ConfigStoreException("Failed to get JSON representation of service spec: " + e.getMessage(), e);
        }
    }

    @Override
    public String toJsonString() throws Exception {
        return SerializationUtils.toJsonString(this);
    }

    /**
     * Returns a {@link ConfigurationFactory} which may be used to deserialize
     * {@link DefaultServiceSpecification}s.
     */
    public static ConfigurationFactory<ServiceSpecification> getFactoryInstance() {
        return FACTORY;
    }

    @Override
    public String toString() {
        try {
            return toJsonString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
