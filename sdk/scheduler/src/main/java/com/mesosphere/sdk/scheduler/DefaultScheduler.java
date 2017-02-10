package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanManagerFactory;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.*;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.api.*;
import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.api.types.RestartHook;
import com.mesosphere.sdk.api.types.StringPropertyDeserializer;
import com.mesosphere.sdk.config.*;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.config.validate.PodSpecsCannotShrink;
import com.mesosphere.sdk.config.validate.TaskVolumesCannotChange;
import com.mesosphere.sdk.curator.CuratorConfigStore;
import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosCertInstaller;
import com.mesosphere.sdk.dcos.DcosCluster;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.reconciliation.DefaultReconciler;
import com.mesosphere.sdk.reconciliation.Reconciler;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.recovery.DefaultRecoveryPlanManager;
import com.mesosphere.sdk.scheduler.recovery.DefaultTaskFailureListener;
import com.mesosphere.sdk.scheduler.recovery.TaskFailureListener;
import com.mesosphere.sdk.scheduler.recovery.constrain.TimedLaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.NeverFailureMonitor;
import com.mesosphere.sdk.scheduler.recovery.monitor.TimedFailureMonitor;
import com.mesosphere.sdk.specification.DefaultPlanGenerator;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ReplacementFailurePolicy;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.validation.CapabilityValidator;
import com.mesosphere.sdk.specification.yaml.RawPlan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * This scheduler when provided with a ServiceSpec will deploy the service and recover from encountered faults
 * when possible.  Changes to the ServiceSpec will result in rolling configuration updates, or the creation of
 * new Tasks where applicable.
 */
public class DefaultScheduler implements Scheduler, Observer {
    protected static final String UNINSTALL_INCOMPLETE_ERROR_MESSAGE = "Framework has been removed";
    protected static final String UNINSTALL_INSTRUCTIONS_URI =
            "https://docs.mesosphere.com/latest/usage/managing-services/uninstall/";

    /**
     * Time to wait for the executor thread to terminate. Only used by unit tests.
     *
     * Default: 10 seconds
     */
    protected static final Integer AWAIT_TERMINATION_TIMEOUT_MS = 10 * 1000;

