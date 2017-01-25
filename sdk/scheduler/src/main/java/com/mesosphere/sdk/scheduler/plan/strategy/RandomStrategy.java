package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.Step;

import java.util.*;

/**
 * {@code RandomStrategy} extends {@link Strategy}, by providing a random {@link Step}selection strategy.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public class RandomStrategy<C extends Element> extends InterruptibleStrategy<C> {

    @Override
    public Collection<C> getCandidates(Collection<C> elements, Collection<String> dirtyAssets) {
        // No prerequites configured, with random selection of one entry from the resulting candidates:
        List<C> candidates = new ArrayList<>(
                new DependencyStrategyHelper<>(elements).getCandidates(isInterrupted(), dirtyAssets));
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
