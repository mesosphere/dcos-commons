package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.Arrays;
import java.util.Collection;

/**
 * This interface defines the requirements for a factory which generates {@link PlacementRule}s.
 */
public interface RuleFactory {
    /**
     * Requires that a task be placed on the provided string matcher.
     *
     * @param matcher matcher for key to require
     */
    PlacementRule require(StringMatcher matcher);

    /**
     * Requires that a task be placed on one of the provided string matchers.
     *
     * @param matchers matchers for key to require
     */
    default PlacementRule require(Collection<StringMatcher> matchers) {
        return PlacementUtils.require(this, matchers);
    }

    /**
     * Requires that a task be placed on one of the provided string matchers.
     *
     * @param matchers matchers for keys to require
     */
    default PlacementRule require(StringMatcher... matchers) {
        return PlacementUtils.require(this, Arrays.asList(matchers));
    }

    /**
     * Requires that a task NOT be placed on the provided string matchers.
     *
     * @param matcher matcher for key to avoid
     */
    default PlacementRule avoid(StringMatcher matcher) {
        return new NotRule(require(matcher));
    }

    /**
     * Requires that a task NOT be placed on any of the provided string matchers.
     *
     * @param matchers matchers for keys to avoid
     */
    default PlacementRule avoid(Collection<StringMatcher> matchers) {
        if (matchers.size() == 1) {
            return avoid(matchers.iterator().next());
        }
        return new NotRule(require(matchers));
    }

    /**
     * Requires that a task NOT be placed on any of the provided string matchers.
     *
     * @param matchers matchers for keys to avoid
     */
    default PlacementRule avoid(StringMatcher... matchers) {
        return avoid(Arrays.asList(matchers));
    }
}
