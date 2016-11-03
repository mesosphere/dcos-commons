package org.apache.mesos.offer.constrain;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.AttributeStringUtils;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ensures that the given Offer’s attributes each have no more than N instances of the task type
 * running on them.
 *
 * For example, this can ensure that no more than N tasks are running against the 'rack:foo'
 * attribute (exact match), or it can ensure that no distinct 'rack:.*' value has more than N tasks
 * running against it (wildcarded grouping).
 *
 * To illustrate, let's look at a deployment scenario of 5 agents with 3 distinct 'rack' values:
 *  agent |  attr  | # tasks
 * -------+--------+---------
 *    1   | rack:a |   3
 *    2   | rack:b |   2
 *    3   | rack:c |   1
 *    4   | rack:a |   2
 *    5   | rack:b |   2
 *
 * Given a {@link MaxPerAttributeGenerator} with a limit of 5 and a regex of 'rack:.*', let's see
 * what PlacementRule would be produced:
 *
 * In this example, the regex of 'rack:.*' will result in grouping the task counts as follows:
 * - rack:a: 5 tasks
 * - rack:b: 4 tasks
 * - rack:c: 1 task
 *
 * With the limit value of 5, this would result in a PlacementRule that blocks any offers with
 * 'rack:a' from future deployments.
 */
public class MaxPerAttributeGenerator implements PlacementRuleGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaxPerAttributeGenerator.class); // TODO(nick) TEMP

    private final int maxTasksPerSelectedAttribute;
    private final AttributeSelector attributeSelector;

    /**
     * Creates a new rule generator which will block deployment on tasks which already have N
     * instances running against a specified attribute.
     */
    @JsonCreator
    public MaxPerAttributeGenerator(
            @JsonProperty("max") int maxTasksPerSelectedAttribute,
            @JsonProperty("selector") AttributeSelector attributeSelector) {
        this.maxTasksPerSelectedAttribute = maxTasksPerSelectedAttribute;
        this.attributeSelector = attributeSelector;
    }

    @Override
    public PlacementRule generate(Collection<TaskInfo> tasks) {
        // Create a rule which will handle the validation. Logic is deferred to avoid
        // double-counting a task against a prior version of itself.
        return new MaxPerAttributeRule(maxTasksPerSelectedAttribute, attributeSelector, tasks);
    }

    @JsonProperty("max")
    private int getMax() {
        return maxTasksPerSelectedAttribute;
    }

    @JsonProperty("selector")
    private AttributeSelector getSelector() {
        return attributeSelector;
    }

    @Override
    public String toString() {
        return String.format("MaxPerAttributeGenerator{max=%s, selector=%s}",
                maxTasksPerSelectedAttribute, attributeSelector);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * Implementation of max attribute counts. Considers attribute usage of tasks in the cluster to
     * determine whether the provided task can be launched against a given offer. This rule is
     * checked at offer evaluation time in order to check that the task isn't being double-counted
     * against itself in a config update scenario.
     */
    private static class MaxPerAttributeRule implements PlacementRule {

        private final int maxTasksPerSelectedAttribute;
        private final AttributeSelector attributeSelector;
        private final Collection<TaskInfo> tasks;

        private MaxPerAttributeRule(
                int maxTasksPerSelectedAttribute,
                AttributeSelector attributeSelector,
                Collection<TaskInfo> tasks) {
            this.maxTasksPerSelectedAttribute = maxTasksPerSelectedAttribute;
            this.attributeSelector = attributeSelector;
            this.tasks = tasks;
        }

        @Override
        public Offer filter(Offer offer, OfferRequirement offerRequirement) {
            // collect all the attribute values present in this offer:
            Set<String> offerAttributeStrings = new HashSet<>();
            for (Attribute attributeProto : offer.getAttributesList()) {
                offerAttributeStrings.add(AttributeStringUtils.toString(attributeProto));
            }
            if (offerAttributeStrings.isEmpty()) {
                // shortcut: offer has no attributes to enforce. offer accepted!
                LOGGER.error("offer has no attributes = accept");
                return offer;
            }

            // map: enforced attribute in offer => # other tasks which were launched against attribute
            Map<String, Integer> offerAttrTaskCounts = new HashMap<>();
            for (TaskInfo task : tasks) {
                if (PlacementUtils.areEquivalent(task, offerRequirement)) {
                    // This is stale data for the same task that we're currently evaluating for
                    // placement. Don't worry about counting its attribute usage. This occurs when we're
                    // redeploying a given task with a new configuration (old data not deleted yet).
                    // NOTE: If it weren't for this case, creation of selectedAttrTaskCounts could've
                    // been put in the PlacementRuleGenerator rather than in the PlacementRule.
                    LOGGER.error("skip equivalent task: {}", task.getName());
                    continue;
                }
                for (String taskAttributeString : TaskUtils.getOfferAttributeStrings(task)) {
                    // only tally attribute values that are actually present in the offer
                    if (!offerAttributeStrings.contains(taskAttributeString)) {
                        LOGGER.error("ignoring attribute not in offer: {}", taskAttributeString);
                        continue;
                    }
                    // only tally attribute(s) that match the selector (eg 'rack:.*'):
                    if (!attributeSelector.select(taskAttributeString)) {
                        LOGGER.error("ignoring unselected attribute: {}", taskAttributeString);
                        continue;
                    }
                    // increment the tally for this exact attribute value (eg 'rack:9'):
                    Integer val = offerAttrTaskCounts.get(taskAttributeString);
                    if (val == null) {
                        val = 0;
                    }
                    val++;
                    if (val >= maxTasksPerSelectedAttribute) {
                        // this attribute value's usage meets or exceeds the limit, and it is
                        // present in this offer per the earlier check. offer denied!
                        LOGGER.error("denied due to attribute {}", taskAttributeString);
                        return offer.toBuilder().clearResources().build();
                    }
                    LOGGER.error("updated attribute {} = {}", taskAttributeString, val);
                    offerAttrTaskCounts.put(taskAttributeString, val);
                }
            }
            // after scanning all the tasks for usage of attributes present in this offer, nothing
            // hit or exceeded the limit. offer accepted!
            LOGGER.error("accepted with attribute counts {}", offerAttrTaskCounts);
            return offer;
        }

        @Override
        public String toString() {
            return String.format("MaxPerAttributeRule{max=%s, selector=%s, tasks=%d}",
                    maxTasksPerSelectedAttribute, attributeSelector, tasks.size());
        }
    }

}
