package com.mesosphere.sdk.offer.evaluate.placement;

/**
 * This class generates {@link RegionRule}s.
 */
public final class RegionRuleFactory implements RuleFactory {
  private static final RegionRuleFactory factory = new RegionRuleFactory();

  private RegionRuleFactory() {
    // Do not instantiate this class
  }

  public static RegionRuleFactory getInstance() {
    return factory;
  }

  @Override
  public PlacementRule require(StringMatcher matcher) {
    return new RegionRule(matcher);
  }
}
