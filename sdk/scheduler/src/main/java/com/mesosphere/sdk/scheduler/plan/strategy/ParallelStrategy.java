package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;

import java.util.Collection;

/**
 * A ParallelStrategy indicates that all Elements are candidates simultaneously.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public class ParallelStrategy<C extends Element> extends InterruptibleStrategy<C> {

    @Override
    public Collection<C> getCandidates(Collection<C> elements, Collection<String> dirtyAssets) {
        // No prerequites configured:
        return new DependencyStrategyHelper<C>(elements).getCandidates(isInterrupted(), dirtyAssets);
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
            return new ParallelStrategy<C>();
        }
    }
}
