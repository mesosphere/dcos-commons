package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.OfferUtils;
import com.mesosphere.sdk.reconciliation.DefaultReconciler;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
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
 * Abstract {@link Scheduler} that provides some default behaviors around Mesos lifecycle events such as
 * getting registered, reregistered, disconnected, etc.
 */
public abstract class AbstractScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractScheduler.class);

    protected final StateStore stateStore;
    protected final ConfigStore<ServiceSpec> configStore;
    protected final SchedulerFlags schedulerFlags;

    private SchedulerApiServer apiServer;
    private final AtomicBoolean apiServerStarted = new AtomicBoolean(false);

    private final MesosScheduler mesosScheduler = new MesosScheduler();
    protected final EventBus eventBus = new AsyncEventBus(Executors.newSingleThreadExecutor());
    protected DefaultReconciler reconciler;

    private Object inProgressLock = new Object();
    private Set<Protos.OfferID> offersInProgress = new HashSet<>();

    /**
     * Executor for handling TaskStatus updates in {@link #statusUpdate(SchedulerDriver, Protos.TaskStatus)}.
     */
    protected final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();

    /**
     * Executor for processing offers off the queue in {@link #processOfferSet(List)}.
     */
    private final ExecutorService offerExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new AbstractScheduler given a {@link StateStore}.
     */
    protected AbstractScheduler(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerFlags schedulerFlags) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.schedulerFlags = schedulerFlags;
    }

    /**
     * Starts any internal threads to be used by the service.
     */
    public void start() {
        this.apiServer = new SchedulerApiServer(schedulerFlags, getResources());
        this.apiServer.start(new AbstractLifeCycle.AbstractLifeCycleListener() {
            @Override
            public void lifeCycleStarted(LifeCycle event) {
                apiServerStarted.set(true);
            }
        });

        // Start consumption of the offer queue. This will idle until offers start arriving.
        offerExecutor.execute(() -> {
            while (true) {
                // This is a blocking call which pulls as many elements from the offer queue as possible.
                List<Protos.Offer> offers = mesosScheduler.offerQueue.takeAll();
                LOGGER.info("Processing {} offer{}:", offers.size(), offers.size() == 1 ? "" : "s");
                for (int i = 0; i < offers.size(); ++i) {
                    LOGGER.info("  {}: {}", i + 1, TextFormat.shortDebugString(offers.get(i)));
                }
                processOfferSet(offers);
                offers.forEach(offer -> eventBus.post(offer));
                synchronized (inProgressLock) {
                    offersInProgress.removeAll(
                            offers.stream()
                                    .map(offer -> offer.getId())
                                    .collect(Collectors.toList()));
                    LOGGER.info("Processed {} queued offer{}. Remaining offers in progress: {}",
                            offers.size(),
                            offers.size() == 1 ? "" : "s",
                            offersInProgress.stream().collect(Collectors.toList()));
                }
            }
        });
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
     */
    @VisibleForTesting
    public void awaitOffersProcessed() throws InterruptedException {
        while (true) {
            synchronized (inProgressLock) {
                if (offersInProgress.isEmpty()) {
                    LOGGER.info("All offers processed.");
                    return;
                }
            }

            LOGGER.warn("Offers in progress {} is non empty, sleeping for 500ms ...", offersInProgress);
            Thread.sleep(500);
        }
    }

    protected abstract Collection<Object> getResources();
    protected abstract void initialize(SchedulerDriver driver) throws Exception;
    protected abstract void processOfferSet(List<Protos.Offer> offers);
    protected abstract void processStatusUpdate(Protos.TaskStatus status);
    protected abstract Collection<PlanManager> getPlanManagers();

    private class MesosScheduler implements Scheduler {

        // Mesos may call registered() multiple times in the lifespan of a Scheduler process, specifically when there's
        // master re-election. Avoid performing initialization multiple times, which would cause queues to be stuck.
        private final AtomicBoolean isAlreadyRegistered = new AtomicBoolean(false);
        private final OfferQueue offerQueue = new OfferQueue();

        private SchedulerDriver driver;
        private SuppressReviveManager suppressReviveManager;

        @Override
        public void registered(SchedulerDriver driver_, Protos.FrameworkID frameworkId, Protos.MasterInfo masterInfo) {
            if (isAlreadyRegistered.getAndSet(true)) {
                // This may occur as the result of a master election.
                LOGGER.info("Already registered, calling reregistered()");
                reregistered(driver, masterInfo);
                return;
            }

            LOGGER.info("Registered framework with frameworkId: {}", frameworkId.getValue());
            try {
                reconciler = new DefaultReconciler(stateStore);
                initialize(driver);
            } catch (Exception e) {
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

            driver = driver_;
            postRegister();
        }

        @Override
        public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
            if (!apiServerStarted.get()) {
                LOGGER.info("Declining {} offer{}: Waiting for API Server to start.",
                        offers.size(), offers.size() == 1 ? "" : "s");
                OfferUtils.declineOffers(driver, offers);
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
                OfferUtils.declineOffers(driver, offers);
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
                        offersInProgress.stream().collect(Collectors.toList()));
            }

            for (Protos.Offer offer : offers) {
                boolean queued = offerQueue.offer(offer);
                if (!queued) {
                    LOGGER.warn("Offer queue is full: Declining offer and removing from in progress: '{}'",
                            offer.getId().getValue());
                    OfferUtils.declineOffers(driver, Arrays.asList(offer));
                    // Remove AFTER decline: Avoid race where we haven't declined yet but appear to be done
                    synchronized (inProgressLock) {
                        offersInProgress.remove(offer.getId());
                    }
                }
            }
        }

        @Override
        public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
            LOGGER.info("Received status update for taskId={} state={} message={} protobuf={}",
                    status.getTaskId().getValue(),
                    status.getState().toString(),
                    status.getMessage(),
                    TextFormat.shortDebugString(status));
            processStatusUpdate(status);
        }

        @Override
        public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
            LOGGER.info("Re-registered with master: {}", TextFormat.shortDebugString(masterInfo));
            postRegister();
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
        public void executorLost(SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID agentId, int status) {
            // TODO: Add recovery optimizations relevant to loss of an Executor.  TaskStatus updates are sufficient now.
            LOGGER.warn("Lost Executor: {} on Agent: {}", executorId.getValue(), agentId.getValue());
        }

        @Override
        public void error(SchedulerDriver driver, String message) {
            LOGGER.error("SchedulerDriver returned an error, shutting down: {}", message);
            SchedulerUtils.hardExit(SchedulerErrorCode.ERROR);
        }

        private void postRegister() {
            reconciler.start();
            reconciler.reconcile(driver);
            if (suppressReviveManager == null) {
                suppressReviveManager = new SuppressReviveManager(
                        stateStore,
                        configStore,
                        driver,
                        eventBus,
                        getPlanManagers());
            }
            suppressReviveManager.start();
        }
    }
}
