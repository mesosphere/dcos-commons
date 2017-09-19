package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class monitors a {@link PlanCoordinator} and revives offers when appropriate.
 */
public class ReviveManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SchedulerDriver driver;
    private final StateStore stateStore;
    private final TokenBucket tokenBucket;
    private Set<WorkItem> candidates = new HashSet<>();

    public ReviveManager(SchedulerDriver driver, StateStore stateStore) {
        this.driver = driver;
        this.stateStore = stateStore;
        this.tokenBucket = new TokenBucket();
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
    public void revive(Collection<Step> steps) {
        Set<WorkItem> newCandidates = getCandidates(steps);
        logger.info("Current candidates: {}", newCandidates);
        newCandidates.removeAll(candidates);

        logger.info("Old     candidates: {}", candidates);
        logger.info("New     candidates: {}", newCandidates);

        if (newCandidates.isEmpty()) {
            logger.info("No new candidates detected, no need to revive");
        } else {
            if (tokenBucket.tryAcquire()) {
                logger.info("Reviving offers.");
                driver.reviveOffers();
                StateStoreUtils.setSuppressed(stateStore, false);
            } else {
                logger.warn("Revive attempt has been throttled.");
                return;
            }
        }

        candidates = newCandidates;
    }

    private Set<WorkItem> getCandidates(Collection<Step> steps) {
        return steps.stream()
                .filter(step -> step.getStatus().equals(Status.PENDING) || step.getStatus().equals(Status.PREPARED))
                .map(step -> new WorkItem(step))
                .collect(Collectors.toSet());
    }

    private static class WorkItem {
        private final Optional<PodInstanceRequirement> podInstanceRequirement;
        private final String name;
        private final Status status;

        private WorkItem(Step step) {
            this.podInstanceRequirement = step.getPodInstanceRequirement();
            this.name = step.getName();
            this.status = step.getStatus();
        }

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }

        @Override
        public String toString() {
            return String.format("%s [%s]", name, status);
        }
    }
}
