package org.apache.mesos.scheduler;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.config.DefaultTaskConfigRouter;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.dcos.DcosConstants;
import org.apache.mesos.offer.*;
import org.apache.mesos.reconciliation.DefaultReconciler;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.api.TaskResource;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.api.PlansResource;
import org.apache.mesos.scheduler.recovery.*;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.constrain.TimedLaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.scheduler.recovery.monitor.NeverFailureMonitor;
import org.apache.mesos.scheduler.recovery.monitor.TimedFailureMonitor;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.state.PersistentOperationRecorder;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreCache;
import org.apache.mesos.state.api.JsonPropertyDeserializer;
import org.apache.mesos.state.api.StateResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * This scheduler when provided with a ServiceSpecification will deploy the service and recover from encountered faults
 * when possible.  Changes to the ServiceSpecification will result in rolling configuration updates, or the creation of
 * new Tasks where applicable.
 */
public class DefaultScheduler implements Scheduler, Observer {
    protected static final String UNINSTALL_INCOMPLETE_ERROR_MESSAGE = "Framework has been removed";
    protected static final String UNINSTALL_INSTRUCTIONS_URI =
            "https://docs.mesosphere.com/latest/usage/managing-services/uninstall/";

    protected static final Integer DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_SEC = 10 * 60;
    protected static final Integer PERMANENT_FAILURE_DELAY_SEC = 20 * 60;
    protected static final Integer AWAIT_TERMINATION_TIMEOUT_MS = 10000;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScheduler.class);

    protected final ExecutorService executor = Executors.newFixedThreadPool(1);
    protected final BlockingQueue<Collection<Object>> resourcesQueue = new ArrayBlockingQueue<>(1);
    protected final String frameworkName;
    protected final PlanManager deployPlanManager;
    protected final OfferRequirementProvider offerRequirementProvider;
    protected final StateStore stateStore;
    protected final Optional<Integer> permanentFailureTimeoutSec;
    protected final Integer destructiveRecoveryDelaySec;

    protected SchedulerDriver driver;
    protected Reconciler reconciler;
    protected TaskFailureListener taskFailureListener;
    protected TaskKiller taskKiller;
    protected OfferAccepter offerAccepter;
    protected PlanScheduler planScheduler;
    protected PlanManager recoveryPlanManager;
    protected PlanCoordinator planCoordinator;
    protected Collection<Object> resources;

    /**
     * Returns a new {@link DefaultScheduler} instance using the provided service-defined
     * {@link ServiceSpecification} and the default ZK location for framework state.
     *
     * @param serviceSpecification specification containing service name and tasks to be deployed
     * @see DcosConstants#MESOS_MASTER_ZK_CONNECTION_STRING
     */
    public static DefaultScheduler create(ServiceSpecification serviceSpecification) {
        return create(
                serviceSpecification,
                createStateStore(serviceSpecification.getName(), DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING));
    }

    /**
     * Returns a new {@link DefaultScheduler} instance using the provided
     * {@link ServiceSpecification} and {@link StateStore}.
     *
     * @param serviceSpecification specification containing service name and tasks to be deployed
     * @param stateStore framework state storage, which must not be written to before the scheduler
     *                   has been registered with mesos as indicated by a call to {@link
     *                   DefaultScheduler#registered(SchedulerDriver,
     *                   org.apache.mesos.Protos.FrameworkID, org.apache.mesos.Protos.MasterInfo)
     * @see #createStateStore(String, String)
     */
    public static DefaultScheduler create(ServiceSpecification serviceSpecification, StateStore stateStore) {
        PlanFactory deployPlanFactory =
                new DefaultPlanFactory(new DefaultPhaseFactory(new DefaultStepFactory(stateStore)));
        return create(
                serviceSpecification.getName(),
                new DefaultPlanManager(deployPlanFactory.getPlan(serviceSpecification)),
                stateStore);
    }

    /**
     * Returns a new {@link DefaultScheduler} instance using the provided
     * {@code frameworkName} and {@link PlanManager} stack, and the default ZK location for
     * framework state.
     *
     * @param frameworkName the name of the framework (service name)
     * @param deployPlanManager the deployment plan to be used by this service
     * @see DcosConstants#MESOS_MASTER_ZK_CONNECTION_STRING
     */
    public static DefaultScheduler create(String frameworkName, PlanManager deploymentPlanManager) {
        return create(
                frameworkName,
                deploymentPlanManager,
                createStateStore(frameworkName, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING));
    }

    /**
     * Returns a new {@link DefaultScheduler} instance using the provided
     * {@code frameworkName}, {@link PlanManager} stack, and {@link StateStore}, but with default
     * recovery durations.
     *
     * @param frameworkName the name of the framework (service name)
     * @param deployPlanManager the deployment plan to be used by this service
     * @param stateStore framework state storage, which must not be written to before the scheduler
     *                   has been registered with mesos as indicated by a call to {@link
     *                   DefaultScheduler#registered(SchedulerDriver,
     *                   org.apache.mesos.Protos.FrameworkID, org.apache.mesos.Protos.MasterInfo)
     * @see #createStateStore(String, String)
     */
    public static DefaultScheduler create(
            String frameworkName,
            PlanManager deploymentPlanManager,
            StateStore stateStore) {
        return create(
                frameworkName,
                deploymentPlanManager,
                stateStore,
                Optional.of(PERMANENT_FAILURE_DELAY_SEC),
                DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_SEC);
    }

    /**
     * Returns a new {@link DefaultScheduler} instance using the provided
     * {@code frameworkName} and {@link PlanManager} stack, and the default ZK location for
     * framework state.
     *
     * @param frameworkName the name of the framework (service name)
     * @param deployPlanManager the deployment plan to be used by this service
     * @param permanentFailureTimeoutSec minimum duration to wait in seconds before deciding that a
     *                                   task has failed, or an empty {@link Optional} to disable
     *                                   this detection
     * @param destructiveRecoveryDelaySec minimum duration to wait in seconds between destructive
     *                                    recovery operations such as destroying a failed task
     * @see DcosConstants#MESOS_MASTER_ZK_CONNECTION_STRING
     */
    public static DefaultScheduler create(
            String frameworkName,
            PlanManager deployPlanManager,
            Optional<Integer> permanentFailureTimeoutSec,
            Integer destructiveRecoveryDelaySec) {
        return create(
                frameworkName,
                deployPlanManager,
                createStateStore(frameworkName, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING),
                permanentFailureTimeoutSec,
                destructiveRecoveryDelaySec);
    }

    /**
     * Returns a new {@link DefaultScheduler} instance using the provided
     * {@code frameworkName}, {@link PlanManager} stack, and {@link StateStore}.
     *
     * @param frameworkName the name of the framework (service name)
     * @param stateStore framework state storage, which must not be written to before the scheduler
     *                   has been registered with mesos as indicated by a call to {@link
     *                   DefaultScheduler#registered(SchedulerDriver,
     *                   org.apache.mesos.Protos.FrameworkID, org.apache.mesos.Protos.MasterInfo)
     * @param permanentFailureTimeoutSec minimum duration to wait in seconds before deciding that a
     *                                   task has failed, or an empty {@link Optional} to disable
     *                                   this detection
     * @param destructiveRecoveryDelaySec minimum duration to wait in seconds between destructive
     *                                    recovery operations such as destroying a failed task
     */
    public static DefaultScheduler create(
            String frameworkName,
            PlanManager deployPlanManager,
            StateStore stateStore,
            Optional<Integer> permanentFailureTimeoutSec,
            Integer destructiveRecoveryDelaySec) {
        return new DefaultScheduler(
                frameworkName,
                deployPlanManager,
                new DefaultOfferRequirementProvider(new DefaultTaskConfigRouter()),
                stateStore,
                permanentFailureTimeoutSec,
                destructiveRecoveryDelaySec);
    }

    /**
     * Creates and returns a new default {@link StateStore} suitable for passing to
     * {@link DefaultScheduler#create}. To avoid the risk of zookeeper consistency issues, the
     * returned storage MUST NOT be written to before the Scheduler has registered with Mesos, as
     * signified by a call to {@link DefaultScheduler#registered(SchedulerDriver,
     * org.apache.mesos.Protos.FrameworkID, org.apache.mesos.Protos.MasterInfo)}
     *
     * @param frameworkName the name of the framework (service name)
     * @param zkConnectionString the zookeeper connection string to be passed to curator (host:port)
     */
    public static StateStore createStateStore(String frameworkName, String zkConnectionString) {
        return StateStoreCache.getInstance(
                new CuratorStateStore(frameworkName, zkConnectionString));
    }

    /**
     * Creates a new DefaultScheduler.
     *
     * @param frameworkName the name of the framework (service name)
     * @param deployPlanManager the deployment plan to be used by this service
     * @param offerRequirementProvider generator of offers in order to run specified tasks
     * @param stateStore framework state storage, which must not be written to before the scheduler
     *                   has been registered with mesos as indicated by a call to {@link
     *                   DefaultScheduler#registered(SchedulerDriver,
     *                   org.apache.mesos.Protos.FrameworkID, org.apache.mesos.Protos.MasterInfo)
     * @param permanentFailureTimeoutSec minimum duration to wait in seconds before deciding that a
     *                                   task has failed, or an empty {@link Optional} to disable
     *                                   this detection
     * @param destructiveRecoveryDelaySec minimum duration to wait in seconds between destructive
     *                                    recovery operations such as destroying a failed task
     */
    protected DefaultScheduler(
            String frameworkName,
            PlanManager deployPlanManager,
            OfferRequirementProvider offerRequirementProvider,
            StateStore stateStore,
            Optional<Integer> permanentFailureTimeoutSec,
            Integer destructiveRecoveryDelaySec) {
        this.frameworkName = frameworkName;
        this.deployPlanManager = deployPlanManager;
        this.offerRequirementProvider = offerRequirementProvider;
        this.stateStore = stateStore;
        this.permanentFailureTimeoutSec = permanentFailureTimeoutSec;
        this.destructiveRecoveryDelaySec = destructiveRecoveryDelaySec;
    }

    public Collection<Object> getResources() throws InterruptedException {
        if (resources == null) {
            resources = resourcesQueue.take();
        }

        return resources;
    }

    void awaitTermination() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(AWAIT_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    Plan getPlan() {
        return deployPlanManager.getPlan();
    }

    private void initialize(SchedulerDriver driver) throws InterruptedException {
        LOGGER.info("Initializing...");
        initializeGlobals(driver);
        initializeRecoveryPlanManager();
        initializeResources();
        final List<PlanManager> planManagers = Arrays.asList(
                recoveryPlanManager,
                deployPlanManager);
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
        planScheduler = new DefaultPlanScheduler(offerAccepter, new OfferEvaluator(stateStore), taskKiller);
    }

    private void initializeRecoveryPlanManager() {
        LOGGER.info("Initializing recovery plan...");
        final RecoveryRequirementProvider recoveryRequirementProvider =
                new DefaultRecoveryRequirementProvider(offerRequirementProvider);
        final LaunchConstrainer constrainer =
                new TimedLaunchConstrainer(Duration.ofSeconds(destructiveRecoveryDelaySec));

        FailureMonitor failureMonitor;
        if (permanentFailureTimeoutSec.isPresent()) {
            failureMonitor = new TimedFailureMonitor(Duration.ofSeconds(permanentFailureTimeoutSec.get()));
        } else {
            failureMonitor = new NeverFailureMonitor();
        }

        recoveryPlanManager = new DefaultRecoveryPlanManager(
                stateStore,
                recoveryRequirementProvider,
                constrainer,
                failureMonitor);
    }

    private void initializeResources() throws InterruptedException {
        LOGGER.info("Initializing resources...");
        Collection<Object> resources = new ArrayList<>();
        resources.add(new PlansResource(ImmutableMap.of(
                "deploy", deployPlanManager,
                "recovery", recoveryPlanManager)));
        resources.add(new StateResource(stateStore, new JsonPropertyDeserializer()));
        resources.add(new TaskResource(stateStore, taskKiller, frameworkName));
        resourcesQueue.put(resources);
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
        reconciler.reconcile(driver);
        suppressOrRevive();
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        LOGGER.error("Re-registration implies we were unregistered.");
        hardExit(SchedulerErrorCode.RE_REGISTRATION);
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
                    deployPlanManager.update(status);
                    recoveryPlanManager.update(status);
                    reconciler.update(status);
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
            LOGGER.info("Reviving offers.");
            driver.reviveOffers();
            stateStore.setSuppressed(false);
        } else {
            LOGGER.info("Suppressing offers.");
            driver.suppressOffers();
            stateStore.setSuppressed(true);
        }
    }

    @Override
    public void update(Observable observable) {
        if (observable == planCoordinator) {
            suppressOrRevive();
        }
    }
}
