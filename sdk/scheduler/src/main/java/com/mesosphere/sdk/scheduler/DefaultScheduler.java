package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.api.*;
import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.api.types.StringPropertyDeserializer;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.config.DefaultConfigurationUpdater;
import com.mesosphere.sdk.config.validate.*;
import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.recovery.*;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.constrain.TimedLaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.constrain.UnconstrainedLaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.scheduler.recovery.monitor.NeverFailureMonitor;
import com.mesosphere.sdk.scheduler.recovery.monitor.TimedFailureMonitor;
import com.mesosphere.sdk.specification.DefaultPlanGenerator;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ReplacementFailurePolicy;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.validation.CapabilityValidator;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.*;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.storage.PersisterException;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This scheduler when provided with a ServiceSpec will deploy the service and recover from encountered faults
 * when possible.  Changes to the ServiceSpec will result in rolling configuration updates, or the creation of
 * new Tasks where applicable.
 */
public class DefaultScheduler extends AbstractScheduler implements Observer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScheduler.class);

    protected final ServiceSpec serviceSpec;
    protected final SchedulerFlags schedulerFlags;
    protected final Collection<Plan> plans;
    protected final ConfigStore<ServiceSpec> configStore;
    final Optional<RecoveryPlanOverriderFactory> recoveryPlanOverriderFactory;
    private final Optional<ReplacementFailurePolicy> failurePolicyOptional;
    private final ConfigurationUpdater.UpdateResult updateResult;
    protected Map<String, EndpointProducer> customEndpointProducers;
    protected TaskFailureListener taskFailureListener;
    protected TaskKiller taskKiller;
    protected OfferAccepter offerAccepter;
    protected PlanScheduler planScheduler;
    protected PlanManager deploymentPlanManager;
    protected PlanManager recoveryPlanManager;
    protected PlanCoordinator planCoordinator;
    protected Collection<Object> resources;
    private SchedulerApiServer schedulerApiServer;

    /**
     * Creates a new DefaultScheduler. See information about parameters in {@link Builder}.
     */
    public static class Builder {
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

        private Builder(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags) throws PersisterException {
            this(
                    serviceSpec,
                    schedulerFlags,
                    schedulerFlags.isStateCacheEnabled() ?
                            new PersisterCache(CuratorPersister.newBuilder(serviceSpec).build()) :
                            CuratorPersister.newBuilder(serviceSpec).build());
        }

        private Builder(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags, Persister persister) {
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
        public Builder setStateStore(StateStore stateStore) {
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
        public Builder setConfigStore(ConfigStore<ServiceSpec> configStore) {
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
        public Builder setCustomResources(Collection<Object> customResources) {
            this.customResources = customResources;
            return this;
        }

        /**
         * Specifies a custom list of configuration validators to be run when updating to a new target configuration,
         * or otherwise uses the default validators returned by {@link DefaultScheduler#defaultConfigValidators()}.
         */
        public Builder setCustomConfigValidators(Collection<ConfigValidator<ServiceSpec>> customConfigValidators) {
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
        public Builder setEndpointProducer(String name, EndpointProducer endpointProducer) {
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
        public Builder setPlansFrom(RawServiceSpec rawServiceSpec) throws ConfigStoreException {
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
        public Builder setPlans(Collection<Plan> plans) {
            this.manualPlans.clear();
            this.manualPlans.addAll(plans);
            return this;
        }

        /**
         * Sets the provided {@link PlanManager} to be the plan manager used for recovery.
         * @param recoveryPlanOverriderFactory the factory whcih generates the custom recovery plan manager
         */
        public Builder setRecoveryManagerFactory(RecoveryPlanOverriderFactory recoveryPlanOverriderFactory) {
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
        public Collection<Plan> getPlans(StateStore stateStore, ConfigStore<ServiceSpec> configStore) {
            LOGGER.info("Getting plans");
            Collection<Plan> plans;
            if (!manualPlans.isEmpty()) {
                LOGGER.info("Using manual plans");
                plans = new ArrayList<>(manualPlans);
            } else if (!yamlPlans.isEmpty()) {
                LOGGER.info("Using YAML plans");
                // Note: Any internal Plan generation must only be AFTER updating/validating the config. Otherwise plans
                // may look at the old config and mistakenly think they're COMPLETE.
                DefaultPlanGenerator planGenerator = new DefaultPlanGenerator(configStore, stateStore);
                plans = yamlPlans.entrySet().stream()
                        .map(e -> planGenerator.generate(e.getValue(), e.getKey(), serviceSpec.getPods()))
                        .collect(Collectors.toList());
            } else {
                LOGGER.info("Generating plans");
                try {
                    if (!configStore.list().isEmpty()) {
                        LOGGER.info("Generating default deploy plan.");
                        plans = Arrays.asList(
                                new DeployPlanFactory(
                                        new DefaultPhaseFactory(
                                                new DefaultStepFactory(configStore, stateStore)))
                                        .getPlan(configStore.fetch(configStore.getTargetConfig())));
                    } else {
                        plans = Collections.emptyList();
                    }
                } catch (ConfigStoreException e) {
                    LOGGER.error("Failed to generate a deploy plan.");
                    throw new IllegalStateException(e);
                }
            }

            LOGGER.info("Got plans: {}", plans.stream().map(plan -> plan.getName()).collect(Collectors.toList()));
            return plans;
        }

        /**
         * Detects whether or not the previous deployment's type was set or not and if not, sets it.
         *
         * @param stateStore The stateStore to get last deployment type from.
         * @param configStore The configStore to get plans from.
         */
        public void fixLastDeploymentType(StateStore stateStore, ConfigStore<ServiceSpec> configStore) {
            LOGGER.info("Fixing last deployment type");
            ConfigurationUpdater.UpdateResult.DeploymentType lastDeploymentType =
                    StateStoreUtils.getLastCompletedUpdateType(stateStore);
            if (lastDeploymentType.equals(ConfigurationUpdater.UpdateResult.DeploymentType.NONE)) {
                Collection<Plan> plans = getPlans(stateStore, configStore);
                Optional<Plan> deployPlan = getDeployPlan(plans);

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
            configValidators.addAll(defaultConfigValidators(getSchedulerFlags()));
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
            Optional<Plan> deployOptional = getDeployPlan(plans);
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
                    Optional.ofNullable(recoveryPlanOverriderFactory),
                    configUpdateResult);
        }

        /**
         * Given the plans specified and the update scenario, the deploy plan may be overriden by a specified update
         * plan.
         */
        public static Collection<Plan> overrideDeployPlan(
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
    }

    /**
     * Creates a new {@link Builder} based on the provided {@link ServiceSpec} describing the service, including
     * details such as the service name, the pods/tasks to be deployed, and the plans describing how the deployment
     * should be organized.
     */
    public static Builder newBuilder(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags) throws PersisterException {
        return new Builder(serviceSpec, schedulerFlags);
    }

    /**
     * Creates a new {@link Builder} based on the provided {@link ServiceSpec} describing the service, including
     * details such as the service name, the pods/tasks to be deployed, and the plans describing how the deployment
     * should be organized.
     */
    @VisibleForTesting
    public static Builder newBuilder(
            ServiceSpec serviceSpec,
            SchedulerFlags schedulerFlags,
            Persister persister) throws PersisterException {
        return new Builder(serviceSpec, schedulerFlags, persister);
    }

    /**
     * Version of {@link #createConfigStore(ServiceSpec, Collection)} which allows passing a custom {@link Persister}
     * object. Exposed for unit tests.
     */
    @VisibleForTesting
    static ConfigStore<ServiceSpec> createConfigStore(
            ServiceSpec serviceSpec, Collection<Class<?>> customDeserializationSubtypes, Persister persister)
                    throws ConfigStoreException {
        return new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec, customDeserializationSubtypes),
                persister);
    }

    /**
     * Returns the default configuration validators used by {@link DefaultScheduler} instances. Additional custom
     * validators may be added to this list using {@link Builder#setCustomConfigValidators(Collection)}.
     */
    public static List<ConfigValidator<ServiceSpec>> defaultConfigValidators(SchedulerFlags flags) {
        // Return a list to allow direct append by the caller.
        return Arrays.asList(
                new ServiceNameCannotContainDoubleUnderscores(),
                new PodSpecsCannotShrink(),
                new TaskVolumesCannotChange(),
                new PodSpecsCannotChangeNetworkRegime(),
                new PreReservationCannotChange(),
                new UserCannotChange(),
                new TLSRequiresServiceAccount(flags));
    }

    /**
     * Updates the configuration target to reflect the provided {@code serviceSpec} using the provided
     * {@code configValidators}, or stays with the previous configuration if there were validation errors.
     *
     * @param serviceSpec the service specification to use
     * @param stateStore the state store to pass to the updater
     * @param configStore the config store to pass to the updater
     * @param configValidators the list of config validators, see {@link #defaultConfigValidators()} for reasonable
     *     defaults
     * @return the config update result, which may contain one or more validation errors produced by
     *     {@code configValidators}
     */
    public static ConfigurationUpdater.UpdateResult updateConfig(
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

    private static Optional<Plan> getDeployPlan(Collection<Plan> plans) {
        List<Plan> deployPlans = plans.stream().filter(Plan::isDeployPlan).collect(Collectors.toList());

        if (deployPlans.size() == 1) {
            return Optional.of(deployPlans.get(0));
        } else if (deployPlans.size() == 0) {
            return Optional.empty();
        } else {
            String errMsg = String.format("Found multiple deploy plans: %s", deployPlans);
            LOGGER.error(errMsg);
            throw new IllegalStateException(errMsg);
        }
    }

    private Plan getDeployPlan() {
        return getDeployPlan(plans).get();
    }

    private static Collection<Plan> updateDeployPlan(Collection<Plan> plans, List<String> errors) {
        if (errors.isEmpty()) {
            return plans;
        }

        Collection<Plan> updatedPlans = new ArrayList<>();
        Plan deployPlan = getDeployPlan(plans).get();
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

    /**
     * Creates a new DefaultScheduler. See information about parameters in {@link Builder}.
     */
    protected DefaultScheduler(
            ServiceSpec serviceSpec,
            SchedulerFlags schedulerFlags,
            Collection<Object> customResources,
            Collection<Plan> plans,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Map<String, EndpointProducer> customEndpointProducers,
            Optional<RecoveryPlanOverriderFactory> recoveryPlanOverriderFactory,
            ConfigurationUpdater.UpdateResult updateResult) {
        super(stateStore);
        this.serviceSpec = serviceSpec;
        this.schedulerFlags = schedulerFlags;
        this.resources = new ArrayList<>();
        this.resources.addAll(customResources);
        this.plans = plans;
        this.configStore = configStore;
        this.customEndpointProducers = customEndpointProducers;
        this.recoveryPlanOverriderFactory = recoveryPlanOverriderFactory;
        this.failurePolicyOptional = serviceSpec.getReplacementFailurePolicy();
        this.updateResult = updateResult;
    }


    protected void initialize(SchedulerDriver driver) throws InterruptedException {
        LOGGER.info("Initializing...");
        // NOTE: We wait until this point to perform any work using configStore/stateStore.
        // We specifically avoid writing any data to ZK before registered() has been called.
        try {
            initializeGlobals(driver);
        } catch (ConfigStoreException e) {
            LOGGER.error("Failed to initialize globals.", e);
            SchedulerUtils.hardExit(SchedulerErrorCode.SCHEDULER_INITIALIZATION_FAILURE);
        }
        initializeDeploymentPlanManager();
        initializeRecoveryPlanManager();
        initializePlanCoordinator();
        initializeResources();
        initializeApiServer();
        planCoordinator.subscribe(this);
        LOGGER.info("Done initializing.");
    }

    private Collection<PlanManager> getOtherPlanManagers() {
        return plans.stream()
                .filter(plan -> !plan.isDeployPlan())
                .map(DefaultPlanManager::new)
                .collect(Collectors.toList());
    }

    private void initializeGlobals(SchedulerDriver driver) throws ConfigStoreException {
        LOGGER.info("Initializing globals...");

        taskFailureListener = new DefaultTaskFailureListener(stateStore, configStore);
        taskKiller = new DefaultTaskKiller(taskFailureListener, driver);
        offerAccepter = new OfferAccepter(Collections.singletonList(new PersistentLaunchRecorder(stateStore,
                serviceSpec)));
        planScheduler = new DefaultPlanScheduler(
                offerAccepter,
                new OfferEvaluator(
                        stateStore,
                        serviceSpec.getName(),
                        configStore.getTargetConfig(),
                        schedulerFlags,
                        Capabilities.getInstance().supportsDefaultExecutor()),
                stateStore,
                taskKiller);
    }

    /**
     * Override this function to inject your own deployment plan manager.
     */
    protected void initializeDeploymentPlanManager() {
        LOGGER.info("Initializing deployment plan manager...");
        deploymentPlanManager = new DefaultPlanManager(getDeployPlan());

        // All plans are initially created with an interrupted strategy. We generally don't want the deployment plan to
        // start out interrupted. CanaryStrategy is an exception which explicitly indicates that the deployment plan
        // should start out interrupted, but CanaryStrategies are only applied to individual Phases, not the Plan as a
        // whole.
        deploymentPlanManager.getPlan().proceed();
    }

    /**
     * Override this function to inject your own recovery plan manager.
     */
    protected void initializeRecoveryPlanManager() {
        LOGGER.info("Initializing recovery plan...");
        LaunchConstrainer launchConstrainer;
        FailureMonitor failureMonitor;

        if (failurePolicyOptional.isPresent()) {
            ReplacementFailurePolicy failurePolicy = failurePolicyOptional.get();
            launchConstrainer = new TimedLaunchConstrainer(
                    Duration.ofMinutes(failurePolicy.getMinReplaceDelayMin()));
            failureMonitor = new TimedFailureMonitor(
                    Duration.ofMinutes(failurePolicy.getPermanentFailureTimoutMin()),
                    stateStore,
                    configStore);
        } else {
            launchConstrainer = new UnconstrainedLaunchConstrainer();
            failureMonitor = new NeverFailureMonitor();
        }

        List<RecoveryPlanOverrider> overrideRecoveryPlanManagers = new ArrayList<>();
        if (recoveryPlanOverriderFactory.isPresent()) {
            LOGGER.info("Adding overriding recovery plan manager.");
            overrideRecoveryPlanManagers.add(recoveryPlanOverriderFactory.get().create(stateStore, plans));
        }

        this.recoveryPlanManager = new DefaultRecoveryPlanManager(
                stateStore,
                configStore,
                launchConstrainer,
                failureMonitor,
                overrideRecoveryPlanManagers);
    }

    protected void initializePlanCoordinator() {
        final List<PlanManager> planManagers = new ArrayList<>();
        planManagers.add(deploymentPlanManager);
        planManagers.add(recoveryPlanManager);
        planManagers.addAll(getOtherPlanManagers());
        planCoordinator = new DefaultPlanCoordinator(planManagers, planScheduler);
    }

    private void initializeResources() throws InterruptedException {
        LOGGER.info("Initializing resources...");
        resources.add(new ArtifactResource(configStore));
        resources.add(new ConfigResource<>(configStore));
        EndpointsResource endpointsResource = new EndpointsResource(stateStore, serviceSpec.getName());
        for (Map.Entry<String, EndpointProducer> entry : customEndpointProducers.entrySet()) {
            endpointsResource.setCustomEndpoint(entry.getKey(), entry.getValue());
        }
        resources.add(endpointsResource);
        resources.add(new PlansResource(planCoordinator));
        resources.add(new PodResource(taskKiller, stateStore));
        resources.add(new StateResource(stateStore, new StringPropertyDeserializer()));
    }

    private void initializeApiServer() {
        schedulerApiServer = new SchedulerApiServer(
                schedulerFlags.getApiServerPort(),
                resources,
                schedulerFlags.getApiServerInitTimeout());
        new Thread(schedulerApiServer).start();
    }

    private Optional<ResourceCleanerScheduler> getCleanerScheduler() {
        try {
            ResourceCleaner cleaner = new DefaultResourceCleaner(stateStore);
            return Optional.of(new ResourceCleanerScheduler(cleaner, offerAccepter));
        } catch (Exception ex) {
            LOGGER.error("Failed to construct ResourceCleaner", ex);
            return Optional.empty();
        }
    }

    /**
     * Receive updates from plan element state changes.  In particular on plan state changes a decision to suppress
     * or revive offers should be made.
     */
    @Override
    public void update(Observable observable) {
        if (observable == planCoordinator) {
            suppressOrRevive(planCoordinator);
            completeDeploy();
        }
    }

    private void completeDeploy() {
        if (!planCoordinator.hasOperations()) {
            StateStoreUtils.setLastCompletedUpdateType(stateStore, updateResult.getDeploymentType());
        }
    }

    protected void processOfferSet(List<Protos.Offer> offers) {
        List<Protos.Offer> localOffers = new ArrayList<>(offers);
        LOGGER.info("Processing {} {}:", localOffers.size(), localOffers.size() == 1 ? "offer" : "offers");
        for (int i = 0; i < localOffers.size(); ++i) {
            LOGGER.info("  {}: {}",
                    i + 1,
                    TextFormat.shortDebugString(localOffers.get(i)));
        }

        // Coordinate amongst all the plans via PlanCoordinator.
        final List<Protos.OfferID> acceptedOffers = new ArrayList<>();
        acceptedOffers.addAll(planCoordinator.processOffers(driver, localOffers));
        LOGGER.info(
                "Offers accepted by plan coordinator: {}",
                acceptedOffers.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()));

        List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(localOffers, acceptedOffers);

        // Resource Cleaning:
        // A ResourceCleaner ensures that reserved Resources are not leaked.  It is possible that an Agent may
        // become inoperable for long enough that Tasks resident there were relocated.  However, this Agent may
        // return at a later point and begin offering reserved Resources again.  To ensure that these unexpected
        // reserved Resources are returned to the Mesos Cluster, the Resource Cleaner performs all necessary
        // UNRESERVE and DESTROY (in the case of persistent volumes) Operations.
        // Note: If there are unused reserved resources on a dirtied offer, then it will be cleaned in the next
        // offer cycle.
        final Optional<ResourceCleanerScheduler> cleanerScheduler = getCleanerScheduler();
        if (cleanerScheduler.isPresent()) {
            List<Protos.OfferID> cleanedOffers = cleanerScheduler.get().resourceOffers(driver, unusedOffers);
            LOGGER.info("Offers accepted by resource cleaner: {}", cleanedOffers);
            acceptedOffers.addAll(cleanedOffers);
        }

        LOGGER.info(
                "Total accepted offers: {}",
                acceptedOffers.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()));

        unusedOffers = OfferUtils.filterOutAcceptedOffers(localOffers, acceptedOffers);
        LOGGER.info(
                "Unused offers to be declined: {}",
                unusedOffers.stream().map(offer -> offer.getId().getValue()).collect(Collectors.toList()));

        // Decline remaining offers.
        OfferUtils.declineOffers(driver, unusedOffers);
    }

    public boolean apiServerReady() {
        return schedulerApiServer.ready();
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        statusExecutor.execute(() -> {
            LOGGER.info("Received status update for taskId={} state={} message={} protobuf={}",
                    status.getTaskId().getValue(),
                    status.getState().toString(),
                    status.getMessage(),
                    TextFormat.shortDebugString(status));

            // Store status, then pass status to PlanManager => Plan => Steps
            try {
                stateStore.storeStatus(status);
                planCoordinator.getPlanManagers().forEach(planManager -> planManager.update(status));
                reconciler.update(status);

                if (StateStoreUtils.isSuppressed(stateStore)
                        && !StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore).isEmpty()) {
                    revive();
                }

                // If the TaskStatus contains an IP Address, store it as a property in the StateStore.
                // We expect the TaskStatus to contain an IP address in both Host or CNI networking.
                // Currently, we are always _missing_ the IP Address on TASK_LOST. We always expect it
                // on TASK_RUNNINGs
                if (status.hasContainerStatus() &&
                        status.getContainerStatus().getNetworkInfosCount() > 0 &&
                        status.getContainerStatus().getNetworkInfosList().stream()
                                .anyMatch(networkInfo -> networkInfo.getIpAddressesCount() > 0)) {
                    // Map the TaskStatus to a TaskInfo. The map will throw a StateStoreException if no such
                    // TaskInfo exists.
                    try {
                        Protos.TaskInfo taskInfo = StateStoreUtils.getTaskInfo(stateStore, status);
                        StateStoreUtils.storeTaskStatusAsProperty(stateStore, taskInfo.getName(), status);
                    } catch (StateStoreException e) {
                        LOGGER.warn("Unable to store network info for status update: " + status, e);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to update TaskStatus received from Mesos. "
                        + "This may be expected if Mesos sent stale status information: " + status, e);
            }
        });
    }
}
