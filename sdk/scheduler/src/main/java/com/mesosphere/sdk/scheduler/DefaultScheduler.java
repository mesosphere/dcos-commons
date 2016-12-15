package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.api.ConfigResource;
import com.mesosphere.sdk.api.PlansResource;
import com.mesosphere.sdk.api.StateResource;
import com.mesosphere.sdk.api.TaskResource;
import com.mesosphere.sdk.api.types.StringPropertyDeserializer;
import com.mesosphere.sdk.config.*;
import com.mesosphere.sdk.config.validate.ConfigurationValidator;
import com.mesosphere.sdk.config.validate.PodSpecsCannotShrink;
import com.mesosphere.sdk.config.validate.TaskVolumesCannotChange;
import com.mesosphere.sdk.curator.CuratorConfigStore;
import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.dcos.DcosCertInstaller;
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
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ReplacementFailurePolicy;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.PersistentOperationRecorder;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * This scheduler when provided with a ServiceSpec will deploy the service and recover from encountered faults
 * when possible.  Changes to the ServiceSpec will result in rolling configuration updates, or the creation of
 * new Tasks where applicable.
 */
public class DefaultScheduler implements Scheduler, Observer {
    protected static final String UNINSTALL_INCOMPLETE_ERROR_MESSAGE = "Framework has been removed";
    protected static final String UNINSTALL_INSTRUCTIONS_URI =
            "https://docs.mesosphere.com/latest/usage/managing-services/uninstall/";

