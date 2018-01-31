package com.mesosphere.sdk.offer.evaluate.placement;

/**
 * This class generates {@link RegionRule}s.
 */
public class RegionRuleFactory implements RuleFactory {
    private static final RegionRuleFactory factory = new RegionRuleFactory();

    public static RegionRuleFactory getInstance() {
        return factory;
    }

    private RegionRuleFactory() {
        // Do not instantiate this class
    }

    @Override
    public PlacementRule require(StringMatcher matcher) {
        return new RegionRule(matcher);
    }
}
