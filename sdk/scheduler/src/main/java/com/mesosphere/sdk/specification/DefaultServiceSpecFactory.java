package com.mesosphere.sdk.specification;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.config.Configuration;
import com.mesosphere.sdk.config.ConfigurationFactory;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.evaluate.placement.AgentRule;
import com.mesosphere.sdk.offer.evaluate.placement.AndRule;
import com.mesosphere.sdk.offer.evaluate.placement.AnyMatcher;
import com.mesosphere.sdk.offer.evaluate.placement.AttributeRule;
import com.mesosphere.sdk.offer.evaluate.placement.ExactMatcher;
import com.mesosphere.sdk.offer.evaluate.placement.HostnameRule;
import com.mesosphere.sdk.offer.evaluate.placement.IsLocalRegionRule;
import com.mesosphere.sdk.offer.evaluate.placement.MaxPerAttributeRule;
import com.mesosphere.sdk.offer.evaluate.placement.MaxPerHostnameRule;
import com.mesosphere.sdk.offer.evaluate.placement.MaxPerRegionRule;
import com.mesosphere.sdk.offer.evaluate.placement.MaxPerZoneRule;
import com.mesosphere.sdk.offer.evaluate.placement.NotRule;
import com.mesosphere.sdk.offer.evaluate.placement.OrRule;
import com.mesosphere.sdk.offer.evaluate.placement.PassthroughRule;
import com.mesosphere.sdk.offer.evaluate.placement.RegexMatcher;
import com.mesosphere.sdk.offer.evaluate.placement.RegionRule;
import com.mesosphere.sdk.offer.evaluate.placement.RoundRobinByAttributeRule;
import com.mesosphere.sdk.offer.evaluate.placement.RoundRobinByHostnameRule;
import com.mesosphere.sdk.offer.evaluate.placement.RoundRobinByRegionRule;
import com.mesosphere.sdk.offer.evaluate.placement.RoundRobinByZoneRule;
import com.mesosphere.sdk.offer.evaluate.placement.TaskTypeLabelConverter;
import com.mesosphere.sdk.offer.evaluate.placement.TaskTypeRule;
import com.mesosphere.sdk.offer.evaluate.placement.ZoneRule;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import jersey.repackaged.com.google.common.base.Joiner;

/**
 * Factory which performs the inverse of {@link DefaultServiceSpec#getBytes()}.
 */
