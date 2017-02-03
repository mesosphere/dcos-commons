package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.Collection;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ensures that no more than N instances of a task type are running on a given hostname (or hostname
 * pattern).
 *
 * For example, this can ensure that no more than N tasks are running against the 'rack:foo'
 * attribute (exact match), or it can ensure that no distinct 'rack:.*' value has more than N tasks
 * running against it (wildcarded grouping).
 *
 * To illustrate, let's look at a deployment scenario of 3 hosts with the following tasks:
 *
 *  hostname |     tasks
 * ----------+---------------
 *   host-1  | a-1, b-1, c-1
 *   host-2  | a-2, c-2, c-3
 *   host-3  | b-2, c-4
 *
 * In this example, let's assume a {@link MaxPerHostnameRule} with a limit of 2 and a regex of
 * 'c-.*'. In this case, we're ensuring that no more than 2 tasks starting with 'c-' can be launched
 * on any given host. Among the above three hosts, any offers from host-1 and host-3 will be
 * accepted and offers from host-2 will be denied.
 */
public class MaxPerHostnameRule implements PlacementRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaxPerHostnameRule.class);

    private final int maxTasksPerHostname;
    private final StringMatcher taskFilter;

    /**
     * Creates a new rule which will block deployment on tasks which already have N instances
     * running against a specified attribute, with no filtering on task names (all tasks across the
     * service are counted against the max).
     *
     * @param maxTasksPerSelectedHostname the maximum number of tasks which may be run on a selected
     *     agent
     */
    public MaxPerHostnameRule(int maxTasksPerSelectedHostname) {
        this(maxTasksPerSelectedHostname, null);
    }

    /**
     * Creates a new rule which will block deployment on tasks which already have N instances
     * running against a specified hostname.
     *
     * @param maxTasksPerSelectedHostname the maximum number of tasks which may be run on a selected
     *     agent
     * @param taskFilter a filter on task names to determine which tasks are included in the count,
     *     for example counting all tasks, or just counting tasks of a given type
     */
    @JsonCreator
    public MaxPerHostnameRule(
            @JsonProperty("max") int maxTasksPerSelectedHostname,
            @JsonProperty("task-filter") StringMatcher taskFilter) {
        this.maxTasksPerHostname = maxTasksPerSelectedHostname;
        if (taskFilter == null) { // null when unspecified in serialized data
            taskFilter = AnyMatcher.create();
        }
        this.taskFilter = taskFilter;
    }

    @Override
    public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        int offerHostnameTaskCounts = 0;
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
            final String taskHostname;
            try {
                taskHostname = CommonTaskUtils.getHostname(task);
            } catch (TaskException e) {
                LOGGER.warn("Unable to extract hostname from task for filtering", e);
                continue;
            }
            if (!taskHostname.equals(offer.getHostname())) {
                continue;
            }
            ++offerHostnameTaskCounts;
            if (offerHostnameTaskCounts >= maxTasksPerHostname) {
                // the hostname for this offer meets or exceeds the limit. offer denied!
                return EvaluationOutcome.fail(this, "%d/%d tasks matching filter '%s' are already present on this host",
                        offerHostnameTaskCounts, maxTasksPerHostname, taskFilter.toString());
            }
        }
        // after scanning all the tasks for usage of attributes present in this offer, nothing
        // hit or exceeded the limit. offer accepted!
        return EvaluationOutcome.pass(
                this,
                "%d/%d tasks matching filter '%s' are present on this host",
                offerHostnameTaskCounts, maxTasksPerHostname, taskFilter.toString());
    }

    @JsonProperty("max")
    private int getMax() {
        return maxTasksPerHostname;
    }

    @JsonProperty("task-filter")
    private StringMatcher getTaskFilter() {
        return taskFilter;
    }

    @Override
    public String toString() {
        return String.format("MaxPerHostnameRule{max=%s, task-filter=%s}",
                maxTasksPerHostname, taskFilter);
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
