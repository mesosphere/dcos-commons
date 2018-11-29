package com.mesosphere.sdk.offer.evaluate.placement;

/**
 * This class generates {@link HostnameRule}s.
 */
public final class HostnameRuleFactory implements RuleFactory {
  private static final HostnameRuleFactory factory = new HostnameRuleFactory();

  private HostnameRuleFactory() {
    // Do not instantiate this class
  }

  public static HostnameRuleFactory getInstance() {
    return factory;
  }

  @Override
  public PlacementRule require(StringMatcher matcher) {
    return new HostnameRule(matcher);
  }
}
