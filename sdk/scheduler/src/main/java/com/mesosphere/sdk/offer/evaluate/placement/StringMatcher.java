package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Implements a check against a provided string, determining whether it matches some criteria.
 * This may be used for checks against e.g. attributes or hostnames.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface StringMatcher {

    /**
     * Returns whether the provided string matches some internal criteria.
     */
    public boolean matches(String value);
}
