package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.PlanUtils;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class monitors all plans and suppresses or revives offers when appropriate.
 */
public class SuppressReviveManager {
    private static SuppressReviveManager suppressReviveManager;
    private static final int SUPPRESSS_REVIVE_POLL_RATE_S = 5;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ScheduledExecutorService plansMonitor = Executors.newScheduledThreadPool(1);
    private final SchedulerDriver driver;
    private final Collection<PlanManager> planManagers;
    private final StateStore stateStore;

    private final Object suppressReviveLock = new Object();
    private static final Object instanceLock = new Object();

    public static void start(
            StateStore stateStore,
            SchedulerDriver driver,
            Collection<PlanManager> planManagers) {

        synchronized (instanceLock) {
            if (suppressReviveManager == null) {
                suppressReviveManager = new SuppressReviveManager(stateStore, driver, planManagers);
                suppressReviveManager.revive();
            }
        }
    }

    public static Optional<SuppressReviveManager> getSuppressReviveManager() {
        synchronized (instanceLock) {
            return Optional.ofNullable(suppressReviveManager);
        }
    }

    private SuppressReviveManager(StateStore stateStore, SchedulerDriver driver, Collection<PlanManager> planManagers) {
        this.stateStore = stateStore;
        this.driver = driver;
        this.planManagers = planManagers;
        plansMonitor.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        suppressOrRevive();
                    }
                },
                0,
                SUPPRESSS_REVIVE_POLL_RATE_S,
                TimeUnit.SECONDS);
        logger.info(
                "Monitoring these plans for suppress/revive: {}",
                planManagers.stream().map(planManager -> planManager.getPlan().getName()).collect(Collectors.toList()));
    }

    public static void revive() {
        Optional<SuppressReviveManager> suppressReviveManager = getSuppressReviveManager();
        if (suppressReviveManager.isPresent()) {
            suppressReviveManager.get().reviveNow();
        }
    }

    public void reviveNow() {
        reviveInternal();
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
        setOfferMode(true);
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
