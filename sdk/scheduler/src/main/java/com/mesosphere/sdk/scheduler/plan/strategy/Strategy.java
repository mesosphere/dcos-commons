package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.Interruptible;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

import java.util.Collection;

/**
 * A strategy indicates which child {@link Element}s are ready to be processed.  It may be interrupted such that it will
 * indicate that no elements are available for processing.  To resume normal operation {@link #proceed()} may be called.
 *
 * @param <C> is the type of child {@link Element}s to which the Strategy applies.
 */
public interface Strategy<C extends Element> extends Interruptible {

    /**
     * Returns the candidate element(s), if any, which may have work performed against them.
     *
     * @param elements the elements to be examined for candidacy
     * @param dirtyAssets any asset names which already have work in progress elsewhere, which should not be returned by
     *     this call
     * @return zero or more candidates for work to be performed
     */
    Collection<C> getCandidates(Collection<C> elements, Collection<PodInstanceRequirement> dirtyAssets);
}
