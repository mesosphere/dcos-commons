package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.List;
import java.util.Optional;

import com.mesosphere.sdk.offer.*;

import com.mesosphere.sdk.specification.PodInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static Optional<PlacementRule> getAgentPlacementRule(
            List<String> avoidAgents,
            List<String> collocateAgents) {

        Optional<PlacementRule> placement;
        if (!avoidAgents.isEmpty()) {
            if (!collocateAgents.isEmpty()) {
                // avoid and collocate enforcement
                placement = Optional.of(new AndRule(
                        AgentRule.avoid(avoidAgents),
                        AgentRule.require(collocateAgents)));
            } else {
                // avoid enforcement only
                placement = Optional.of(AgentRule.avoid(avoidAgents));
            }
        } else if (!collocateAgents.isEmpty()) {
            // collocate enforcement only
            placement = Optional.of(AgentRule.require(collocateAgents));
        } else {
            // no collocate/avoid enforcement
            placement = Optional.empty();
        }

        return placement;
    }

    /**
     * Returns whether the provided {@link TaskInfo}, representing a previously-launched task,
     * is in the same provided pod provided in the {@link PodInstance}.
     */
    public static boolean areEquivalent(TaskInfo taskInfo, PodInstance podInstance) {
        try {
            return TaskUtils.isSamePodInstance(taskInfo, podInstance.getPod().getType(), podInstance.getIndex());
        } catch (TaskException e) {
            LOGGER.warn("Unable to extract pod type or index from TaskInfo", e);
            return false;
        }
    }
}
