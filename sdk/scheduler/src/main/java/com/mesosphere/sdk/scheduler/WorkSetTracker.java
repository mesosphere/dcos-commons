package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tracks the current work set in the service, and determines whether the service has new work that warrants a revive.
 */
public class WorkSetTracker {

    private final Logger logger;

    // NOTE: In practice we are only accessed within a single thread, so we don't worry about locking here.
    private Set<WorkItem> candidates = new HashSet<>();
    private boolean hasNewWork = false;

    /**
     * Creates an instance which will use the configured singleton {@link TokenBucket} for rate limiting. The
     * {@link TokenBucket} is a shared singleton because we want revive rate limits to be enforced across all running
     * services in the scheduler process.
     */
    public WorkSetTracker(Optional<String> namespace) {
        this.logger = LoggingUtils.getLogger(getClass(), namespace);
    }

    /**
     * Instead of just suppressing offers when all work is complete, we set refuse seconds of 2 weeks
     * (a.k.a. forever) whenever we decline any offer.  When we see *new* work ({@link PodInstanceRequirement}s) we
     * assume that the offers we've declined forever may be useful to that work, and so we revive offers.
     *
     * Pseudo-code algorithm:
     *
     *     // We always start out revived
     *     List<Requirement> oldWorkSet = getWorkSet();
     *
     *     while (true) {
     *         List<Requirement> currWorkSet = getWorkSet()
     *         List<Requirement> newWorkSet = oldWorkSet - currWorkSet;
     *         if (newWorkSet.isEmpty()) {
     *             print "No new work";
     *         } else {
     *             oldWorkSet = currWorkSet;
     *             newWork = true;
     *         }
     *     }
     *
     * The invariant maintained is that {@code oldWorkSet} always had revive called for it at least once, giving it
     * access to offers.
     *
     * This case must work:
     * <ul>
     * <li>kafka-0-broker fails    @ 10:30, it's new work!</li>
     * <li>kafka-0-broker recovers @ 10:35</li>
     * <li>kafka-0-broker fails    @ 11:00, it's new work!</li>
     * <li>...</li>
     * </ul>
     */
    public void updateWorkSet(Collection<Step> activeWorkSet) {
        Set<WorkItem> currCandidates = activeWorkSet.stream()
                .map(step -> new WorkItem(step))
                .collect(Collectors.toSet());
        Set<WorkItem> newCandidates = new HashSet<>(currCandidates);
        newCandidates.removeAll(this.candidates);

        // If all three lists are empty (idle state), avoid logging:
        if (!newCandidates.isEmpty()) {
            logger.info("New work detected: old:{}, current:{} => new:{}",
                    this.candidates, currCandidates, newCandidates);
            this.hasNewWork = true;
        } else if (!this.candidates.isEmpty() || !currCandidates.isEmpty()) {
            logger.info("No new work: old:{}, current:{}", this.candidates, currCandidates);
        }

        this.candidates = currCandidates;
    }

    /**
     * Returns whether new work has appeared via a prior call to {@link #updateWorkSet(Collection)}. This information
     * should be passed upstream so that they know to revive offers. The state is internally reset after this call has
     * occurred.
     */
    public boolean hasNewWork() {
        boolean ret = this.hasNewWork;
        this.hasNewWork = false;
        return ret;
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
