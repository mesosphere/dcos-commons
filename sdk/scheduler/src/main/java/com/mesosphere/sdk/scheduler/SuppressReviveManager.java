package com.mesosphere.sdk.scheduler;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.PlanUtils;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * This class monitors all plans and suppresses or revives offers when appropriate.
 */
public class SuppressReviveManager {
    private static SuppressReviveManager suppressReviveManager;
    private static final int SUPPRESSS_REVIVE_POLL_RATE_S = 5;
    private static final int SUPPRESSS_REVIVE_DELAY_S = 30;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ScheduledExecutorService plansMonitor = Executors.newScheduledThreadPool(1);
    private final SchedulerDriver driver;
    private final Collection<PlanManager> planManagers;
    private final StateStore stateStore;

    /**
     * A race is possible with the scheduled thread.
     *     1. Revive
     *     2. Check plans for suppress/revive
     *     3. Plans are still all complete because they haven't been refreshed by an Offer
     *     4. Suppress
     *     5. Oops
     *
     * To avoid this race if we are revived, we cannot be again suppressed until an Offer and been processed.  See
     * {@link AbstractScheduler#processOffers()}
     */
    private AtomicBoolean eligibleToSuppress = new AtomicBoolean(false);

    private final Object suppressReviveLock = new Object();
    private static final Object instanceLock = new Object();

    public static void start(
            StateStore stateStore,
            SchedulerDriver driver,
            EventBus eventBus,
            Collection<PlanManager> planManagers) {

        synchronized (instanceLock) {
            if (suppressReviveManager == null) {
                suppressReviveManager = new SuppressReviveManager(stateStore, driver, eventBus, planManagers);
                suppressReviveManager.revive();
            }
        }
    }

    public static Optional<SuppressReviveManager> getSuppressReviveManager() {
        synchronized (instanceLock) {
            return Optional.ofNullable(suppressReviveManager);
        }
    }

    public static void reviveNow() {
        Optional<SuppressReviveManager> suppressReviveManager = getSuppressReviveManager();
        if (suppressReviveManager.isPresent()) {
            suppressReviveManager.get().revive();
        }
    }

    public void revive() {
        reviveInternal();
    }

    @Subscribe
    public void handleTaskStatus(Protos.TaskStatus taskStatus) {
        if (TaskUtils.isRecoveryNeeded(taskStatus)) {
            SuppressReviveManager.reviveNow();
        }
    }

    @Subscribe
    public void handleOffer(Protos.Offer offer) {
        eligibleToSuppress.set(true);
    }

    private SuppressReviveManager(
            StateStore stateStore,
            SchedulerDriver driver,
            EventBus eventBus,
            Collection<PlanManager> planManagers) {

        this.stateStore = stateStore;
        this.driver = driver;
        this.planManagers = planManagers;
        eventBus.register(this);
        plansMonitor.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        suppressOrRevive();
                    }
                },
                SUPPRESSS_REVIVE_DELAY_S,
                SUPPRESSS_REVIVE_POLL_RATE_S,
                TimeUnit.SECONDS);
        logger.info(
                "Monitoring these plans for suppress/revive: {}",
                planManagers.stream().map(planManager -> planManager.getPlan().getName()).collect(Collectors.toList()));
    }

    private void suppressOrRevive() {
        synchronized (suppressReviveLock) {
            boolean hasOperations = planManagers.stream()
                    .anyMatch(planManager -> PlanUtils.hasOperations(planManager.getPlan()));
            if (hasOperations) {
                if (StateStoreUtils.isSuppressed(stateStore)) {
                    reviveInternal();
                } else {
                    logger.info("Already revived.");
                }
            } else {
                if (StateStoreUtils.isSuppressed(stateStore)) {
                    logger.info("Already suppressed.");
                } else {
                    suppressInternal();
                }
            }
        }
    }

    private void suppressInternal() {
        if (eligibleToSuppress.get()) {
            setOfferMode(true);
            eligibleToSuppress.set(false);
        } else {
            logger.warn("Skipping suppress, because not yet eligible to suppress.");
        }
    }

    private void reviveInternal() {
        setOfferMode(false);
    }

    private void setOfferMode(boolean suppressed) {
        synchronized (suppressReviveLock) {
            if (suppressed) {
                logger.info("Suppressing offers.");
                driver.suppressOffers();
                StateStoreUtils.setSuppressed(stateStore, true);
            } else {
                logger.info("Reviving offers.");
                driver.reviveOffers();
                StateStoreUtils.setSuppressed(stateStore, false);
            }
        }
    }
}
