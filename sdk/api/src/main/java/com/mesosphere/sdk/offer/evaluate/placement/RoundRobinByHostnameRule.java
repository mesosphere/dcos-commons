package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * Implements logic for Marathon's GROUP_BY operator for hostnames. Ensures that tasks are evenly
 * distributed across agents in the system as they are rolled out.
 *
 * Example:
 *  hostname |     tasks
 * ----------+---------------
 *   host-1  | a-1, b-1, c-1
 *   host-2  | a-2, c-2, c-3
 *   host-3  | b-2, c-4
 * Result:
 *  allow (only) host-3, unless we know that there's >=4 hosts via the agent-count parameter
 *
 * Example:
 *  hostname |     tasks
 * ----------+---------------
 *   host-1  | a-1, b-1, c-1
 *   host-2  | a-2, c-2, c-3
 *   host-3  | b-2, c-4, b-3
 * Result:
 *  allow any of host-1/host-2/host-3, unless we know that there's >=4 hosts via the agent-count
 *  parameter.
 *
 * This enforcement is applied by task name. By default the rule will only count e.g. tasks named
 * 'index-.*'. This allows us to only enforce the rule against certain task types or task instances
 * within the service.
 */
public class RoundRobinByHostnameRule extends AbstractRoundRobinRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinByHostnameRule.class);

    public RoundRobinByHostnameRule(Optional<Integer> agentCount) {
        this(agentCount, null);
    }

    @JsonCreator
    public RoundRobinByHostnameRule(
            @JsonProperty("agent-count") Optional<Integer> agentCount,
            @JsonProperty("task-filter") StringMatcher taskFilter) {
        super(taskFilter, agentCount);
    }

    /**
     * Returns a value to round robin against from the provided {@link Offer}.
     */
    protected String getKey(Offer offer) {
        return offer.getHostname();
    }

    /**
     * Returns a value to round robin against from the provided {@link TaskInfo}.
     */
    protected String getKey(TaskInfo task) {
        try {
            return new TaskLabelReader(task).getHostname();
        } catch (TaskException e) {
            LOGGER.warn("Unable to extract hostname from task for filtering", e);
            return null;
        }
    }

    @JsonProperty("agent-count")
    private Optional<Integer> getAgentCount() {
        return distinctKeyCount;
    }

    @Override
    public String toString() {
        return String.format("RoundRobinByHostnameRule{agent-count=%s, task-filter=%s}",
                distinctKeyCount, taskFilter);
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return Arrays.asList(PlacementField.HOSTNAME);
    }
}

