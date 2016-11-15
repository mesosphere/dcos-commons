package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.ConfigurationComparator;
import org.apache.mesos.config.ConfigurationFactory;
import org.apache.mesos.config.SerializationUtils;
import org.apache.mesos.offer.constrain.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Default implementation of {@link ServiceSpec}.
 */
public class DefaultServiceSpec implements ServiceSpec {
    private static final Comparator COMPARATOR = new Comparator();

    private String name;
    private String role;
    private String principal;
    private int apiPort;
    private String zookeeperConnection;
    private List<PodSpec> pods;
    private ReplacementFailurePolicy replacementFailurePolicy;

    @JsonCreator
    public DefaultServiceSpec(
            @JsonProperty("name") String name,
            @JsonProperty("role") String role,
            @JsonProperty("principal") String principal,
            @JsonProperty("api_port") int apiPort,
            @JsonProperty("zookeeper") String zookeeperConnection,
            @JsonProperty("pod_specs") List<PodSpec> pods,
            @JsonProperty("replacement_failure_policy") ReplacementFailurePolicy replacementFailurePolicy) {
        this.name = name;
        this.role = role;
        this.principal = principal;
        this.apiPort = apiPort;
        this.zookeeperConnection = zookeeperConnection;
        this.pods = pods;
        this.replacementFailurePolicy = replacementFailurePolicy;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public String getPrincipal() {
        return principal;
    }

    @Override
    public int getApiPort() {
        return apiPort;
    }

    @Override
    public String getZookeeperConnection() {
        return zookeeperConnection;
    }

    @Override
    public List<PodSpec> getPods() {
        return pods;
    }

    @Override
    public ReplacementFailurePolicy getReplacementFailurePolicy() {
        return replacementFailurePolicy;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * Returns a {@link ConfigurationComparer} which may be used to compare
     * {@link DefaultServiceSpecification}s.
     */
    public static ConfigurationComparator<ServiceSpec> getComparatorInstance() {
        return COMPARATOR;
    }

    /**
     * Comparer which checks for equality of {@link DefaultServiceSpecification}s.
     */
    public static class Comparator implements ConfigurationComparator<ServiceSpec> {

        /**
         * Call {@link DefaultServiceSpecification#getComparatorInstance()} instead.
         */
        private Comparator() {
        }

        @Override
        public boolean equals(ServiceSpec first, ServiceSpec second) {
            return EqualsBuilder.reflectionEquals(first, second);
        }
    }

    /**
     * Returns a {@link ConfigurationFactory} which may be used to deserialize
     * {@link DefaultServiceSpecification}s, which has been confirmed to successfully and
     * consistently serialize/deserialize the provided {@code ServiceSpecification} instance.
     *
     * @param serviceSpec                  specification to test for successful serialization/deserialization
     * @param additionalSubtypesToRegister any class subtypes which should be registered with
     *                                     Jackson for deserialization. any custom placement rule implementations
     *                                     must be provided
     * @throws ConfigStoreException if testing the provided specification fails
     */
    public static ConfigurationFactory<ServiceSpec> getFactory(
            ServiceSpec serviceSpec,
            Collection<Class<?>> additionalSubtypesToRegister) throws ConfigStoreException {
        ConfigurationFactory<ServiceSpec> factory = new Factory(additionalSubtypesToRegister);
        // Serialize and then deserialize:
        ServiceSpec loopbackSpecification = factory.parse(serviceSpec.getBytes());
        // Verify that equality works:
        if (!loopbackSpecification.equals(serviceSpec)) {
            StringBuilder error = new StringBuilder();
            error.append("Equality test failed: Loopback result is not equal to original:\n");
            error.append("- Original:\n");
            error.append(serviceSpec.toJsonString());
            error.append('\n');
            error.append("- Result:\n");
            error.append(loopbackSpecification.toJsonString());
            error.append('\n');
            throw new ConfigStoreException(error.toString());
        }
        return factory;
    }

    /**
     * Factory which performs the inverse of {@link DefaultServiceSpecification#getBytes()}.
     */
    public static class Factory implements ConfigurationFactory<ServiceSpec> {

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
        public ServiceSpec parse(byte[] bytes) throws ConfigStoreException {
            try {
                return SerializationUtils.fromString(
                        new String(bytes, CHARSET), DefaultServiceSpec.class, objectMapper);
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
     * Builder for DefaultServiceSpec.
     */
    public static class Builder {
        private String name;
        private String role;
        private String principal;
        private int apiPort;
        private String zookeeperConnection;
        private List<PodSpec> pods;
        private ReplacementFailurePolicy replacementFailurePolicy;

        public static Builder newBuilder() {
            return new Builder();
        }

        public static Builder newBuilder(DefaultServiceSpec spec) {
            return new Builder();
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder principal(String principal) {
            this.principal = principal;
            return this;
        }

        public Builder apiPort(int apiPort) {
            this.apiPort = apiPort;
            return this;
        }

        public Builder zookeeperConnection(String zookeeperConnection) {
            this.zookeeperConnection = zookeeperConnection;
            return this;
        }

        public Builder pods(List<PodSpec> pods) {
            this.pods = pods;
            return this;
        }

        public Builder replacementFailurePolicy(ReplacementFailurePolicy replacementFailurePolicy) {
            this.replacementFailurePolicy = replacementFailurePolicy;
            return this;
        }

        public DefaultServiceSpec build() {
            return new DefaultServiceSpec(name, role, principal, apiPort, zookeeperConnection, pods,
                    replacementFailurePolicy);
        }
    }
}
