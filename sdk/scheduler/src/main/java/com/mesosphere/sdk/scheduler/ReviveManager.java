package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.queue.WorkSet;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class monitors a {@link PlanCoordinator} and revives offers when appropriate.
 */
public class ReviveManager {
    public static final int REVIVE_INTERVAL_S = 5;
    public static final int REVIVE_DELAY_S = 5;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SchedulerDriver driver;
    private final ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
    private final StateStore stateStore;
    private final WorkSet workSet;
    private Set<Step> candidates;

    public ReviveManager(
            SchedulerDriver driver,
            StateStore stateStore,
            WorkSet workSet) {
        this(
                driver,
                stateStore,
                workSet,
                REVIVE_DELAY_S,
                REVIVE_INTERVAL_S);
    }

    public ReviveManager(
            SchedulerDriver driver,
            StateStore stateStore,
            WorkSet workSet,
            int pollDelay,
            int pollInterval) {

        this.driver = driver;
        this.stateStore = stateStore;
        this.workSet = workSet;
        this.candidates = workSet.getWork();
        monitor.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        revive();
                    }
                },
                pollDelay,
                pollInterval,
                TimeUnit.SECONDS);
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
     *
     *     while (true) {
     *         List<Requirement> newRequirements = getRequirements() - currRequirements;
     *         if (newRequirements.isEmpty()) {
     *             print “No new work”;
     *         } else {
     *             currRequirements = newRequirements;
     *             revive();
     *         }
     *     }
     *
     * A natural question is why do we overwrite {@code currRequirements} with {@code newRequirements}?  Anything that
     * is in {@code currRequirements} has had revive called for it.  Any change with regard to {@code currRequirements}
     * i.e. {@code newRequirements} implies that those requirements may need an offer which has been declined forever,
     * so we need to revive.  We cannot maintain a cummulative list in {@code currRequirements} because we may see the
     * same work twice.

     * e.g.
     *     kafka-0-broker fails    @ 10:30, it's new work!
     *     kafka-0-broker recovers @ 10:35
     *     kafka-0-broker fails    @ 11:00, it's new work!
     *     ...
     */
    private void revive() {
        Set<Step> newCandidates = workSet.getWork();
        newCandidates.removeAll(candidates);

        logger.debug("Old candidates: {}", candidates);
        logger.debug("New candidates: {}", newCandidates);
        candidates = newCandidates;

        if (newCandidates.isEmpty()) {
            logger.debug("No new candidates detected, no need to revive");
        } else {
            logger.info("Reviving offers.");
            driver.reviveOffers();
            StateStoreUtils.setSuppressed(stateStore, false);
        }
    }
}
