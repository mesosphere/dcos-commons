package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.api.EndpointsResource;
import com.mesosphere.sdk.api.JettyApiServer;
import com.mesosphere.sdk.api.PodsResource;
import com.mesosphere.sdk.api.StateResource;
import com.mesosphere.sdk.api.types.StringPropertyDeserializer;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import com.mesosphere.sdk.reconciliation.DefaultReconciler;
import com.mesosphere.sdk.reconciliation.Reconciler;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanScheduler;
import com.mesosphere.sdk.scheduler.plan.PlanScheduler;
import com.mesosphere.sdk.scheduler.recovery.TaskFailureListener;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.PersistentLaunchRecorder;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This scheduler uninstalls the framework and releases all of its resources.
 */
public class UninstallScheduler implements Scheduler {

    /**
     * Time to wait for the executor thread to terminate. Only used by unit tests.
     *
     * Default: 10 seconds
     */
    protected static final Integer AWAIT_TERMINATION_TIMEOUT_MS = 10 * 1000;

    private static final Logger LOGGER = LoggerFactory.getLogger(UninstallScheduler.class);

    protected final ExecutorService executor = Executors.newFixedThreadPool(1);

    // Mesos may call registered() multiple times in the lifespan of a Scheduler process, specifically when there's
    // master re-election. Avoid performing initialization multiple times, which would cause resourcesQueue to be stuck.
    protected final AtomicBoolean isAlreadyRegistered = new AtomicBoolean(false);

    protected final ServiceSpec serviceSpec;
    protected final SchedulerFlags schedulerFlags;
    protected final StateStore stateStore;

    private JettyApiServer apiServer;
    private Stopwatch apiServerStopwatch = Stopwatch.createStarted();

    protected SchedulerDriver driver;
    protected OfferRequirementProvider offerRequirementProvider;
    protected Reconciler reconciler;
    protected TaskFailureListener taskFailureListener;
    protected TaskKiller taskKiller;
    protected OfferAccepter offerAccepter;
    protected PlanScheduler planScheduler;
    protected Collection<Object> resources;

    /**
     * Builder class for {@link UninstallScheduler}s. Uses provided custom values or reasonable defaults.
     *
     * Instances may be created via {@link UninstallScheduler#newBuilder(ServiceSpec, SchedulerFlags)}.
     */
    public static class Builder {
        private final ServiceSpec serviceSpec;
        private final SchedulerFlags schedulerFlags;

        // When these optionals are unset, we use default values:
        private Optional<StateStore> stateStoreOptional = Optional.empty();

        private Builder(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags) {
            this.serviceSpec = serviceSpec;
            this.schedulerFlags = schedulerFlags;
        }

        /**
         * Returns the {@link ServiceSpec} which was provided via the constructor.
         */
        public ServiceSpec getServiceSpec() {
            return serviceSpec;
        }

        /**
         * Specifies a custom {@link StateStore}, otherwise the return value of
         * {@link UninstallScheduler#createStateStore(ServiceSpec, SchedulerFlags)} will be used.
         *
         * The state store persists copies of task information and task status for all tasks running in the service.
         *
         * @throws IllegalStateException if the state store is already set, via a previous call to either
         *                               {@link #setStateStore(StateStore)} or to {@link #getStateStore()}
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
         * created via {@link UninstallScheduler#createStateStore(ServiceSpec, SchedulerFlags)}.
         *
         * In order to avoid cohesiveness issues between this setting and the {@link #build()} step,
         * {@link #setStateStore(StateStore)} may not be invoked after this has been called.
         */
        public StateStore getStateStore() {
            if (!stateStoreOptional.isPresent()) {
                setStateStore(createStateStore(serviceSpec, getSchedulerFlags()));
            }
            return stateStoreOptional.get();
        }

        /**
         * Returns the {@link SchedulerFlags} object to be used for the scheduler instance.
         */
        public SchedulerFlags getSchedulerFlags() {
            return schedulerFlags;
        }

