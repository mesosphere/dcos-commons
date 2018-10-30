package com.mesosphere.sdk.offer.evaluate.placement;

/**
 * This class generates {@link ZoneRule}s.
 */
public final class ZoneRuleFactory implements RuleFactory {
  private static final ZoneRuleFactory factory = new ZoneRuleFactory();

  private ZoneRuleFactory() {}

  public static ZoneRuleFactory getInstance() {
    return factory;
  }

  @Override
  public PlacementRule require(StringMatcher matcher) {
    return new ZoneRule(matcher);
  }
}
