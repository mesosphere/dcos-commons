package com.mesosphere.sdk.offer.evaluate;

import java.util.List;
import java.util.Optional;

import com.mesosphere.sdk.offer.evaluate.placement.AgentRule;
import com.mesosphere.sdk.offer.evaluate.placement.AndRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementField;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.specification.PodSpec;

/**
 * Utilities relating to scheduler-internal operation of Placement Rules.
 */
public class SchedulerPlacementUtils {

    private SchedulerPlacementUtils() {
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

    public static boolean placementRuleReferencesRegion(PodSpec podSpec) {
        return placementRuleReferencesField(PlacementField.REGION, podSpec);
    }

    public static boolean placementRuleReferencesZone(PodSpec podSpec) {
        return placementRuleReferencesField(PlacementField.ZONE, podSpec);
    }

    private static boolean placementRuleReferencesField(PlacementField field, PodSpec podSpec) {
        if (!podSpec.getPlacementRule().isPresent()) {
            return false;
        }

        return podSpec.getPlacementRule().get().getPlacementFields().stream()
                .anyMatch(placementField -> placementField.equals(field));
    }
}