        /**
         * Creates a new scheduler instance with the provided values or their defaults.
         *
         * @return a new scheduler instance
         * @throws IllegalStateException if config validation failed when updating the target config for a default
         *                               {@link OfferRequirementProvider}, or if creating a default {@link ConfigStore}
         *                               failed
         */
        public UninstallScheduler build() {
            // Get custom or default state store (defaults handled by getStateStore())::
            final StateStore stateStore = getStateStore();

            return new UninstallScheduler(serviceSpec, schedulerFlags, stateStore);
        }
    }

    /**
     * Creates a new {@link Builder} based on the provided {@link ServiceSpec} describing the service, including
     * details such as the service name, the pods/tasks to be deployed, and the plans describing how the deployment
     * should be organized.
     */
    public static Builder newBuilder(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags) {
        return new Builder(serviceSpec, schedulerFlags);
    }

    /**
     * Creates and returns a new default {@link StateStore} suitable for passing to
     * {@link Builder#setStateStore(StateStore)}. To avoid the risk of zookeeper consistency issues, the
     * returned storage MUST NOT be written to before the Scheduler has registered with Mesos, as
     * signified by a call to
     * {@link UninstallScheduler#registered(SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)}
     *
     * @param zkConnectionString the zookeeper connection string to be passed to curator (host:port)
     */
    public static StateStore createStateStore(
            ServiceSpec serviceSpec, SchedulerFlags schedulerFlags, String zkConnectionString) {
        StateStore stateStore = new CuratorStateStore(serviceSpec.getName(), zkConnectionString);
        if (schedulerFlags.isStateCacheEnabled()) {
            return StateStoreCache.getInstance(stateStore);
        } else {
            return stateStore;
        }
    }

