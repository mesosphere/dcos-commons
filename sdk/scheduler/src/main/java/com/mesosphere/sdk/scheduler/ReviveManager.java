package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
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
 * This class determines whether offers should be revived based on changes to the work being processed by the scheduler.
 */
public class ReviveManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SchedulerDriver driver;
    private final TokenBucket tokenBucket;
    private Set<WorkItem> candidates = new HashSet<>();

    public ReviveManager(SchedulerDriver driver) {
        this(driver, TokenBucket.newBuilder().build());
    }

    public ReviveManager(SchedulerDriver driver, TokenBucket tokenBucket) {
        this.driver = driver;
        this.tokenBucket = tokenBucket;
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
     *     List<Requirement> oldRequirements = getRequirements();
     *
     *     while (true) {
     *         List<Requirement> currRequirements = getRequirements()
     *         List<Requirement> newRequirements = oldRequirements - currRequirements;
     *         if (newRequirements.isEmpty()) {
     *             print “No new work”;
     *         } else {
     *             oldRequirements = currRequirements;
     *             revive();
     *         }
     *     }
     *
     * The invariant maintained is that the oldRequirements have always had revive called for them at least once, giving
     * them access to offers.
     *
     * This case must work:
     *
     *     kafka-0-broker fails    @ 10:30, it's new work!
     *     kafka-0-broker recovers @ 10:35
     *     kafka-0-broker fails    @ 11:00, it's new work!
     *     ...
     */
    public void revive(Collection<Step> activeWorkSet) {
        Set<WorkItem> currCandidates = activeWorkSet.stream()
                .map(step -> new WorkItem(step))
                .collect(Collectors.toSet());
        Set<WorkItem> newCandidates = new HashSet<>(currCandidates);
        newCandidates.removeAll(this.candidates);

        logger.info("Candidates, old: {}, current: {}, new:{}", this.candidates, currCandidates, newCandidates);

        if (!newCandidates.isEmpty()) {
            if (tokenBucket.tryAcquire()) {
                logger.info(
                        "Reviving offers with candidates, old: {}, current: {}, new:{}",
                        this.candidates,
                        currCandidates,
                        newCandidates);
                driver.reviveOffers();
                Metrics.incrementRevives();
            } else {
                logger.warn("Revive attempt has been throttled.");
                Metrics.incrementReviveThrottles();
                return;
            }
        }

        this.candidates = currCandidates;
    }

    /**
     * A WorkItem encapsulates the relevant elements of a {@link Step} for the purposes of determining whether reviving
     * offers is necessary.
     */
    private static class WorkItem {
        private final Optional<PodInstanceRequirement> podInstanceRequirement;
        private final String name;

        private WorkItem(Step step) {
            this.podInstanceRequirement = step.getPodInstanceRequirement();
            this.name = step.getName();
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
            return String.format("%s [%s]",
                    name,
                    podInstanceRequirement.isPresent() ?
                            podInstanceRequirement.get().getRecoveryType() :
                            "N/A");
        }
    }
}
