package com.mesosphere.sdk.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.config.DefaultConfigurationUpdater;
import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.config.validate.PodSpecsCannotChangeNetworkRegime;
import com.mesosphere.sdk.config.validate.PodSpecsCannotShrink;
import com.mesosphere.sdk.config.validate.PreReservationCannotChange;
import com.mesosphere.sdk.config.validate.ServiceNameCannotContainDoubleUnderscores;
import com.mesosphere.sdk.config.validate.TLSRequiresServiceAccount;
import com.mesosphere.sdk.config.validate.TaskVolumesCannotChange;
import com.mesosphere.sdk.config.validate.UserCannotChange;
import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.DefaultPhaseFactory;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.DefaultStepFactory;
import com.mesosphere.sdk.scheduler.plan.DeployPlanFactory;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverriderFactory;
import com.mesosphere.sdk.specification.DefaultPlanGenerator;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.validation.CapabilityValidator;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.storage.PersisterException;

/**
 * Creates a new {@link DefaultScheduler}.
 */
public class SchedulerBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerBuilder.class);

    private ServiceSpec serviceSpec;
    private final SchedulerFlags schedulerFlags;
    private final Persister persister;

    // When these optionals are unset, we use default values:
    private Optional<StateStore> stateStoreOptional = Optional.empty();
    private Optional<ConfigStore<ServiceSpec>> configStoreOptional = Optional.empty();

    // When these collections are empty, we don't do anything extra:
    private final List<Plan> manualPlans = new ArrayList<>();
    private final Map<String, RawPlan> yamlPlans = new HashMap<>();
    private final Map<String, EndpointProducer> endpointProducers = new HashMap<>();
    private Collection<ConfigValidator<ServiceSpec>> customConfigValidators = new ArrayList<>();
    private Collection<Object> customResources = new ArrayList<>();
    private RecoveryPlanOverriderFactory recoveryPlanOverriderFactory;

    SchedulerBuilder(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags) throws PersisterException {
        this(
                serviceSpec,
                schedulerFlags,
                schedulerFlags.isStateCacheEnabled() ?
                        new PersisterCache(CuratorPersister.newBuilder(serviceSpec).build()) :
                        CuratorPersister.newBuilder(serviceSpec).build());
    }

    SchedulerBuilder(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags, Persister persister) {
        this.serviceSpec = serviceSpec;
        this.schedulerFlags = schedulerFlags;
        this.persister = persister;
    }

    /**
     * Returns the {@link ServiceSpec} which was provided via the constructor.
     */
    public ServiceSpec getServiceSpec() {
        return serviceSpec;
    }

    /**
     * Returns the {@link SchedulerFlags} object which was provided via the constructor.
     */
    public SchedulerFlags getSchedulerFlags() {
        return schedulerFlags;
    }

    /**
     * Specifies a custom {@link StateStore}, otherwise the return value of
     * {@link DefaultScheduler#createStateStore(ServiceSpec, SchedulerFlags)} will be used.
     *
     * The state store persists copies of task information and task status for all tasks running in the service.
     *
     * @throws IllegalStateException if the state store is already set, via a previous call to either
     * {@link #setStateStore(StateStore)} or to {@link #getStateStore()}
     */
    public SchedulerBuilder setStateStore(StateStore stateStore) {
        if (stateStoreOptional.isPresent()) {
            // Any customization of the state store must be applied BEFORE getStateStore() is ever called.
            throw new IllegalStateException("State store is already set. Was getStateStore() invoked before this?");
        }
        this.stateStoreOptional = Optional.ofNullable(stateStore);
        return this;
    }

    /**
     * Returns the {@link StateStore} provided via {@link #setStateStore(StateStore)}, or a reasonable default
     * created via {@link DefaultScheduler#createStateStore(ServiceSpec, SchedulerFlags)}.
     *
     * In order to avoid cohesiveness issues between this setting and the {@link #build()} step,
     * {@link #setStateStore(StateStore)} may not be invoked after this has been called.
     */
    public StateStore getStateStore() {
        if (!stateStoreOptional.isPresent()) {
            setStateStore(new StateStore(persister));
        }
        return stateStoreOptional.get();
    }

    /**
     * Specifies a custom {@link ConfigStore}.
     *
     * The config store persists a copy of the current configuration ('target' configuration),
     * while also storing historical configurations.
     */
    public SchedulerBuilder setConfigStore(ConfigStore<ServiceSpec> configStore) {
        if (configStoreOptional.isPresent()) {
            // Any customization of the state store must be applied BEFORE getConfigStore() is ever called.
            throw new IllegalStateException(
                    "Config store is already set. Was getConfigStore() invoked before this?");
        }
        this.configStoreOptional = Optional.ofNullable(configStore);
        return this;
    }

    /**
     * Returns the {@link ConfigStore} provided via {@link #setConfigStore(ConfigStore)}, or a reasonable default
     * created via {@link DefaultScheduler#createConfigStore(ServiceSpec, Collection)}.
     *
     * In order to avoid cohesiveness issues between this setting and the {@link #build()} step,
     * {@link #setConfigStore(ConfigStore)} may not be invoked after this has been called.
     */
    public ConfigStore<ServiceSpec> getConfigStore() {
        if (!configStoreOptional.isPresent()) {
            try {
                setConfigStore(createConfigStore(serviceSpec, Collections.emptyList(), persister));
            } catch (ConfigStoreException e) {
                throw new IllegalStateException("Failed to create default config store", e);
            }
        }
        return configStoreOptional.get();
    }

    /**
     * Specifies custom endpoint resources which should be exposed through the scheduler's API server, in addition
     * to the defaults.
     */
    public SchedulerBuilder setCustomResources(Collection<Object> customResources) {
        this.customResources = customResources;
        return this;
    }

    /**
     * Specifies a custom list of configuration validators to be run when updating to a new target configuration,
     * or otherwise uses the default validators returned by {@link DefaultScheduler#defaultConfigValidators()}.
     */
    public SchedulerBuilder setCustomConfigValidators(Collection<ConfigValidator<ServiceSpec>> customConfigValidators) {
        this.customConfigValidators = customConfigValidators;
        return this;
    }

    /**
     * Specifies a custom {@link EndpointProducer} to be added to the /endpoints API. This may be used by services
     * which wish to expose custom endpoint information via that API.
     *
     * @param name the name of the endpoint to be exposed
     * @param endpointProducer the producer to be invoked when the provided endpoint name is requested
     */
    public SchedulerBuilder setEndpointProducer(String name, EndpointProducer endpointProducer) {
        endpointProducers.put(name, endpointProducer);
        return this;
    }

    /**
     * Sets the {@link Plan}s from the provided {@link RawServiceSpec} to this instance, using a
     * {@link DefaultPlanGenerator} to handle conversion. This is overridden by any plans manually provided by
     * {@link #setPlans(Collection)}.
     *
     * @throws ConfigStoreException if creating a default config store fails
     * @throws IllegalStateException if the plans were already set either via this call or via
     * {@link #setPlans(Collection)}
     */
    public SchedulerBuilder setPlansFrom(RawServiceSpec rawServiceSpec) throws ConfigStoreException {
        if (rawServiceSpec.getPlans() != null) {
            this.yamlPlans.clear();
            this.yamlPlans.putAll(rawServiceSpec.getPlans());
        }
        return this;
    }

    /**
     * Sets the provided {@link Plan}s to this instance. This may be used when no {@link RawServiceSpec} is
     * available, and overrides any calls to {@link #setPlansFrom(RawServiceSpec)}.
     *
     * @throws IllegalStateException if the plans were already set either via this call or via
     * {@link #setPlansFrom(RawServiceSpec)}
     */
    public SchedulerBuilder setPlans(Collection<Plan> plans) {
        this.manualPlans.clear();
        this.manualPlans.addAll(plans);
        return this;
    }

    /**
     * Sets the provided {@link PlanManager} to be the plan manager used for recovery.
     * @param recoveryPlanOverriderFactory the factory whcih generates the custom recovery plan manager
     */
    public SchedulerBuilder setRecoveryManagerFactory(RecoveryPlanOverriderFactory recoveryPlanOverriderFactory) {
        this.recoveryPlanOverriderFactory = recoveryPlanOverriderFactory;
        return this;
    }

    /**
     * Gets or generate plans against the given service spec.
     *
     * @param stateStore The state store to use for plan generation.
     * @param configStore The config store to use for plan generation.
     * @return a collection of plans
     */
    private Collection<Plan> getPlans(StateStore stateStore, ConfigStore<ServiceSpec> configStore) {
        final String plansType;
        final Collection<Plan> plans;
        if (!manualPlans.isEmpty()) {
            plansType = "manual";
            plans = new ArrayList<>(manualPlans);
        } else if (!yamlPlans.isEmpty()) {
            plansType = "YAML";
            // Note: Any internal Plan generation must only be AFTER updating/validating the config. Otherwise plans
            // may look at the old config and mistakenly think they're COMPLETE.
            DefaultPlanGenerator planGenerator = new DefaultPlanGenerator(configStore, stateStore);
            plans = yamlPlans.entrySet().stream()
                    .map(e -> planGenerator.generate(e.getValue(), e.getKey(), serviceSpec.getPods()))
                    .collect(Collectors.toList());
        } else {
            plansType = "generated";
            try {
                if (!configStore.list().isEmpty()) {
                    plans = Arrays.asList(
                            new DeployPlanFactory(
                                    new DefaultPhaseFactory(
                                            new DefaultStepFactory(configStore, stateStore)))
                                    .getPlan(configStore.fetch(configStore.getTargetConfig())));
                } else {
                    plans = Collections.emptyList();
                }
            } catch (ConfigStoreException e) {
                throw new IllegalStateException(e);
            }
        }

        LOGGER.info("Got {} {} plan{}: {}",
                plans.size(),
                plansType,
                plans.size() == 1 ? "" : "s",
                plans.stream().map(plan -> plan.getName()).collect(Collectors.toList()));
        return plans;
    }

    /**
     * Detects whether or not the previous deployment's type was set or not and if not, sets it in the state store.
     *
     * @param stateStore The stateStore to get last deployment type from.
     * @param configStore The configStore to get plans from.
     */
    private void fixLastDeploymentType(StateStore stateStore, ConfigStore<ServiceSpec> configStore) {
        ConfigurationUpdater.UpdateResult.DeploymentType lastDeploymentType =
                StateStoreUtils.getLastCompletedUpdateType(stateStore);
        if (lastDeploymentType.equals(ConfigurationUpdater.UpdateResult.DeploymentType.NONE)) {
            Optional<Plan> deployPlan = SchedulerUtils.getDeployPlan(getPlans(stateStore, configStore));
            if (deployPlan.isPresent() && deployPlan.get().isComplete()) {
                StateStoreUtils.setLastCompletedUpdateType(
                        stateStore,
                        ConfigurationUpdater.UpdateResult.DeploymentType.DEPLOY);
            }
        }
    }

    /**
     * Creates a new scheduler instance with the provided values or their defaults.
     *
     * @return a new scheduler instance
     * @throws IllegalStateException if config validation failed when updating the target config.
     */
    public DefaultScheduler build() {
        try {
            new CapabilityValidator().validate(serviceSpec);
        } catch (CapabilityValidator.CapabilityValidationException e) {
            throw new IllegalStateException("Failed to validate provided ServiceSpec", e);
        }

        // Get custom or default config and state stores (defaults handled by getStateStore()/getConfigStore()):
        final StateStore stateStore = getStateStore();
        final ConfigStore<ServiceSpec> configStore = getConfigStore();
        fixLastDeploymentType(stateStore, configStore);

        // Update/validate config as needed to reflect the new service spec:
        Collection<ConfigValidator<ServiceSpec>> configValidators = new ArrayList<>();
        configValidators.addAll(Arrays.asList(
                new ServiceNameCannotContainDoubleUnderscores(),
                new PodSpecsCannotShrink(),
                new TaskVolumesCannotChange(),
                new PodSpecsCannotChangeNetworkRegime(),
                new PreReservationCannotChange(),
                new UserCannotChange(),
                new TLSRequiresServiceAccount(getSchedulerFlags())));
        configValidators.addAll(customConfigValidators);

        final ConfigurationUpdater.UpdateResult configUpdateResult =
                updateConfig(serviceSpec, stateStore, configStore, configValidators);

        if (!configUpdateResult.getErrors().isEmpty()) {
            LOGGER.warn("Failed to update configuration due to errors with configuration {}: {}",
                    configUpdateResult.getTargetId(), configUpdateResult.getErrors());
            try {
                // If there were errors maintain the last accepted target configuration.
                serviceSpec = configStore.fetch(configStore.getTargetConfig());
            } catch (ConfigStoreException e) {
                LOGGER.error("Failed to maintain pervious target configuration.");
                throw new IllegalStateException(e);
            }
        }

        Collection<Plan> plans = getPlans(stateStore, configStore);
        plans = overrideDeployPlan(plans, configUpdateResult);
        Optional<Plan> deployOptional = SchedulerUtils.getDeployPlan(plans);
        if (!deployOptional.isPresent()) {
            throw new IllegalStateException("No deploy plan provided.");
        }

        List<String> errors = configUpdateResult.getErrors().stream()
                .map(ConfigValidationError::toString)
                .collect(Collectors.toList());
        plans = updateDeployPlan(plans, errors);

        return new DefaultScheduler(
                serviceSpec,
                getSchedulerFlags(),
                customResources,
                plans,
                stateStore,
                configStore,
                endpointProducers,
                Optional.ofNullable(recoveryPlanOverriderFactory));
    }

    private static Collection<Plan> updateDeployPlan(Collection<Plan> plans, List<String> errors) {
        if (errors.isEmpty()) {
            return plans;
        }

        Collection<Plan> updatedPlans = new ArrayList<>();
        Plan deployPlan = SchedulerUtils.getDeployPlan(plans).get();
        deployPlan = new DefaultPlan(
                deployPlan.getName(),
                deployPlan.getChildren(),
                deployPlan.getStrategy(),
                errors);

        updatedPlans.add(deployPlan);
        for (Plan plan : plans) {
            if (!plan.isDeployPlan()) {
                updatedPlans.add(plan);
            }
        }

        return updatedPlans;
    }

    @VisibleForTesting
    static ConfigStore<ServiceSpec> createConfigStore(
            ServiceSpec serviceSpec, Collection<Class<?>> customDeserializationSubtypes, Persister persister)
                    throws ConfigStoreException {
        return new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec, customDeserializationSubtypes),
                persister);
    }

    /**
     * Given the plans specified and the update scenario, the deploy plan may be overriden by a specified update
     * plan.
     */
    private static Collection<Plan> overrideDeployPlan(
            Collection<Plan> plans,
            ConfigurationUpdater.UpdateResult updateResult) {

        Optional<Plan> updatePlanOptional = plans.stream()
                .filter(plan -> plan.getName().equals(Constants.UPDATE_PLAN_NAME))
                .findFirst();

        LOGGER.info(String.format("Update type: '%s', Found update plan: '%s'",
                updateResult.getDeploymentType().name(),
                updatePlanOptional.isPresent()));

        if (updateResult.getDeploymentType().equals(ConfigurationUpdater.UpdateResult.DeploymentType.UPDATE)
                && updatePlanOptional.isPresent()) {
            LOGGER.info("Overriding deploy plan with update plan.");

            Plan updatePlan = updatePlanOptional.get();
            Plan deployPlan = new DefaultPlan(
                    Constants.DEPLOY_PLAN_NAME,
                    updatePlan.getChildren(),
                    updatePlan.getStrategy(),
                    Collections.emptyList());

            plans = new ArrayList<>(
                    plans.stream()
                            .filter(plan -> !plan.getName().equals(Constants.DEPLOY_PLAN_NAME))
                            .filter(plan -> !plan.getName().equals(Constants.UPDATE_PLAN_NAME))
                            .collect(Collectors.toList()));

            plans.add(deployPlan);
        }

        return plans;
    }

    /**
     * Updates the configuration target to reflect the provided {@code serviceSpec} using the provided
     * {@code configValidators}, or stays with the previous configuration if there were validation errors.
     *
     * @param serviceSpec the service specification to use
     * @param stateStore the state store to pass to the updater
     * @param configStore the config store to pass to the updater
     * @param configValidators the list of config validators, see {@link SchedulerBuilder#defaultConfigValidators()}
     *     for reasonable defaults
     * @return the config update result, which may contain one or more validation errors produced by
     *     {@code configValidators}
     */
    private static ConfigurationUpdater.UpdateResult updateConfig(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Collection<ConfigValidator<ServiceSpec>> configValidators) {
        LOGGER.info("Updating config with {} validators...", configValidators.size());
        ConfigurationUpdater<ServiceSpec> configurationUpdater = new DefaultConfigurationUpdater(
                stateStore, configStore, DefaultServiceSpec.getComparatorInstance(), configValidators);
        try {
            return configurationUpdater.updateConfiguration(serviceSpec);
        } catch (ConfigStoreException e) {
            LOGGER.error("Fatal error when performing configuration update. Service exiting.", e);
            throw new IllegalStateException(e);
        }
    }
}