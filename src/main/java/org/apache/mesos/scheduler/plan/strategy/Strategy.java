package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.Element;

import java.util.Collection;

/**
 * A strategy indicates which {@link Element}s are ready to be processed.  It may be interrupted such that it will
 * indicate that no elements are available for processing.  To resume normal operation {@link #proceed()} may be called.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public interface Strategy<C extends Element> {
    Collection<C> getCandidates(Element<C> parentElement, Collection<String> dirtyAssets);

    void interrupt();

    void proceed();

    boolean isInterrupted();
}
