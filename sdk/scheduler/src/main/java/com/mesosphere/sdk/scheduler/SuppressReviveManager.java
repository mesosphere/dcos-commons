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

    private final Object stateLock = new Object();
    private AtomicReference<State> state = new AtomicReference<>(State.INITIAL);

    /**
     * The states of the suppress/revive state machine.
     */
    public enum State {
        INITIAL,
        WAITING_FOR_OFFER,
        REVIVED,
        SUPPRESSED
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
                transitionState(State.WAITING_FOR_OFFER);
            }
        }).start();
    }

    public State getState() {
        return state.get();
    }

    @Subscribe
    public void handleTaskStatus(Protos.TaskStatus taskStatus) {
        logger.debug("Handling TaskStatus: {}", taskStatus);
        if (TaskUtils.isRecoveryNeeded(taskStatus)) {
            transitionState(State.WAITING_FOR_OFFER);
        }
    }

    @Subscribe
    public void handleOffer(Protos.Offer offer) {
        logger.debug("Handling offer: {}", offer);
        State state = this.state.get();
        switch (state) {
            case WAITING_FOR_OFFER:
                transitionState(State.REVIVED);
                break;
            default:
                logger.debug("State remains '{}' after receiving Offer: {}", state, offer);
        }
    }

    private void transitionState(State target) {
        synchronized (stateLock) {
            State current = getState();

            if (current.equals(target)) {
                logger.debug("NOOP transition for state: '{}'", target);
                return;
            }

            switch (target) {
                case INITIAL:
                    logger.error("Invalid state transition.  End state should never be INITIAL");
                    return;
                case WAITING_FOR_OFFER:
                    revive();
                    break;
                case REVIVED:
                    switch (current) {
                        case WAITING_FOR_OFFER:
                            // The only acceptable transition
                            break;
                        default:
                            logTransitionError(current, target);
                            return;
                    }
                    break;
                case SUPPRESSED:
                    switch (current) {
                        case REVIVED:
                            suppress();
                            break;
                        case WAITING_FOR_OFFER:
                            logTransitionWarning(current, target);
                            return;
                        default:
                            logTransitionError(current, target);
                            return;
                    }
                    break;
            }

            if (state.compareAndSet(current, target)) {
                logger.debug("Transitioned from '{}' to '{}'", current, target);
            } else {
                logger.error("Failed to transitioned from '{}' to '{}'", current, target);
                return;
            }
        }
    }

    private void logTransitionError(State start, State end) {
        logger.error("Invalid transition from '{}' to '{}'.", start, end);
    }

    private void logTransitionWarning(State start, State end) {
        logger.warn("Unexpected transition from '{}' to '{}'.", start, end);
    }

    private void suppressOrRevive() {
        boolean hasOperations = planManagers.stream()
                .anyMatch(planManager -> PlanUtils.hasOperations(planManager.getPlan()));
        if (hasOperations) {
            transitionState(State.WAITING_FOR_OFFER);
        } else {
            transitionState(State.SUPPRESSED);
        }
    }

    private void suppress() {
        synchronized (suppressReviveLock) {
            setOfferMode(true);
        }
    }

    private void revive() {
        synchronized (suppressReviveLock) {
            setOfferMode(false);
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
