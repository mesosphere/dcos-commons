package com.mesosphere.sdk.offer.constrain;

import java.util.*;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.AttributeStringUtils;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.OfferRequirement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ensures that the given Offer’s attributes each have no more than N instances of tasks running on
 * them.
 *
 * For example, this can ensure that no more than N tasks are running against the 'rack:foo'
 * attribute (exact match), or it can ensure that no distinct 'rack:.*' value has more than N tasks
 * running against it (wildcarded grouping).
 *
 * To illustrate, let's look at a deployment scenario of 5 agents with 3 distinct 'rack' values:
 *
 *  agent |  attr  | # tasks
 * -------+--------+---------
 *    1   | rack:a |   3
 *    2   | rack:b |   2
 *    3   | rack:c |   1
 *    4   | rack:a |   2
 *    5   | rack:b |   2
 *
 * In this example, let's assume a {@link MaxPerAttributeRule} with a limit of 5 and a regex of
 * 'rack:.*':
 *
 * The regex of 'rack:.*' will result in internally grouping the task counts as follows:
 * - rack:a: 5 tasks
 * - rack:b: 4 tasks
 * - rack:c: 1 task
 *
 * After grouping the task counts according to the distinct values produced by the regex, we can now
 * enforce the limit value of 5. In this case, we see that rack:a is full but that rack:b and rack:c
 * still have room. Therefore the PlacementRule will allow deployments given an attribute of
 * 'rack:b' or 'rack:c', and will block deployments onto 'rack:a'.
 *
 * In addition, this enforcement can be selectively applied by task name. By default the rule will
 * look at ALL tasks in the service, but with a task matcher we can only count e.g. tasks named
 * 'index-.*'. This allows us to only enforce the rule against certain task types or task instances
 * within the service.
 */
public class MaxPerAttributeRule implements PlacementRule {

    private final int maxTasksPerSelectedAttribute;
    private final StringMatcher attributeMatcher;
    private final StringMatcher taskFilter;

    /**
     * Creates a new rule which will block deployment on tasks which already have N instances
     * running against a specified attribute, with no filtering on task names (all tasks across the
     * service are counted against the max).
     *
     * @param maxTasksPerSelectedAttribute the maximum number of tasks which may be run on an agent
     *     which has attributes matching the provided {@code matcher}
     * @param attributeMatcher the filter on which attributes should be counted and tallied
     */
    public MaxPerAttributeRule(
            int maxTasksPerSelectedAttribute,
            StringMatcher attributeMatcher) {
        this(maxTasksPerSelectedAttribute, attributeMatcher, null);
    }

    /**
     * Creates a new rule which will block deployment on tasks which already have N instances
     * running against a specified attribute, with the provided filtering on task names to be
     * counted against the maximum.
     *
     * @param maxTasksPerSelectedAttribute the maximum number of tasks which may be run on an agent
     *     which has attributes matching the provided {@code matcher}
     * @param attributeMatcher the filter on which attributes should be counted and tallied, for
     *     example only checking attributes which match a {@code rack:*} pattern
     * @param taskFilter a filter on task names to determine which tasks are included in the count,
     *     for example counting all tasks, or just counting tasks of a given type
     */
    @JsonCreator
    public MaxPerAttributeRule(
            @JsonProperty("max") int maxTasksPerSelectedAttribute,
            @JsonProperty("matcher") StringMatcher attributeMatcher,
            @JsonProperty("task-filter") StringMatcher taskFilter) {
        this.maxTasksPerSelectedAttribute = maxTasksPerSelectedAttribute;
        this.attributeMatcher = attributeMatcher;
        if (taskFilter == null) { // null when unspecified in serialized data
            taskFilter = AnyMatcher.create();
        }
        this.taskFilter = taskFilter;
    }

    @Override
    public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        // collect all the attribute values present in this offer:
        Set<String> offerAttributeStrings = new HashSet<>();
        for (Attribute attributeProto : offer.getAttributesList()) {
            offerAttributeStrings.add(AttributeStringUtils.toString(attributeProto));
        }
        if (offerAttributeStrings.isEmpty()) {
            // shortcut: offer has no attributes to enforce. offer accepted!
            return offer;
        }

        // map: enforced attribute in offer => # other tasks which were launched against attribute
        Map<String, Integer> offerAttrTaskCounts = new HashMap<>();
        for (TaskInfo task : tasks) {
            // only tally tasks which match the task matcher (eg 'index-.*')
            if (!taskFilter.matches(task.getName())) {
                continue;
            }
            if (PlacementUtils.areEquivalent(task, offerRequirement)) {
                // This is stale data for the same task that we're currently evaluating for
                // placement. Don't worry about counting its attribute usage. This occurs when we're
                // redeploying a given task with a new configuration (old data not deleted yet).
                continue;
            }
            for (String taskAttributeString : CommonTaskUtils.getOfferAttributeStrings(task)) {
                // only tally attribute values that are actually present in the offer
                if (!offerAttributeStrings.contains(taskAttributeString)) {
                    continue;
                }
                // only tally attribute(s) that match the attribute matcher (eg 'rack:.*'):
                if (!attributeMatcher.matches(taskAttributeString)) {
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
                    return offer.toBuilder().clearResources().build();
                }
                offerAttrTaskCounts.put(taskAttributeString, val);
            }
        }
        // after scanning all the tasks for usage of attributes present in this offer, nothing
        // hit or exceeded the limit. offer accepted!
        return offer;
    }

    @JsonProperty("max")
    private int getMax() {
        return maxTasksPerSelectedAttribute;
    }

    @JsonProperty("matcher")
    private StringMatcher getAttributeMatcher() {
        return attributeMatcher;
    }

    @JsonProperty("task-filter")
    private StringMatcher getTaskFilter() {
        return taskFilter;
    }

    @Override
    public String toString() {
        return String.format("MaxPerAttributeRule{max=%s, matcher=%s, task-filter=%s}",
                maxTasksPerSelectedAttribute, attributeMatcher, taskFilter);
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
