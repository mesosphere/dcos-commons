package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.Element;

import java.util.Collection;

/**
 * A strategy indicates which {@link Element}s are ready to be processed.  It may be interrupted such that it will
 * indicate that no elements are available for processing.  To resume normal operation {@link #proceed()} may be called.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
@SuppressWarnings("rawtypes")
public interface Strategy<C extends Element> {
    Collection<C> getCandidates(Element<C> parentElement, Collection<String> dirtyAssets);

    /**
     * A call to interrupt indicates to a Strategy that it should not produce {@link Element}s when
     * {@link #getCandidates(Element, Collection)} is called.  If a Strategy is already interrupted, this should have
     * no effect.
     */
    void interrupt();

    /**
     * If a Strategy is interrupted this should now allow a Strategy to produce candidates on calls to
     * {@link #getCandidates(Element, Collection)}.  If the Strategy was already interrupted a call to proceed should
     * have no effect.
     */
    void proceed();

    /**
     * Indicates whether a Strategy is interrupted or not.
     *
     * @return true if a Strategy is interrupted, false otherwise.
     */
    boolean isInterrupted();
}
