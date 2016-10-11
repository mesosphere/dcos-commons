package org.apache.mesos.offer.constrain;

import java.util.List;
import java.util.Optional;

/**
 * This class provides Utilities for commonly needed Placement rule scenarios.
 */
public class PlacementUtils {

    /**
     * Given a list of agents to avoid and collocate with, the appropriate rule is generated.
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
}