    /**
     * Calls {@link #createStateStore(ServiceSpec, SchedulerFlags, String)} with the specification name as the
     * {@code frameworkName} and with a reasonable default for {@code zkConnectionString}.
     *
     * @see DcosConstants#MESOS_MASTER_ZK_CONNECTION_STRING
     */
    public static StateStore createStateStore(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags) {
        return createStateStore(serviceSpec, schedulerFlags, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    /**
     * Creates a new UninstallScheduler. See information about parameters in {@link Builder}.
     */
    protected UninstallScheduler(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags, StateStore stateStore) {
        this.serviceSpec = serviceSpec;
        this.schedulerFlags = schedulerFlags;
        this.stateStore = stateStore;
    }

    public Collection<Object> getResources() throws InterruptedException {
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
//        initializeApiServer();
        LOGGER.info("Done initializing.");
    }


    private void initializeGlobals(SchedulerDriver driver) {
        LOGGER.info("Initializing globals...");
        taskKiller = new DefaultTaskKiller(taskFailureListener, driver);
        reconciler = new DefaultReconciler(stateStore);
        offerAccepter = new OfferAccepter(Arrays.asList(new PersistentLaunchRecorder(stateStore, serviceSpec)));
        OfferEvaluator offerEvaluator = new OfferEvaluator(stateStore, offerRequirementProvider);
        planScheduler = new DefaultPlanScheduler(offerAccepter, offerEvaluator, stateStore, taskKiller);
    }


    private void initializeResources() throws InterruptedException {
        LOGGER.info("Initializing resources...");
        EndpointsResource endpointsResource = new EndpointsResource(stateStore, serviceSpec.getName());
        resources.add(endpointsResource);
        resources.add(new PodsResource(taskKiller, stateStore));
        resources.add(new StateResource(stateStore, new StringPropertyDeserializer()));
    }

    private void initializeApiServer() {
        if (apiServerReady()) {
            return;
        }

        new Thread(() -> {
            try {
                LOGGER.info("Starting API server.");
                apiServer = new JettyApiServer(serviceSpec.getApiPort(), getResources());
                apiServer.start();
            } catch (Exception e) {
                LOGGER.error("API Server failed with exception: ", e);
            } finally {
                LOGGER.info("API Server exiting.");
                try {
                    if (apiServer != null) {
                        apiServer.stop();
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to stop API server with exception: ", e);
                }
            }
        }).start();
    }

    private void declineOffers(SchedulerDriver driver, List<Protos.OfferID> acceptedOffers, List<Protos.Offer> offers) {
        final List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(offers, acceptedOffers);
        LOGGER.info("Declining {} unused offers:", unusedOffers.size());
        unusedOffers.forEach(offer -> {
            final Protos.OfferID offerId = offer.getId();
            LOGGER.info("  {}", offerId.getValue());
            driver.declineOffer(offerId);
        });
    }

    private ResourceCleanerScheduler getCleanerScheduler() {
        ResourceCleaner cleaner = new UninstallResourceCleaner();
        return new ResourceCleanerScheduler(cleaner, offerAccepter);
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
        postRegister();
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        LOGGER.info("Re-registered with master: {}", TextFormat.shortDebugString(masterInfo));
        postRegister();
    }

    public boolean apiServerReady() {
        boolean serverStarted = apiServer != null && apiServer.isStarted();

        if (serverStarted) {
            apiServerStopwatch.reset();
        } else {
            Duration initTimeout = schedulerFlags.getApiServerInitTimeout();
            if (apiServerStopwatch.elapsed(TimeUnit.MILLISECONDS) > initTimeout.toMillis()) {
                LOGGER.error("API Server failed to start within {} seconds.", initTimeout.getSeconds());
                SchedulerUtils.hardExit(SchedulerErrorCode.API_SERVER_TIMEOUT);
            }
        }

        return serverStarted;
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offersToProcess) {
        List<Protos.Offer> offers = new ArrayList<>(offersToProcess);
        executor.execute(() -> {
//            if (!apiServerReady()) {
//                LOGGER.info("Declining all offers. Waiting for API Server to start ...");
//                declineOffers(driver, Collections.emptyList(), offersToProcess);
//                return;
//            }

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

            // Destroy/Unreserve everything
            final List<Protos.OfferID> offersWithReservedResources = new ArrayList<>();
            offersWithReservedResources.addAll(getCleanerScheduler().resourceOffers(driver, offers));

            List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(offers, offersWithReservedResources);
            offers.clear();
            offers.addAll(unusedOffers);

            // Decline remaining offers.
            declineOffers(driver, offersWithReservedResources, offers);
        });
    }

    @Override
    public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerId) {
        LOGGER.warn("Ignoring rescinded Offer: {}.", offerId.getValue());
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        executor.execute(() -> {
            LOGGER.info("Received status update for taskId={} state={} message='{}'",
                    status.getTaskId().getValue(),
                    status.getState().toString(),
                    status.getMessage());

            // Store status, then pass status to PlanManager => Plan => Steps
            try {
                stateStore.storeStatus(status);
                reconciler.update(status);

            } catch (Exception e) {
                LOGGER.warn("Failed to update TaskStatus received from Mesos. "
                        + "This may be expected if Mesos sent stale status information: " + status, e);
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
        SchedulerUtils.hardExit(SchedulerErrorCode.ERROR);
    }

    private void revive() {
        LOGGER.info("Reviving offers.");
        driver.reviveOffers();
        StateStoreUtils.setSuppressed(stateStore, false);
    }

    private void postRegister() {
        reconciler.start();
        reconciler.reconcile(driver);
        // phase 1 complete
        revive();
        Collection<String> taskNames = stateStore.fetchTaskNames();
        LOGGER.info("Found {} tasks to restart and clear: {}", taskNames.size(), taskNames);
        for (String taskName : taskNames) {
            Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskName);
            taskInfoOptional.ifPresent(taskInfo -> taskKiller.killTask(taskInfo.getTaskId(), false));
        }
        // phase 2 complete
    }

}
