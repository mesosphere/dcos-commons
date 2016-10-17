package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.Element;

import java.util.Collection;

/**
 * A ParallelStrategy indicates that all Elements are candidates simultaneously.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public class ParallelStrategy<C extends Element> extends InterruptableStrategy<C> {
    @Override
    public Collection<C> getCandidates(Element<C> parentElement, Collection<String> dirtyAssets) {
        DependencyStrategyHelper helper = new DependencyStrategyHelper(parentElement);
        return helper.getCandidates(dirtyAssets);
    }
}
