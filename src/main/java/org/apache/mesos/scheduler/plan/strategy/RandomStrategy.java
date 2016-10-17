package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.Element;

import java.util.*;

/**
 * {@code RandomStrategy} extends {@link Strategy}, by providing a random block selection strategy.
 */
public class RandomStrategy extends InterruptableStrategy {
    // TODO: RandomStrategy should only return a Block if nothing is InProgress.

    @Override
    public Collection getCandidates(Element parentElement, Collection dirtyAssets) {
        DependencyStrategyHelper strategyHelper = new DependencyStrategyHelper(parentElement);
        List candidates = new ArrayList(strategyHelper.getCandidates(dirtyAssets));
        Collections.shuffle(candidates);
        Optional candidateOptional = candidates.stream().findFirst();

        return candidateOptional.isPresent() ? Arrays.asList(candidateOptional.get()) : Collections.emptyList();
    }
}
