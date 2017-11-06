package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.offer.evaluate.SpecVisitor;
import com.mesosphere.sdk.offer.evaluate.SpecVisitorException;

/**
 * A SpecVisitee is capable of accepting a {@link SpecVisitor}.
 */
public interface SpecVisitee {
    void accept(SpecVisitor visitor) throws SpecVisitorException;
}
