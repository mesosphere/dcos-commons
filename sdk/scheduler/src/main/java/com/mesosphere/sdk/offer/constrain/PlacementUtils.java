package com.mesosphere.sdk.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
     * @param avoidAgents Agents which should not have Tasks placed on them
     * @param targetAgents Agents which should have Tasks placed on them
     * @return The appropriate placement rule, or an empty Optional if no rules are relevant
     */
    public static Optional<PlacementRule> getAgentPlacementRule(
            Collection<String> avoidAgents,
            Collection<String> targetAgents) {

        Optional<PlacementRule> placement;
        if (!avoidAgents.isEmpty()) {
            if (!targetAgents.isEmpty()) {
                // avoid and require enforcement
                placement = Optional.of(new AndRule(
                        AgentRule.avoid(avoidAgents),
                        AgentRule.require(targetAgents)));
            } else {
                // avoid enforcement only
                placement = Optional.of(AgentRule.avoid(avoidAgents));
            }
        } else if (!targetAgents.isEmpty()) {
            // require enforcement only
            placement = Optional.of(AgentRule.require(targetAgents));
        } else {
            // no require/avoid enforcement
            placement = Optional.empty();
        }

        return placement;
    }

    /**
     * Returns the appropriate placement rule, given a set of task types to avoid or colocate with.
     *
     * @param avoidTypes Task types which should not be colocated with
     * @param colocateTypes Task types which should be colocated with
     * @param customPlacementRule Custom rule which should be ANDed against the task type placement
     * @return The appropriate placement rule, or an empty Optional if no rules are relevant
     */
    public static Optional<PlacementRule> getTaskTypePlacementRule(
            Collection<String> avoidTypes,
            Collection<String> colocateTypes,
            Optional<PlacementRule> customPlacementRule) {

        Optional<PlacementRule> placement;
        if (!avoidTypes.isEmpty()) {
            if (!colocateTypes.isEmpty()) {
                // avoid and colocate enforcement
                placement = combine(
                        customPlacementRule,
                        TaskTypeRule.avoid(avoidTypes),
                        TaskTypeRule.colocateWith(colocateTypes));
            } else {
                // avoid enforcement only
                placement = combine(customPlacementRule, TaskTypeRule.avoid(avoidTypes));
            }
        } else if (!colocateTypes.isEmpty()) {
            // colocate enforcement only
            placement = combine(customPlacementRule, TaskTypeRule.colocateWith(colocateTypes));
        } else {
            // no colocate/avoid enforcement
            placement = customPlacementRule;
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

        // Check task names
        Set<String> offerRequirementTaskNames = new HashSet<>();
        for (TaskRequirement taskRequirement : offerRequirement.getTaskRequirements()) {
            offerRequirementTaskNames.add(taskRequirement.getTaskInfo().getName());
        }
        return offerRequirementTaskNames.contains(taskInfo.getName());
    }

    /**
     * Returns a flat ANDed combination of the provided {@code customPlacementRule} and/or
     * {@code generatedRules} as needed.
     */
    private static Optional<PlacementRule> combine(
            Optional<PlacementRule> customPlacementRule, PlacementRule... generatedRules) {
        List<PlacementRule> rules = new ArrayList<>(Arrays.asList(generatedRules));
        if (customPlacementRule.isPresent()) {
            rules.add(customPlacementRule.get());
        }
        switch (rules.size()) {
        case 0:
            return Optional.empty();
        case 1:
            return Optional.of(rules.iterator().next());
        default:
            return Optional.of(new AndRule(rules));
        }
    }
}
