package com.mesosphere.sdk.offer.evaluate.placement;

/**
 * Created by gabriel on 11/8/17.
 */
public class HostnameRuleFactory implements RuleFactory {
    private static final HostnameRuleFactory factory = new HostnameRuleFactory();

    public static HostnameRuleFactory getInstance() {
        return factory;
    }

    private HostnameRuleFactory() {
        // Do not instantiate this class
    }

    @Override
    public PlacementRule require(StringMatcher matcher) {
        return new HostnameRule(matcher);
    }
}
