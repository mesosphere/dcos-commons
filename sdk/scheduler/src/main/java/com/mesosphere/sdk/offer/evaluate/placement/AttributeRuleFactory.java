package com.mesosphere.sdk.offer.evaluate.placement;

/**
 * This class generates {@link AttributeRule}s.
 */
public final class AttributeRuleFactory implements RuleFactory {
  private static final AttributeRuleFactory factory = new AttributeRuleFactory();

  private AttributeRuleFactory() {
    // Do not instantiate this class
  }

  public static AttributeRuleFactory getInstance() {
    return factory;
  }

  @Override
  public PlacementRule require(StringMatcher matcher) {
    return new AttributeRule(matcher);
  }
}
