package org.apache.mesos.specification;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.ConfigurationComparator;
import org.apache.mesos.config.ConfigurationFactory;
import org.apache.mesos.config.SerializationUtils;
import org.apache.mesos.offer.constrain.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This class is a default implementation of the ServiceSpecification interface.
 */
public class DefaultServiceSpecification implements ServiceSpecification {

    private static final Comparator COMPARATOR = new Comparator();
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Factory which performs the inverse of {@link DefaultServiceSpecification#getBytes()}.
     */
    public static class Factory implements ConfigurationFactory<ServiceSpecification> {

        /**
         * Subtypes to be registered by defaults. This list should include all
         * {@link PlacementRule}s that are included in the library.
         */
        private static final Collection<Class<?>> defaultRegisteredSubtypes = Arrays.asList(
                AgentRule.class,
                AndRule.class,
                AttributeRule.class,
                HostnameRule.class,
                MaxPerAttributeRule.class,
                NotRule.class,
                OrRule.class,
                TaskTypeRule.class);

        private final ObjectMapper objectMapper;

        /**
         * @see DefaultServiceSpecification#getFactoryInstance()
         */
        private Factory(Collection<Class<?>> additionalSubtypes) {
            objectMapper = SerializationUtils.registerDefaultModules(new ObjectMapper());
            for (Class<?> subtype : defaultRegisteredSubtypes) {
                objectMapper.registerSubtypes(subtype);
            }
            for (Class<?> subtype : additionalSubtypes) {
                objectMapper.registerSubtypes(subtype);
            }
        }

        @Override
        public ServiceSpecification parse(byte[] bytes) throws ConfigStoreException {
            try {
                return SerializationUtils.fromString(
                        new String(bytes, CHARSET), DefaultServiceSpecification.class, objectMapper);
            } catch (IOException e) {
                throw new ConfigStoreException(
                        "Failed to deserialize DefaultServiceSpecification from JSON: " + e.getMessage(), e);
            }
        }

        public static final Collection<Class<?>> getDefaultRegisteredSubtypes() {
            return defaultRegisteredSubtypes;
        }
    }

    /**
     * Comparer which checks for equality of {@link DefaultServiceSpecification}s.
     */
    public static class Comparator implements ConfigurationComparator<ServiceSpecification> {

        /**
         * Call {@link DefaultServiceSpecification#getComparatorInstance()} instead.
         */
        private Comparator() { }

        @Override
        public boolean equals(ServiceSpecification first, ServiceSpecification second) {
            return EqualsBuilder.reflectionEquals(first, second);
        }
    }

    private final String name;
    private final List<TaskSet> taskSets;

    @JsonCreator
    public DefaultServiceSpecification(
            @JsonProperty("name") String name,
            @JsonProperty("task_sets") List<TaskSet> taskSets) {
        this.name = name;
        this.taskSets = taskSets;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<TaskSet> getTaskSets() {
        return taskSets;
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
     *
     * @param additionalSubtypesToRegister any class subtypes which should be registered with
     *     Jackson for deserialization. any custom placement rule implementations must be provided
     */
    public static ConfigurationFactory<ServiceSpecification> getFactory(
            Collection<Class<?>> additionalSubtypesToRegister) {
        return new Factory(additionalSubtypesToRegister);
    }

    /**
     * Returns a {@link ConfigurationComparer} which may be used to compare
     * {@link DefaultServiceSpecification}s.
     */
    public static ConfigurationComparator<ServiceSpecification> getComparatorInstance() {
        return COMPARATOR;
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
