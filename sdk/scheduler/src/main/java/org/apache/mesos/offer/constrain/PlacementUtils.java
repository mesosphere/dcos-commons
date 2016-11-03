package org.apache.mesos.offer.constrain;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import org.apache.mesos.Protos.TaskInfo;

/**
 * This class provides Utilities for commonly needed Placement rule scenarios.
 */
public class PlacementUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlacementUtils.class);

    private PlacementUtils() {
        // do not instantiate
    }

    /**
     * Returns the appropriate placement rule, given a set of agents to avoid or colocate with.
     *
     * @param avoidAgents Agents which should not have Tasks placed on them.
     * @param collocateAgents Agents which should have Tasks placed on them.
     * @return The appropriate placement rule.
     */
    public static Optional<PlacementRuleGenerator> getAgentPlacementRule(
            List<String> avoidAgents,
            List<String> collocateAgents) {

        Optional<PlacementRuleGenerator> placement;
        if (!avoidAgents.isEmpty()) {
            if (!collocateAgents.isEmpty()) {
                // avoid and collocate enforcement
                placement = Optional.of(new AndRule.Generator(
                        new AgentRule.AvoidAgentsGenerator(avoidAgents),
                        new AgentRule.RequireAgentsGenerator(collocateAgents)));
            } else {
                // avoid enforcement only
                placement = Optional.of(new AgentRule.AvoidAgentsGenerator(avoidAgents));
            }
        } else if (!collocateAgents.isEmpty()) {
            // collocate enforcement only
            placement = Optional.of(new AgentRule.RequireAgentsGenerator(collocateAgents));
        } else {
            // no collocate/avoid enforcement
            placement = Optional.empty();
        }

        return placement;
    }

    /**
     * Returns whether the provided {@link TaskInfo},representing a previously-launched task,
     * has a matching the task name and task type within the provided {@link OfferRequirement},
     * representing a new task about to be launched.
     */
    public static boolean areEquivalent(TaskInfo taskInfo, OfferRequirement offerRequirement) {
        // Check task types
        String taskInfoType;
        try {
            taskInfoType = TaskUtils.getTaskType(taskInfo);
        } catch (TaskException e) {
            LOGGER.warn("Unable to extract task type from taskinfo", e);
            taskInfoType = null;
        }
        if (!Objects.equal(taskInfoType, offerRequirement.getTaskType())) {
            return false;
        }

        // Check task names
        Set<String> offerRequirementTaskNames = new HashSet<>();
        for (TaskRequirement taskRequirement : offerRequirement.getTaskRequirements()) {
            offerRequirementTaskNames.add(taskRequirement.getTaskInfo().getName());
        }
        return offerRequirementTaskNames.contains(taskInfo.getName());
    }
}
