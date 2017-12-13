package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Base implementation of common round-robin logic.
 */
abstract class AbstractRoundRobinRule implements PlacementRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRoundRobinRule.class);

    protected final StringMatcher taskFilter;
    protected final Optional<Integer> distinctKeyCount;

    protected AbstractRoundRobinRule(StringMatcher taskFilter, Optional<Integer> distinctKeyCount) {
        if (taskFilter == null) { // null when unspecified in serialized data
            taskFilter = AnyMatcher.create();
        }
        this.taskFilter = taskFilter;
        this.distinctKeyCount = distinctKeyCount;
    }

    /**
     * Returns a key to round robin against from the provided {@link Offer}, or {@code null} if
     * none was found.
     */
    protected abstract String getKey(Offer offer);

    /**
     * Returns a key to round robin against from the provided {@link TaskInfo}, or {@code null} if
     * none was found.
     */
    protected abstract String getKey(TaskInfo task);

    @Override
    public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
        final String offerKey = getKey(offer);
        if (offerKey == null) {
            // offer doesn't have the required attribute at all. denied.
            return EvaluationOutcome.fail(this, "Offer lacks required round robin key").build();
        }

        // search across tasks, keeping key counts on a per-key basis.
        // key => # of instances on key
        Map<String, Integer> counts = new HashMap<>();
        for (TaskInfo task : tasks) {
            // only tally tasks which match the task matcher (eg 'index-.*')
            if (!taskFilter.matches(task.getName())) {
                continue;
            }
            if (PlacementUtils.areEquivalent(task, podInstance)) {
                // This is stale data for the same task that we're currently evaluating for
                // placement. Don't worry about counting its usage. This occurs when we're
                // redeploying a given task with a new configuration (old data not deleted yet).
                continue;
            }

            final String taskKey = getKey(task);
            if (taskKey == null) {
                // no key matching the name was found. ignore.
                continue;
            }
            Integer count = counts.get(taskKey);
            counts.put(taskKey, (count == null) ? 1 : count + 1);
        }

        int maxKnownKeyCount = 0;
        int minKnownKeyCount = Integer.MAX_VALUE;
        for (Integer count : counts.values()) {
            if (count > maxKnownKeyCount) {
                maxKnownKeyCount = count;
            }
            if (count < minKnownKeyCount) {
                minKnownKeyCount = count;
            }
        }
        if (minKnownKeyCount == Integer.MAX_VALUE) {
            minKnownKeyCount = 0;
        }
        Integer offerKeyCount = counts.get(offerKey);
        if (offerKeyCount == null) {
            offerKeyCount = 0;
        }
        LOGGER.info("Key counts: {}, knownMin: {}, knownMax: {}, offer: {}",
                counts, minKnownKeyCount, maxKnownKeyCount, offerKeyCount);

        if (minKnownKeyCount == maxKnownKeyCount
                || offerKeyCount <= minKnownKeyCount) {
            // all (known) keys are full at the current level, or this offer is on a key
            // which is at the smallest number of tasks (or below if we haven't expanded to this key yet)
            if (!distinctKeyCount.isPresent()) {
                // we don't know how many distinct keys are out there, and this key has fewer instances
                // than some other key in the system.
                return EvaluationOutcome.pass(
                        this,
                        "Distinct key count is unspecified, and '%s' has %d instances while others have%d to %d",
                        offerKey, offerKeyCount, minKnownKeyCount, maxKnownKeyCount).build();
            } else if (counts.size() >= distinctKeyCount.get()) {
                // no keys are missing from our counts, and this key has fewer instances than some other key in
                // the system.
                return EvaluationOutcome.pass(
                        this,
                        "All distinct keys are found, and '%s' has %d instances while others have %d to %d",
                        offerKey, offerKeyCount, minKnownKeyCount, maxKnownKeyCount).build();
            }
            // we know that there are other keys out there which have nothing on them at all.
            // only launch here if this key also has nothing on it.
            if (offerKeyCount == 0) {
                return EvaluationOutcome.pass(
                        this, "Other keys have zero usage, and so does key '%s'", offerKey).build();
            } else {
                return EvaluationOutcome.fail(
                        this, "Other keys have zero instances, but key '%s' has %d", offerKey, offerKeyCount)
                        .build();
            }
        } else {
            // this key is full at the current level, but other (known) keys are not full yet.
            return EvaluationOutcome.fail(
                    this, "Key '%s' is already full, and others are known to not be full", offerKey).build();
        }
    }

    @JsonProperty("task-filter")
    private StringMatcher getTaskFilter() {
        return taskFilter;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
