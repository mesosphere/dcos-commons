package com.mesosphere.sdk.offer.constrain;

import java.util.List;
import java.util.Optional;

import com.mesosphere.sdk.offer.*;
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
     * has a matching the task name and task type within the provided {@link OfferRequirement},
     * representing a new task about to be launched.
     */
    public static boolean areEquivalent(TaskInfo taskInfo, OfferRequirement offerRequirement) {
        // Check task types
        String taskInfoType;
        try {
            taskInfoType = CommonTaskUtils.getType(taskInfo);
        } catch (TaskException e) {
            LOGGER.warn("Unable to extract task type from taskinfo", e);
            taskInfoType = null;
        }

        if (!Objects.equal(taskInfoType, offerRequirement.getType())) {
            return false;
        }

        // Check pod index
        Integer index;
        try {
            index = CommonTaskUtils.getIndex(taskInfo);
        } catch (TaskException e) {
            LOGGER.warn("Unable to extract index from taskinfo", e);
            index = null;
        }

        return Objects.equal(index, offerRequirement.getIndex());
    }
}
