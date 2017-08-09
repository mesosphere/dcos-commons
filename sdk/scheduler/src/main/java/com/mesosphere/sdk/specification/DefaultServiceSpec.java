package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.config.ConfigurationComparator;
import com.mesosphere.sdk.config.ConfigurationFactory;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.evaluate.placement.*;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.validation.UniquePodType;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLToInternalMappers;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;


/**
 * Default implementation of {@link ServiceSpec}.
 */
public class DefaultServiceSpec implements ServiceSpec {
    private static final Comparator COMPARATOR = new Comparator();
    private static final Logger logger = LoggerFactory.getLogger(DefaultServiceSpec.class);

    @NotNull(message = "Service name cannot be empty")
    @Size(min = 1, message = "Service name cannot be empty")
    private String name;
    private String role;
    private String principal;
    private String user;

    private String webUrl;
    private String zookeeperConnection;

    @Valid
    @NotNull
    @Size(min = 1, message = "At least one pod must be configured.")
    @UniquePodType(message = "Pod types must be unique")
    private List<PodSpec> pods;

    @Valid
    private ReplacementFailurePolicy replacementFailurePolicy;

    @JsonCreator
    public DefaultServiceSpec(
            @JsonProperty("name") String name,
            @JsonProperty("role") String role,
            @JsonProperty("principal") String principal,
            @JsonProperty("web-url") String webUrl,
            @JsonProperty("zookeeper") String zookeeperConnection,
            @JsonProperty("pod-specs") List<PodSpec> pods,
            @JsonProperty("replacement-failure-policy") ReplacementFailurePolicy replacementFailurePolicy,
            @JsonProperty("user") String user) {
        this.name = name;
        this.role = role;
        this.principal = principal;
        this.webUrl = webUrl;
        // If no zookeeperConnection string is configured, fallback to the default value.
        this.zookeeperConnection = StringUtils.isBlank(zookeeperConnection)
                ? DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING : zookeeperConnection;
        this.pods = pods;
        this.replacementFailurePolicy = replacementFailurePolicy;
        this.user = getUser(user, pods);
        ValidationUtils.validate(this);
    }

    @VisibleForTesting
    static String getUser(String user, List<PodSpec> podSpecs) {
        if (!StringUtils.isBlank(user)) {
            return user;
        }

        Optional<PodSpec> podSpecOptional = podSpecs.stream()
                .filter(podSpec -> podSpec.getUser() != null && podSpec.getUser().isPresent())
                .findFirst();

        if (podSpecOptional.isPresent()) {
            return podSpecOptional.get().getUser().get();
        } else {
            return DcosConstants.DEFAULT_SERVICE_USER;
        }
    }

    private DefaultServiceSpec(Builder builder) {
        this(
                builder.name,
                builder.role,
                builder.principal,
                builder.webUrl,
                builder.zookeeperConnection,
                builder.pods,
                builder.replacementFailurePolicy,
                builder.user);
    }


    public static Generator newGenerator(RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags) {
        return newGenerator(rawServiceSpec, schedulerFlags, new TaskEnvRouter());
    }

