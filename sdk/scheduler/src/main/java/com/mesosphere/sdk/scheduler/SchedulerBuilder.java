package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.config.DefaultConfigurationUpdater;
import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.config.validate.DefaultConfigValidators;
import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.evaluate.placement.AndRule;
import com.mesosphere.sdk.offer.evaluate.placement.IsLocalRegionRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementUtils;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverriderFactory;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.storage.PersisterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates a new {@link DefaultScheduler}.
 */
public class SchedulerBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerBuilder.class);

    private ServiceSpec serviceSpec;
    private final SchedulerConfig schedulerConfig;
    private final Persister persister;

    // When these optionals are unset, we use default values:
    private Optional<StateStore> stateStoreOptional = Optional.empty();
    private Optional<ConfigStore<ServiceSpec>> configStoreOptional = Optional.empty();

    // When these collections are empty, we don't do anything extra:
    private final Map<String, RawPlan> yamlPlans = new HashMap<>();
    private final Map<String, EndpointProducer> endpointProducers = new HashMap<>();
    private Collection<ConfigValidator<ServiceSpec>> customConfigValidators = new ArrayList<>();
    private Collection<Object> customResources = new ArrayList<>();
    private RecoveryPlanOverriderFactory recoveryPlanOverriderFactory;
    private PlanCustomizer planCustomizer;

    SchedulerBuilder(ServiceSpec serviceSpec, SchedulerConfig schedulerConfig) throws PersisterException {
        this(
                serviceSpec,
                schedulerConfig,
                schedulerConfig.isStateCacheEnabled() ?
                        new PersisterCache(CuratorPersister.newBuilder(serviceSpec).build()) :
                        CuratorPersister.newBuilder(serviceSpec).build());
    }

    SchedulerBuilder(ServiceSpec serviceSpec, SchedulerConfig schedulerConfig, Persister persister) {
        this.serviceSpec = serviceSpec;
        this.schedulerConfig = schedulerConfig;
        this.persister = persister;
    }

    /**
     * Returns the {@link ServiceSpec} which was provided via the constructor.
     */
    public ServiceSpec getServiceSpec() {
        return serviceSpec;
    }

    /**
     * Returns the {@link SchedulerConfig} object which was provided via the constructor.
     */
    public SchedulerConfig getSchedulerConfig() {
        return schedulerConfig;
    }

    /**
     * Specifies a custom {@link StateStore}.  The state store persists copies of task information and task status for
     * all tasks running in the service.
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
     * Returns the {@link StateStore} provided via {@link #setStateStore(StateStore)}, or a reasonable default.
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
            // Any customization of the config store must be applied BEFORE getConfigStore() is ever called.
            throw new IllegalStateException(
                    "Config store is already set. Was getConfigStore() invoked before this?");
        }
        this.configStoreOptional = Optional.ofNullable(configStore);
        return this;
    }

    /**
     * Returns the {@link ConfigStore} provided via {@link #setConfigStore(ConfigStore)}, or a reasonable default.
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
     * or otherwise uses the default validators returned by
     * {@link DefaultConfigValidators#getValidators(SchedulerConfig)}.
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
     */
    public SchedulerBuilder setPlansFrom(RawServiceSpec rawServiceSpec) throws ConfigStoreException {
        if (rawServiceSpec.getPlans() != null) {
            this.yamlPlans.clear();
            this.yamlPlans.putAll(rawServiceSpec.getPlans());
        }
        return this;
    }

    /**
     * Assigns a {@link RecoveryPlanOverriderFactory} to be used for generating the recovery plan manager.
     *
     * @param recoveryPlanOverriderFactory the factory which generates the custom recovery plan manager
     */
    public SchedulerBuilder setRecoveryManagerFactory(RecoveryPlanOverriderFactory recoveryPlanOverriderFactory) {
        this.recoveryPlanOverriderFactory = recoveryPlanOverriderFactory;
        return this;
    }

    public SchedulerBuilder setPlanCustomizer(PlanCustomizer planCustomizer) {
        this.planCustomizer = planCustomizer;
        return this;
    }

    /**
     * Creates a new Mesos scheduler instance with the provided values or their defaults, or an empty {@link Optional}
     * if no Mesos scheduler should be registered for this run.
     *
     * @return a new Mesos scheduler instance to be registered, or an empty {@link Optional}
     * @throws IllegalArgumentException if validating the provided configuration failed
     */
    public AbstractScheduler build() {
        // Get custom or default config and state stores (defaults handled by getStateStore()/getConfigStore()):
        final StateStore stateStore = getStateStore();
        final ConfigStore<ServiceSpec> configStore = getConfigStore();

        if (schedulerConfig.isUninstallEnabled()) {
            if (!StateStoreUtils.isUninstalling(stateStore)) {
                LOGGER.info("Service has been told to uninstall. Marking this in the persistent state store. " +
                        "Uninstall cannot be canceled once enabled.");
                StateStoreUtils.setUninstalling(stateStore);
            }

            return new UninstallScheduler(serviceSpec, stateStore, configStore, schedulerConfig);
        } else {
            if (StateStoreUtils.isUninstalling(stateStore)) {
                LOGGER.error("Service has been previously told to uninstall, this cannot be reversed. " +
                        "Reenable the uninstall flag to complete the process.");
                SchedulerUtils.hardExit(SchedulerErrorCode.SCHEDULER_ALREADY_UNINSTALLING);
            }

            return getDefaultScheduler(stateStore, configStore);
        }
    }

    /**
     * Creates a new scheduler instance with the provided values or their defaults.
     *
     * @return a new scheduler instance
     * @throws IllegalArgumentException if config validation failed when updating the target config.
     */
    private DefaultScheduler getDefaultScheduler(StateStore stateStore, ConfigStore<ServiceSpec> configStore) {
        // Determine whether deployment had previously completed BEFORE we update the config.
        // Plans may be generated from the config content.
        boolean hasCompletedDeployment = StateStoreUtils.getDeploymentWasCompleted(stateStore);
        if (!hasCompletedDeployment) {
            try {
                // Check for completion against the PRIOR service spec. For example, if the new service spec has n+1
                // nodes, then we want to check that the prior n nodes had successfully deployed.
                ServiceSpec lastServiceSpec = configStore.fetch(configStore.getTargetConfig());
                Optional<Plan> deployPlan = SchedulerUtils.getDeployPlan(
                        getPlans(stateStore, configStore, lastServiceSpec, yamlPlans));
                if (deployPlan.isPresent() && deployPlan.get().isComplete()) {
                    LOGGER.info("Marking deployment as having been previously completed");
                    StateStoreUtils.setDeploymentWasCompleted(stateStore);
                    hasCompletedDeployment = true;
                }
            } catch (ConfigStoreException e) {
                // This is expected during initial deployment, when there is no prior configuration.
                LOGGER.info("Unable to retrieve last configuration. Assuming that no prior deployment has completed");
            }
        }

        List<PodSpec> pods = serviceSpec.getPods().stream()
                .map(podSpec -> updatePodPlacement(podSpec))
                .collect(Collectors.toList());
        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec).pods(pods).build();

        // Update/validate config as needed to reflect the new service spec:
        Collection<ConfigValidator<ServiceSpec>> configValidators = new ArrayList<>();
        configValidators.addAll(DefaultConfigValidators.getValidators(schedulerConfig));
        configValidators.addAll(customConfigValidators);
        final ConfigurationUpdater.UpdateResult configUpdateResult =
                updateConfig(serviceSpec, stateStore, configStore, configValidators);
        if (!configUpdateResult.getErrors().isEmpty()) {
            LOGGER.warn("Failed to update configuration due to validation errors: {}", configUpdateResult.getErrors());
            try {
                // If there were errors, stick with the last accepted target configuration.
                serviceSpec = configStore.fetch(configStore.getTargetConfig());
            } catch (ConfigStoreException e) {
                // Uh oh. Bail.
                LOGGER.error("Failed to retrieve previous target configuration", e);
                throw new IllegalArgumentException(e);
            }
        }

        // Now that a ServiceSpec has been chosen, generate the plans.
        Collection<Plan> plans = getPlans(stateStore, configStore, serviceSpec, yamlPlans);
        plans = selectDeployPlan(plans, hasCompletedDeployment);
        Optional<Plan> deployPlan = SchedulerUtils.getDeployPlan(plans);
        if (!deployPlan.isPresent()) {
            throw new IllegalArgumentException("No deploy plan provided: " + plans);
        }

        if (planCustomizer != null) {
            plans = plans.stream()
                    .map(plan -> planCustomizer.updatePlan(plan))
                    .collect(Collectors.toList());
        }

        List<String> errors = configUpdateResult.getErrors().stream()
                .map(ConfigValidationError::toString)
                .collect(Collectors.toList());

        if (!errors.isEmpty()) {
            plans = setDeployPlanErrors(plans, deployPlan.get(), errors);
        }

        return new DefaultScheduler(
                serviceSpec,
                schedulerConfig,
                customResources,
                plans,
                stateStore,
                configStore,
                endpointProducers,
                Optional.ofNullable(recoveryPlanOverriderFactory));
    }

    /**
     * Update pods with appropriate placement constraints to enforce user REGION intent.
     * If a pod's placement rules do not explicitly reference a REGION the assumption should be that
     * the user intends that a pod be restriced to the local REGION.
     *
     * @param podSpec The {@link PodSpec} whose placement rule will be updated to enforce appropriate region placement.
     * @return The updated {@link PodSpec}
     */
    static PodSpec updatePodPlacement(PodSpec podSpec) {
        if (!Capabilities.getInstance().supportsDomains() || PlacementUtils.placementRuleReferencesRegion(podSpec)) {
            return podSpec;
        }

        PlacementRule rule;
        if (podSpec.getPlacementRule().isPresent()) {
            rule = new AndRule(new IsLocalRegionRule(), podSpec.getPlacementRule().get());
        } else {
            rule = new IsLocalRegionRule();
        }

        return DefaultPodSpec.newBuilder(podSpec).placementRule(rule).build();
    }

    /**
     * Gets or generate plans against the given service spec.
     *
     * @param stateStore The state store to use for plan generation.
     * @param configStore The config store to use for plan generation.
     * @return a collection of plans
     */
    private static Collection<Plan> getPlans(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            ServiceSpec serviceSpec,
            Map<String, RawPlan> yamlPlans) {
        final String plansType;
        final Collection<Plan> plans;
        if (!yamlPlans.isEmpty()) {
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
                    PlanFactory planFactory = new DeployPlanFactory(
                            new DefaultPhaseFactory(new DefaultStepFactory(configStore, stateStore)));
                    plans = Arrays.asList(planFactory.getPlan(configStore.fetch(configStore.getTargetConfig())));
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
     * Updates the Deploy plan in the provided list of {@code plans} to contain the provided {@code errors}.
     * Returns a new list of plans containing the updates.
     */
    private static Collection<Plan> setDeployPlanErrors(
            Collection<Plan> allPlans, Plan deployPlan, List<String> errors) {
        Collection<Plan> updatedPlans = new ArrayList<>();
        updatedPlans.add(new DefaultPlan(
                deployPlan.getName(),
                deployPlan.getChildren(),
                deployPlan.getStrategy(),
                errors));
        for (Plan plan : allPlans) {
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
     * Replaces the deploy plan with an update plan, in the case that an update deployment is being performed AND that
     * a custom update plan has been specified.
     */
    @VisibleForTesting
    static Collection<Plan> selectDeployPlan(Collection<Plan> plans, boolean hasCompletedDeployment) {
        Optional<Plan> updatePlanOptional = plans.stream()
                .filter(plan -> plan.getName().equals(Constants.UPDATE_PLAN_NAME))
                .findFirst();

        if (!hasCompletedDeployment || !updatePlanOptional.isPresent()) {
            LOGGER.info("Using regular deploy plan. (Has completed deployment: {}, Custom update plan defined: {})",
                    hasCompletedDeployment, updatePlanOptional.isPresent());
            return plans;
        }

        LOGGER.info("Overriding deploy plan with custom update plan. " +
                "(Has completed deployment: {}, Custom update plan defined: {})",
                hasCompletedDeployment, updatePlanOptional.isPresent());
        Collection<Plan> newPlans = new ArrayList<>();
        newPlans.addAll(plans.stream()
                .filter(plan -> !plan.isDeployPlan() && !plan.getName().equals(Constants.UPDATE_PLAN_NAME))
                .collect(Collectors.toList()));
        newPlans.add(new DefaultPlan(
                Constants.DEPLOY_PLAN_NAME,
                updatePlanOptional.get().getChildren(),
                updatePlanOptional.get().getStrategy(),
                Collections.emptyList()));
        return newPlans;
    }

    /**
     * Updates the configuration target to reflect the provided {@code serviceSpec} using the provided
     * {@code configValidators}, or stays with the previous configuration if there were validation errors.
     *
     * @param serviceSpec the service specification to use
     * @param stateStore the state store to pass to the updater
     * @param configStore the config store to pass to the updater
     * @param configValidators the list of config validators,
     *                         see {@link DefaultConfigValidators#getValidators(SchedulerConfig)} for reasonable
     *                         defaults
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
