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
    public static final int SUPPRESS_REVIVE_INTERVAL_S = 5;
    public static final int SUPPRESS_REVIVE_DELAY_S = 5;

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
                SUPPRESS_REVIVE_DELAY_S,
                SUPPRESS_REVIVE_INTERVAL_S);
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

    /**
     *
     * Instead of just suppressing offers when all work is complete, we set refuse seconds of 2 weeks
     * (a.k.a. forever) whenever we decline any offer.  When we see *new* work ({@link PodInstanceRequirement}`s) we
     * assume that the offers we've declined forever may be useful to that work, and so we revive offers.
     *
     * Pseudo-code algorithm is this:
     *
     *     // We always start out revived
     *     List<Requirement> currRequirements = getRequirements();

     *     while (true) {
     *         List<Requirement> newRequirements = getRequirements() - currRequirements;
     *         if (newRequirements.isEmpty()) {
     *             print “No new work”;
     *         } else {
     *             currRequirements = newRequirements;
     *             revive();
     *         }
     *     }

     * I've left out the `supress` portion of the logic in the pseudocode, but it's in the real code.  In short, if
     * there's no work, suppress.

     * A natural question is why do we overwrite `currRequirements` with `newRequirements`?  Anything that is in
     * `currRequirements` has had revive called for it.  Any change with regard to `currRequirements` i.e.
     * `newRequirements` implies that those requirements may need an offer which has been declined forever, so we need
     * to revive.  We cannot maintain a cummulative list in `currRequirements` because we may see the same work twice.

     * e.g.
     *     `kafka-0-broker` fails    @ 10:30, it's new work!
     *     `kafka-0-broker` recovers @ 10:35
     *     `kafka-0-broker` fails    @ 11:00, it's new work!
     *     ...
     */
    private void suppressOrRevive() {
        Set<PodInstanceRequirement> newCandidates = getRequirements(planCoordinator.getCandidates());
        if (newCandidates.isEmpty()) {
            logger.debug("No candidates found");
            suppress();
            candidates = newCandidates;
            return;
        }

        newCandidates.removeAll(candidates);

        logger.debug("Old candidates: {}", candidates);
        logger.debug("New candidates: {}", newCandidates);

        if (newCandidates.isEmpty()) {
            logger.debug("No new candidates detected, no need to revive");
        } else {
            candidates = newCandidates;
            revive();
        }
    }

    private void suppress() {
        setSuppressed(true);
    }

    private void revive() {
        setSuppressed(false);
    }

    private void setSuppressed(boolean suppressed) {
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
