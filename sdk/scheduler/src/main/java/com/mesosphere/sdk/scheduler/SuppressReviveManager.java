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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * This class monitors all plans and suppresses or revives offers when appropriate.
 */
public class SuppressReviveManager {
    public static final int SUPPRESSS_REVIVE_INTERVAL_S = 5;
    public static final int SUPPRESSS_REVIVE_DELAY_S = 5;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ScheduledExecutorService plansMonitor = Executors.newScheduledThreadPool(1);
    private final SchedulerDriver driver;
    private final Collection<PlanManager> planManagers;
    private final StateStore stateStore;

    private final Object suppressReviveLock = new Object();
    private AtomicReference<State> state = new AtomicReference<>(State.INITIAL);

    public enum State {
        INITIAL,
        WAITING_FOR_OFFER,
        REVIVED,
        SUPPRESSED
    }

    @Subscribe
    public void handleTaskStatus(Protos.TaskStatus taskStatus) {
        logger.debug("Handling TaskStatus: {}", taskStatus);
        if (TaskUtils.isRecoveryNeeded(taskStatus)) {
            revive();
        }
    }

    @Subscribe
    public void handleOffer(Protos.Offer offer) {
        logger.debug("Handling offer: {}", offer);
        state.compareAndSet(State.WAITING_FOR_OFFER, State.REVIVED);
    }

    public State getState() {
        return state.get();
    }

    public SuppressReviveManager(
            StateStore stateStore,
            SchedulerDriver driver,
            EventBus eventBus,
            Collection<PlanManager> planManagers) {
        this(stateStore, driver, eventBus, planManagers, SUPPRESSS_REVIVE_DELAY_S, SUPPRESSS_REVIVE_INTERVAL_S);
    }

    public SuppressReviveManager(
            StateStore stateStore,
            SchedulerDriver driver,
            EventBus eventBus,
            Collection<PlanManager> planManagers,
            int pollDelay,
            int pollInterval) {

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
                pollDelay,
                pollInterval,
                TimeUnit.SECONDS);
        logger.info(
                "Monitoring these plans for suppress/revive: {}",
                planManagers.stream().map(planManager -> planManager.getPlan().getName()).collect(Collectors.toList()));
    }

    public void start() {
        // This must be run on a separate thread to avoid deadlock with the MesosToSchedulerDriverAdapter
        new Thread(new Runnable() {
            @Override
            public void run() {
                revive();
            }
        }).start();
    }

    private void suppressOrRevive() {
        synchronized (suppressReviveLock) {
            if (state.get().equals(State.WAITING_FOR_OFFER)) {
                logger.debug("Waiting for an offer.");
                return;
            }

            boolean hasOperations = planManagers.stream()
                    .anyMatch(planManager -> PlanUtils.hasOperations(planManager.getPlan()));
            if (hasOperations) {
                if (StateStoreUtils.isSuppressed(stateStore)) {
                    revive();
                } else {
                    logger.debug("Already revived.");
                }
            } else {
                if (StateStoreUtils.isSuppressed(stateStore)) {
                    logger.debug("Already suppressed.");
                } else {
                    suppress();
                }
            }
        }
    }

    private void suppress() {
        synchronized (suppressReviveLock) {
            setOfferMode(true);
            state.set(State.SUPPRESSED);
        }
    }

    private void revive() {
        synchronized (suppressReviveLock) {
            setOfferMode(false);
            state.set(State.WAITING_FOR_OFFER);
        }
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