    protected static final Integer DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_SEC = 10 * 60;
    protected static final Integer PERMANENT_FAILURE_DELAY_SEC = 20 * 60;
    protected static final Integer AWAIT_TERMINATION_TIMEOUT_MS = 10000;
    protected static final Integer AWAIT_RESOURCES_TIMEOUT_MS = 60000;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScheduler.class);

    protected final ExecutorService executor = Executors.newFixedThreadPool(1);
    protected final BlockingQueue<Collection<Object>> resourcesQueue = new ArrayBlockingQueue<>(1);
    protected final ServiceSpec serviceSpec;
    protected final Collection<Plan> plans;
    protected final StateStore stateStore;
    protected final ConfigStore<ServiceSpec> configStore;
    protected final Collection<ConfigurationValidator<ServiceSpec>> configValidators;
    protected final Optional<Integer> permanentFailureTimeoutSec;
    protected final Integer destructiveRecoveryDelaySec;

    protected SchedulerDriver driver;
    protected OfferRequirementProvider offerRequirementProvider;
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
     * Returns a new {@link DefaultScheduler} instance using the provided
     * {@link ServiceSpec}, {@link ConfigStore}, and {@link StateStore}.
     *
     * @param serviceSpec specification containing service name and tasks to be deployed
     * @param stateStore  framework state storage, which must not be written to before the scheduler
     *                    has been registered with mesos as indicated by a call to
     *                    {@link DefaultScheduler#registered(SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)} (
     *SchedulerDriver, com.mesosphere.sdk.Protos.FrameworkID, com.mesosphere.sdk.Protos.MasterInfo)
     * @param configStore framework config storage, which must not be written to before the scheduler
     *                    has been registered with mesos as indicated by a call to {@link DefaultScheduler#registered(
     *SchedulerDriver, org.apache.mesos.Protos.FrameworkID, org.apache.mesos.Protos.MasterInfo)
     * @see #createStateStore(String, String)
     */
    public static DefaultScheduler create(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            OfferRequirementProvider offerRequirementProvider) {
        return create(
                serviceSpec,
                Collections.emptyList(),
                stateStore,
                configStore,
                offerRequirementProvider,
                defaultConfigValidators());
    }

    /**
     * Returns a new {@link DefaultScheduler} instance using the provided
     * {@link ServiceSpec}, {@link ConfigStore}, and {@link StateStore}.
     *
     * @param serviceSpec specification containing service name and tasks to be deployed
     *
     * @param stateStore  framework state storage, which must not be written to before the scheduler
     *                    has been registered with mesos as indicated by a call to
     *                    {@link DefaultScheduler#registered(SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)} (
     *SchedulerDriver, com.mesosphere.sdk.Protos.FrameworkID, com.mesosphere.sdk.Protos.MasterInfo)
     * @param configStore framework config storage, which must not be written to before the scheduler
     *                    has been registered with mesos as indicated by a call to {@link DefaultScheduler#registered(
     *SchedulerDriver, org.apache.mesos.Protos.FrameworkID, org.apache.mesos.Protos.MasterInfo)
     * @see #createStateStore(String, String)
     */
    public static DefaultScheduler create(
            ServiceSpec serviceSpec,
            Collection<Plan> plans,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            OfferRequirementProvider offerRequirementProvider) {
        return create(
                serviceSpec,
                plans,
                stateStore,
                configStore,
                offerRequirementProvider,
                defaultConfigValidators());
    }

    /**
     * Returns a new {@link DefaultScheduler} instance using the provided
     * {@code frameworkName}, {@link PlanManager} stack, and {@link StateStore}.
     *
     * @param serviceSpec      specification containing service name and tasks to be deployed
     * @param stateStore       framework state storage, which must not be written to before the scheduler
     *                         has been registered with mesos as indicated by a call to
     *                         {@link DefaultScheduler#registered
     *                         (SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)}
     *                         (SchedulerDriver, com.mesosphere.sdk.Protos.FrameworkID,
     *                         com.mesosphere.sdk.Protos.MasterInfo)
     * @param configStore      framework config storage, which must not be written to before the scheduler
     *                         has been registered with mesos as indicated by a call to
     *                         {@link DefaultScheduler#registered
     *                         (SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)}
     *                         (SchedulerDriver, com.mesosphere.sdk.Protos.FrameworkID,
     *                         com.mesosphere.sdk.Protos.MasterInfo)
     * @param configValidators configuration validators to be used when evaluating config changes
     */
    public static DefaultScheduler create(
            ServiceSpec serviceSpec,
            Collection<Plan> plans,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            OfferRequirementProvider offerRequirementProvider,
            Collection<ConfigurationValidator<ServiceSpec>> configValidators) {
        ReplacementFailurePolicy replacementFailurePolicy = serviceSpec.getReplacementFailurePolicy();
        Integer permanentFailureTimeoutSec = PERMANENT_FAILURE_DELAY_SEC;
        int destructiveRecoveryDelaySec = DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_SEC;
        if (replacementFailurePolicy != null) {
            permanentFailureTimeoutSec = replacementFailurePolicy.getPermanentFailureTimoutMs();
            destructiveRecoveryDelaySec = replacementFailurePolicy.getMinReplaceDelayMs();
        }
        return new DefaultScheduler(
                serviceSpec,
                plans,
                stateStore,
                configStore,
                offerRequirementProvider,
                configValidators,
                Optional.of(permanentFailureTimeoutSec),
                destructiveRecoveryDelaySec);
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
        return StateStoreCache.getInstance(new CuratorStateStore(
                serviceSpec.getName(),
                zkConnectionString));
    }

    public static ConfigurationUpdater.UpdateResult updateConfig(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore) {
        LOGGER.info("Updating config...");
        ConfigurationUpdater<ServiceSpec> configurationUpdater =
                new DefaultConfigurationUpdater(
                        stateStore,
                        configStore,
                        DefaultServiceSpec.getComparatorInstance(),
                        DefaultScheduler.defaultConfigValidators());
        final ConfigurationUpdater.UpdateResult configUpdateResult;
        try {
            configUpdateResult = configurationUpdater.updateConfiguration(serviceSpec);
            return configUpdateResult;
        } catch (ConfigStoreException e) {
            LOGGER.error("Fatal error when performing configuration update. Service exiting.", e);
            throw new IllegalStateException(e);
        }
    }

    public static OfferRequirementProvider createOfferRequirementProvider(
            StateStore stateStore, UUID targetConfigId) {
        return new DefaultOfferRequirementProvider(
                new DefaultTaskConfigRouter(),
                stateStore,
                targetConfigId);
    }

    /**
     * Calls {@link #createStateStore(ServiceSpec, String)} with the specification name as the
     * {@code frameworkName} and with a reasonable default for {@code zkConnectionString}.
     *
     * @see DcosConstants#MESOS_MASTER_ZK_CONNECTION_STRING
     */
    public static StateStore createStateStore(ServiceSpec serviceSpecification) {
        return createStateStore(
                serviceSpecification,
                DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    /**
     * Creates and returns a new default {@link ConfigStore} suitable for passing to
     * {@link DefaultScheduler#create}. To avoid the risk of zookeeper consistency issues, the
     * returned storage MUST NOT be written to before the Scheduler has registered with Mesos, as
     * signified by a call to
     * {@link DefaultScheduler#registered(SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)} (SchedulerDriver,
     * org.apache.mesos.Protos.FrameworkID, org.apache.mesos.Protos.MasterInfo)}.
     *
     * @param zkConnectionString            the zookeeper connection string to be passed to curator (host:port)
     * @param customDeserializationSubtypes custom subtypes to register for deserialization of
     *                                      {@link DefaultServiceSpec}, mainly useful for deserializing custom
     *                                      implementations of
     *                                      {@link com.mesosphere.sdk.offer.constrain.PlacementRule}s.
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
     * @param customDeserializationSubtypes custom subtypes to register for deserialization of
     *                                      {@link DefaultServiceSpec}, mainly useful for deserializing custom
     *                                      implementations of
     *                                      {@link com.mesosphere.sdk.offer.constrain.PlacementRule}s.
     * @throws ConfigStoreException if validating serialization of the config fails, e.g. due to an
     *                              unrecognized deserialization type
     * @see DcosConstants#MESOS_MASTER_ZK_CONNECTION_STRING
     */
    public static ConfigStore<ServiceSpec> createConfigStore(
            ServiceSpec serviceSpec,
            Collection<Class<?>> customDeserializationSubtypes) throws ConfigStoreException {
        return createConfigStore(
                serviceSpec,
                DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING,
                customDeserializationSubtypes);
    }

    /**
     * Calls {@link #createConfigStore(ServiceSpec, Collection)} with an empty list of
     * custom deserialization types.
     *
     * @throws ConfigStoreException if validating serialization of the config fails, e.g. due to an
     *                              unrecognized deserialization type
     */
    public static ConfigStore<ServiceSpec> createConfigStore(
            ServiceSpec serviceSpec) throws ConfigStoreException {
        return createConfigStore(serviceSpec, Collections.emptyList());
    }

    /**
     * Returns the default configuration validators:
     * - Task sets cannot shrink (each set's task count must stay the same or increase).
     * - Task volumes cannot be changed.
     * <p>
     * This function may be used to get the default validators and add more to the list when
     * constructing the {@link DefaultScheduler}.
     */
    public static List<ConfigurationValidator<ServiceSpec>> defaultConfigValidators() {
        // Return a list to allow direct append by the caller.
        return Arrays.asList(
                new PodSpecsCannotShrink(),
                new TaskVolumesCannotChange());
    }

    /**
     * Creates a new DefaultScheduler.
     *
     * @param serviceSpec                 specification containing service name and tasks to be deployed
     * @param stateStore                  framework state storage, which must not be written to before the scheduler
     *                                    has been registered with mesos as indicated by a call to
     *                                    {@link DefaultScheduler#registered
     *                                    (SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)} (SchedulerDriver,
     *                                    com.mesosphere.sdk.Protos.FrameworkID, com.mesosphere.sdk.Protos.MasterInfo)
     * @param configStore                 framework config storage, which must not be written to before the scheduler
     *                                    has been registered with mesos as indicated by a call to
     *                                    {@link DefaultScheduler#registered
     *                                    (SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)} (SchedulerDriver,
     *                                    org.apache.mesos.Protos.FrameworkID, org.apache.mesos.Protos.MasterInfo)
     * @param configValidators            custom validators to be used, instead of the default validators
     *                                    returned by {@link #defaultConfigValidators()}
     * @param permanentFailureTimeoutSec  minimum duration to wait in seconds before deciding that a
     *                                    task has failed, or an empty {@link Optional} to disable this detection
     * @param destructiveRecoveryDelaySec minimum duration to wait in seconds between destructive
     *                                    recovery operations such as destroying a failed task
     */
    protected DefaultScheduler(
            ServiceSpec serviceSpec,
            Collection<Plan> plans,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            OfferRequirementProvider offerRequirementProvider,
            Collection<ConfigurationValidator<ServiceSpec>> configValidators,
            Optional<Integer> permanentFailureTimeoutSec,
            Integer destructiveRecoveryDelaySec) {
        this.serviceSpec = serviceSpec;
        this.plans = plans;
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.offerRequirementProvider = offerRequirementProvider;
        this.configValidators = configValidators;
        this.permanentFailureTimeoutSec = permanentFailureTimeoutSec;
        this.destructiveRecoveryDelaySec = destructiveRecoveryDelaySec;
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
        initializeResources();
        DcosCertInstaller.installCertificate(System.getenv("JAVA_HOME"));
        final List<PlanManager> planManagers = Arrays.asList(
                deploymentPlanManager,
                recoveryPlanManager);
        planCoordinator = new DefaultPlanCoordinator(planManagers, planScheduler);
        planCoordinator.subscribe(this);
        LOGGER.info("Done initializing.");
    }

    private void initializeGlobals(SchedulerDriver driver) {
        LOGGER.info("Initializing globals...");
        taskFailureListener = new DefaultTaskFailureListener(stateStore);
        taskKiller = new DefaultTaskKiller(stateStore, taskFailureListener, driver);
        reconciler = new DefaultReconciler(stateStore);
        offerAccepter = new OfferAccepter(Arrays.asList(new PersistentOperationRecorder(stateStore)));
        planScheduler = new DefaultPlanScheduler(
                offerAccepter,
                new OfferEvaluator(stateStore, offerRequirementProvider), taskKiller);
    }

    /**
     * Override this function to inject your own deployment plan manager.
     */
    protected void initializeDeploymentPlanManager() {
        LOGGER.info("Initializing deployment plan...");
        Optional<Plan> deploy = plans.stream().filter(plan -> Objects.equals(plan.getName(), "deploy")).findFirst();
        Plan deployPlan;
        if (!deploy.isPresent()) {
            LOGGER.info("No deploy plan provided. Generating one");
             deployPlan = new DefaultPlanFactory(new DefaultPhaseFactory(new DefaultStepFactory(
                    configStore,
                    stateStore,
                    offerRequirementProvider)))
                    .getPlan(serviceSpec);
        } else {
            deployPlan = deploy.get();
        }
        deploymentPlanManager = new DefaultPlanManager(deployPlan);
    }

    /**
     * Override this function to inject your own recovery plan manager.
     */
    protected void initializeRecoveryPlanManager() {
        LOGGER.info("Initializing recovery plan...");
        recoveryPlanManager = new DefaultRecoveryPlanManager(
                stateStore,
                configStore,
                new TimedLaunchConstrainer(Duration.ofSeconds(destructiveRecoveryDelaySec)),
                permanentFailureTimeoutSec.isPresent()
                        ? new TimedFailureMonitor(Duration.ofSeconds(permanentFailureTimeoutSec.get()))
                        : new NeverFailureMonitor());
    }

    private void initializeResources() throws InterruptedException {
        LOGGER.info("Initializing resources...");
        Collection<Object> resources = new ArrayList<>();
        resources.add(new PlansResource(ImmutableMap.of(
                "deploy", deploymentPlanManager,
                "recovery", recoveryPlanManager)));
        resources.add(new StateResource(stateStore, new StringPropertyDeserializer()));
        resources.add(new TaskResource(stateStore, taskKiller, serviceSpec.getName()));
        resources.add(new ConfigResource<ServiceSpec>(configStore));
        // use add() instead of put(): throw exception instead of waiting indefinitely
        resourcesQueue.add(resources);
    }

    private void logOffers(List<Protos.Offer> offers) {
        if (offers == null) {
            return;
        }

        LOGGER.info(String.format("Received %d offers:", offers.size()));
        for (int i = 0; i < offers.size(); ++i) {
            // Offer protobuffers are very long. print each as a single line:
            LOGGER.info(String.format("- Offer %d: %s", i + 1, TextFormat.shortDebugString(offers.get(i))));
        }
    }

    private void declineOffers(SchedulerDriver driver, List<Protos.OfferID> acceptedOffers, List<Protos.Offer> offers) {
        final List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(offers, acceptedOffers);
        unusedOffers.stream().forEach(offer -> {
            final Protos.OfferID offerId = offer.getId();
            LOGGER.info("Declining offer: " + offerId.getValue());
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

    @SuppressWarnings({"DM_EXIT"})
    private void hardExit(SchedulerErrorCode errorCode) {
        System.exit(errorCode.ordinal());
    }

    @Override
    public void registered(SchedulerDriver driver, Protos.FrameworkID frameworkId, Protos.MasterInfo masterInfo) {
        LOGGER.info("Registered framework with frameworkId: " + frameworkId.getValue());
        try {
            initialize(driver);
        } catch (InterruptedException e) {
            LOGGER.error("Initialization failed with exception: ", e);
            hardExit(SchedulerErrorCode.INITIALIZATION_FAILURE);
        }

        try {
            stateStore.storeFrameworkId(frameworkId);
        } catch (Exception e) {
            LOGGER.error(String.format(
                    "Unable to store registered framework ID '%s'", frameworkId.getValue()), e);
            hardExit(SchedulerErrorCode.REGISTRATION_FAILURE);
        }

        this.driver = driver;
        reconciler.start();
        reconciler.reconcile(driver);
        suppressOrRevive();
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        LOGGER.error("Re-registration implies we were unregistered.");
        hardExit(SchedulerErrorCode.RE_REGISTRATION);
        reconciler.start();
        reconciler.reconcile(driver);
        suppressOrRevive();
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offersToProcess) {
        List<Protos.Offer> offers = new ArrayList<>(offersToProcess);
        executor.execute(() -> {
            logOffers(offers);

            // Task Reconciliation:
            // Task Reconciliation must complete before any Tasks may be launched.  It ensures that a Scheduler and
            // Mesos have agreed upon the state of all Tasks of interest to the scheduler.
            // http://mesos.apache.org/documentation/latest/reconciliation/
            reconciler.reconcile(driver);
            if (!reconciler.isReconciled()) {
                LOGGER.info("Reconciliation is still in progress.");
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
        hardExit(SchedulerErrorCode.OFFER_RESCINDED);
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                LOGGER.info(String.format(
                        "Received status update for taskId=%s state=%s message='%s'",
                        status.getTaskId().getValue(),
                        status.getState().toString(),
                        status.getMessage()));

                // Store status, then pass status to PlanManager => Plan => Steps
                try {
                    stateStore.storeStatus(status);
                    deploymentPlanManager.update(status);
                    recoveryPlanManager.update(status);
                    reconciler.update(status);

                    if (CommonTaskUtils.needsRecovery(status)) {
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
        hardExit(SchedulerErrorCode.DISCONNECTED);
    }

    @Override
    public void slaveLost(SchedulerDriver driver, Protos.SlaveID agentId) {
        // TODO: Add recovery optimizations relevant to loss of an Agent.  TaskStatus updates are sufficient now.
        LOGGER.warn("Agent lost: " + agentId);
    }

    @Override
    public void executorLost(SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, int status) {
        // TODO: Add recovery optimizations relevant to loss of an Executor.  TaskStatus updates are sufficient now.
        LOGGER.warn(String.format("Lost Executor: %s on Agent: %s", executorId, slaveId));
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

        hardExit(SchedulerErrorCode.ERROR);
    }

    private void suppressOrRevive() {
        if (planCoordinator.hasOperations()) {
            revive();
        } else {
            suppress();
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
