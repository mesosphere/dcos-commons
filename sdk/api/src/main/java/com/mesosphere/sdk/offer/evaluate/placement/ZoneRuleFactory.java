package com.mesosphere.sdk.offer.evaluate.placement;

/**
 * This class generates {@link ZoneRule}s.
 */
public class ZoneRuleFactory implements RuleFactory {
    private static final ZoneRuleFactory factory = new ZoneRuleFactory();

    public static ZoneRuleFactory getInstance() {
        return factory;
    }

    private ZoneRuleFactory() {
        // Do not instantiate this class
    }

    @Override
    public PlacementRule require(StringMatcher matcher) {
        return new ZoneRule(matcher);
    }
}
