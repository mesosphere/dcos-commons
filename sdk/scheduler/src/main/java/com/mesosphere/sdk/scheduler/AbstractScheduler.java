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
public abstract class AbstractScheduler implements Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractScheduler.class);
    private SuppressReviveManager suppressReviveManager;

    protected final StateStore stateStore;
    protected final ConfigStore<ServiceSpec> configStore;
    // Mesos may call registered() multiple times in the lifespan of a Scheduler process, specifically when there's
    // master re-election. Avoid performing initialization multiple times, which would cause resourcesQueue to be stuck.
    private final AtomicBoolean isAlreadyRegistered = new AtomicBoolean(false);
    protected final OfferQueue offerQueue = new OfferQueue();
    protected SchedulerDriver driver;
    protected DefaultReconciler reconciler;
    protected final EventBus eventBus = new AsyncEventBus(Executors.newSingleThreadExecutor());

    private Object inProgressLock = new Object();
    private Set<Protos.OfferID> offersInProgress = new HashSet<>();
    private AtomicBoolean processingOffers = new AtomicBoolean(false);

    /**
     * Executor for handling TaskStatus updates in {@link #statusUpdate(SchedulerDriver, Protos.TaskStatus)}.
     */
    protected final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();

    /**
     * Executor for processing offers off the queue in {@link #executePlansLoop()}.
     */
    private final ExecutorService offerExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new AbstractScheduler given a {@link StateStore}.
     */
    protected AbstractScheduler(StateStore stateStore, ConfigStore<ServiceSpec> configStore) {
        this.stateStore = stateStore;
        this.configStore = configStore;
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
            this.reconciler = new DefaultReconciler(stateStore);
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

    /**
     * This method starts the main execution thread for the scheduler.  It drives the execution of plans including
     * providing offers.
     */
    private void executePlansLoop() {
        offerExecutor.execute(() -> {
            while (true) {
                List<Protos.Offer> offers = offerQueue.takeAll();
                LOGGER.info("Processing {} offer{}:", offers.size(), offers.size() == 1 ? "" : "s");
                for (int i = 0; i < offers.size(); ++i) {
                    LOGGER.info("  {}: {}", i + 1, TextFormat.shortDebugString(offers.get(i)));
                }
                executePlans(offers);
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


    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        if (!apiServerReady()) {
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

    /**
     * Registration can occur multiple times in a scheduler's lifecycle via both
     * {@link Scheduler#registered(SchedulerDriver, Protos.FrameworkID, Protos.MasterInfo)} and
     * {@link Scheduler#reregistered(SchedulerDriver, Protos.MasterInfo)} calls.
     */
    protected void postRegister() {
        // Task reconciliation should be started on all registrations.
        reconciler.start();
        reconciler.reconcile(driver);

        // A SuppressReviveManager should be constructed only once.
        if (suppressReviveManager == null) {
            suppressReviveManager = new SuppressReviveManager(
                    stateStore,
                    configStore,
                    driver,
                    eventBus,
                    getPlanManagers());
        }

        suppressReviveManager.start();

        // The main plan execution loop should only be started once.
        if (processingOffers.compareAndSet(false, true)) {
            executePlansLoop();
        }
    }

    protected abstract void initialize(SchedulerDriver driver) throws InterruptedException;

    protected abstract boolean apiServerReady();

    /**
     * The abstract scheduler will periodically call this method with a list of available offers, which may be empty.
     */
    protected abstract void executePlans(List<Protos.Offer> offers);

    protected abstract Collection<PlanManager> getPlanManagers();
}