    @VisibleForTesting
    public static Generator newGenerator(
            RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags, TaskEnvRouter taskEnvRouter) {
        return new Generator(rawServiceSpec, schedulerFlags, taskEnvRouter);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(ServiceSpec copy) {
        Builder builder = new Builder();
        builder.name = copy.getName();
        builder.role = copy.getRole();
        builder.principal = copy.getPrincipal();
        builder.zookeeperConnection = copy.getZookeeperConnection();
        builder.webUrl = copy.getWebUrl();
        builder.pods = copy.getPods();
        builder.replacementFailurePolicy = copy.getReplacementFailurePolicy().orElse(null);
        builder.user = copy.getUser();
        return builder;
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
    public String getWebUrl() {
        return webUrl;
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
    public Optional<ReplacementFailurePolicy> getReplacementFailurePolicy() {
        return Optional.ofNullable(replacementFailurePolicy);
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
     * Returns a {@link ConfigurationComparator} which may be used to compare
     * {@link DefaultServiceSpec}s.
     */
    public static ConfigurationComparator<ServiceSpec> getComparatorInstance() {
        return COMPARATOR;
    }

    @Override
    public String getUser() {
        return user;
    }

    /**
     * Comparer which checks for equality of {@link DefaultServiceSpec}s.
     */
    public static class Comparator implements ConfigurationComparator<ServiceSpec> {

        /**
         * Call {@link DefaultServiceSpec#getComparatorInstance()} instead.
         */
        private Comparator() {
        }

        @Override
        public boolean equals(ServiceSpec first, ServiceSpec second) {
            return EqualsBuilder.reflectionEquals(first, second);
        }
    }

    /**
     * Returns a {@link ConfigFactory} which may be used to deserialize
     * {@link DefaultServiceSpec}s, which has been confirmed to successfully and
     * consistently serialize/deserialize the provided {@code ServiceSpecification} instance.
     *
     * @param serviceSpec           specification to test for successful serialization/deserialization
     * @throws ConfigStoreException if testing the provided specification fails
     */
    public static ConfigurationFactory<ServiceSpec> getConfigurationFactory(ServiceSpec serviceSpec)
            throws ConfigStoreException {
        return getConfigurationFactory(serviceSpec, Collections.emptyList());
    }

    /**
     * Returns a {@link ConfigFactory} which may be used to deserialize
     * {@link DefaultServiceSpec}s, which has been confirmed to successfully and
     * consistently serialize/deserialize the provided {@code ServiceSpecification} instance.
     *
     * @param serviceSpec                  specification to test for successful serialization/deserialization
     * @param additionalSubtypesToRegister any class subtypes which should be registered with
     *                                     Jackson for deserialization. any custom placement rule implementations
     *                                     must be provided
     * @throws ConfigStoreException        if testing the provided specification fails
     */
    public static ConfigurationFactory<ServiceSpec> getConfigurationFactory(
            ServiceSpec serviceSpec, Collection<Class<?>> additionalSubtypesToRegister) throws ConfigStoreException {
        ConfigurationFactory<ServiceSpec> factory = new ConfigFactory(additionalSubtypesToRegister);

        ServiceSpec loopbackSpecification;
        try {
            // Serialize and then deserialize:
            loopbackSpecification = factory.parse(serviceSpec.getBytes());
        } catch (Exception e) {
            logger.error("Failed to parse JSON for loopback validation", e);
            logger.error("JSON to be parsed was:\n{}",
                    new String(serviceSpec.getBytes(), StandardCharsets.UTF_8));
            throw e;
        }
        // Verify that equality works:
        if (!loopbackSpecification.equals(serviceSpec)) {
            StringBuilder error = new StringBuilder();  // TODO (arand) this is not a very helpful error message
            error.append("Equality test failed: Loopback result is not equal to original:\n");
            error.append("- Original:\n");
            error.append(serviceSpec.toJsonString());
            error.append('\n');
            error.append("- Result:\n");
            error.append(loopbackSpecification.toJsonString());
            error.append('\n');
            throw new ConfigStoreException(Reason.LOGIC_ERROR, error.toString());
        }
        return factory;
    }

    /**
     * Factory which performs the inverse of {@link DefaultServiceSpec#getBytes()}.
     */
    public static class ConfigFactory implements ConfigurationFactory<ServiceSpec> {

        /**
         * Subtypes to be registered by defaults. This list should include all
         * {@link PlacementRule}s that are included in the library.
         */
        private static final Collection<Class<?>> defaultRegisteredSubtypes = Arrays.asList(
                AgentRule.class,
                AndRule.class,
                AnyMatcher.class,
                AttributeRule.class,
                DefaultResourceSpec.class,
                DefaultVolumeSpec.class,
                ExactMatcher.class,
                HostnameRule.class,
                MaxPerAttributeRule.class,
                MaxPerHostnameRule.class,
                NamedVIPSpec.class,
                NotRule.class,
                OrRule.class,
                PassthroughRule.class,
                PortSpec.class,
                RegexMatcher.class,
                RoundRobinByAttributeRule.class,
                RoundRobinByHostnameRule.class,
                TaskTypeLabelConverter.class,
                TaskTypeRule.class,
                DefaultSecretSpec.class);

        private final ObjectMapper objectMapper;

        /**
         * @see DefaultServiceSpec#getConfigurationFactory(ServiceSpec, Collection)
         */
        private ConfigFactory(Collection<Class<?>> additionalSubtypes) {
            objectMapper = SerializationUtils.registerDefaultModules(new ObjectMapper());
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
                throw new ConfigStoreException(Reason.SERIALIZATION_ERROR,
                        "Failed to deserialize DefaultServiceSpecification from JSON: " + e.getMessage(), e);
            }
        }

        @VisibleForTesting
        public static final Collection<Class<?>> getDefaultRegisteredSubtypes() {
            return defaultRegisteredSubtypes;
        }
    }

    /**
     * Generates a {@link ServiceSpec} from a given YAML definition in the form of a {@link RawServiceSpec}.
     */
    public static class Generator {

        private final RawServiceSpec rawServiceSpec;
        private final SchedulerFlags schedulerFlags;
        private final TaskEnvRouter taskEnvRouter;
        private YAMLToInternalMappers.FileReader fileReader;

        private Generator(
                RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags, TaskEnvRouter taskEnvRouter) {
            this.rawServiceSpec = rawServiceSpec;
            this.schedulerFlags = schedulerFlags;
            this.taskEnvRouter = taskEnvRouter;
            this.fileReader = new YAMLToInternalMappers.FileReader();
        }

        /**
         * Assigns an environment variable to be included in all service tasks. Note that this may be overridden via
         * {@code TASKCFG_*} scheduler environment variables at runtime, and by pod-specific settings provided via
         * {@link #setPodEnv(String, String, String)}.
         */
        public Generator setAllPodsEnv(String key, String value) {
            this.taskEnvRouter.setAllPodsEnv(key, value);
            return this;
        }

        /**
         * Assigns an environment variable to be included in tasks for the specified pod type. For example, all tasks
         * running inside of "index" pods. Note that this may be overridden via {@code TASKCFG_*} scheduler environment
         * variables at runtime.
         */
        public Generator setPodEnv(String podType, String key, String value) {
            this.taskEnvRouter.setPodEnv(podType, key, value);
            return this;
        }

        /**
         * Assigns a custom {@link YAMLToInternalMappers.FileReader} implementation for reading config file templates.
         * This is exposed to support mockery in tests.
         */
        @VisibleForTesting
        public Generator setFileReader(YAMLToInternalMappers.FileReader fileReader) {
            this.fileReader = fileReader;
            return this;
        }

        public DefaultServiceSpec build() throws Exception {
            return YAMLToInternalMappers.convertServiceSpec(rawServiceSpec, schedulerFlags, taskEnvRouter, fileReader);
        }
    }


    /**
     * {@code DefaultServiceSpec} builder static inner class.
     */
    public static final class Builder {
        private String name;
        private String role;
        private String principal;
        private String webUrl;
        private String zookeeperConnection;
        private List<PodSpec> pods = new ArrayList<>();
        private ReplacementFailurePolicy replacementFailurePolicy;
        private String user;

        private Builder() {
        }

        /**
         * Sets the {@code name} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param name the {@code name} to set
         * @return a reference to this Builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the {@code role} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param role the {@code role} to set
         * @return a reference to this Builder
         */
        public Builder role(String role) {
            this.role = role;
            return this;
        }

        /**
         * Sets the {@code principal} and returns a reference to this Builder so that the methods can be chained
         * together.
         *
         * @param principal the {@code principal} to set
         * @return a reference to this Builder
         */
        public Builder principal(String principal) {
            this.principal = principal;
            return this;
        }

        /**
         * Sets the advertised web UI URL for the service and returns a reference to this Builder so that the methods
         * can be chained together.
         *
         * @param webUrl the web URL to set
         * @return a reference to this Builder
         */
        public Builder webUrl(String webUrl) {
            this.webUrl = webUrl;
            return this;
        }

        /**
         * Sets the {@code user} and returns a reference to this Builder so that the methods can be chained
         * together.
         *
         * @param user the {@code principal} to set
         * @return a reference to this Builder
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Sets the {@code zookeeperConnection} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param zookeeperConnection the {@code zookeeperConnection} to set
         * @return a reference to this Builder
         */
        public Builder zookeeperConnection(String zookeeperConnection) {
            this.zookeeperConnection = zookeeperConnection;
            return this;
        }

        /**
         * Sets the {@code pods} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param pods the {@code pods} to set
         * @return a reference to this Builder
         */
        public Builder pods(List<PodSpec> pods) {
            this.pods = pods;
            return this;
        }

        /**
         * Adds the {@code pod} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param pod the {@code pod} to add
         * @return a reference to this Builder
         */
        public Builder addPod(PodSpec pod) {
            this.pods.add(pod);
            return this;
        }

        /**
         * Sets the {@code replacementFailurePolicy} and returns a reference to this Builder so that the methods can be
         * chained together.
         *
         * @param replacementFailurePolicy the {@code replacementFailurePolicy} to set
         * @return a reference to this Builder
         */
        public Builder replacementFailurePolicy(ReplacementFailurePolicy replacementFailurePolicy) {
            this.replacementFailurePolicy = replacementFailurePolicy;
            return this;
        }

        /**
         * Returns a {@code DefaultServiceSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultServiceSpec} built with parameters of this {@code DefaultServiceSpec.Builder}
         */
        public DefaultServiceSpec build() {
            return new DefaultServiceSpec(this);
        }
    }
}
