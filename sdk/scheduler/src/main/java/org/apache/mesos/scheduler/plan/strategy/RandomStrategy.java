package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.Element;
import org.apache.mesos.scheduler.plan.Step;

import java.util.*;

/**
 * {@code RandomStrategy} extends {@link Strategy}, by providing a random {@link Step}selection strategy.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
@SuppressWarnings("rawtypes")
public class RandomStrategy<C extends Element> extends InterruptibleStrategy<C> {

    @Override
    public Collection<C> getCandidates(Element<C> parentElement, Collection<String> dirtyAssets) {
        DependencyStrategyHelper<C> strategyHelper = new DependencyStrategyHelper<>(parentElement);
        List<C> candidates = new ArrayList<>(strategyHelper.getCandidates(dirtyAssets));
        Collections.shuffle(candidates);
        Optional<C> candidateOptional = candidates.stream().findFirst();

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
