package com.mesosphere.sdk.scheduler;

import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.OfferUtils;
import com.mesosphere.sdk.offer.evaluate.placement.IsLocalRegionRule;
import com.mesosphere.sdk.queue.OfferQueue;
import com.mesosphere.sdk.reconciliation.DefaultReconciler;
import com.mesosphere.sdk.reconciliation.Reconciler;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Abstract main scheduler class that ties together the main pieces of a SDK Scheduler process.
 * Handles interaction with Mesos via an embedded {@link AbstractScheduler.MesosScheduler} object.
 */
public abstract class AbstractScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractScheduler.class);

    protected final StateStore stateStore;
    protected final ConfigStore<ServiceSpec> configStore;
    protected final SchedulerConfig schedulerConfig;

    // Tracks whether apiServer has entered a started state. We avoid launching tasks until after the API server has
    // started, because when tasks launch they typically require access to ArtifactResource for config templates.
    private final AtomicBoolean apiServerStarted = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);

    // Whether we should run in multithreaded mode. Should only be disabled for tests.
    private boolean multithreaded = true;

    private final MesosScheduler mesosScheduler = new MesosScheduler();

    private final Object inProgressLock = new Object();
    private final Set<Protos.OfferID> offersInProgress = new HashSet<>();

    /**
     * Executor for handling TaskStatus updates in {@link Scheduler#statusUpdate(SchedulerDriver, Protos.TaskStatus)}.
     */
    protected final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();

    /**
     * Executor for processing offers off the queue in {@link #start()}.
     */
    private final ExecutorService offerExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new AbstractScheduler given a {@link StateStore}.
     */
    protected AbstractScheduler(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerConfig schedulerConfig) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.schedulerConfig = schedulerConfig;
    }

    /**
     * Starts any internal threads to be used by the service.
     * Must be called after construction, once, in order for work to proceed.
     *
     * @return this
     */
    public AbstractScheduler start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("start() can only be called once");
        }

        if (multithreaded) {
            // Start consumption of the offer queue. This will idle until offers start arriving.
            offerExecutor.execute(() -> {
                while (true) {
                    try {
                        mesosScheduler.processQueuedOffers();
                    } catch (Exception e) {
                        LOGGER.error("Error encountered when processing offers, exiting to avoid zombie state", e);
                        SchedulerUtils.hardExit(SchedulerErrorCode.ERROR);
                    }
                }
            });
        }

        return this;
    }

    /**
     * Returns a Mesos API {@link Scheduler} object to be registered with Mesos, or an empty {@link Optional} if Mesos
     * registration should not be performed.
     */
    public Optional<Scheduler> getMesosScheduler() {
        return Optional.of(mesosScheduler);
    }

    /**
     * All offers must have been presented to resourceOffers() before calling this.  This call will block until all
     * offers have been processed.
     *
     * @throws InterruptedException if waiting for offers to be processed is interrupted
     * @throws IllegalStateException if offers were not processed in a reasonable amount of time
     */
    @VisibleForTesting
    public void awaitOffersProcessed() throws InterruptedException {
        final int totalDurationMs = 5000;
        final int sleepDurationMs = 100;
        for (int i = 0; i < totalDurationMs / sleepDurationMs; ++i) {
            synchronized (inProgressLock) {
                if (offersInProgress.isEmpty()) {
                    LOGGER.info("All offers processed.");
                    return;
                }
                LOGGER.warn("Offers in progress {} is non-empty, sleeping for {}ms ...",
                        offersInProgress, sleepDurationMs);
            }
            Thread.sleep(sleepDurationMs);
        }
        throw new IllegalStateException(String.format(
                "Timed out after %dms waiting for offers to be processed", totalDurationMs));
    }

    /**
     * Skips the creation of the API server and marks it as "started". In order for this to have any effect, it must
     * be called before {@link #start()}.
     *
     * @return this
     */
    @VisibleForTesting
    public AbstractScheduler disableApiServer() {
        apiServerStarted.set(true);
        return this;
    }

    /**
     * Forces the Scheduler to run in a synchronous/single-threaded mode for tests. To have any effect, this must be
     * called before calling {@link #start()}.
     *
     * @return this
     */
    @VisibleForTesting
    public AbstractScheduler disableThreading() {
        multithreaded = false;
        return this;
    }

    /**
     * Overrides the Scheduler's offer queue size. Must only be called before the scheduler has {@link #start()}ed.
     *
     * @param queueSize the queue size to use, zero for infinite
     */
    @VisibleForTesting
    public AbstractScheduler setOfferQueueSize(int queueSize) {
        mesosScheduler.offerQueue = new OfferQueue(queueSize);
        return this;
    }

    /**
     * Returns the plans defined for this scheduler. Useful for scheduler tests.
     */
    @VisibleForTesting
    public Collection<Plan> getPlans() {
        return mesosScheduler.planCoordinator.getPlanManagers().stream()
                .map(planManager -> planManager.getPlan())
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of API resources to be served by the scheduler to the local cluster.
     * This may be called before {@link #initialize(SchedulerDriver)} has been called.
     */
    protected abstract Collection<Object> getResources();

    /**
     * Performs any additional Scheduler initialization after registration has completed. The provided
     * {@link SchedulerDriver} may be used to talk to Mesos. Returns a {@link PlanCoordinator} which will be used to
     * select candidate workloads.
     */
    protected abstract PlanCoordinator initialize(SchedulerDriver driver) throws Exception;

    /**
     * The abstract scheduler will periodically call this method with a list of available offers, which may be empty.
     */
    protected abstract void processOffers(SchedulerDriver driver, List<Protos.Offer> offers, Collection<Step> steps);

    /**
     * Handles a task status update which was received from Mesos. This call is executed on a separate thread which is
     * run by the Mesos Scheduler Driver.
     */
    protected abstract void processStatusUpdate(Protos.TaskStatus status) throws Exception;

    /**
     * Implementation of Mesos' {@link Scheduler} interface.
     * Messages received from Mesos are forwarded to the parent {@link AbstractScheduler} instance.
     */
    private class MesosScheduler implements Scheduler {

        // Mesos may call registered() multiple times in the lifespan of a Scheduler process, specifically when there's
        // master re-election. Avoid performing initialization multiple times, which would cause queues to be stuck.
        private final AtomicBoolean isRegisterStarted = new AtomicBoolean(false);
        // Avoid attempting to process offers until initialization has completed via the first call to registered().
        private final AtomicBoolean isInitialized = new AtomicBoolean(false);

        // May be overridden in tests:
        private OfferQueue offerQueue = new OfferQueue();

        // These are all (re)assigned when the scheduler has (re)registered:
        private SchedulerDriver driver;
        private ReviveManager reviveManager;
        private Reconciler reconciler;
        private PlanCoordinator planCoordinator;
        private TaskCleaner taskCleaner;

        @Override
        public void registered(SchedulerDriver driver, Protos.FrameworkID frameworkId, Protos.MasterInfo masterInfo) {
            if (isRegisterStarted.getAndSet(true)) {
                // This may occur as the result of a master election.
                LOGGER.info("Already registered, calling reregistered()");
                reregistered(driver, masterInfo);
                return;
            }

            LOGGER.info("Registered framework with frameworkId: {}", frameworkId.getValue());
            this.driver = driver;
            this.reviveManager = new ReviveManager(driver);
            this.reconciler = new DefaultReconciler(stateStore);
            this.taskCleaner = new TaskCleaner(stateStore, new TaskKiller(driver), multithreaded);

            try {
                this.planCoordinator = initialize(driver);
            } catch (Exception e) {
                LOGGER.error("Initialization failed with exception: ", e);
                SchedulerUtils.hardExit(SchedulerErrorCode.INITIALIZATION_FAILURE);
            }

            // Trigger launch of the API server. We start processing offers only once the API server has launched.
            if (apiServerStarted.get()) {
                LOGGER.info("Skipping API server setup");
            } else {
                SchedulerApiServer apiServer = new SchedulerApiServer(schedulerConfig, getResources());
                apiServer.start(new AbstractLifeCycle.AbstractLifeCycleListener() {
                    @Override
                    public void lifeCycleStarted(LifeCycle event) {
                        apiServerStarted.set(true);
                    }
                });
            }

            try {
                stateStore.storeFrameworkId(frameworkId);
            } catch (Exception e) {
                LOGGER.error(String.format(
                        "Unable to store registered framework ID '%s'", frameworkId.getValue()), e);
                SchedulerUtils.hardExit(SchedulerErrorCode.REGISTRATION_FAILURE);
            }

            postRegister(masterInfo);

            isInitialized.set(true);
        }

        @Override
        public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
            LOGGER.info("Re-registered with master: {}", TextFormat.shortDebugString(masterInfo));
            postRegister(masterInfo);
        }

        private void postRegister(Protos.MasterInfo masterInfo) {
            restartReconciliation();
            if (masterInfo.hasDomain()) {
                IsLocalRegionRule.setLocalDomain(masterInfo.getDomain());
            }
        }

        @Override
        public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
            Metrics.incrementReceivedOffers(offers.size());

            if (!apiServerStarted.get()) {
                LOGGER.info("Declining {} offer{}: Waiting for API Server to start.",
                        offers.size(), offers.size() == 1 ? "" : "s");
                OfferUtils.declineShort(driver, offers);
                return;
            }

            synchronized (inProgressLock) {
                offersInProgress.addAll(
                        offers.stream()
                                .map(offer -> offer.getId())
                                .collect(Collectors.toList()));

                LOGGER.info("Enqueuing {} offer{}. Updated offers in progress: {}",
                        offers.size(),
                        offers.size() == 1 ? "" : "s",
                        offersInProgress.stream()
                                .map(offerID -> offerID.getValue())
                                .collect(Collectors.toList()));
            }

            for (Protos.Offer offer : offers) {
                boolean queued = offerQueue.offer(offer);
                if (!queued) {
                    LOGGER.warn("Offer queue is full: Declining offer and removing from in progress: '{}'",
                            offer.getId().getValue());
                    OfferUtils.declineShort(driver, Arrays.asList(offer));
                    // Remove AFTER decline: Avoid race where we haven't declined yet but appear to be done
                    synchronized (inProgressLock) {
                        offersInProgress.remove(offer.getId());
                    }
                }
            }

            if (!multithreaded) {
                processQueuedOffers();
            }
        }

        @Override
        public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
            LOGGER.info("Received status update for taskId={} state={} message={} protobuf={}",
                    status.getTaskId().getValue(),
                    status.getState().toString(),
                    status.getMessage(),
                    TextFormat.shortDebugString(status));
            try {
                processStatusUpdate(status);
                reconciler.update(status);

                Metrics.record(status);
            } catch (Exception e) {
                LOGGER.warn("Failed to update TaskStatus received from Mesos. "
                        + "This may be expected if Mesos sent stale status information: " + status, e);
            }

            taskCleaner.statusUpdate(status);
        }

        @Override
        public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerId) {
            LOGGER.info("Rescinding offer: {}", offerId.getValue());
            offerQueue.remove(offerId);
        }

        @Override
        public void frameworkMessage(
                SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID agentId, byte[] data) {
            LOGGER.error("Received a {} byte Framework Message from Executor {}, but don't know how to process it",
                    data.length, executorId.getValue());
        }

        @Override
        public void disconnected(SchedulerDriver driver) {
            LOGGER.error("Disconnected from Master, shutting down.");
            SchedulerUtils.hardExit(SchedulerErrorCode.DISCONNECTED);
        }

        @Override
        public void slaveLost(SchedulerDriver driver, Protos.SlaveID agentId) {
            // TODO: Add recovery optimizations relevant to loss of an Agent.  TaskStatus updates are sufficient now.
            LOGGER.warn("Agent lost: {}", agentId.getValue());
        }

        @Override
        public void executorLost(
                SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID agentId, int status) {
            // TODO: Add recovery optimizations relevant to loss of an Executor.  TaskStatus updates are sufficient now.
            LOGGER.warn("Lost Executor: {} on Agent: {}", executorId.getValue(), agentId.getValue());
        }

        @Override
        public void error(SchedulerDriver driver, String message) {
            LOGGER.error("SchedulerDriver returned an error, shutting down: {}", message);
            SchedulerUtils.hardExit(SchedulerErrorCode.ERROR);
        }

        /**
         * Dequeues and processes any elements which are present on the offer queue, potentially blocking for offers to
         * appear.
         */
        private void processQueuedOffers() {
            LOGGER.info("Waiting for queued offers...");
            List<Protos.Offer> offers = offerQueue.takeAll();
            try {
                if (offers.isEmpty() && !isInitialized.get()) {
                    // The scheduler hasn't finished registration yet, so many members haven't been initialized either.
                    // Avoid hitting NPE for planCoordinator, driver, etc.
                    LOGGER.info("Retrying wait for offers: Registration hasn't completed yet.");
                    return;
                }

                // Task Reconciliation:
                // Task Reconciliation must complete before any Tasks may be launched.  It ensures that a Scheduler and
                // Mesos have agreed upon the state of all Tasks of interest to the scheduler.
                // http://mesos.apache.org/documentation/latest/reconciliation/
                reconciler.reconcile(driver);
                if (!reconciler.isReconciled()) {
                    LOGGER.info("Declining {} offer{}: Waiting for task reconciliation to complete.",
                            offers.size(), offers.size() == 1 ? "" : "s");
                    OfferUtils.declineShort(driver, offers);
                    return;
                }

                // Get the current work
                Collection<Step> steps = planCoordinator.getCandidates();

                // Revive previously suspended offers, if necessary
                Collection<Step> activeWorkSet = new HashSet<>(steps);
                Collection<Step> inProgressSteps = getInProgressSteps(planCoordinator);
                LOGGER.info(
                        "InProgress Steps: {}",
                        inProgressSteps.stream()
                                .map(step -> step.getMessage())
                                .collect(Collectors.toList()));
                activeWorkSet.addAll(inProgressSteps);
                reviveManager.revive(activeWorkSet);

                LOGGER.info("Processing {} offer{} against {} step{}:",
                        offers.size(), offers.size() == 1 ? "" : "s",
                        steps.size(), steps.size() == 1 ? "" : "s");
                for (int i = 0; i < offers.size(); ++i) {
                    LOGGER.info("  {}: {}", i + 1, TextFormat.shortDebugString(offers.get(i)));
                }

                // Match offers with work (call into implementation)
                final Timer.Context context = Metrics.getProcessOffersDurationTimer();
                try {
                    processOffers(driver, offers, steps);
                } finally {
                    context.stop();
                }
            } finally {
                Metrics.incrementProcessedOffers(offers.size());

                synchronized (inProgressLock) {
                    offersInProgress.removeAll(
                            offers.stream()
                                    .map(offer -> offer.getId())
                                    .collect(Collectors.toList()));
                    LOGGER.info("Processed {} queued offer{}. {} {} in progress: {}",
                            offers.size(),
                            offers.size() == 1 ? "" : "s",
                            offersInProgress.size(),
                            offersInProgress.size() == 1 ? "offer remains" : "offers remain",
                            offersInProgress.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()));
                }
            }
        }

        private Set<Step> getInProgressSteps(PlanCoordinator planCoordinator) {
            return planCoordinator.getPlanManagers().stream()
                    .map(planManager -> planManager.getPlan())
                    .flatMap(plan -> plan.getChildren().stream())
                    .flatMap(phase -> phase.getChildren().stream())
                    .filter(step -> step.isRunning())
                    .collect(Collectors.toSet());
        }

        /**
         * Restarts reconciliation following a registration or re-registration.
         */
        private void restartReconciliation() {
            // Task reconciliation should be (re)started on all (re-)registrations.
            reconciler.start();
            reconciler.reconcile(driver);
        }
    }
}
