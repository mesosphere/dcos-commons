package com.mesosphere.sdk.scheduler;

import com.google.common.eventbus.EventBus;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanUtils;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
    private final PlanCoordinator planCoordinator;
    private final StateStore stateStore;

    private final ConfigStore<ServiceSpec> configStore;
    private AtomicReference<State> state = new AtomicReference<>(State.INITIAL);
    private Set<String> candidates = Collections.emptySet();

    /**
     * The states of the suppress/revive state machine.
     */
    public enum State {
        INITIAL,
        WAITING_FOR_OFFER,
        REVIVED
    }

    public SuppressReviveManager(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerDriver driver,
            EventBus eventBus,
            PlanCoordinator planCoordinator) {
        this(
                stateStore,
                configStore,
                driver,
                eventBus,
                planCoordinator,
                SUPPRESSS_REVIVE_DELAY_S,
                SUPPRESSS_REVIVE_INTERVAL_S);
    }

    public SuppressReviveManager(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerDriver driver,
            EventBus eventBus,
            PlanCoordinator planCoordinator,
            int pollDelay,
            int pollInterval) {

        this.stateStore = stateStore;
        this.configStore = configStore;
        this.driver = driver;
        this.planCoordinator = planCoordinator;
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
                planCoordinator.getPlanManagers().stream()
                        .map(planManager -> planManager.getPlan().getName())
                        .collect(Collectors.toList()));
    }

    private void suppressOrRevive() {
        Set<String> newCandidates = planCoordinator.getCandidates().stream()
                .filter(step -> step.getPodInstanceRequirement().isPresent())
                .map(step -> step.getPodInstanceRequirement().get())
                .flatMap(req -> TaskUtils.getTaskNames(req.getPodInstance()).stream())
                .collect(Collectors.toSet());
        logger.debug("Got candidates: {}", newCandidates);

        newCandidates.removeAll(candidates);

        logger.debug("Old candidates: {}", candidates);
        logger.debug("New candidates: {}", newCandidates);

        if (newCandidates.isEmpty()) {
            logger.debug("No new candidates detected, no need to revive.");
        } else {
            logger.info("Reviving, new candidates detected: {}", newCandidates);
            candidates = newCandidates;
            logger.info("Added new candidates");
            revive();
        }
    }

    private void revive() {
        logger.info("Reviving offers.");
        StateStoreUtils.setSuppressed(stateStore, false);
        driver.reviveOffers();
    }
}
