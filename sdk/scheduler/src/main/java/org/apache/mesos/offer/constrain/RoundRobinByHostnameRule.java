package org.apache.mesos.offer.constrain;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.OfferRequirement;
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
public class RoundRobinByHostnameRule implements PlacementRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinByHostnameRule.class);

    private final Optional<Integer> agentCountOptional;
    private final StringMatcher taskFilter;

    public RoundRobinByHostnameRule(Optional<Integer> agentCount) {
        this(agentCount, null);
    }

    @JsonCreator
    public RoundRobinByHostnameRule(
            @JsonProperty("agent_count") Optional<Integer> agentCount,
            @JsonProperty("task_filter") StringMatcher taskFilter) {
        this.agentCountOptional = agentCount;
        if (taskFilter == null) { // null when unspecified in serialized data
            taskFilter = AnyMatcher.create();
        }
        this.taskFilter = taskFilter;
    }

    @Override
    public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        // search across tasks, keeping counts on a per-hostname basis.
        // Hostname => # of instances on hostname
        Map<String, Integer> hostnameCounts = new HashMap<>();
        for (TaskInfo task : tasks) {
            // only tally tasks which match the task matcher (eg 'index-.*')
            if (!taskFilter.match(task.getName())) {
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
                taskHostname = TaskUtils.getHostname(task);
            } catch (TaskException e) {
                LOGGER.warn("Unable to extract hostname from task for filtering", e);
                continue;
            }
            Integer value = hostnameCounts.get(taskHostname);
            hostnameCounts.put(taskHostname, (value == null) ? 1 : value + 1);
        }

        int maxHostnameCount = 0;
        int minHostnameCount = Integer.MAX_VALUE;
        for (Integer count : hostnameCounts.values()) {
            if (count > maxHostnameCount) {
                maxHostnameCount = count;
            }
            if (count < minHostnameCount) {
                minHostnameCount = count;
            }
        }
        if (minHostnameCount == Integer.MAX_VALUE) {
            minHostnameCount = 0;
        }
        Integer offerHostnameCount = hostnameCounts.get(offer.getHostname());
        if (offerHostnameCount == null) {
            offerHostnameCount = 0;
        }

        if (minHostnameCount == maxHostnameCount || offerHostnameCount < maxHostnameCount) {
            // all (known) nodes are full at the current level,
            // or this offer's node is not full at the current level
            if (agentCountOptional.isPresent()
                    && hostnameCounts.size() < agentCountOptional.get()) {
                // we know that there are other nodes out there which have nothing on them at all.
                // only launch here if this node also has nothing on it.
                if (maxHostnameCount == 0) {
                    return offer;
                } else {
                    return offer.toBuilder().clearResources().build();
                }
            } else {
                // either we don't know how many nodes are out there, or we know that no nodes are
                // currently missing from our counts, but in either case this node has fewer instances
                // than some other node in the system.
                return offer;
            }
        } else {
            // this node is full at the current level, but other (known) nodes are not full yet.
            return offer.toBuilder().clearResources().build();
        }
    }

    @JsonProperty("agent_count")
    private Optional<Integer> getAgentCount() {
        return agentCountOptional;
    }

    @JsonProperty("task_filter")
    private StringMatcher getTaskFilter() {
        return taskFilter;
    }

    @Override
    public String toString() {
        return String.format("RoundRobinByHostnameRule{agent_count=%s, task_filter=%s}",
                agentCountOptional, taskFilter);
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
