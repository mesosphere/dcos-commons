package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation of common round-robin logic used by {@link RoundRobinByHostnameRule} and
 * {@link RoundRobinByAttributeRule}.
 */
abstract class AbstractRoundRobinRule implements PlacementRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRoundRobinRule.class);

    protected final StringMatcher taskFilter;
    protected final Optional<Integer> distinctValueCount;

    protected AbstractRoundRobinRule(StringMatcher taskFilter, Optional<Integer> distinctValueCount) {
        if (taskFilter == null) { // null when unspecified in serialized data
            taskFilter = AnyMatcher.create();
        }
        this.taskFilter = taskFilter;
        this.distinctValueCount = distinctValueCount;
    }

    /**
     * Returns a value to round robin against from the provided {@link Offer}, or {@code null} if
     * none was found.
     */
    protected abstract String getValue(Offer offer);

    /**
     * Returns a value to round robin against from the provided {@link TaskInfo}, or {@code null} if
     * none was found.
     */
    protected abstract String getValue(TaskInfo task);

    @Override
    public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        final String offerValue = getValue(offer);
        if (offerValue == null) {
            // offer doesn't have the required attribute at all. denied.
            return EvaluationOutcome.fail(this, "Offer lacks required round robin value");
        }

        // search across tasks, keeping value counts on a per-value basis.
        // attribute value (for selected attribute name) => # of instances on attribute value
        Map<String, Integer> valueCounts = new HashMap<>();
        for (TaskInfo task : tasks) {
            // only tally tasks which match the task matcher (eg 'index-.*')
            if (!taskFilter.matches(task.getName())) {
                continue;
            }
            if (PlacementUtils.areEquivalent(task, offerRequirement)) {
                // This is stale data for the same task that we're currently evaluating for
                // placement. Don't worry about counting its usage. This occurs when we're
                // redeploying a given task with a new configuration (old data not deleted yet).
                continue;
            }

            final String taskAttributeValue = getValue(task);
            if (taskAttributeValue == null) {
                // no attribute matching the name was found. ignore.
                continue;
            }
            Integer value = valueCounts.get(taskAttributeValue);
            valueCounts.put(taskAttributeValue, (value == null) ? 1 : value + 1);
        }

        int maxKnownValueCount = 0;
        int minKnownValueCount = Integer.MAX_VALUE;
        for (Integer count : valueCounts.values()) {
            if (count > maxKnownValueCount) {
                maxKnownValueCount = count;
            }
            if (count < minKnownValueCount) {
                minKnownValueCount = count;
            }
        }
        if (minKnownValueCount == Integer.MAX_VALUE) {
            minKnownValueCount = 0;
        }
        Integer offerValueCount = valueCounts.get(offerValue);
        if (offerValueCount == null) {
            offerValueCount = 0;
        }
        LOGGER.info("Value counts: {}, knownMin: {}, knownMax: {}, offer: {}",
                valueCounts, minKnownValueCount, maxKnownValueCount, offerValueCount);

        if (minKnownValueCount == maxKnownValueCount
                || offerValueCount <= minKnownValueCount) {
            // all (known) attribute values are full at the current level, or this offer is on a value
            // which is at the smallest number of tasks (or below if we haven't expanded to this value yet)
            if (!distinctValueCount.isPresent()) {
                // we don't know how many distinct attribute values are out there, and this value has fewer instances
                // than some other value in the system.
                return EvaluationOutcome.pass(
                        this,
                        "Distinct value count is unspecified, and '%s' has %d instances while others have%d to %d",
                        offerValue, offerValueCount, minKnownValueCount, maxKnownValueCount);
            } else if (valueCounts.size() >= distinctValueCount.get()) {
                // no values are missing from our counts, and this value has fewer instances than some other value in
                // the system.
                return EvaluationOutcome.pass(
                        this,
                        "All distinct values are found, and '%s' has %d instances while others have %d to %d",
                        offerValue, offerValueCount, minKnownValueCount, maxKnownValueCount);
            }
            // we know that there are other attribute values out there which have nothing on them at all.
            // only launch here if this value also has nothing on it.
            if (offerValueCount == 0) {
                return EvaluationOutcome.pass(
                        this, "Other values have zero usage, but so does value '%s'", offerValue);
            } else {
                return EvaluationOutcome.fail(
                        this, "Other values have zero instances, but value '%s' has %d", offerValue, offerValueCount);

            }
        } else {
            // this attribute value is full at the current level, but other (known) values are not full yet.
            return EvaluationOutcome.fail(
                    this, "Value '%s' is already full, and others are known to not be full", offerValue);
        }
    }
}
