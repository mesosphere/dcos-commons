package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.OfferUtils;
import com.mesosphere.sdk.reconciliation.DefaultReconciler;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    protected final StateStore stateStore;
    // Mesos may call registered() multiple times in the lifespan of a Scheduler process, specifically when there's
    // master re-election. Avoid performing initialization multiple times, which would cause resourcesQueue to be stuck.
    private final AtomicBoolean isAlreadyRegistered = new AtomicBoolean(false);
    protected final OfferQueue offerQueue = new OfferQueue();
    protected SchedulerDriver driver;
    protected DefaultReconciler reconciler;

    private Object inProgressLock = new Object();
    private Set<Protos.OfferID> offersInProgress = new HashSet<>();

    private Object suppressReviveLock = new Object();

    /**
     * Executor for handling TaskStatus updates in {@link #statusUpdate(SchedulerDriver, Protos.TaskStatus)}.
     */
    protected final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();

    /**
     * Executor for processing offers off the queue in {@code processOffers()}.
     */
    private final ExecutorService offerExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new AbstractScheduler given a {@link StateStore}.
     */
    protected AbstractScheduler(StateStore stateStore) {
        this.stateStore = stateStore;
        processOffers();
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

    private void processOffers() {
        offerExecutor.execute(() -> {
            while (true) {
                // This is a blocking call which pulls as many elements from the offer queue as possible.
                List<Protos.Offer> offers = offerQueue.takeAll();
                processOfferSet(offers);
                synchronized (inProgressLock) {
                    offersInProgress.removeAll(
                            offers.stream()
                                    .map(offer -> offer.getId())
                                    .collect(Collectors.toList()));

                    LOGGER.info("Offers in progress: {}", offersInProgress.stream().collect(Collectors.toList()));
                }
            }
        });
    }

    protected abstract void processOfferSet(List<Protos.Offer> offers);

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        if (!apiServerReady()) {
            LOGGER.info("Waiting for API Server to start ...");
            OfferUtils.declineOffers(driver, offers);
            return;
        }

        // Task Reconciliation:
        // Task Reconciliation must complete before any Tasks may be launched.  It ensures that a Scheduler and
        // Mesos have agreed upon the state of all Tasks of interest to the scheduler.
        // http://mesos.apache.org/documentation/latest/reconciliation/
        reconciler.reconcile(driver);
        if (!reconciler.isReconciled()) {
            LOGGER.info("Waiting for task reconciliation to complete...");
            OfferUtils.declineOffers(driver, offers);
            return;
        }

        synchronized (inProgressLock) {
            offersInProgress.addAll(
                    offers.stream()
                            .map(offer -> offer.getId())
                            .collect(Collectors.toList()));
        }

        for (Protos.Offer offer : offers) {
            boolean queued = offerQueue.offer(offer);
            if (!queued) {
                LOGGER.warn(
                        "Failed to enqueue offer. Offer queue is full. Declining offer: '{}'",
                        offer.getId().getValue());
                OfferUtils.declineOffers(driver, Arrays.asList(offer));
            }
        }
    }

    protected abstract void initialize(SchedulerDriver driver) throws InterruptedException;

    protected abstract boolean apiServerReady();

    /**
     * All offers must have been presented to resourceOffers() before calling this.  This call will block until all
     * offers have been processed.
     * @throws InterruptedException
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

            LOGGER.warn("Offers to be processed {} is non empty, sleeping for 500ms ...", offersInProgress);
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
    public void frameworkMessage(SchedulerDriver driver,
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

    protected void suppressOrRevive(PlanCoordinator planCoordinator) {
        synchronized (suppressReviveLock) {
            if (planCoordinator.hasOperations()) {
                if (StateStoreUtils.isSuppressed(stateStore)) {
                    revive();
                } else {
                    LOGGER.info("Already revived.");
                }
            } else {
                if (StateStoreUtils.isSuppressed(stateStore)) {
                    LOGGER.info("Already suppressed.");
                } else {
                    suppress();
                }
            }
        }
    }

    void suppress() {
        setOfferMode(true);
    }

    void revive() {
        setOfferMode(false);
    }

    private void setOfferMode(boolean suppressed) {
        synchronized (suppressReviveLock) {
            if (suppressed) {
                LOGGER.info("Suppressing offers.");
                driver.suppressOffers();
                StateStoreUtils.setSuppressed(stateStore, true);
            } else {
                LOGGER.info("Reviving offers.");
                driver.reviveOffers();
                StateStoreUtils.setSuppressed(stateStore, false);
            }
        }
    }

    protected void postRegister() {
        reconciler.start();
        reconciler.reconcile(driver);
        revive();
    }

}
