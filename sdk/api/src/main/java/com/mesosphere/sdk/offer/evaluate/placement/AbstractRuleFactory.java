package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This interface defines the requirements for a factory which generates {@link PlacementRule}s.
 */
public abstract class AbstractRuleFactory {

    /**
     * Requires that a task be placed on the provided string matcher.
     *
     * @param matcher matcher for key to require
     */
    public abstract PlacementRule require(StringMatcher matcher);

    /**
     * Requires that a task be placed on one of the provided string matchers.
     *
     * @param matchers matchers for key to require
     */
    public PlacementRule require(Collection<StringMatcher> matchers) {
        return require(this, matchers);
    }

    /**
     * Requires that a task NOT be placed on the provided string matchers.
     *
     * @param matcher matcher for key to avoid
     */
    public PlacementRule avoid(StringMatcher matcher) {
        return new NotRule(require(matcher));
    }

    /**
     * Requires that a task NOT be placed on any of the provided string matchers.
     *
     * @param matchers matchers for keys to avoid
     */
    public PlacementRule avoid(Collection<StringMatcher> matchers) {
        if (matchers.size() == 1) {
            return avoid(matchers.iterator().next());
        }
        return new NotRule(require(matchers));
    }

    /**
     * Requires that a task be placed on one of the provided string matchers.
     *
     * @param matchers matchers for keys to require
     */
    private static PlacementRule require(AbstractRuleFactory ruleFactory, Collection<StringMatcher> matchers) {
        if (matchers.size() == 1) {
            return ruleFactory.require(matchers.iterator().next());
        }
        List<PlacementRule> rules = new ArrayList<>();
        for (StringMatcher matcher : matchers) {
            rules.add(ruleFactory.require(matcher));
        }
        return new OrRule(rules);
    }
}
