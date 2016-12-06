package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;

import java.util.Collection;

/**
 * A ParallelStrategy indicates that all Elements are candidates simultaneously.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public class ParallelStrategy<C extends Element> extends InterruptableStrategy<C> {
    @Override
    public Collection<C> getCandidates(Element<C> parentElement, Collection<String> dirtyAssets) {
        DependencyStrategyHelper<C> helper = new DependencyStrategyHelper<C>(parentElement);
        return helper.getCandidates(dirtyAssets);
    }

    public StrategyGenerator<C> getGenerator() {
        return new Generator<>();
    }

    /**
     * This class generates Strategy objects of the appropriate type.
     *
     * @param <C> is the type of {@link Element}s to which the Strategy applies.
     */
    public static class Generator<C extends Element> implements StrategyGenerator<C> {
        @Override
        public Strategy<C> generate() {
            return new RandomStrategy<>();
        }
    }
}
