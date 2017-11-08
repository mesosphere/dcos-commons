package com.mesosphere.sdk.offer.evaluate.placement;


/**
 * Created by gabriel on 11/8/17.
 */
public class AttributeRuleFactory implements RuleFactory {
    private static final AttributeRuleFactory factory = new AttributeRuleFactory();

    public static AttributeRuleFactory getInstance() {
        return factory;
    }

    private AttributeRuleFactory() {
        // Do not instantiate this class
    }

    @Override
    public PlacementRule require(StringMatcher matcher) {
        return new AttributeRule(matcher);
    }
}