    /**
     * Time to wait during scheduler initialization for API resources to be initialized. This should be
     * near-instantaneous, we just have an explicit deadline to avoid the potential for waiting indefinitely.
     *
     * Default: 60 seconds
     */
    protected static final Integer AWAIT_RESOURCES_TIMEOUT_MS = 60 * 1000;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScheduler.class);

    protected final ExecutorService executor = Executors.newFixedThreadPool(1);

    protected final BlockingQueue<Collection<Object>> resourcesQueue = new ArrayBlockingQueue<>(1);
    // Mesos may call registered() multiple times in the lifespan of a Scheduler process, specifically when there's
    // master re-election. Avoid performing initialization multiple times, which would cause resourcesQueue to be stuck.
    protected final AtomicBoolean isAlreadyRegistered = new AtomicBoolean(false);

    protected final ServiceSpec serviceSpec;
    protected final Collection<Plan> plans;
    protected final StateStore stateStore;
    protected final ConfigStore<ServiceSpec> configStore;
    /**
     * Minimum duration to wait in milliseconds before deciding that a task has failed,
     * or zero to disable this detection.
     */
    protected final Optional<Integer> permanentFailureTimeoutMs;
    /**
     * Minimum duration to wait in milliseconds between destructive recovery operations,
     * such as destroying a failed task.
     */
    protected final int destructiveRecoveryDelayMs;
    private final Optional<RecoveryPlanManagerFactory> recoveryPlanManagerFactoryOptional;

    protected SchedulerDriver driver;
    protected OfferRequirementProvider offerRequirementProvider;
    protected Map<String, EndpointProducer> customEndpointProducers;
    protected Optional<RestartHook> customRestartHook;
    protected Reconciler reconciler;
    protected TaskFailureListener taskFailureListener;
    protected TaskKiller taskKiller;
    protected OfferAccepter offerAccepter;
    protected PlanScheduler planScheduler;
    protected PlanManager deploymentPlanManager;
    protected PlanManager recoveryPlanManager;
    protected PlanCoordinator planCoordinator;
    protected Collection<Object> resources;

    /**
     * Builder class for {@link DefaultScheduler}s. Uses provided custom values or reasonable defaults.
     *
     * Instances may be created via {@link DefaultScheduler#newBuilder(ServiceSpec)}.
     */
    public static class Builder {
        private final ServiceSpec serviceSpec;

        // When these optionals are unset, we use default values:
        private Optional<StateStore> stateStoreOptional = Optional.empty();
        private Optional<ConfigStore<ServiceSpec>> configStoreOptional = Optional.empty();
        private Optional<Collection<ConfigValidator<ServiceSpec>>> configValidatorsOptional = Optional.empty();
        private Optional<RestartHook> restartHookOptional = Optional.empty();

        // When these collections are empty, we don't do anything extra:
        private final List<Plan> manualPlans = new ArrayList<>();
        private final Map<String, RawPlan> yamlPlans = new HashMap<>();
        private final Map<String, EndpointProducer> endpointProducers = new HashMap<>();
        private Capabilities capabilities;
        private RecoveryPlanManagerFactory recoveryPlanManagerFactory;

        private Builder(ServiceSpec serviceSpec) {
            this.serviceSpec = serviceSpec;
        }

        /**
         * Returns the {@link ServiceSpec} which was provided via the constructor.
         */
        public ServiceSpec getServiceSpec() {
            return serviceSpec;
        }

        /**
         * Specifies a custom {@link StateStore}, otherwise the return value of
         * {@link DefaultScheduler#createStateStore(ServiceSpec)} will be used.
         *
         * The state store persists copies of task information and task status for all tasks running in the service.
         *
         * @throws IllegalStateException if the state store is already set, via a previous call to either
         *     {@link #setStateStore(StateStore)} or to {@link #getStateStore()}
         */
        public Builder setStateStore(StateStore stateStore) {
            if (this.stateStoreOptional.isPresent()) {
                // Any customization of the state store must be applied BEFORE getStateStore() is ever called.
                throw new IllegalStateException("State store is already set. Was getStateStore() invoked before this?");
            }
            this.stateStoreOptional = Optional.of(stateStore);
            return this;
        }

        /**
         * Returns the {@link StateStore} provided via {@link #setStateStore(StateStore)}, or a reasonable default
         * created via {@link DefaultScheduler#createStateStore(ServiceSpec)}.
         *
         * In order to avoid cohesiveness issues between this setting and the {@link #build()} step,
         * {@link #setStateStore(StateStore)} may not be invoked after this has been called.
         */
        public StateStore getStateStore() {
            if (!stateStoreOptional.isPresent()) {
                setStateStore(createStateStore(serviceSpec));
            }
            return stateStoreOptional.get();
        }

        /**
         * Specifies a custom {@link ConfigStore}, otherwise the return value of
         * {@link DefaultScheduler#createConfigStore(ServiceSpec)} will be used.
         *
         * The config store persists a copy of the current configuration ('target' configuration),
         * while also storing historical configurations.
         */
        public Builder setConfigStore(ConfigStore<ServiceSpec> configStore) {
            this.configStoreOptional = Optional.of(configStore);
            return this;
        }

        /**
         * Specifies a custom list of configuration validators to be run when updating to a new target configuration,
         * or otherwise uses the default validators returned by {@link DefaultScheduler#defaultConfigValidators()}.
         *
         * These validators are only used if {@link #withOfferRequirementProvider(OfferRequirementProvider)} was NOT
         * invoked.
         */
        public Builder setConfigValidators(Collection<ConfigValidator<ServiceSpec>> configValidators) {
            this.configValidatorsOptional = Optional.of(configValidators);
            return this;
        }

        /**
         * Specifies a custom {@link RestartHook} to be added to the /pods/<name>/restart and /pods/<name>/replace APIs.
         * This may be used to define any custom teardown behavior which should be invoked before a task is restarted
         * and/or replaced.
         */
        public Builder setRestartHook(RestartHook restartHook) {
            this.restartHookOptional = Optional.of(restartHook);
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
         *     {@link #setPlans(Collection)}
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
         *     {@link #setPlans(RawServiceSpec)}
         */
        public Builder setPlans(Collection<Plan> plans) {
            this.manualPlans.clear();
            this.manualPlans.addAll(plans);
            return this;
        }

        /**
         * Sets the provided {@link PlanManager} to be the plan manager used for recovery.
         * @param recoveryManagerFactory the factory whcih generates the custom recovery plan manager
         */
        public Builder setRecoveryManagerFactory(RecoveryPlanManagerFactory recoveryManagerFactory) {
            this.recoveryPlanManagerFactory = recoveryManagerFactory;
            return this;
        }

        /**
         * Allow setting the capabilities of the DC/OS cluster.  Generally this should not be used except in test
         * environments as it may return incorrect information regarding the capabilities of the DC/OS cluster.
         * @param capabilities the capabilities used to validate the ServiceSpec
         */
        @VisibleForTesting
        public Builder setCapabilities(Capabilities capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        /**
         * Creates a new scheduler instance with the provided values or their defaults.
         *
         * @return a new scheduler instance
         * @throws IllegalStateException if config validation failed when updating the target config for a default
         *     {@link OfferRequirementProvider}, or if creating a default {@link ConfigStore} failed
         */
        public DefaultScheduler build() {
            if (capabilities == null) {
                this.capabilities = new Capabilities(new DcosCluster());
            }

            try {
                new CapabilityValidator(capabilities).validate(serviceSpec);
            } catch (CapabilityValidator.CapabilityValidationException e) {
                throw new IllegalStateException("Failed to validate provided ServiceSpec", e);
            }

            // Get custom or default state store (defaults handled by getStateStore())::
            final StateStore stateStore = getStateStore();

            // Get custom or default config store:
            final ConfigStore<ServiceSpec> configStore;
            if (configStoreOptional.isPresent()) {
                configStore = configStoreOptional.get();
            } else {
                try {
                    configStore = createConfigStore(serviceSpec);
                } catch (ConfigStoreException e) {
                    throw new IllegalStateException("Failed to create default config store", e);
                }
            }

            // Update/validate config as needed to reflect the new service spec:
            final ConfigurationUpdater.UpdateResult configUpdateResult = updateConfig(
                    serviceSpec,
                    stateStore,
                    configStore,
                    configValidatorsOptional.orElse(defaultConfigValidators()));
            if (!configUpdateResult.errors.isEmpty()) {
                throw new IllegalStateException(String.format(
                        "Failed to update configuration due to errors with configuration %s: %s",
                        configUpdateResult.targetId, configUpdateResult.errors));
            }

            // Get or generate plans. Any plan generation is against the service spec that we just updated:
            final List<Plan> plans;
            if (!manualPlans.isEmpty()) {
                plans = new ArrayList<>(manualPlans);
            } else if (!yamlPlans.isEmpty()) {
                // Note: Any internal Plan generation must only be AFTER updating/validating the config. Otherwise plans
                // may look at the old config and mistakenly think they're COMPLETE.
                DefaultPlanGenerator planGenerator = new DefaultPlanGenerator(configStore, stateStore);
                plans = yamlPlans.entrySet().stream()
                        .map(e -> planGenerator.generate(e.getValue(), e.getKey(), serviceSpec.getPods()))
                        .collect(Collectors.toList());
            } else {
                // A default deployment plan will automatically be generated:
                plans = Collections.emptyList();
            }

            return new DefaultScheduler(
                    serviceSpec,
                    plans,
                    stateStore,
                    configStore,
                    new DefaultOfferRequirementProvider(stateStore, serviceSpec.getName(), configUpdateResult.targetId),
                    endpointProducers,
                    restartHookOptional,
                    Optional.ofNullable(recoveryPlanManagerFactory));
        }
    }

    /**
     * Creates a new {@link Builder} based on the provided {@link ServiceSpec} describing the service, including
     * details such as the service name, the pods/tasks to be deployed, and the plans describing how the deployment
     * should be organized.
     */
    public static Builder newBuilder(ServiceSpec serviceSpec) {
        return new Builder(serviceSpec);
    }

    /**
     * Creates and returns a new default {@link StateStore} suitable for passing to
     * {@link DefaultScheduler#create}. To avoid the risk of zookeeper consistency issues, the
     * returned storage MUST NOT be written to before the Scheduler has registered with Mesos, as
     * signified by a call to {@link DefaultScheduler#registered
     * (SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)} (SchedulerDriver,
     * org.apache.mesos.Protos.FrameworkID, org.apache.mesos.Protos.MasterInfo)}
     *
     * @param zkConnectionString the zookeeper connection string to be passed to curator (host:port)
     */
    public static StateStore createStateStore(ServiceSpec serviceSpec, String zkConnectionString) {
        return StateStoreCache.getInstance(new CuratorStateStore(serviceSpec.getName(), zkConnectionString));
    }

    /**
     * Calls {@link #createStateStore(ServiceSpec, String)} with the specification name as the
     * {@code frameworkName} and with a reasonable default for {@code zkConnectionString}.
     *
     * @see DcosConstants#MESOS_MASTER_ZK_CONNECTION_STRING
     */
    public static StateStore createStateStore(ServiceSpec serviceSpec) {
        return createStateStore(serviceSpec, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    /**
     * Creates and returns a new default {@link ConfigStore} suitable for passing to
     * {@link DefaultScheduler#create}. To avoid the risk of zookeeper consistency issues, the
     * returned storage MUST NOT be written to before the Scheduler has registered with Mesos, as
     * signified by a call to {@link #registered(SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)}.
     *
     * @param zkConnectionString the zookeeper connection string to be passed to curator (host:port)
     * @param customDeserializationSubtypes custom subtypes to register for deserialization of
     *     {@link DefaultServiceSpec}, mainly useful for deserializing custom implementations of
     *     {@link com.mesosphere.sdk.offer.evaluate.placement.PlacementRule}s.
     * @throws ConfigStoreException if validating serialization of the config fails, e.g. due to an
     *                              unrecognized deserialization type
     */
    public static ConfigStore<ServiceSpec> createConfigStore(
            ServiceSpec serviceSpec,
            String zkConnectionString,
            Collection<Class<?>> customDeserializationSubtypes) throws ConfigStoreException {
        return new CuratorConfigStore<>(
                DefaultServiceSpec.getFactory(serviceSpec, customDeserializationSubtypes),
                serviceSpec.getName(),
                zkConnectionString);
    }

    /**
     * Calls {@link #createConfigStore(ServiceSpec, String, Collection))} with the specification name as
     * the {@code frameworkName} and with a reasonable default for {@code zkConnectionString}.
     *
     * @param zkConnectionString the zookeeper connection string to be passed to curator (host:port)
     * @throws ConfigStoreException if validating serialization of the config fails, e.g. due to an
     *                              unrecognized deserialization type
     */
    public static ConfigStore<ServiceSpec> createConfigStore(ServiceSpec serviceSpec, String zkConnectionString)
            throws ConfigStoreException {
        return createConfigStore(serviceSpec, zkConnectionString, Collections.emptyList());
    }

    /**
     * Calls {@link #createConfigStore(ServiceSpec, Collection)} with an empty list of
     * custom deserialization types.
     *
     * @throws ConfigStoreException if validating serialization of the config fails, e.g. due to an
     *                              unrecognized deserialization type
     * @see DcosConstants#MESOS_MASTER_ZK_CONNECTION_STRING
     */
    public static ConfigStore<ServiceSpec> createConfigStore(ServiceSpec serviceSpec) throws ConfigStoreException {
        return createConfigStore(serviceSpec, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    /**
     * Returns the default configuration validators:
     * - Task sets cannot shrink (each set's task count must stay the same or increase).
     * - Task volumes cannot be changed.
     * <p>
     * This function may be used to get the default validators and add more to the list when
     * constructing the {@link DefaultScheduler}.
     */
    public static List<ConfigValidator<ServiceSpec>> defaultConfigValidators() {
        // Return a list to allow direct append by the caller.
        return Arrays.asList(
                new PodSpecsCannotShrink(),
                new TaskVolumesCannotChange());
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

    /**
     * Creates a new DefaultScheduler. See information about parameters in {@link Builder}.
     */
    protected DefaultScheduler(
            ServiceSpec serviceSpec,
            Collection<Plan> plans,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            OfferRequirementProvider offerRequirementProvider,
            Map<String, EndpointProducer> customEndpointProducers,
            Optional<RestartHook> restartHookOptional,
            Optional<RecoveryPlanManagerFactory> recoveryPlanManagerFactoryOptional) {
        this.serviceSpec = serviceSpec;
        this.plans = plans;
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.offerRequirementProvider = offerRequirementProvider;
        this.customEndpointProducers = customEndpointProducers;
        this.customRestartHook = restartHookOptional;
        this.recoveryPlanManagerFactoryOptional = recoveryPlanManagerFactoryOptional;
        ReplacementFailurePolicy replacementFailurePolicy = serviceSpec.getReplacementFailurePolicy();
        if (replacementFailurePolicy != null) {
            // interpret unset/null as disabled:
            this.permanentFailureTimeoutMs =
                    Optional.ofNullable(replacementFailurePolicy.getPermanentFailureTimoutMins());
            // interpret unset/null as zero delay:
            this.destructiveRecoveryDelayMs = replacementFailurePolicy.getMinReplaceDelayMins() == null
                    ? 0 : replacementFailurePolicy.getMinReplaceDelayMins();
        } else {
            // default values if policy section is unset:
            this.permanentFailureTimeoutMs = Optional.of(ReplacementFailurePolicy.PERMANENT_FAILURE_DELAY_MS);
            this.destructiveRecoveryDelayMs = ReplacementFailurePolicy.DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_MS;
        }
    }

    public Collection<Object> getResources() throws InterruptedException {
        if (resources == null) {
            // Wait up to 60 seconds for resources to be available. This should be
            // near-instantaneous, we just have an explicit deadline to avoid the potential for
            // waiting indefinitely.
            resources = resourcesQueue.poll(AWAIT_RESOURCES_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (resources == null) {
                throw new RuntimeException(String.format(
                        "Timed out waiting %dms for resources from scheduler", AWAIT_RESOURCES_TIMEOUT_MS));
            }
        }

        return resources;
    }

    @VisibleForTesting
    void awaitTermination() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(AWAIT_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void initialize(SchedulerDriver driver) throws InterruptedException {
        LOGGER.info("Initializing...");
        // NOTE: We wait until this point to perform any work using configStore/stateStore.
        // We specifically avoid writing any data to ZK before registered() has been called.
        initializeGlobals(driver);
        initializeDeploymentPlanManager();
        initializeRecoveryPlanManager();
        initializePlanCoordinator();
        initializeResources();
        DcosCertInstaller.installCertificate(System.getenv("JAVA_HOME"));
        planCoordinator.subscribe(this);
        LOGGER.info("Done initializing.");
    }

    private Collection<PlanManager> getOtherPlanManagers() {
        return plans.stream()
                .filter(plan -> !PlanUtils.isDeployPlan(plan))
                .map(plan -> new DefaultPlanManager(plan))
                .collect(Collectors.toList());
    }

    private void initializeGlobals(SchedulerDriver driver) {
        LOGGER.info("Initializing globals...");
        taskFailureListener = new DefaultTaskFailureListener(stateStore);
        taskKiller = new DefaultTaskKiller(taskFailureListener, driver);
        reconciler = new DefaultReconciler(stateStore);
        offerAccepter = new OfferAccepter(Arrays.asList(new PersistentLaunchRecorder(stateStore, serviceSpec)));
        planScheduler = new DefaultPlanScheduler(
                offerAccepter,
                new OfferEvaluator(stateStore, offerRequirementProvider), stateStore, taskKiller);
    }

    /**
     * Override this function to inject your own deployment plan manager.
     */
    protected void initializeDeploymentPlanManager() {
        LOGGER.info("Initializing deployment plan...");
        Optional<Plan> deploy = plans.stream()
                .filter(plan -> PlanUtils.isDeployPlan(plan))
                .findFirst();
        Plan deployPlan;
        if (!deploy.isPresent()) {
            LOGGER.info("No deploy plan provided. Generating one");
             deployPlan = new DefaultPlanFactory(
                     new DefaultPhaseFactory(new DefaultStepFactory(configStore, stateStore)))
                     .getPlan(serviceSpec);
        } else {
            deployPlan = deploy.get();
        }
        deploymentPlanManager = new DefaultPlanManager(deployPlan);

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
        LaunchConstrainer launchConstrainer = new TimedLaunchConstrainer(Duration.ofMillis(destructiveRecoveryDelayMs));
        FailureMonitor failureMonitor =
                permanentFailureTimeoutMs.isPresent()
                        ? new TimedFailureMonitor(Duration.ofMillis(permanentFailureTimeoutMs.get()))
                        : new NeverFailureMonitor();
        if (recoveryPlanManagerFactoryOptional.isPresent()) {
            LOGGER.info("Using custom recovery plan manager.");
            this.recoveryPlanManager = recoveryPlanManagerFactoryOptional.get().create(
                    stateStore,
                    configStore,
                    launchConstrainer,
                    failureMonitor,
                    plans);
        } else {
            LOGGER.info("Using default recovery plan manager.");
            this.recoveryPlanManager = new DefaultRecoveryPlanManager(
                    stateStore,
                    configStore,
                    launchConstrainer,
                    failureMonitor);
        }
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
        Collection<Object> resources = new ArrayList<>();
        resources.add(new ArtifactResource(configStore));
        resources.add(new ConfigResource<>(configStore));
        EndpointsResource endpointsResource = new EndpointsResource(stateStore, serviceSpec.getName());
        for (Map.Entry<String, EndpointProducer> entry : customEndpointProducers.entrySet()) {
            endpointsResource.setCustomEndpoint(entry.getKey(), entry.getValue());
        }
        resources.add(endpointsResource);
        resources.add(new PlansResource(planCoordinator));
        if (customRestartHook.isPresent()) {
            resources.add(new PodsResource(taskKiller, stateStore, customRestartHook.get()));
        } else {
            resources.add(new PodsResource(taskKiller, stateStore));
        }
        resources.add(new StateResource(stateStore, new StringPropertyDeserializer()));
        resources.add(new TaskResource(stateStore, taskKiller, serviceSpec.getName()));
        // use add() instead of put(): throw exception instead of waiting indefinitely
        resourcesQueue.add(resources);
    }

    private void declineOffers(SchedulerDriver driver, List<Protos.OfferID> acceptedOffers, List<Protos.Offer> offers) {
        final List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(offers, acceptedOffers);
        LOGGER.info("Declining {} unused offers:", unusedOffers.size());
        unusedOffers.stream().forEach(offer -> {
            final Protos.OfferID offerId = offer.getId();
            LOGGER.info("  {}", offerId.getValue());
            driver.declineOffer(offerId);
        });
    }

    private Optional<ResourceCleanerScheduler> getCleanerScheduler() {
        try {
            ResourceCleaner cleaner = new ResourceCleaner(stateStore);
            return Optional.of(new ResourceCleanerScheduler(cleaner, offerAccepter));
        } catch (Exception ex) {
            LOGGER.error("Failed to construct ResourceCleaner", ex);
            return Optional.empty();
        }
    }

    @Override
    public void registered(SchedulerDriver driver, Protos.FrameworkID frameworkId, Protos.MasterInfo masterInfo) {
        if (isAlreadyRegistered.getAndSet(true)) {
            // This may occur as the result of a master election.
            LOGGER.info("Already registered, calling reregistered()");
            reregistered(driver, masterInfo);
            return;
        }

        LOGGER.info("Registered framework with frameworkId: {}", frameworkId.getValue());
        try {
            initialize(driver);
        } catch (InterruptedException e) {
            LOGGER.error("Initialization failed with exception: ", e);
            SchedulerUtils.hardExit(SchedulerErrorCode.INITIALIZATION_FAILURE);
        }

        try {
            stateStore.storeFrameworkId(frameworkId);
        } catch (Exception e) {
            LOGGER.error(String.format(
                    "Unable to store registered framework ID '%s'", frameworkId.getValue()), e);
            SchedulerUtils.hardExit(SchedulerErrorCode.REGISTRATION_FAILURE);
        }

        this.driver = driver;
        reconciler.start();
        reconciler.reconcile(driver);
        revive();
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        LOGGER.info("Re-registered with master: {}", TextFormat.shortDebugString(masterInfo));
        reconciler.start();
        reconciler.reconcile(driver);
        suppressOrRevive();
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offersToProcess) {
        List<Protos.Offer> offers = new ArrayList<>(offersToProcess);
        executor.execute(() -> {
            LOGGER.info("Received {} {}:", offers.size(), offers.size() == 1 ? "offer" : "offers");
            for (int i = 0; i < offers.size(); ++i) {
                LOGGER.info("  {}: {}", i + 1, TextFormat.shortDebugString(offers.get(i)));
            }

            // Task Reconciliation:
            // Task Reconciliation must complete before any Tasks may be launched.  It ensures that a Scheduler and
            // Mesos have agreed upon the state of all Tasks of interest to the scheduler.
            // http://mesos.apache.org/documentation/latest/reconciliation/
            reconciler.reconcile(driver);
            if (!reconciler.isReconciled()) {
                LOGGER.info("Reconciliation is still in progress, declining all offers.");
                declineOffers(driver, Collections.emptyList(), offers);
                return;
            }

            // Coordinate amongst all the plans via PlanCoordinator.
            final List<Protos.OfferID> acceptedOffers = new ArrayList<>();
            acceptedOffers.addAll(planCoordinator.processOffers(driver, offers));

            List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(offers, acceptedOffers);
            offers.clear();
            offers.addAll(unusedOffers);

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
                acceptedOffers.addAll(cleanerScheduler.get().resourceOffers(driver, offers));
            }

            unusedOffers = OfferUtils.filterOutAcceptedOffers(offers, acceptedOffers);
            offers.clear();
            offers.addAll(unusedOffers);

            // Decline remaining offers.
            declineOffers(driver, acceptedOffers, offers);
        });
    }

    @Override
    public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerId) {
        LOGGER.error("Rescinding offers is not supported.");
        SchedulerUtils.hardExit(SchedulerErrorCode.OFFER_RESCINDED);
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("Received status update for taskId={} state={} message='{}'",
                        status.getTaskId().getValue(),
                        status.getState().toString(),
                        status.getMessage());

                // Store status, then pass status to PlanManager => Plan => Steps
                try {
                    stateStore.storeStatus(status);
                    planCoordinator.getPlanManagers().stream()
                            .forEach(planManager -> planManager.update(status));
                    reconciler.update(status);

                    if (stateStore.isSuppressed()
                            && !StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore).isEmpty()) {
                        revive();
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to update TaskStatus received from Mesos. "
                            + "This may be expected if Mesos sent stale status information: " + status, e);
                }
            }
        });
    }

    @Override
    public void frameworkMessage(
            SchedulerDriver driver,
            Protos.ExecutorID executorId,
            Protos.SlaveID slaveId,
            byte[] data) {
        LOGGER.error("Received a Framework Message, but don't know how to process it");
    }

    @Override
    public void disconnected(SchedulerDriver driver) {
        LOGGER.error("Disconnected from Master.");
        SchedulerUtils.hardExit(SchedulerErrorCode.DISCONNECTED);
    }

    @Override
    public void slaveLost(SchedulerDriver driver, Protos.SlaveID agentId) {
        // TODO: Add recovery optimizations relevant to loss of an Agent.  TaskStatus updates are sufficient now.
        LOGGER.warn("Agent lost: {}", agentId.getValue());
    }

    @Override
    public void executorLost(SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, int status) {
        // TODO: Add recovery optimizations relevant to loss of an Executor.  TaskStatus updates are sufficient now.
        LOGGER.warn("Lost Executor: {} on Agent: {}", executorId.getValue(), slaveId.getValue());
    }

    @Override
    public void error(SchedulerDriver driver, String message) {
        LOGGER.error("SchedulerDriver failed with message: " + message);

        // Update or remove this when uninstall is solved:
        if (message.contains(UNINSTALL_INCOMPLETE_ERROR_MESSAGE)) {
            // Scenario:
            // - User installs service X
            // - X registers against a new framework ID, then stores that ID in ZK
            // - User uninstalls service X without wiping ZK and/or resources
            // - User reinstalls service X
            // - X sees previous framework ID in ZK and attempts to register against it
            // - Mesos returns this error because that framework ID is no longer available for use
            LOGGER.error("This error is usually the result of an incomplete cleanup of Zookeeper "
                    + "and/or reserved resources following a previous uninstall of the service.");
            LOGGER.error("Please uninstall this service, read and perform the steps described at "
                    + UNINSTALL_INSTRUCTIONS_URI + " to delete the reserved resources, and then "
                    + "install this service once more.");
        }

        SchedulerUtils.hardExit(SchedulerErrorCode.ERROR);
    }

    private void suppressOrRevive() {
        if (planCoordinator.hasOperations()) {
            if (stateStore.isSuppressed()) {
                revive();
            } else {
                LOGGER.info("Already revived.");
            }
        } else {
            if (stateStore.isSuppressed()) {
                LOGGER.info("Already suppressed.");
            } else {
                suppress();
            }
        }
    }

    private void suppress() {
        LOGGER.info("Suppressing offers.");
        driver.suppressOffers();
        stateStore.setSuppressed(true);
    }

    private void revive() {
        LOGGER.info("Reviving offers.");
        driver.reviveOffers();
        stateStore.setSuppressed(false);
    }

    @Override
    public void update(Observable observable) {
        if (observable == planCoordinator) {
            suppressOrRevive();
        }
    }
}