public class DefaultServiceSpecFactory implements ConfigurationFactory<ServiceSpec> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultServiceSpecFactory.class);

    /**
     * Subtypes which are mapped to supertypes when serialized.
     *
     * <p>For example, {@link DefaultPodSpec} is serialized with "@type" = {@link PodSpec}, but should be mapped back to
     * {@link DefaultPodSpec} when being deserialized.
     *
     * <p>Note that ResourceSpec and its subclasses require custom handling, specifically due to the
     * {@code Collection<ResourceSpec>} field in {@link ResourceSet}. The default Jackson behavior during
     * deserialization is to map {@link PortSpec}, {@link NamedVIPSpec} etc. to {@link ResourceSpec}s, ignoring the
     * {@code @type} annotations which explicitly specify the correct subclass. We resolve this with a custom
     * deserializer for subclasses of {@link ResourceSpec} used in {@link ResourceSet}.
     * See {@link ResourceSpecDeserializer}.
     */
    private static final SimpleAbstractTypeResolver defaultServiceSpecTypes = new SimpleAbstractTypeResolver()
            .addMapping(CommandSpec.class, DefaultCommandSpec.class)
            .addMapping(ConfigFileSpec.class, DefaultConfigFileSpec.class)
            .addMapping(DiscoverySpec.class, DefaultDiscoverySpec.class)
            .addMapping(HealthCheckSpec.class, DefaultHealthCheckSpec.class)
            .addMapping(NetworkSpec.class, DefaultNetworkSpec.class)
            .addMapping(PodSpec.class, DefaultPodSpec.class)
            .addMapping(ReadinessCheckSpec.class, DefaultReadinessCheckSpec.class)
            .addMapping(ResourceSet.class, DefaultResourceSet.class)
            .addMapping(RLimitSpec.class, DefaultRLimitSpec.class)
            .addMapping(SecretSpec.class, DefaultSecretSpec.class)
            .addMapping(TaskSpec.class, DefaultTaskSpec.class)
            .addMapping(TransportEncryptionSpec.class, DefaultTransportEncryptionSpec.class)
            .addMapping(VolumeSpec.class, DefaultVolumeSpec.class);

    /**
     * All PlacementRules and related types, which are serialized as part of ServiceSpecs against their original
     * class names. For direct ServiceSpec types (with default/interface pairs),
     * see {@link DefaultServiceSpecFactory#defaultServiceSpecTypes}.
     */
    private static final Collection<Class<?>> defaultPlacementTypes = Arrays.asList(
            AgentRule.class,
            AndRule.class,
            AnyMatcher.class,
            AttributeRule.class,
            ExactMatcher.class,
            HostnameRule.class,
            IsLocalRegionRule.class,
            MaxPerAttributeRule.class,
            MaxPerHostnameRule.class,
            MaxPerRegionRule.class,
            MaxPerZoneRule.class,
            NotRule.class,
            OrRule.class,
            PassthroughRule.class,
            RegexMatcher.class,
            RegionRule.class,
            RoundRobinByAttributeRule.class,
            RoundRobinByHostnameRule.class,
            RoundRobinByRegionRule.class,
            RoundRobinByZoneRule.class,
            TaskTypeLabelConverter.class,
            TaskTypeRule.class,
            ZoneRule.class);

    private final ObjectMapper objectMapper;

    /**
     * @see DefaultServiceSpec#getConfigurationFactory(ServiceSpec, Collection)
     */
    DefaultServiceSpecFactory(Collection<Class<?>> additionalSubtypes, ServiceSpec serviceSpec) {
        objectMapper = SerializationUtils.registerDefaultModules(new ObjectMapper())
                // Avoid breaking upgrade+downgrade compatibility if fields are added later:
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                /*.enableDefaultTyping()*/;

        SimpleModule module = new SimpleModule()
                .addDeserializer(GoalState.class, new GoalStateDeserializer(serviceSpec))
                .addDeserializer(ResourceSpec.class, new ResourceSpecDeserializer<>())
                .registerSubtypes(defaultPlacementTypes.toArray(new Class<?>[0]))
                .registerSubtypes(additionalSubtypes.toArray(new Class<?>[0]));
        module.setAbstractTypes(defaultServiceSpecTypes);

        objectMapper.registerModule(module);
    }

    @Override
    public ServiceSpec parse(byte[] bytes) throws ConfigStoreException {
        try {
            return SerializationUtils.fromString(
                    new String(bytes, Configuration.CHARSET), DefaultServiceSpec.class, objectMapper);
        } catch (IOException e) {
            throw new ConfigStoreException(Reason.SERIALIZATION_ERROR,
                    String.format(
                            "Failed to deserialize %s from JSON: %s",
                            DefaultServiceSpec.class.getName(), e.getMessage()),
                    e);
        }
    }

    @VisibleForTesting
    public static final Collection<Class<?>> getDefaultPlacementTypes() {
        return defaultPlacementTypes;
    }

    /**
     * Custom deserializer for goal states to accomodate transition from {@code FINISHED} to {@code ONCE} and
     * {@code FINISH}.
     */
    static class GoalStateDeserializer extends StdDeserializer<GoalState> {

        private final GoalState referenceTerminalGoalState;

        GoalStateDeserializer(ServiceSpec serviceSpec) {
            super((Class<?>)null);
            referenceTerminalGoalState = getReferenceTerminalGoalState(serviceSpec);
        }

        @Override
        public GoalState deserialize(
                JsonParser p, DeserializationContext ctxt) throws IOException, JsonParseException {
            String value = ((TextNode) p.getCodec().readTree(p)).textValue();

            if (value.equals("FINISHED") || value.equals("ONCE")) {
                return referenceTerminalGoalState;
            } else if (value.equals("FINISH")) {
                return GoalState.FINISH;
            } else if (value.equals("RUNNING")) {
                return GoalState.RUNNING;
            } else {
                logger.warn("Found unknown goal state in config store: {}", value);
                return GoalState.UNKNOWN;
            }
        }

        private static GoalState getReferenceTerminalGoalState(ServiceSpec serviceSpec) {
            Collection<TaskSpec> serviceTasks =
                    serviceSpec.getPods().stream().flatMap(p -> p.getTasks().stream()).collect(Collectors.toList());
            for (TaskSpec taskSpec : serviceTasks) {
                if (taskSpec.getGoal().equals(GoalState.FINISHED)) {
                    return GoalState.FINISHED;
                }
            }

            return GoalState.ONCE;
        }
    }

    /**
     * Custom deserializer to handle automatically rendering subclasses of {@link ResourceSpec}. For whatever reason,
     * Jackson will ignore the "@type" annotations and automatically convert e.g. a {@link NamedVIPSpec} or
     * {@link PortSpec} into a {@link ResourceSpec}, throwing the subclass-specific properties on the floor. This occurs
     * even if subtype mappings are defined for e.g. {@link NamedVIPSpec} => {@link DefaultNamedVIPSpec}.
     */
    static class ResourceSpecDeserializer<T extends Object> extends StdDeserializer<T> {

        private static final Map<String, Class<?>> RESOURCE_SPEC_CLASSES = new HashMap<>();
        static {
            RESOURCE_SPEC_CLASSES.put(NamedVIPSpec.class.getSimpleName(), DefaultNamedVIPSpec.class);
            RESOURCE_SPEC_CLASSES.put(PortSpec.class.getSimpleName(), DefaultPortSpec.class);
            // In practice, VolumeSpecs would be in a separate list in the ResourceSet and wouldn't get handled here.
            // However we still support it here just in case...
            RESOURCE_SPEC_CLASSES.put(VolumeSpec.class.getSimpleName(), DefaultVolumeSpec.class);
            RESOURCE_SPEC_CLASSES.put(ResourceSpec.class.getSimpleName(), DefaultResourceSpec.class);
        }

        ResourceSpecDeserializer() {
            super((Class<?>)null);
        }

        @Override
        public T deserialize(
                JsonParser jp, DeserializationContext ctxt) throws IOException, JsonParseException {
            throw new UnsupportedOperationException();
        }

        /**
         * Manually fetches the abstract "@type" value, maps it to a configured concrete type, then returns the result.
         * By default Jackson does the wrong thing, and treats e.g. {@link PortSpec} as a {@link ResourceSpec} even
         * though "@type" is {@link PortSpec}.
         */
        @Override
        public Object deserializeWithType(JsonParser jp, DeserializationContext ctxt, TypeDeserializer typeDeserializer)
                throws IOException {
            // Extract the "@type" value from the object.
            TreeNode node = jp.getCodec().readTree(jp);
            TreeNode typeVal = node.get(typeDeserializer.getPropertyName());
            if (typeVal == null) {
                throw new IllegalArgumentException(String.format(
                        "Missing field named '%s' in ResourceSpec. " +
                        "Missing 'visible=true' in base class JsonTypeInfo? Available fields were: %s",
                        typeDeserializer.getPropertyName(), Joiner.on(", ").join(node.fieldNames())));
            }
            if (!(typeVal instanceof TextNode)) {
                throw new IllegalArgumentException(String.format(
                        "Expected '%s' value in ResourceSpec to be TextNode, got: %s",
                        typeDeserializer.getPropertyName(), typeVal.getClass().getName()));

            }
            // Map the "@type" value to a configured concrete type.
            String metaType = ((TextNode) typeVal).asText();
            Class<?> classToUse = RESOURCE_SPEC_CLASSES.get(metaType);
            if (classToUse == null) {
                throw new IllegalArgumentException(String.format(
                        "Unsupported ResourceSpec type=%s, expected one of %s: %s",
                        metaType, RESOURCE_SPEC_CLASSES.keySet(), node));
            }
            // Parse the object as the configured concrete type.
            return jp.getCodec().treeToValue(node, classToUse);
        }
    }
}
