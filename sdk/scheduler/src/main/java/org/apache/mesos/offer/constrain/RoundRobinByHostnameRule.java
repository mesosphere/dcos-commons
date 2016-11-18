package org.apache.mesos.offer.constrain;

import java.util.Optional;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
 *  allow (only) host-3, unless we know that there's >=4 hosts via the agent_count parameter
 *
 * Example:
 *  hostname |     tasks
 * ----------+---------------
 *   host-1  | a-1, b-1, c-1
 *   host-2  | a-2, c-2, c-3
 *   host-3  | b-2, c-4, b-3
 * Result:
 *  allow any of host-1/host-2/host-3, unless we know that there's >=4 hosts via the agent_count
 *  parameter.
 */
public class RoundRobinByHostnameRule extends RoundRobinBaseRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinByHostnameRule.class);

    public RoundRobinByHostnameRule(Optional<Integer> agentCount) {
        this(agentCount, null);
    }

    @JsonCreator
    public RoundRobinByHostnameRule(
            @JsonProperty("agent_count") Optional<Integer> agentCount,
            @JsonProperty("task_filter") StringMatcher taskFilter) {
        super(taskFilter, agentCount);
    }

    /**
     * Returns a value to round robin against from the provided {@link Offer}.
     */
    protected String getValue(Offer offer) {
        return offer.getHostname();
    }

    /**
     * Returns a value to round robin against from the provided {@link TaskInfo}.
     */
    protected String getValue(TaskInfo task) {
        try {
            return TaskUtils.getHostname(task);
        } catch (TaskException e) {
            LOGGER.warn("Unable to extract hostname from task for filtering", e);
            return null;
        }
    }

    @JsonProperty("agent_count")
    private Optional<Integer> getAgentCount() {
        return distinctValueCount;
    }

    @JsonProperty("task_filter")
    private StringMatcher getTaskFilter() {
        return taskFilter;
    }

    @Override
    public String toString() {
        return String.format("RoundRobinByHostnameRule{agent_count=%s, task_filter=%s}",
                distinctValueCount, taskFilter);
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
