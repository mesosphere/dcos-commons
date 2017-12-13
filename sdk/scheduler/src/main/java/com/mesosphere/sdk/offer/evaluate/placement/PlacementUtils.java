package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class provides Utilities for commonly needed Placement rule scenarios.
 */
public class PlacementUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlacementUtils.class);

    private static final String HOSTNAME_FIELD_LEGACY = "hostname";
    private static final String HOSTNAME_FIELD = "@hostname";
    private static final String REGION_FIELD = "@region";
    private static final String ZONE_FIELD = "@zone";

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

    /**
     * Requires that a task be placed on one of the provided string matchers.
     *
     * @param matchers matchers for keys to require
     */
    public static PlacementRule require(RuleFactory ruleFactory, Collection<StringMatcher> matchers) {
        if (matchers.size() == 1) {
            return ruleFactory.require(matchers.iterator().next());
        }
        List<PlacementRule> rules = new ArrayList<>();
        for (StringMatcher matcher : matchers) {
            rules.add(ruleFactory.require(matcher));
        }
        return new OrRule(rules);
    }

    /**
     * Converts the provided keys into {@link ExactMatcher}s.
     */
    public static Collection<StringMatcher> toExactMatchers(String... hostnames) {
        return toExactMatchers(Arrays.asList(hostnames));
    }


    /**
     * Converts the provided keys into {@link ExactMatcher}s.
     */
    public static Collection<StringMatcher> toExactMatchers(Collection<String> hostnames) {
        List<StringMatcher> matchers = new ArrayList<>();
        for (String hostname : hostnames) {
            matchers.add(ExactMatcher.create(hostname));
        }
        return matchers;
    }

    public static PlacementField getField(String fieldName) {
        switch (fieldName) {
            case PlacementUtils.HOSTNAME_FIELD_LEGACY:
            case PlacementUtils.HOSTNAME_FIELD:
                return PlacementField.HOSTNAME;
            case PlacementUtils.REGION_FIELD:
                return PlacementField.REGION;
            case PlacementUtils.ZONE_FIELD:
                return PlacementField.ZONE;
            default:
                return PlacementField.ATTRIBUTE;
        }
    }

    public static boolean hasZone(Protos.Offer offer) {
        if (!offer.hasDomain()) {
            return false;
        }

        Protos.DomainInfo domainInfo = offer.getDomain();
        if (!domainInfo.hasFaultDomain()) {
            return false;
        }

        return domainInfo.getFaultDomain().hasZone();
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
