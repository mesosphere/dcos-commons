package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.Element;

import java.util.*;

/**
 * {@code RandomStrategy} extends {@link Strategy}, by providing a random block selection strategy.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public class RandomStrategy<C extends Element> extends InterruptableStrategy<C> {
    // TODO: RandomStrategy should only return a Block if nothing is InProgress.

    @Override
    public Collection getCandidates(Element parentElement, Collection dirtyAssets) {
        DependencyStrategyHelper strategyHelper = new DependencyStrategyHelper(parentElement);
        List candidates = new ArrayList(strategyHelper.getCandidates(dirtyAssets));
        Collections.shuffle(candidates);
        Optional candidateOptional = candidates.stream().findFirst();

        return candidateOptional.isPresent() ? Arrays.asList(candidateOptional.get()) : Collections.emptyList();
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
