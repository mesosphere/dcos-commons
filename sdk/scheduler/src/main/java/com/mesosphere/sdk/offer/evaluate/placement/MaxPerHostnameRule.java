package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

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
public class MaxPerHostnameRule extends MaxPerRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaxPerHostnameRule.class);

    @Valid
    @Min(1)
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
        super(maxTasksPerSelectedHostname, taskFilter);
        this.maxTasksPerHostname = maxTasksPerSelectedHostname;
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
                    "Fewer than %d tasks matching filter '%s' are present on this host",
                    maxTasksPerHostname, taskFilter.toString())
                    .build();
        } else {
            return EvaluationOutcome.fail(
                    this,
                    "%d tasks matching filter '%s' are already present on this host",
                    maxTasksPerHostname, taskFilter.toString())
                    .build();
        }
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return Arrays.asList(PlacementField.HOSTNAME);
    }

    @Override
    public Collection<String> getKeys(TaskInfo taskInfo) {
        try {
            return Arrays.asList(new TaskLabelReader(taskInfo).getHostname());
        } catch (TaskException e) {
            LOGGER.warn("Unable to extract hostname from task for filtering", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Collection<String> getKeys(Offer offer) {
        return Arrays.asList(offer.getHostname());
    }

    @Override
    public String toString() {
        return String.format("MaxPerHostnameRule{max=%s, task-filter=%s}",
                maxTasksPerHostname, taskFilter);
    }
}
