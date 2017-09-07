package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class monitors a {@link PlanCoordinator} and suppresses or revives offers when appropriate.
 */
public class SuppressReviveManager {
    public static final int REVIVE_INTERVAL_S = 5;
    public static final int REVIVE_DELAY_S = 5;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SchedulerDriver driver;
    private final PlanCoordinator planCoordinator;
    private final ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
    private final StateStore stateStore;
    private Set<PodInstanceRequirement> candidates;

    public SuppressReviveManager(
            SchedulerDriver driver,
            StateStore stateStore,
            PlanCoordinator planCoordinator) {
        this(
                driver,
                stateStore,
                planCoordinator,
                REVIVE_DELAY_S,
                REVIVE_INTERVAL_S);
    }

    public SuppressReviveManager(
            SchedulerDriver driver,
            StateStore stateStore,
            PlanCoordinator planCoordinator,
            int pollDelay,
            int pollInterval) {

        this.driver = driver;
        this.stateStore = stateStore;
        this.planCoordinator = planCoordinator;
        this.candidates = getRequirements(planCoordinator.getCandidates());
        monitor.scheduleAtFixedRate(
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
                "Monitoring these plans for suppress/suppressOrRevive: {}",
                planCoordinator.getPlanManagers().stream()
                        .map(planManager -> planManager.getPlan().getName())
                        .collect(Collectors.toList()));
    }

    private Set<PodInstanceRequirement> getRequirements(Collection<Step> steps) {
        return steps.stream()
                .filter(step -> step.getPodInstanceRequirement().isPresent())
                .map(step -> step.getPodInstanceRequirement().get())
                .collect(Collectors.toSet());
    }

    private void suppressOrRevive() {
        Set<PodInstanceRequirement> newCandidates = getRequirements(planCoordinator.getCandidates());
        if (newCandidates.isEmpty()) {
            logger.info("No candidates, suppressing offers");
            suppress();
            candidates = newCandidates;
            return;
        }

        newCandidates.removeAll(candidates);

        logger.info("Old candidates: {}", candidates);
        logger.info("New candidates: {}", newCandidates);

        if (newCandidates.isEmpty()) {
            logger.info("No new candidates detected, no need to revive.");
        } else {
            candidates = newCandidates;
            logger.info("Reviving offers");
            revive();
        }
    }

    private void suppress() {
        setOfferMode(true);
    }

    private void revive() {
        setOfferMode(false);
    }

    private void setOfferMode(boolean suppressed) {
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
