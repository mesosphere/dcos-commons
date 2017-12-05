package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.taskdata.AttributeStringUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Ensures that the given Offer’s attributes each have no more than N instances of tasks of a given task type
 * running on them.
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
 * This enforcement is applied by task name. By default the rule will only count e.g. tasks named
 * 'index-.*'. This allows us to only enforce the rule against certain task types or task instances
 * within the service.
 */
public class MaxPerAttributeRule extends MaxPerRule {

    private final StringMatcher attributeMatcher;
    private final StringMatcher taskFilter;

    /**
     * Creates a new rule which will block deployment on tasks which already have N instances
     * running against a specified attribute, with no filtering on task names (all tasks across the
     * service are counted against the max).
     *
     * @param maxTasksPerSelectedAttribute the maximum number of tasks which may be run on an agent
     *                                     which has attributes matching the provided {@code matcher}
     * @param attributeMatcher             the filter on which attributes should be counted and tallied
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
     *                                     which has attributes matching the provided {@code matcher}
     * @param attributeMatcher             the filter on which attributes should be counted and tallied, for
     *                                     example only checking attributes which match a {@code rack:*} pattern
     * @param taskFilter                   a filter on task names to determine which tasks are included in the count,
     *                                     for example counting all tasks, or just counting tasks of a given type
     */
    @JsonCreator
    public MaxPerAttributeRule(
            @JsonProperty("max") int maxTasksPerSelectedAttribute,
            @JsonProperty("matcher") StringMatcher attributeMatcher,
            @JsonProperty("task-filter") StringMatcher taskFilter) {
        super(maxTasksPerSelectedAttribute, taskFilter);
        this.attributeMatcher = attributeMatcher;
        if (taskFilter == null) { // null when unspecified in serialized data
            taskFilter = AnyMatcher.create();
        }
        this.taskFilter = taskFilter;
        ValidationUtils.validate(this);
    }

    @Override
    public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
        if (isAcceptable(offer, podInstance, tasks)) {
            return EvaluationOutcome.pass(
                    this,
                    "Fits within limit of %d tasks matching filter '%s' on this agent with attribute: %s",
                    max, taskFilter.toString(), attributeMatcher.toString())
                    .build();
        } else {
            return EvaluationOutcome.fail(
                    this,
                    "Reached greater than %d tasks matching filter '%s' on this agent with attribute: %s",
                    max,
                    taskFilter.toString(),
                    attributeMatcher.toString())
                    .build();
        }
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return Arrays.asList(PlacementField.ATTRIBUTE);
    }

    @JsonProperty("matcher")
    private StringMatcher getAttributeMatcher() {
        return attributeMatcher;
    }

    @Override
    public Collection<String> getKeys(TaskInfo taskInfo) {
        if (!taskFilter.matches(taskInfo.getName())) {
            return Collections.emptyList();
        }

        return new TaskLabelReader(taskInfo).getOfferAttributeStrings().stream()
                .filter(attribute -> attributeMatcher.matches(attribute))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<String> getKeys(Offer offer) {
        return offer.getAttributesList().stream()
                .map(proto -> AttributeStringUtils.toString(proto))
                .filter(attribute -> attributeMatcher.matches(attribute))
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String.format("MaxPerAttributeRule{max=%s, matcher=%s, task-filter=%s}",
                max, attributeMatcher, taskFilter);
    }
}
